package com.example.lia_phone.schedule

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import com.example.lia_phone.HealthUploadWorker

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d("AlarmReceiver", "📢 정각 알람 수신됨, 서버로 데이터 전송 시작")

        // 서버 전송용 Worker 실행 변경전
//        WorkManager.getInstance(context).enqueue(
//            OneTimeWorkRequestBuilder<HealthUploadWorker>().build()
//        )

        // 변경 후
        val request = OneTimeWorkRequestBuilder<HealthUploadWorker>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()

        WorkManager.getInstance(context).enqueue(request)

        // 다음 정각 알람 예약
        AlarmScheduler.scheduleNextAlarm(context)
    }
}
