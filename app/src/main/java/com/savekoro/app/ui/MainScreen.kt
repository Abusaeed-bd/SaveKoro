@file:OptIn(ExperimentalMaterial3Api::class)

package com.savekoro.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.savekoro.app.MainViewModel
import com.savekoro.app.Platform
import com.savekoro.app.Quality
import com.savekoro.app.SavedItem
import com.savekoro.app.UiState
import com.savekoro.app.VideoMeta

@Composable
fun MainScreen(vm: MainViewModel) {
    val s by vm.state.collectAsState()
    val ctx = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(s.message) {
        s.message?.let {
            snackbar.showSnackbar(it)
            vm.consumeMessage()
        }
    }

    // Android 8/9 need storage permission to write to the gallery
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) vm.download()
    }
    fun startDownload() {
        val legacy = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
        val perm = Manifest.permission.WRITE_EXTERNAL_STORAGE
        if (legacy && ContextCompat.checkSelfPermission(ctx, perm) != PackageManager.PERMISSION_GRANTED) {
            permLauncher.launch(perm)
        } else {
            vm.download()
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(title = { Text("SaveKoro", fontWeight = FontWeight.Bold) })
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            PlatformRow(s.platform)

            OutlinedTextField(
                value = s.url,
                onValueChange = vm::onUrlChange,
                label = { Text("Paste video link") },
                placeholder = { Text("https://…") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    if (s.url.isNotEmpty()) {
                        IconButton(onClick = { vm.onUrlChange("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { vm.fetchInfo() }),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = {
                        val text = clipboard.getText()?.text.orEmpty()
                        val url = Platform.extractUrl(text)
                        if (url != null) {
                            vm.onUrlChange(url)
                            vm.fetchInfo()
                        } else {
                            vm.onUrlChange(text.trim())
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("📋 Paste") }

                Button(
                    onClick = vm::fetchInfo,
                    enabled = s.url.isNotBlank() && !s.fetching && s.engineReady,
                    modifier = Modifier.weight(1f),
                ) { Text("🔍 Fetch info") }
            }

            if (!s.engineReady && s.error == null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "Preparing engine (first launch takes a moment)…",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            if (s.fetching) LinearProgressIndicator(Modifier.fillMaxWidth())

            s.error?.let { ErrorCard(it) }

            s.meta?.let { MetaCard(it) }

            QualityRow(s.quality, enabled = !s.downloading, onPick = vm::setQuality)

            if (s.downloading) {
                ProgressCard(s, onCancel = vm::cancel)
            } else {
                Button(
                    onClick = ::startDownload,
                    enabled = s.url.isNotBlank() && s.engineReady,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) { Text("⬇️  Download", style = MaterialTheme.typography.titleMedium) }
            }

            if (s.saved.isNotEmpty()) {
                Text("Saved this session", style = MaterialTheme.typography.titleSmall)
                s.saved.forEach { SavedRow(it) }
            }

            HorizontalDivider()

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "yt-dlp: ${s.engineVersion.ifBlank { "…" }}",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = vm::updateEngine, enabled = s.engineReady && !s.updating && !s.downloading) {
                    Text(if (s.updating) "Updating…" else "🔄 Update engine")
                }
            }

            Text(
                "Only download your own videos or content you have permission to download. " +
                    "Re-uploading other people's content may violate copyright.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun PlatformRow(detected: Platform?) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Platform.supported.forEach { p ->
            val active = detected == p
            val c = Color(p.color)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (active) c else MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (active) Color.White else c)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    p.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (active) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ErrorCard(msg: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            "⚠️ $msg",
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(14.dp),
        )
    }
}

@Composable
private fun MetaCard(m: VideoMeta) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column {
            if (!m.thumbnail.isNullOrBlank()) {
                AsyncImage(
                    model = m.thumbnail,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f),
                )
            }
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    m.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val sub = listOfNotNull(
                    m.uploader,
                    m.durationSec.takeIf { it > 0 }?.let { formatTime(it.toLong()) },
                ).joinToString(" • ")
                if (sub.isNotEmpty()) {
                    Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun QualityRow(selected: Quality, enabled: Boolean, onPick: (Quality) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Quality", style = MaterialTheme.typography.titleSmall)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Quality.entries.forEach { q ->
                FilterChip(
                    selected = q == selected,
                    onClick = { onPick(q) },
                    enabled = enabled,
                    label = { Text(q.label) },
                )
            }
        }
    }
}

@Composable
private fun ProgressCard(s: UiState, onCancel: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${(s.progress * 100).toInt()}%",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.width(12.dp))
                if (s.etaSec > 0) {
                    Text("~${formatTime(s.etaSec)} left", style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onCancel) { Text("Cancel") }
            }
            LinearProgressIndicator(progress = { s.progress }, modifier = Modifier.fillMaxWidth())
            if (s.statusLine.isNotBlank()) {
                Text(
                    s.statusLine,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SavedRow(item: SavedItem) {
    val ctx = LocalContext.current
    val mime = if (item.isAudio) "audio/*" else "video/*"
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                (if (item.isAudio) "🎵 " else "🎬 ") + item.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            item.uri?.let { uri ->
                TextButton(onClick = {
                    val i = Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    runCatching { ctx.startActivity(i) }
                }) { Text("Play") }
                TextButton(onClick = {
                    val i = Intent(Intent.ACTION_SEND).setType(mime)
                        .putExtra(Intent.EXTRA_STREAM, uri)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    runCatching { ctx.startActivity(Intent.createChooser(i, "Share via")) }
                }) { Text("Share") }
            }
        }
    }
}

private fun formatTime(sec: Long): String {
    val h = sec / 3600
    val m = (sec % 3600) / 60
    val s = sec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
