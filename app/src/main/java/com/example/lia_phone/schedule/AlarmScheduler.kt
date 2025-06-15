package com.example.lia_phone.schedule

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.annotation.RequiresPermission
import java.time.ZonedDateTime

object AlarmScheduler {

    @RequiresPermission(Manifest.permission.SCHEDULE_EXACT_ALARM)
    fun scheduleNextAlarm(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val intent = Intent(context, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val now = ZonedDateTime.now()
        val nextHour = now.plusHours(1).withMinute(0).withSecond(0).withNano(0)
        val triggerAtMillis = nextHour.toInstant().toEpochMilli()
        val millis = nextHour.toInstant().toEpochMilli()
        Log.d("AlarmScheduler", "🔔 다음 정각 예약: $nextHour")


//        // 🔁 테스트용: 지금으로부터 1분 뒤
//        val testTime = now.plusMinutes(1)
//        val millis = testTime.toInstant().toEpochMilli()
//        Log.d("AlarmScheduler", "🧪 테스트용 알람 예약: $testTime")

        // ✅ Android 12 이상에서는 정확한 알람 허용 여부 확인 필요
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    millis,  // 테스트용 알람
                    pendingIntent
                )
            } else {
                Log.w("AlarmScheduler", "⚠ 정확한 알람 예약 권한 없음. 설정 필요")
                // 필요하다면 설정 유도용 인텐트도 추가 가능
                // val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                // context.startActivity(intent)
            }
        } else {
            // Android 11 이하
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                millis,  // 테스트용 알람
                pendingIntent
            )
        }
    }
}
