package com.example.lia_phone.network

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST

interface ApiService {
    @POST("/users/google")  // ← 서버 API 경로에 맞게 수정
    fun sendLogin(@Body loginRequest: LoginRequest): Call<LoginResponse>
}
