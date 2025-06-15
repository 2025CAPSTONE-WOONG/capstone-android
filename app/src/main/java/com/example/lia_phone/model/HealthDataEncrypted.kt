package com.example.lia_phone.model

import com.example.lia_phone.network.HeartRateDataEncrypted
import com.example.lia_phone.model.DatedTimeValue

data class HealthDataEncrypted(
    val stepData: List<DatedTimeValue<String>>,
    val heartRateData: List<HeartRateDataEncrypted>,
    val caloriesBurnedData: List<DatedTimeValue<String>>,
    val distanceWalked: List<DatedTimeValue<String>>,
    val totalSleepMinutes: String,
    val deepSleepMinutes: List<DatedTimeValue<String>>,
    val remSleepMinutes: List<DatedTimeValue<String>>,
    val lightSleepMinutes: List<DatedTimeValue<String>>
)
