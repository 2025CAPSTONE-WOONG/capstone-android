package com.example.lia_phone.network

data class LoginResponse(
    val status: Int,
    val message: String,
    val data: LoginData
)

data class LoginData(
    val token: String,
    val user: LoginUser
)

data class LoginUser(
    val id: String,
    val email: String,
    val nickname: String
)
