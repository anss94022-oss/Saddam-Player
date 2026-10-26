package com.local.videoplayer.ui.library

import android.Manifest
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.local.videoplayer.data.model.VideoItem
import com.local.videoplayer.data.repository.VideoRepository
import com.local.videoplayer.ui.player.VideoPlayerActivity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen() {
    val context = LocalContext.current
    var videos by remember { mutableStateOf<List<VideoItem>>(emptyList()) }
    var hasPermission by remember { mutableStateOf(false) }

    val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_VIDEO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
    }

    LaunchedEffect(hasPermission) {
        if (hasPermission) {
            val repo = VideoRepository(context)
            videos = repo.getVideos()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Local Video Studio") })
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (!hasPermission) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Permission required to read videos")
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { launcher.launch(permission) }) {
                        Text("Grant Permission")
                    }
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(videos) { video ->
                        ListItem(
                            headlineContent = { Text(video.title) },
                            supportingContent = { Text("Duration: ${video.duration / 1000}s") },
                            modifier = Modifier.clickable {
                                val intent = Intent(context, VideoPlayerActivity::class.java).apply {
                                    putExtra("video_uri", video.uri)
                                }
                                context.startActivity(intent)
                            }
                        )
                        Divider()
                    }
                }
            }
        }
    }
}
