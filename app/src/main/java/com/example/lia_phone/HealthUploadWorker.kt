package com.example.lia_phone

import android.content.Context
import android.util.Log
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.lia_phone.model.DatedTimeValue
import com.example.lia_phone.model.HealthData
import com.example.lia_phone.network.HeartRateData
import com.example.lia_phone.network.sendDataToServer
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

import com.example.lia_phone.model.HealthDataEncrypted
import com.example.lia_phone.network.HeartRateDataEncrypted
import com.example.lia_phone.util.EncryptionUtils


class HealthUploadWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val manager = HealthConnectManager(applicationContext)

        if (!manager.hasAllPermissions()) {
            Log.w("HealthUploadWorker", "❌ Health Connect 권한 없음 → 데이터 전송 스킵")
            return Result.success()
        }

        val zoneId = ZoneId.systemDefault()
        val nowZoned = ZonedDateTime.now(zoneId)
        val end = nowZoned.withMinute(0).withSecond(0).withNano(0).toInstant()
        val start = end.minus(1, ChronoUnit.HOURS)

        fun isWithinRange(t: Instant): Boolean =
            !t.isBefore(start) && !t.isAfter(end.minusMillis(1))

        fun roundToHour(instant: Instant): Pair<String, String> {
            val zdt = instant.atZone(zoneId).withMinute(0).withSecond(0).withNano(0)
            return zdt.toLocalDate().toString() to zdt.toLocalTime().toString().substring(0, 5)
        }

        try {
            val prefs = applicationContext.getSharedPreferences("appPrefs", Context.MODE_PRIVATE)
            val currentWindowKey = "${start.epochSecond}-${end.epochSecond}"
            val lastWindow = prefs.getString("lastUploadWindow", null)

            if (currentWindowKey == lastWindow) {
                Log.w("HealthUploadWorker", "⏹ 이미 전송된 구간 → 중복 전송 방지")
                return Result.success()
            }

            val steps = manager.readStepCounts()
                .filter { isWithinRange(it.startTime) }
                .groupBy { roundToHour(it.startTime) }
                .map { (key, records) ->
                    val (date, time) = key
                    val total = records.sumOf { it.count }.toInt()
                    DatedTimeValue(date, time, total)
                }

            val calories = manager.readCaloriesBurned()
                .filter { isWithinRange(it.startTime) }
                .groupBy { roundToHour(it.startTime) }
                .map { (key, records) ->
                    val (date, time) = key
                    val total = records.sumOf { it.energy.inKilocalories }
                    DatedTimeValue(date, time, total)
                }

            val distances = manager.readDistanceWalked()
                .filter { isWithinRange(it.startTime) }
                .groupBy { roundToHour(it.startTime) }
                .map { (key, records) ->
                    val (date, time) = key
                    val total = records.sumOf { it.distance.inMeters }
                    DatedTimeValue(date, time, total)
                }

            val heartRates = manager.readHeartRates().flatMap { record ->
                record.samples.filter { isWithinRange(it.time) }.map {
                    val (date, time) = roundToHour(it.time)
                    HeartRateData(it.beatsPerMinute.toDouble().toString(), date, time)
                }
            }

            val groupedHeartRate = heartRates.groupBy { it.date to it.time }

            val heartRateStats = groupedHeartRate.flatMap { (key, list) ->
                val (date, time) = key
                val values = list.map { it.bpm.toDoubleOrNull() ?: 0.0 }

                listOf(
                    HeartRateData(values.average().toString(), date, time),
                    HeartRateData((values.maxOrNull() ?: 0.0).toString(), date, time),
                    HeartRateData((values.minOrNull() ?: 0.0).toString(), date, time)
                )
            }

            val sleep = manager.readSleepSessions().filter { isWithinRange(it.startTime) }

            val deep = mutableListOf<DatedTimeValue<Long>>()
            val rem = mutableListOf<DatedTimeValue<Long>>()
            val light = mutableListOf<DatedTimeValue<Long>>()
            var totalSleepMinutes = 0L

            sleep.forEach { session ->
                totalSleepMinutes += Duration.between(session.startTime, session.endTime).toMinutes()
                session.stages.forEach { stage ->
                    if (!isWithinRange(stage.startTime)) return@forEach
                    val (date, time) = roundToHour(stage.startTime)
                    val duration = Duration.between(stage.startTime, stage.endTime).toMinutes()
                    when (stage.stage) {
                        SleepSessionRecord.STAGE_TYPE_DEEP -> deep.add(DatedTimeValue(date, time, duration))
                        SleepSessionRecord.STAGE_TYPE_REM -> rem.add(DatedTimeValue(date, time, duration))
                        SleepSessionRecord.STAGE_TYPE_LIGHT -> light.add(DatedTimeValue(date, time, duration))
                    }
                }
            }

            val healthData = HealthData(
                stepData = steps,
                heartRateData = heartRateStats,
                caloriesBurnedData = calories,
                distanceWalked = distances,
                totalSleepMinutes = totalSleepMinutes,
                deepSleepMinutes = deep,
                remSleepMinutes = rem,
                lightSleepMinutes = light
            )


            val encryptedHealthData = HealthDataEncrypted(
                stepData = steps.map {
                    DatedTimeValue(it.date, it.time, EncryptionUtils.encrypt(it.value.toString()))
                },
                heartRateData = heartRateStats.map {
                    HeartRateDataEncrypted(EncryptionUtils.encrypt(it.bpm), it.date, it.time)
                },
                caloriesBurnedData = calories.map {
                    DatedTimeValue(it.date, it.time, EncryptionUtils.encrypt(it.value.toString()))
                },
                distanceWalked = distances.map {
                    DatedTimeValue(it.date, it.time, EncryptionUtils.encrypt(it.value.toString()))
                },
                totalSleepMinutes = EncryptionUtils.encrypt(totalSleepMinutes.toString()),
                deepSleepMinutes = deep.map {
                    DatedTimeValue(it.date, it.time, EncryptionUtils.encrypt(it.value.toString()))
                },
                remSleepMinutes = rem.map {
                    DatedTimeValue(it.date, it.time, EncryptionUtils.encrypt(it.value.toString()))
                },
                lightSleepMinutes = light.map {
                    DatedTimeValue(it.date, it.time, EncryptionUtils.encrypt(it.value.toString()))
                }
            )

            sendDataToServer(applicationContext, encryptedHealthData)

            val formattedTime = nowZoned.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
            with(prefs.edit()) {
                putString("lastUploadTime", formattedTime)
                putString("lastUploadWindow", currentWindowKey)
                apply()
            }

            Log.d("HealthUploadWorker", "✅ 전송 완료: $formattedTime")
            return Result.success()

        } catch (e: Exception) {
            Log.e("HealthUploadWorker", "전송 실패: ${e.message}", e)

            val errorTime = ZonedDateTime.now(zoneId).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
            val prefs = applicationContext.getSharedPreferences("appPrefs", Context.MODE_PRIVATE)
            with(prefs.edit()) {
                putString("lastUploadErrorTime", errorTime)
                apply()
            }

            return Result.retry()
        }
    }
}