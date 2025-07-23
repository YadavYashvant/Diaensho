package com.example.diaensho.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.diaensho.viewmodel.SearchViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.diaensho.data.db.entity.DiaryEntryEntity
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    searchViewModel: SearchViewModel = viewModel(),
    onBack: () -> Unit
) {
    val searchState by searchViewModel.searchState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Search Diary") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            SearchBar(
                query = searchState.query,
                onQueryChange = { searchViewModel.search(it) },
                onClear = { searchViewModel.clearSearch() },
                isLoading = searchState.isLoading
            )

            Spacer(modifier = Modifier.height(16.dp))

            when {
                searchState.error != null -> {
                    ErrorMessage(
                        error = searchState.error!!,
                        onRetry = { searchViewModel.search(searchState.query) }
                    )
                }
                searchState.isLoading -> {
                    LoadingIndicator()
                }
                searchState.results.isEmpty() && searchState.query.isNotEmpty() -> {
                    EmptyResults()
                }
                else -> {
                    SearchResults(results = searchState.results)
                }
            }
        }
    }
}

@Composable
private fun ErrorMessage(error: String, onRetry: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = error,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = onRetry) {
                Text("Retry")
            }
        }
    }
}

@Composable
private fun LoadingIndicator() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun EmptyResults() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text("No entries found")
    }
}

@Composable
private fun SearchResults(results: List<DiaryEntryEntity>) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(results, key = { it.id }) { entry ->
            SearchResultCard(
                text = entry.text,
                timestamp = entry.timestamp.format(DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm"))
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    isLoading: Boolean
) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text("Search diary entries...") },
        singleLine = true,
        trailingIcon = {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp
                )
            } else if (query.isNotEmpty()) {
                IconButton(onClick = onClear) {
                    Icon(Icons.Default.Clear, "Clear search")
                }
            }
        }
    )
}

@Composable
private fun SearchResultCard(
    text: String,
    timestamp: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = timestamp,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
