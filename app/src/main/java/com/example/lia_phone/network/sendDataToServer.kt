package com.example.lia_phone.network

import android.content.Context
import android.util.Log
import com.example.lia_phone.model.HealthDataEncrypted
import com.google.gson.Gson
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

fun sendDataToServer(context: Context, healthData: HealthDataEncrypted) {
    val gson = Gson()
    val json = gson.toJson(healthData)

    // 🔍 전송 전 JSON 확인 로그
    Log.d("API", "📦 전송 JSON:\n$json")


    val requestBody = json.toRequestBody("application/json".toMediaTypeOrNull())

    // ✅ 저장된 토큰 불러오기
    val prefs = context.getSharedPreferences("auth", Context.MODE_PRIVATE)
    val jwtToken = prefs.getString("jwt_token", null)

    val requestBuilder = Request.Builder()
        .url("http://15.165.19.114:3000/data/receive")
//        .url("http://172.20.10.4:5000/data/receive") // ⚠️ IP/Port 꼭 실제로 수정 -> TEST
//        .url("http://192.168.35.167:5000/data/receive") // ⚠️ IP/Port 꼭 실제로 수정 -> 집
        .post(requestBody)

    if (!jwtToken.isNullOrEmpty()) {
        requestBuilder.addHeader("Authorization", "Bearer $jwtToken")
    }

    val request = requestBuilder.build()

    OkHttpClient().newCall(request).enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            Log.e("API", "❌ 전송 실패", e)
        }

        override fun onResponse(call: Call, response: Response) {
            val responseBody = response.body?.string()
            Log.d("API", "✅ 전송 성공: ${response.code}, 응답: $responseBody")
        }
    })
}

