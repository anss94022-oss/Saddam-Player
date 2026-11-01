package com.sdm.videoplayer.ui.library

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sdm.videoplayer.data.model.LibraryEntry
import com.sdm.videoplayer.data.model.SortOrder
import com.sdm.videoplayer.data.model.ViewMode
import com.sdm.videoplayer.ui.LibraryUiState
import com.sdm.videoplayer.ui.common.EmptyState
import com.sdm.videoplayer.ui.common.SectionHeader
import com.sdm.videoplayer.ui.common.ThumbnailBox
import com.sdm.videoplayer.ui.common.VideoGridCard
import com.sdm.videoplayer.ui.common.VideoRow
import com.sdm.videoplayer.ui.theme.SdmColors
import com.sdm.videoplayer.util.formatDuration

@Composable
fun LibraryScreen(
    state: LibraryUiState,
    onQueryChange: (String) -> Unit,
    onSortChange: (SortOrder) -> Unit,
    onViewModeChange: (ViewMode) -> Unit,
    onRefresh: () -> Unit,
    onAddFolder: () -> Unit,
    onGrantPermission: () -> Unit,
    onVideoClick: (List<LibraryEntry>, Int, String) -> Unit,
    onVideoLongClick: (LibraryEntry) -> Unit,
    modifier: Modifier = Modifier
) {
    var sortMenu by remember { mutableStateOf(false) }
    var folderFilter by remember { mutableStateOf<String?>(null) }

    val visible = remember(state.entries, folderFilter) {
        if (folderFilter == null) state.entries
        else state.entries.filter { it.video.folderLabel == folderFilter }
    }
    val normal = visible.filterNot { it.isHidden }
    val hidden = visible.filter { it.isHidden }

    Column(modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = state.query,
                onValueChange = onQueryChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("ابحث باسم الفيديو أو المجلد") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp)
            )
            Spacer(Modifier.width(4.dp))
            Box {
                IconButton(onClick = { sortMenu = true }) {
                    Icon(Icons.Default.Sort, "الفرز", tint = SdmColors.Violet)
                }
                DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                    SortOrder.entries.forEach { order ->
                        DropdownMenuItem(
                            text = { Text(sortLabel(order)) },
                            leadingIcon = {
                                if (state.sortOrder == order) Icon(Icons.Default.Check, null, tint = SdmColors.Violet)
                            },
                            onClick = { onSortChange(order); sortMenu = false }
                        )
                    }
                }
            }
            IconButton(onClick = {
                onViewModeChange(if (state.viewMode == ViewMode.GRID) ViewMode.LIST else ViewMode.GRID)
            }) {
                Icon(
                    if (state.viewMode == ViewMode.GRID) Icons.Default.ViewList else Icons.Default.Apps,
                    "طريقة العرض"
                )
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onRefresh) {
                Icon(Icons.Default.Refresh, null, Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("تحديث")
            }
            TextButton(onClick = onAddFolder) {
                Icon(Icons.Default.FolderOpen, null, Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("إضافة مجلد")
            }
            Spacer(Modifier.weight(1f))
            Text(
                "${normal.size} فيديو",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (state.folders.size > 1) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 2.dp)
            ) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    item {
                        FilterChip(
                            selected = folderFilter == null,
                            onClick = { folderFilter = null },
                            label = { Text("الكل") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = SdmColors.Violet.copy(alpha = .25f)
                            )
                        )
                    }
                    items(state.folders.size) { index ->
                        val folder = state.folders[index]
                        FilterChip(
                            selected = folderFilter == folder.name,
                            onClick = {
                                folderFilter = if (folderFilter == folder.name) null else folder.name
                            },
                            label = {
                                Text(
                                    "${folder.name} (${folder.videoCount})",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = SdmColors.Violet.copy(alpha = .25f)
                            )
                        )
                    }
                }
            }
        }

        when {
            state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = SdmColors.Violet)
            }

            normal.isEmpty() && hidden.isEmpty() && !state.hasPermission -> EmptyState(
                title = "صلاحية قراءة الفيديوهات مطلوبة",
                subtitle = "امنح التطبيق الصلاحية لعرض فيديوهات جهازك، أو أضف مجلدًا محددًا.",
                action = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = onGrantPermission) { Text("منح الصلاحية") }
                        TextButton(onClick = onAddFolder) { Text("إضافة مجلد") }
                    }
                }
            )

            normal.isEmpty() && hidden.isEmpty() -> EmptyState(
                title = "لا توجد فيديوهات",
                subtitle = "أضف مجلدًا يحتوي على فيديوهات أو حدّث المكتبة.",
                action = { TextButton(onClick = onAddFolder) { Text("إضافة مجلد") } }
            )

            state.viewMode == ViewMode.GRID -> LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 156.dp),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (state.resumeList.isNotEmpty() && state.query.isBlank() && folderFilter == null) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Column {
                            SectionHeader("استكمال المشاهدة")
                            ResumeRow(state.resumeList, onVideoClick)
                        }
                    }
                }
                items(normal.size) { index ->
                    VideoGridCard(
                        entry = normal[index],
                        onClick = { onVideoClick(normal, index, folderFilter ?: "كل الفيديوهات") },
                        onLongClick = { onVideoLongClick(normal[index]) }
                    )
                }
                if (hidden.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            "مخفي من المكتبة (${hidden.size})",
                            modifier = Modifier.padding(top = 12.dp, start = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    items(hidden.size) { index ->
                        VideoGridCard(
                            entry = hidden[index],
                            onClick = { onVideoClick(hidden, index, "المخفي") },
                            onLongClick = { onVideoLongClick(hidden[index]) }
                        )
                    }
                }
            }

            else -> LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
                if (state.resumeList.isNotEmpty() && state.query.isBlank() && folderFilter == null) {
                    item { SectionHeader("استكمال المشاهدة") }
                    items(state.resumeList) { entry ->
                        VideoRow(
                            entry = entry,
                            onClick = {
                                onVideoClick(state.resumeList, state.resumeList.indexOf(entry), "استكمال المشاهدة")
                            }
                        )
                    }
                }
                items(normal) { entry ->
                    VideoRow(
                        entry = entry,
                        onClick = { onVideoClick(normal, normal.indexOf(entry), folderFilter ?: "كل الفيديوهات") }
                    )
                }
            }
        }
    }
}

@Composable
private fun ResumeRow(
    list: List<LibraryEntry>,
    onVideoClick: (List<LibraryEntry>, Int, String) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(list.size) { index ->
            val entry = list[index]
            Column(
                Modifier
                    .width(180.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .clickable { onVideoClick(list, index, "استكمال المشاهدة") }
                    .padding(6.dp)
            ) {
                ThumbnailBox(entry, Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                Text(
                    entry.video.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(width = 26.dp, height = 3.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Brush.horizontalGradient(listOf(SdmColors.Violet, SdmColors.NeonCyan)))
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "متبقي ${formatDuration((entry.video.durationMs - entry.resumePosition).coerceAtLeast(0))}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private fun sortLabel(order: SortOrder): String = when (order) {
    SortOrder.DATE_NEWEST -> "الأحدث أولًا"
    SortOrder.DATE_OLDEST -> "الأقدم أولًا"
    SortOrder.NAME_ASC -> "الاسم أ-ي"
    SortOrder.NAME_DESC -> "الاسم ي-أ"
    SortOrder.DURATION_DESC -> "الأطول مدة"
    SortOrder.SIZE_DESC -> "الأكبر حجمًا"
}
