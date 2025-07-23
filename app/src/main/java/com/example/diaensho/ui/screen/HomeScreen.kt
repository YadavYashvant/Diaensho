package com.example.diaensho.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.diaensho.viewmodel.HomeViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.diaensho.data.db.entity.AppUsageStatEntity
import com.example.diaensho.data.db.entity.DiaryEntryEntity
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

@Composable
fun HomeScreen(
    homeViewModel: HomeViewModel = viewModel(),
    selectedDate: LocalDate,
    onDateChange: (LocalDate) -> Unit,
    onSearchClick: () -> Unit
) {
    val entries by homeViewModel.entries.collectAsStateWithLifecycle()
    val appUsageStats by homeViewModel.appUsageStats.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // Date selector
        DateSelector(
            selectedDate = selectedDate,
            onPreviousDay = { onDateChange(selectedDate.minusDays(1)) },
            onNextDay = { onDateChange(selectedDate.plusDays(1)) }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // App usage stats summary
        AppUsageSection(appUsageStats)

        Spacer(modifier = Modifier.height(16.dp))

        // Diary entries
        Text(
            text = "Diary Entries",
            style = MaterialTheme.typography.titleMedium
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(entries) { entry ->
                DiaryEntryCard(entry)
            }
        }

        // Search button
        Button(
            onClick = onSearchClick,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
        ) {
            Text("Search Entries")
        }
    }
}

@Composable
private fun DateSelector(
    selectedDate: LocalDate,
    onPreviousDay: () -> Unit,
    onNextDay: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPreviousDay) {
            Icon(Icons.AutoMirrored.Default.KeyboardArrowLeft, "Previous day")
        }

        Text(
            text = selectedDate.format(DateTimeFormatter.ofPattern("MMMM d, yyyy")),
            style = MaterialTheme.typography.titleMedium
        )

        IconButton(onClick = onNextDay) {
            Icon(Icons.AutoMirrored.Default.KeyboardArrowRight, "Next day")
        }
    }
}

@Composable
private fun AppUsageSection(stats: List<AppUsageStatEntity>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "App Usage Summary",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            stats.take(5).forEach { stat ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(stat.packageName.split(".").last())
                    Text(formatDuration(stat.totalTimeInForeground))
                }
            }
        }
    }
}

@Composable
private fun DiaryEntryCard(entry: DiaryEntryEntity) {
    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = entry.text,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = entry.timestamp.format(DateTimeFormatter.ofPattern("HH:mm")),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

private fun formatDuration(millis: Long): String {
    val hours = TimeUnit.MILLISECONDS.toHours(millis)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60
    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m"
        else -> "< 1m"
    }
}
