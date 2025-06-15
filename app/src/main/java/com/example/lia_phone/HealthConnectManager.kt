package com.example.lia_phone

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.*
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Instant
import java.time.temporal.ChronoUnit

class HealthConnectManager(private val context: Context) {

    suspend fun hasAllPermissions(): Boolean {
        val client = healthConnectClient ?: return false
        val granted = client.permissionController.getGrantedPermissions()
        return permissions.all { it in granted }
    }


    val healthConnectClient: HealthConnectClient? = try {
        val status = HealthConnectClient.getSdkStatus(context)
        if (status == HealthConnectClient.SDK_AVAILABLE) {
            HealthConnectClient.getOrCreate(context)
        } else {
            Log.w("HealthConnectManager", "Health Connect not available. Status: $status")
            null
        }
    } catch (e: Exception) {
        Log.e("HealthConnectManager", "Health Connect init error: ${e.message}")
        null
    }

    // 요청할 권한 리스트
    val permissions = setOf(
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(DistanceRecord::class),
        HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(ExerciseSessionRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class),
    )

    // ✅ 현재 시간 저장
    private val now = Instant.now()

    // ✅ 7일 범위 유지
    private val startTime = now.minus(10, ChronoUnit.DAYS)

    // 심박수 데이터 읽기
    suspend fun readHeartRates(): List<HeartRateRecord> {
        val client = healthConnectClient ?: return emptyList()
        val request = ReadRecordsRequest(
            recordType = HeartRateRecord::class,
            timeRangeFilter = TimeRangeFilter.between(startTime, now)
        )
        return client.readRecords(request).records
    }

    // 걸음수 데이터 읽기
    suspend fun readStepCounts(): List<StepsRecord> {
        val client = healthConnectClient ?: return emptyList()
        val request = ReadRecordsRequest(
            recordType = StepsRecord::class,
            timeRangeFilter = TimeRangeFilter.between(startTime, now)
        )
        return client.readRecords(request).records
    }

    // 총 소모 칼로리 데이터 읽기
    suspend fun readCaloriesBurned(): List<TotalCaloriesBurnedRecord> {
        val client = healthConnectClient ?: return emptyList()
        val request = ReadRecordsRequest(
            recordType = TotalCaloriesBurnedRecord::class,
            timeRangeFilter = TimeRangeFilter.between(startTime, now)
        )
        return client.readRecords(request).records
    }

    // 이동 거리 데이터 읽기
    suspend fun readDistanceWalked(): List<DistanceRecord> {
        val client = healthConnectClient ?: return emptyList()
        val request = ReadRecordsRequest(
            recordType = DistanceRecord::class,
            timeRangeFilter = TimeRangeFilter.between(startTime, now)
        )
        return client.readRecords(request).records
    }

    // 활동 칼로리 데이터 읽기
//    suspend fun readActiveCaloriesBurned(): List<ActiveCaloriesBurnedRecord> {
//        val client = healthConnectClient ?: return emptyList()
//        val request = ReadRecordsRequest(
//            recordType = ActiveCaloriesBurnedRecord::class,
//            timeRangeFilter = TimeRangeFilter.between(startTime, now)
//        )
//        return client.readRecords(request).records
//    }

    // 운동 세션 데이터 읽기
//    suspend fun readExerciseSessions(): List<ExerciseSessionRecord> {
//        val client = healthConnectClient ?: return emptyList()
//        val request = ReadRecordsRequest(
//            recordType = ExerciseSessionRecord::class,
//            timeRangeFilter = TimeRangeFilter.between(startTime, now)
//        )
//        return client.readRecords(request).records
//    }

    // 수면 세션 데이터 읽기
    suspend fun readSleepSessions(): List<SleepSessionRecord> {
        val client = healthConnectClient ?: return emptyList()
        val request = ReadRecordsRequest(
            recordType = SleepSessionRecord::class,
            timeRangeFilter = TimeRangeFilter.between(startTime, now)
        )
        return client.readRecords(request).records
    }
}
