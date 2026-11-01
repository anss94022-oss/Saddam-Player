package com.sdm.videoplayer.ui.lists

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sdm.videoplayer.data.db.PlaylistWithCount
import com.sdm.videoplayer.ui.common.EmptyState
import com.sdm.videoplayer.ui.theme.SdmColors

@Composable
fun PlaylistsScreen(
    playlists: List<PlaylistWithCount>,
    onCreate: (String) -> Unit,
    onOpen: (PlaylistWithCount) -> Unit,
    onDelete: (PlaylistWithCount) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }

    Box(Modifier.fillMaxSize()) {
        if (playlists.isEmpty()) {
            EmptyState(
                title = "لا توجد قوائم تشغيل",
                subtitle = "أنشئ قائمة وضع فيها فيديوهاتك المفضلة بترتيب تشغيل خاص."
            )
        } else {
            LazyColumn(contentPadding = PaddingValues(12.dp, 12.dp, 12.dp, 100.dp)) {
                items(playlists, key = { it.id }) { playlist ->
                    Card(
                        Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                            .clickable { onOpen(playlist) },
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Row(
                            Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        Brush.linearGradient(
                                            listOf(SdmColors.Violet.copy(alpha = .35f), SdmColors.NeonCyan.copy(alpha = .2f))
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.PlaylistPlay, null, tint = SdmColors.Violet)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    playlist.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    "${playlist.itemCount} فيديو",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { onDelete(playlist) }) {
                                Icon(Icons.Default.Delete, "حذف القائمة", tint = SdmColors.Magenta)
                            }
                        }
                    }
                }
            }
        }

        ExtendedFloatingActionButton(
            onClick = { name = ""; showDialog = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            containerColor = SdmColors.Violet,
            contentColor = androidx.compose.ui.graphics.Color.White,
            icon = { Icon(Icons.Default.Add, null) },
            text = { Text("قائمة جديدة") }
        )
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("قائمة تشغيل جديدة") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("اسم القائمة") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (name.isNotBlank()) {
                            onCreate(name.trim())
                            showDialog = false
                        }
                    }
                ) { Text("إنشاء") }
            },
            dismissButton = { TextButton(onClick = { showDialog = false }) { Text("إلغاء") } }
        )
    }
}

@Composable
fun PlaylistDetailScreen(
    title: String,
    entries: List<com.sdm.videoplayer.data.model.LibraryEntry>,
    onBack: () -> Unit,
    onPlayAll: (Int) -> Unit,
    onRemove: (String) -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) { Text("← رجوع") }
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (entries.isNotEmpty()) {
                TextButton(onClick = { onPlayAll(0) }) { Text("▶ تشغيل الكل") }
            }
        }
        if (entries.isEmpty()) {
            EmptyState(title = "القائمة فارغة", subtitle = "أضف فيديوهات من المكتبة عبر الضغط المطول على الفيديو.")
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 90.dp)) {
                items(entries.size) { index ->
                    com.sdm.videoplayer.ui.common.VideoRow(
                        entry = entries[index],
                        onClick = { onPlayAll(index) },
                        trailing = {
                            IconButton(onClick = { onRemove(entries[index].video.uri) }) {
                                Text("✕", color = SdmColors.Magenta)
                            }
                        }
                    )
                }
            }
        }
    }
}
