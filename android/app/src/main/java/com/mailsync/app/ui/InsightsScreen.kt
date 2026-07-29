package com.mailsync.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mailsync.app.data.OtpRepository
import com.mailsync.app.data.SenderCount
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

class InsightsViewModel(private val repository: OtpRepository) : ViewModel() {
    private val _topSenders = MutableStateFlow<List<SenderCount>>(emptyList())
    val topSenders: StateFlow<List<SenderCount>> = _topSenders.asStateFlow()

    private val _weeklyCounts = MutableStateFlow<List<Int>>(List(7) { 0 })
    val weeklyCounts: StateFlow<List<Int>> = _weeklyCounts.asStateFlow()

    private val _totalOtps = MutableStateFlow(0)
    val totalOtps: StateFlow<Int> = _totalOtps.asStateFlow()
    
    private val _successRate = MutableStateFlow(100)
    val successRate: StateFlow<Int> = _successRate.asStateFlow()

    init {
        viewModelScope.launch {
            repository.getTopSenders().collectLatest {
                _topSenders.value = it
            }
        }
        viewModelScope.launch {
            repository.getTotalOtpsCount().collectLatest {
                _totalOtps.value = it
                _successRate.value = repository.getSuccessRate()
            }
        }
        viewModelScope.launch {
            val sevenDaysAgo = Instant.now().minus(7, ChronoUnit.DAYS).toEpochMilli()
            repository.getOtpsSince(sevenDaysAgo).collectLatest { otps ->
                val counts = IntArray(7)
                val today = Instant.now().atZone(ZoneId.systemDefault()).toLocalDate()
                
                otps.forEach { otp ->
                    val otpDate = Instant.ofEpochMilli(otp.receivedAt).atZone(ZoneId.systemDefault()).toLocalDate()
                    val daysBetween = ChronoUnit.DAYS.between(otpDate, today).toInt()
                    if (daysBetween in 0..6) {
                        counts[6 - daysBetween]++
                    }
                }
                _weeklyCounts.value = counts.toList()
            }
        }
    }

    class Factory(private val repository: OtpRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return InsightsViewModel(repository) as T
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InsightsScreen(viewModel: InsightsViewModel) {
    val topSenders by viewModel.topSenders.collectAsState()
    val weeklyCounts by viewModel.weeklyCounts.collectAsState()
    val totalOtps by viewModel.totalOtps.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Insights") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            item {
                Text("Your usage insights", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Total OTPs", fontSize = 14.sp)
                        Text(totalOtps.toString(), fontSize = 32.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(16.dp))
                        WeeklyBarChart(weeklyCounts)
                    }
                }
            }

            item {
                Text("Top Senders", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
            }

            items(topSenders) { senderCount ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.primary)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(senderCount.sender, fontWeight = FontWeight.Medium)
                    }
                    Text(
                        "${senderCount.count} (${if (totalOtps > 0) (senderCount.count * 100 / totalOtps) else 0}%)",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun WeeklyBarChart(counts: List<Int>) {
    val barColor = MaterialTheme.colorScheme.primary
    val maxCount = counts.maxOrNull()?.coerceAtLeast(1) ?: 1
    
    Canvas(modifier = Modifier
        .fillMaxWidth()
        .height(120.dp)
        .padding(top = 16.dp)) {
        
        val barWidth = size.width / (counts.size * 2)
        val spaceWidth = barWidth
        
        counts.forEachIndexed { index, count ->
            val barHeight = (count.toFloat() / maxCount) * size.height
            val startX = index * (barWidth + spaceWidth) + (spaceWidth / 2)
            
            drawRoundRect(
                color = barColor,
                topLeft = Offset(startX, size.height - barHeight),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
            )
        }
    }
}
