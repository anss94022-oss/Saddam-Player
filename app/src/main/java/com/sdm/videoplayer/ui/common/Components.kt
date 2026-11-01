package com.sdm.videoplayer.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import com.sdm.videoplayer.data.model.LibraryEntry
import com.sdm.videoplayer.ui.theme.SdmColors
import com.sdm.videoplayer.util.formatDuration
import com.sdm.videoplayer.util.rememberThumbnail

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, trailing: @Composable (() -> Unit)? = null) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(width = 3.dp, height = 16.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Brush.verticalGradient(listOf(SdmColors.Violet, SdmColors.NeonCyan)))
        )
        Spacer(Modifier.width(8.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.weight(1f))
        trailing?.invoke()
    }
}

/** غلاف الصورة المصغرة مع شارة المدة وشريط تقدم المشاهدة. */
@Composable
fun ThumbnailBox(
    entry: LibraryEntry,
    modifier: Modifier = Modifier,
    aspect: Float = 16f / 9f,
    showResume: Boolean = true
) {
    val context = LocalContext.current
    val thumb = rememberThumbnail(context, entry.video)
    Box(
        modifier
            .aspectRatio(aspect)
            .clip(RoundedCornerShape(12.dp))
            .background(SdmColors.NightElevated)
    ) {
        if (thumb != null) {
            Image(
                bitmap = thumb,
                contentDescription = entry.video.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Movie, null, tint = SdmColors.TextMuted, modifier = Modifier.size(30.dp))
            }
        }
        // شارة المدة
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(6.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xCC000000))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(
                formatDuration(entry.video.durationMs),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White
            )
        }
        if (showResume && entry.hasResume) {
            LinearProgressIndicator(
                progress = { entry.watchedPercent / 100f },
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp),
                color = SdmColors.Violet,
                trackColor = Color(0x66000000)
            )
        }
        // زر التشغيل
        Box(
            Modifier
                .align(Alignment.Center)
                .size(40.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0x66000000)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(26.dp))
        }
    }
}

@Composable
fun VideoGridCard(
    entry: LibraryEntry,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column {
            ThumbnailBox(entry, Modifier.fillMaxWidth())
            Column(Modifier.padding(8.dp)) {
                Text(
                    entry.video.title,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    entry.video.folderLabel,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun VideoRow(
    entry: LibraryEntry,
    onClick: () -> Unit,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ThumbnailBox(entry, Modifier.width(132.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                entry.video.title,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Folder, null, Modifier.size(12.dp), tint = SdmColors.TextMuted)
                Spacer(Modifier.width(4.dp))
                Text(
                    entry.video.folderLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (entry.hasResume) {
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { entry.watchedPercent / 100f },
                    modifier = Modifier.fillMaxWidth(0.8f).height(3.dp),
                    color = SdmColors.Violet,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }
        }
        trailing?.invoke()
    }
}

@Composable
fun EmptyState(title: String, subtitle: String? = null, action: @Composable (() -> Unit)? = null) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(36.dp))
                .background(Brush.linearGradient(listOf(SdmColors.Violet.copy(alpha = .25f), SdmColors.NeonCyan.copy(alpha = .15f)))),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Movie, null, tint = SdmColors.Violet, modifier = Modifier.size(34.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
        if (subtitle != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (action != null) {
            Spacer(Modifier.height(16.dp))
            action()
        }
    }
}
