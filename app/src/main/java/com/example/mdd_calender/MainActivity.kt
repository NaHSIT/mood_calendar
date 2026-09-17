package com.example.mdd_calender

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mdd_calender.data.MoodDatabase
import com.example.mdd_calender.data.MoodRepository
import com.example.mdd_calender.integration.app.AppCareServices
import com.example.mdd_calender.ui.MoodViewModel
import com.example.mdd_calender.ui.navigation.AppNavigation
import com.example.mdd_calender.ui.theme.Mdd_calenderTheme
import androidx.compose.material.icons.filled.Add

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Initialize DB and Repository
        val db = MoodDatabase.getDatabase(applicationContext)
        val repository = MoodRepository(db.moodDao(), db.anniversaryDao())
        val preferences = com.example.mdd_calender.data.MoodPreferences(applicationContext)
        val careServices = AppCareServices(applicationContext)
        
        setContent {
            Mdd_calenderTheme {
                val viewModel: MoodViewModel = viewModel(
                    factory = MoodViewModel.Factory(repository, preferences)
                )
                
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation(viewModel = viewModel, careServices = careServices)
                }
            }
        }
    }
}
