package com.example.lia_phone

import android.app.Activity
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.*
import androidx.lifecycle.lifecycleScope
import com.example.lia_phone.model.DatedTimeValue
import com.example.lia_phone.model.HealthData
import com.example.lia_phone.network.HeartRateData
import com.example.lia_phone.network.sendDataToServer
import com.example.lia_phone.schedule.AlarmScheduler
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import com.example.lia_phone.model.HealthDataEncrypted
import com.example.lia_phone.network.HeartRateDataEncrypted
import com.example.lia_phone.util.EncryptionUtils

// 구글 로그인
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import com.example.lia_phone.network.LoginRequest
import com.example.lia_phone.network.LoginResponse
import com.example.lia_phone.network.RetrofitClient
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException


class MainActivity : ComponentActivity() {
    // 구글 로그인 관련
    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var googleSignInLauncher: ActivityResultLauncher<Intent>
    private val RC_SIGN_IN = 1001


    private val healthPermissions = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(DistanceRecord::class),
        HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class)
    )

    private val permissionLauncher =
        registerForActivityResult(
            PermissionController.createRequestPermissionResultContract()
        ) { granted ->
            if (granted.containsAll(healthPermissions)) {
                Log.d("MainActivity", "✅ 사용자 권한 허용 완료")
            } else {
                Log.w("MainActivity", "❌ 사용자 권한 일부 또는 전체 거부됨")
            }
        }

    private lateinit var healthConnectManager: HealthConnectManager
    private var healthData by mutableStateOf<HealthData?>(null)
    private var lastUploadTime by mutableStateOf<String?>(null)

    override fun onStop() {
        super.onStop()
        AlarmScheduler.scheduleNextAlarm(this)  // 앱이 백그라운드로 가도 알람 유지
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        healthConnectManager = HealthConnectManager(this)

        // ✅ 권한 체크 및 요청
        lifecycleScope.launch {
            val client = HealthConnectClient.getOrCreate(this@MainActivity)
            val granted = client.permissionController.getGrantedPermissions()
            if (!healthPermissions.all { it in granted }) {
                Log.w("MainActivity", "❗ Health Connect 권한 없음 → 요청 실행")
                permissionLauncher.launch(healthPermissions)
            } else {
                Log.d("MainActivity", "✅ Health Connect 권한 이미 있음")
            }
        }

        // 🔐 Google 로그인 클라이언트 설정
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestIdToken("829026060536-f7dpc16930esthgnn97soleggvmv3o16.apps.googleusercontent.com")
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        googleSignInLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                try {
                    val account: GoogleSignInAccount = task.getResult(ApiException::class.java)
                    val email = account.email
                    val credential = account.idToken

                    if (email == null || credential == null) {
                        Log.w("GoogleLogin", "❗ email 또는 credential 이 null 입니다.")
                        return@registerForActivityResult
                    }

                    Log.d("GoogleLogin", "📤 sendLoginToServer 호출 지전: $email")
                    sendLoginToServer(email, credential)

                } catch (e: ApiException) {
                    Log.w("GoogleLogin", "❌ 로그인 실패: ${e.statusCode}", e)
                }
            }
        }



        // ⏰ 정각 알람 등록
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (alarmManager.canScheduleExactAlarms()) {
                AlarmScheduler.scheduleNextAlarm(this)
            } else {
                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                startActivity(intent)
            }
        } else {
            AlarmScheduler.scheduleNextAlarm(this)
        }

        // 전송 시간 로드
        val prefs = getSharedPreferences("appPrefs", Context.MODE_PRIVATE)
        lastUploadTime = prefs.getString("lastUploadTime", null)

        // 데이터 불러오기
        fetchAndDisplayLocalData()

        // ✅ UI
        setContent {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = "LIA",
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    ),
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 32.dp)
                )

                lastUploadTime?.let {
                    Text("📤 마지막 전송: $it - 완료", color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold)
                }

                healthData?.let { data ->
                    DataSection("걸음수", data.stepData) { "${it.value}보" }
//                    DataSection("칼로리 소모량", data.caloriesBurnedData) { "${"%.2f".format(it.value)} kcal" }
//                    DataSection("이동 거리", data.distanceWalked) { "${"%.2f".format(it.value)} m" }

                    DataSection("칼로리 소모량", data.caloriesBurnedData) {
                        "${"%.2f".format(it.value.toString().toDouble())} kcal"
                    }
                    DataSection("이동 거리", data.distanceWalked) {
                        "${"%.2f".format(it.value.toString().toDouble())} m"
                    }

                    Text("총 수면 시간: ${data.totalSleepMinutes}분", fontWeight = FontWeight.Bold)
                    DataSection("깊은 수면", data.deepSleepMinutes) { "${it.value}분" }
                    DataSection("렘 수면", data.remSleepMinutes) { "${it.value}분" }
                    DataSection("얕은 수면", data.lightSleepMinutes) { "${it.value}분" }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text("심박수 (평균 / 최대 / 최소)", fontWeight = FontWeight.Bold)
                    data.heartRateData.forEach {
//                        Text("${it.date} ${it.time} ${"%.1f".format(it.bpm)} bpm")
                        Text("${it.date} ${it.time} ${"%.1f".format(it.bpm.toDoubleOrNull() ?: 0.0)} bpm")

                    }
                }

                Button(
                    onClick = {
                        val signInIntent = googleSignInClient.signInIntent
                        googleSignInLauncher.launch(signInIntent)
                    },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text("🔐 Google 로그인")
                }

                Button(
                    onClick = {
                        googleSignInClient.signOut().addOnCompleteListener {
                            Toast.makeText(this@MainActivity, "로그아웃 되었습니다.", Toast.LENGTH_SHORT).show()
                            Log.d("GoogleLogin", "사용자 로그아웃 완료")
                        }
                    },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text("🚪 Google 로그아웃")
                }

                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { permissionLauncher.launch(healthPermissions) },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text("권한 요청 및 업로드 시작")
                }

                Button(
                    onClick = { sendRecent2DaysData() },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text("📤 최근 2일 데이터 수동 전송")
                }

            }
        }
    }

    // ✅ 서버 전송용: 암호화 적용
    private fun sendRecent2DaysData() {
        lifecycleScope.launch {
            try {
                val zoneId = ZoneId.systemDefault()
                val now = Instant.now()
                val start = now.minus(2, ChronoUnit.DAYS)
                val end = now

                fun isWithinRange(t: Instant): Boolean =
                    !t.isBefore(start) && !t.isAfter(end)

                fun roundToHour(instant: Instant): Pair<String, String> {
                    val zdt = instant.atZone(zoneId).withMinute(0).withSecond(0).withNano(0)
                    return zdt.toLocalDate().toString() to zdt.toLocalTime().toString().substring(0, 5)
                }

                val manager = HealthConnectManager(applicationContext)

                if (!manager.hasAllPermissions()) {
                    Log.w("MainActivity", "❌ 권한 없음 → 수동 전송 중단")
                    return@launch
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
                        HeartRateData(it.beatsPerMinute.toString(), date, time)
                    }
                }

                val groupedHeartRate = heartRates.groupBy { it.date to it.time }

                val heartRateStats = groupedHeartRate.flatMap { (key, list) ->
                    val (date, time) = key
                    val values = list.map { it.bpm.toDoubleOrNull() ?: 0.0 }

                    listOf(
                        HeartRateDataEncrypted(EncryptionUtils.encrypt(values.average().toString()), date, time),
                        HeartRateDataEncrypted(EncryptionUtils.encrypt((values.maxOrNull() ?: 0.0).toString()), date, time),
                        HeartRateDataEncrypted(EncryptionUtils.encrypt((values.minOrNull() ?: 0.0).toString()), date, time)
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

                val encryptedHealthData = HealthDataEncrypted(
                    stepData = steps.map {
                        DatedTimeValue(it.date, it.time, EncryptionUtils.encrypt(it.value.toString()))
                    },
                    heartRateData = heartRateStats,
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
                Log.d("MainActivity", "📤 최근 2일 데이터 수동 전송 완료")

            } catch (e: Exception) {
                Log.e("MainActivity", "❌ 수동 전송 중 에러 발생", e)
            }
        }
    }

    @Composable
    private fun <T> DataSection(title: String, items: List<DatedTimeValue<T>>, valueFormatter: (DatedTimeValue<T>) -> String) {
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = title, fontWeight = FontWeight.Bold)
        items.forEach {
            Text("${it.date} ${it.time} ${valueFormatter(it)}")
        }
    }

    // ✅ UI 출력용: 암호화 없이 평문 처리
    private fun fetchAndDisplayLocalData() {
        lifecycleScope.launch {
            try {
                val zoneId = ZoneId.systemDefault()
                fun roundToHour(instant: Instant): Pair<String, String> {
                    val zdt = instant.atZone(zoneId).withMinute(0).withSecond(0).withNano(0)
                    return zdt.toLocalDate().toString() to zdt.toLocalTime().toString().substring(0, 5)
                }

                val steps = healthConnectManager.readStepCounts()
                    .groupBy { roundToHour(it.startTime) }
                    .map { (key, records) ->
                        val (date, time) = key
                        val total = records.sumOf { it.count }.toInt()
                        DatedTimeValue(date, time, total)
                    }

                val calories = healthConnectManager.readCaloriesBurned()
                    .groupBy { roundToHour(it.startTime) }
                    .map { (key, records) ->
                        val (date, time) = key
                        val total = records.sumOf { it.energy.inKilocalories }
                        DatedTimeValue(date, time, total)
                    }

                val distances = healthConnectManager.readDistanceWalked()
                    .groupBy { roundToHour(it.startTime) }
                    .map { (key, records) ->
                        val (date, time) = key
                        val total = records.sumOf { it.distance.inMeters }
                        DatedTimeValue(date, time, total)
                    }

                val heartRates = healthConnectManager.readHeartRates().flatMap { record ->
                    record.samples.map {
                        val (date, time) = roundToHour(it.time)
                        HeartRateData(it.beatsPerMinute.toString(), date, time)
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

                healthData = HealthData(
                    stepData = steps,
                    caloriesBurnedData = calories,
                    distanceWalked = distances,
                    totalSleepMinutes = 0,
                    deepSleepMinutes = listOf(),
                    remSleepMinutes = listOf(),
                    lightSleepMinutes = listOf(),
                    heartRateData = heartRateStats
                )
            } catch (e: Exception) {
                Log.e("FETCH_DATA", "에러 발생", e)
            }
        }
    }

    // 구글 로그인 이메일 정보 서버로 전송
    private fun sendLoginToServer(email: String, credential: String) {
        val request = LoginRequest(email, credential)
        val json = """{"email": "$email", "credential": "$credential"}"""

        Log.d("LoginPost", "📤 로그인 정보 전송 중: $json")

        RetrofitClient.api.sendLogin(request).enqueue(object : retrofit2.Callback<LoginResponse> {
            override fun onResponse(call: retrofit2.Call<LoginResponse>, response: retrofit2.Response<LoginResponse>) {
                Log.d("LoginPost", "✅ 전체 응답 raw: $response")

                val responseBody = response.body()
                Log.d("LoginPost", "✅ 응답 바디: $responseBody")

                if (response.isSuccessful && responseBody != null) {
                    val jwtToken = responseBody.data.token

                    if (!jwtToken.isNullOrBlank()) {
                        val prefs = getSharedPreferences("auth", Context.MODE_PRIVATE)
                        prefs.edit().putString("jwt_token", jwtToken).apply()
                        Log.d("LoginPost", "✅ JWT 저장 완료: $jwtToken")
                    } else {
                        Log.w("LoginPost", "⚠️ JWT 토큰이 응답에 없습니다.")
                    }

                    Log.d("LoginPost", "✅ 로그인 전송 성공: $email")
                } else {
                    Log.w("LoginPost", "⚠️ 로그인 전송 실패 - 응답 코드: ${response.code()}, 내용: $json")
                }
            }

            override fun onFailure(call: retrofit2.Call<LoginResponse>, t: Throwable) {
                Log.e("LoginPost", "❌ 로그인 전송 중 네트워크 오류: $json", t)
            }
        })
    }


}
