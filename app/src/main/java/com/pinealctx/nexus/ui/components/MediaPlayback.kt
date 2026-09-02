package com.pinealctx.nexus.ui.components

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.compose.material3.Player as Media3Player
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlin.math.roundToLong

@Stable
class ChatMediaController(context: Context) : Player.Listener {
    val player: ExoPlayer = ExoPlayer.Builder(context.applicationContext).build()

    var activeMediaId by mutableStateOf<String?>(null)
        private set
    var isPlaying by mutableStateOf(false)
        private set
    var isBuffering by mutableStateOf(false)
        private set
    var positionMs by mutableLongStateOf(0L)
        private set
    var durationMs by mutableLongStateOf(0L)
        private set
    var playbackError by mutableStateOf<String?>(null)
        private set

    init {
        player.addListener(this)
    }

    fun toggle(mediaId: String, url: String) {
        if (mediaId != activeMediaId) {
            activeMediaId = mediaId
            playbackError = null
            positionMs = 0L
            durationMs = 0L
            player.setMediaItem(MediaItem.fromUri(url))
            player.prepare()
            player.playWhenReady = true
        } else if (player.isPlaying) {
            player.pause()
        } else {
            player.play()
        }
        updateSnapshot()
    }

    fun seekTo(valueMs: Long) {
        player.seekTo(valueMs.coerceIn(0L, durationMs.coerceAtLeast(0L)))
        updateSnapshot()
    }

    fun updateSnapshot() {
        isPlaying = player.isPlaying
        isBuffering = player.playbackState == Player.STATE_BUFFERING
        positionMs = player.currentPosition.coerceAtLeast(0L)
        durationMs = player.duration.takeUnless { it == C.TIME_UNSET }?.coerceAtLeast(0L) ?: 0L
    }

    override fun onIsPlayingChanged(value: Boolean) {
        updateSnapshot()
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        updateSnapshot()
    }

    override fun onPlayerError(error: PlaybackException) {
        playbackError = error.message
        updateSnapshot()
    }

    fun release() {
        player.removeListener(this)
        player.release()
    }
}

@Composable
fun rememberChatMediaController(): ChatMediaController {
    val context = LocalContext.current
    val controller = remember(context.applicationContext) { ChatMediaController(context) }
    DisposableEffect(controller) {
        onDispose(controller::release)
    }
    LaunchedEffect(controller) {
        while (true) {
            controller.updateSnapshot()
            delay(if (controller.isPlaying) 250L else 750L)
        }
    }
    return controller
}

@Composable
fun AudioMessagePlayer(
    mediaId: String,
    url: String?,
    declaredDurationMs: Int,
    transcript: String? = null,
    controller: ChatMediaController?
) {
    val activeController = controller?.takeIf { it.activeMediaId == mediaId }
    val totalMs = if (activeController != null && activeController.durationMs > 0) {
        activeController.durationMs
    } else {
        declaredDurationMs.toLong()
    }.coerceAtLeast(0L)
    val currentMs = activeController?.positionMs ?: 0L

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { if (url != null && controller != null) controller.toggle(mediaId, url) },
                    enabled = url != null && controller != null
                ) {
                    when {
                        activeController?.isBuffering == true -> CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp
                        )
                        activeController?.isPlaying == true -> Icon(Icons.Filled.Pause, contentDescription = null)
                        else -> Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    }
                }
                Slider(
                    value = currentMs.toFloat(),
                    onValueChange = { controller?.seekTo(it.roundToLong()) },
                    valueRange = 0f..totalMs.coerceAtLeast(1L).toFloat(),
                    enabled = activeController != null && totalMs > 0L,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "${formatPlaybackTime(currentMs)} / ${formatPlaybackTime(totalMs)}",
                    style = MaterialTheme.typography.labelSmall
                )
            }
            transcript?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
            if (activeController?.playbackError != null) {
                Text(
                    text = activeController.playbackError.orEmpty(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }
        }
    }
}

@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
@Composable
fun VideoMessagePlayer(
    mediaId: String,
    url: String?,
    thumbnailUrl: String?,
    declaredDurationMs: Int,
    width: Int,
    height: Int,
    controller: ChatMediaController?
) {
    val activeController = controller?.takeIf { it.activeMediaId == mediaId }
    val aspectRatio = if (width > 0 && height > 0) {
        width.toFloat() / height.toFloat()
    } else {
        16f / 9f
    }.coerceIn(0.56f, 1.8f)

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = Color.Black,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(aspectRatio)
            .clip(RoundedCornerShape(10.dp))
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (activeController != null) {
                Media3Player(
                    player = activeController.player,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                if (thumbnailUrl != null) {
                    AsyncImage(
                        model = thumbnailUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Box(Modifier.matchParentSize().background(Color.Black))
                }
                Surface(
                    shape = RoundedCornerShape(50),
                    color = Color.Black.copy(alpha = 0.58f),
                    modifier = Modifier.clickable(
                        enabled = url != null && controller != null,
                        onClick = { if (url != null && controller != null) controller.toggle(mediaId, url) }
                    )
                ) {
                    Icon(
                        Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.padding(12.dp).size(28.dp)
                    )
                }
                Text(
                    text = formatPlaybackTime(declaredDurationMs.toLong()),
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp)
                )
            }
        }
    }
}

private fun formatPlaybackTime(milliseconds: Long): String {
    val totalSeconds = (milliseconds.coerceAtLeast(0L) / 1000L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "%d:%02d".format(minutes, seconds)
}
