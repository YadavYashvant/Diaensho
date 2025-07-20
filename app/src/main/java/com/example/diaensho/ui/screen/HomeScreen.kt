package com.example.diaensho.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.diaensho.viewmodel.HomeViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDate

@Composable
fun HomeScreen(
    homeViewModel: HomeViewModel = viewModel(),
    selectedDate: LocalDate,
    onDateChange: (LocalDate) -> Unit
) {
    val entries by homeViewModel.entries.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // TODO: Replace with a Compose-based CalendarView
        Text("Selected date: $selectedDate")
        Button(onClick = { onDateChange(selectedDate.minusDays(1)) }) { Text("Previous Day") }
        Button(onClick = { onDateChange(selectedDate.plusDays(1)) }) { Text("Next Day") }
        Spacer(modifier = Modifier.height(16.dp))
        Text("Summary:")
        // TODO: Replace with actual summary display
        entries.forEach { entry ->
            Text("- ${entry.text}")
        }
    }
} 