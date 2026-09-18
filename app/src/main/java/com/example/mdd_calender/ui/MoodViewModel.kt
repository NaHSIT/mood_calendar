package com.example.mdd_calender.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.mdd_calender.data.MoodRecord
import com.example.mdd_calender.data.AnniversaryRecord
import com.example.mdd_calender.data.MoodRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.LocalTime

import com.example.mdd_calender.data.MoodPreferences

class MoodViewModel(
    private val repository: MoodRepository,
    val preferences: MoodPreferences
) : ViewModel() {

    val iconConfig = preferences.configFlow

    private val _selectedDate = MutableStateFlow(LocalDate.now())
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

    private val _currentMonth = MutableStateFlow(YearMonth.now())
    val currentMonth: StateFlow<YearMonth> = _currentMonth.asStateFlow()

    private val _monthlyRecords = MutableStateFlow<List<MoodRecord>>(emptyList())
    val monthlyRecords: StateFlow<List<MoodRecord>> = _monthlyRecords.asStateFlow()

    private val _selectedDateRecords = MutableStateFlow<List<MoodRecord>>(emptyList())
    val selectedDateRecords: StateFlow<List<MoodRecord>> = _selectedDateRecords.asStateFlow()
    
    private val _anniversaries = MutableStateFlow<List<AnniversaryRecord>>(emptyList())
    val anniversaries: StateFlow<List<AnniversaryRecord>> = _anniversaries.asStateFlow()

    data class WeatherData(val condition: com.example.mdd_calender.data.WeatherCondition, val temperature: Double)
    private val _weatherData = MutableStateFlow<WeatherData?>(null)
    val weatherData: StateFlow<WeatherData?> = _weatherData.asStateFlow()

    private val _locationName = MutableStateFlow<String?>(null)
    val locationName: StateFlow<String?> = _locationName.asStateFlow()

    fun fetchLocationAndWeather(context: android.content.Context) {
        viewModelScope.launch {
            try {
                if (androidx.core.content.ContextCompat.checkSelfPermission(
                        context,
                        android.Manifest.permission.ACCESS_FINE_LOCATION
                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED ||
                    androidx.core.content.ContextCompat.checkSelfPermission(
                        context,
                        android.Manifest.permission.ACCESS_COARSE_LOCATION
                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                ) {
                    val fusedLocationClient = com.google.android.gms.location.LocationServices.getFusedLocationProviderClient(context)
                    fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                        if (location != null) {
                            val lat = location.latitude
                            val lon = location.longitude
                            
                            // Get city name
                            try {
                                val geocoder = android.location.Geocoder(context, java.util.Locale.getDefault())
                                val addresses = geocoder.getFromLocation(lat, lon, 1)
                                if (!addresses.isNullOrEmpty()) {
                                    val city = addresses[0].locality ?: addresses[0].subAdminArea ?: addresses[0].adminArea
                                    _locationName.value = city
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                            
                            // Fetch Weather
                            viewModelScope.launch {
                                try {
                                    val response = com.example.mdd_calender.data.WeatherService.api.getCurrentWeather(lat, lon)
                                    val condition = com.example.mdd_calender.data.mapWmoCodeToCondition(response.currentWeather.weathercode)
                                    _weatherData.value = WeatherData(condition, response.currentWeather.temperature)
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private var monthJob: Job? = null
    private var dateJob: Job? = null
    private var anniversaryJob: Job? = null

    init {
        loadRecordsForMonth(_currentMonth.value)
        loadRecordForDate(_selectedDate.value)
        loadAnniversaries()
    }

    fun selectDate(date: LocalDate) {
        _selectedDate.value = date
        _selectedDateRecords.value = emptyList() // Prevent residue from previous selections
        if (date.yearMonth != _currentMonth.value) {
            changeMonth(date.yearMonth)
        }
        loadRecordForDate(date)
    }

    fun changeMonth(yearMonth: YearMonth) {
        _currentMonth.value = yearMonth
        loadRecordsForMonth(yearMonth)
    }

    private fun loadRecordsForMonth(yearMonth: YearMonth) {
        monthJob?.cancel()
        monthJob = viewModelScope.launch {
            val formattedMonth = yearMonth.format(DateTimeFormatter.ofPattern("yyyy-MM"))
            repository.getMoodRecordsForMonth(formattedMonth).collect { records ->
                _monthlyRecords.value = records
            }
        }
    }

    private fun loadRecordForDate(date: LocalDate) {
        dateJob?.cancel()
        dateJob = viewModelScope.launch {
            val formattedDate = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
            repository.getMoodRecordsByDate(formattedDate).collect { records ->
                _selectedDateRecords.value = records
            }
        }
    }
    
    private fun loadAnniversaries() {
        anniversaryJob?.cancel()
        anniversaryJob = viewModelScope.launch {
            repository.getAllAnniversaries().collect { records ->
                _anniversaries.value = records
            }
        }
    }

    fun saveMoodWithContent(id: Int = 0, date: String, time: String, moodType: String, content: String?, imageUris: String?) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val record = MoodRecord(
                id = id,
                date = date,
                time = time,
                moodType = moodType,
                note = null,
                content = content,
                imageUris = imageUris,
                createdAt = resolveCreatedAt(id, _selectedDateRecords.value, now),
                updatedAt = now
            )
            repository.saveMoodRecord(record)
        }
    }
    
    fun saveAnniversary(title: String, targetDate: String, isCountdown: Boolean, colorHex: String) {
        viewModelScope.launch {
            repository.saveAnniversary(AnniversaryRecord(
                title = title,
                targetDate = targetDate,
                isCountdown = isCountdown,
                colorHex = colorHex
            ))
        }
    }
    
    fun deleteAnniversary(record: AnniversaryRecord) {
        viewModelScope.launch {
            repository.deleteAnniversary(record)
        }
    }

    class Factory(
        private val repository: MoodRepository,
        private val preferences: com.example.mdd_calender.data.MoodPreferences
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(MoodViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return MoodViewModel(repository, preferences) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}

internal fun resolveCreatedAt(id: Int, records: List<MoodRecord>, now: Long): Long =
    if (id == 0) now else records.firstOrNull { it.id == id }?.createdAt ?: now

val LocalDate.yearMonth: YearMonth
    get() = YearMonth.of(this.year, this.month)
