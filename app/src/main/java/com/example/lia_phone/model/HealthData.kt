package com.example.lia_phone.model

import com.example.lia_phone.network.HeartRateData

data class DatedTimeValue<T>(
    val date: String,
    val time: String,
    val value: T
)

data class HealthData(
    val stepData: List<DatedTimeValue<Int>>,
    val heartRateData: List<HeartRateData>,
    val caloriesBurnedData: List<DatedTimeValue<Double>>,
    val distanceWalked: List<DatedTimeValue<Double>>,
    val totalSleepMinutes: Long,
    val deepSleepMinutes: List<DatedTimeValue<Long>>,
    val remSleepMinutes: List<DatedTimeValue<Long>>,
    val lightSleepMinutes: List<DatedTimeValue<Long>>
)