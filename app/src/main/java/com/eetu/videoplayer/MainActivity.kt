package com.eetu.videoplayer

import android.app.PictureInPictureParams
import android.content.ComponentName
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrightnessLow
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import androidx.media3.ui.PlayerView
import com.eetu.videoplayer.ui.theme.VideoPlayerTheme
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.delay
import java.util.Locale
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {

    private var isInPiPMode by mutableStateOf(false)
    private var isPlayingState by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VideoPlayerTheme {
                var videoUri by remember { mutableStateOf<Uri?>(intent?.data) }

                LaunchedEffect(Unit) {
                    if (intent?.action == Intent.ACTION_VIEW) {
                        videoUri = intent.data
                    }
                }

                VideoPlayerScreen(videoUri, isInPiPMode, onIsPlayingChanged = { isPlayingState = it })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == Intent.ACTION_VIEW) {
            setContent {
                VideoPlayerTheme {
                    VideoPlayerScreen(intent.data, isInPiPMode, onIsPlayingChanged = { isPlayingState = it })
                }
            }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (isPlayingState) {
            enterPiPMode()
        }
    }

    private fun enterPiPMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
                .build()
            enterPictureInPictureMode(params)
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isInPiPMode = isInPictureInPictureMode
    }
}

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerScreen(
    videoUri: Uri?,
    isInPiPMode: Boolean,
    onIsPlayingChanged: (Boolean) -> Unit
) {
    val context = LocalContext.current
    
    // Player State
    var isPlaying by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }
    var brightness by remember { mutableFloatStateOf(0.0f) }
    var contrast by remember { mutableFloatStateOf(0.0f) }
    
    // Zoom/Pan State
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    // UI State
    var showControls by remember { mutableStateOf(true) }
    var showSpeedDialog by remember { mutableStateOf(false) }
    var showAdjustments by remember { mutableStateOf(false) }

    var playerController by remember { mutableStateOf<MediaController?>(null) }

    DisposableEffect(context) {
        val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val controllerFuture: ListenableFuture<MediaController> = MediaController.Builder(context, sessionToken).buildAsync()
        
        controllerFuture.addListener({
            val controller = controllerFuture.get()
            playerController = controller
            
            isPlaying = controller.isPlaying
            onIsPlayingChanged(isPlaying)
            duration = controller.duration
            playbackSpeed = controller.playbackParameters.speed
            
            controller.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlayingChanged: Boolean) {
                    isPlaying = isPlayingChanged
                    onIsPlayingChanged(isPlaying)
                }
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_READY) {
                        duration = controller.duration
                    }
                }
            })
        }, MoreExecutors.directExecutor())

        onDispose {
            MediaController.releaseFuture(controllerFuture)
        }
    }

    // Effect Update Task with Debounce using Custom Command
    LaunchedEffect(brightness, contrast, playerController) {
        playerController?.let { controller ->
            delay(150)
            val args = Bundle().apply {
                putFloat(PlaybackService.KEY_BRIGHTNESS, brightness)
                putFloat(PlaybackService.KEY_CONTRAST, contrast)
            }
            controller.sendCustomCommand(
                SessionCommand(PlaybackService.ACTION_SET_VIDEO_EFFECTS, Bundle.EMPTY),
                args
            )
        }
    }

    // Position Update Loop
    LaunchedEffect(isPlaying, playerController) {
        while (isPlaying) {
            playerController?.let {
                currentPosition = it.currentPosition
            }
            delay(500)
        }
    }

    // Load video
    LaunchedEffect(videoUri, playerController) {
        if (videoUri != null && playerController != null) {
            val mediaItem = MediaItem.fromUri(videoUri)
            playerController?.let {
                it.setMediaItem(mediaItem)
                it.prepare()
                it.playWhenReady = true
            }
        }
    }

    // Auto-hide controls
    LaunchedEffect(showControls) {
        if (showControls && !showSpeedDialog && !showAdjustments) {
            delay(3000)
            showControls = false
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.Black
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .pointerInput(Unit) {
                    if (!isInPiPMode) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 5f)
                            val maxX = (size.width * (scale - 1)) / 2
                            val maxY = (size.height * (scale - 1)) / 2
                            offset = Offset(
                                (offset.x + pan.x).coerceIn(-maxX, maxX),
                                (offset.y + pan.y).coerceIn(-maxY, maxY)
                            )
                        }
                    }
                }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    enabled = !isInPiPMode
                ) { showControls = !showControls },
            contentAlignment = Alignment.Center
        ) {
            if (videoUri != null && playerController != null) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            player = playerController
                            useController = false
                        }
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = if (isInPiPMode) 1f else scale,
                            scaleY = if (isInPiPMode) 1f else scale,
                            translationX = if (isInPiPMode) 0f else offset.x,
                            translationY = if (isInPiPMode) 0f else offset.y
                        )
                )

                // Custom Controls Overlay
                AnimatedVisibility(
                    visible = showControls && !isInPiPMode,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    PlayerControls(
                        isPlaying = isPlaying,
                        currentPosition = currentPosition,
                        duration = duration,
                        playbackSpeed = playbackSpeed,
                        onPlayPauseToggle = {
                            playerController?.let {
                                if (it.isPlaying) it.pause() else it.play()
                            }
                        },
                        onSeek = { position ->
                            playerController?.seekTo(position)
                            currentPosition = position
                        },
                        onSpeedClick = { showSpeedDialog = true },
                        onAdjustmentsClick = { showAdjustments = true }
                    )
                }

                // Sub-menus
                if (showSpeedDialog && !isInPiPMode) {
                    SpeedDialog(
                        currentSpeed = playbackSpeed,
                        onSpeedSelected = {
                            playbackSpeed = it
                            playerController?.playbackParameters = PlaybackParameters(it)
                            showSpeedDialog = false
                        },
                        onDismiss = { showSpeedDialog = false }
                    )
                }

                if (showAdjustments && !isInPiPMode) {
                    AdjustmentsOverlay(
                        brightness = brightness,
                        contrast = contrast,
                        onBrightnessChange = { brightness = it },
                        onContrastChange = { contrast = it },
                        onDismiss = { showAdjustments = false }
                    )
                }

            } else if (videoUri == null) {
                EmptyState()
            } else {
                // Loading state or waiting for controller
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    androidx.compose.material3.CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
fun PlayerControls(
    isPlaying: Boolean,
    currentPosition: Long,
    duration: Long,
    playbackSpeed: Float,
    onPlayPauseToggle: () -> Unit,
    onSeek: (Long) -> Unit,
    onSpeedClick: () -> Unit,
    onAdjustmentsClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.4f))
    ) {
        // Center Play/Pause
        IconButton(
            onClick = onPlayPauseToggle,
            modifier = Modifier
                .align(Alignment.Center)
                .size(80.dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.8f), CircleShape)
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onPrimary
            )
        }

        // Bottom Controls
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatTime(currentPosition),
                    color = Color.White,
                    fontSize = 14.sp
                )
                
                CustomSlider(
                    value = currentPosition.toFloat(),
                    onValueChange = { onSeek(it.toLong()) },
                    valueRange = 0f..duration.coerceAtLeast(0).toFloat(),
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                )

                Text(
                    text = formatTime(duration),
                    color = Color.White,
                    fontSize = 14.sp
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                ControlIcon(
                    icon = Icons.Default.BrightnessLow,
                    label = "Adjust",
                    onClick = onAdjustmentsClick
                )
                Spacer(modifier = Modifier.width(16.dp))
                ControlIcon(
                    icon = Icons.Default.Speed,
                    label = String.format(Locale.US, "%.2fx", playbackSpeed),
                    onClick = onSpeedClick
                )
            }
        }
    }
}

@Composable
fun CustomSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier
) {
    Slider(
        value = value,
        onValueChange = onValueChange,
        valueRange = valueRange,
        modifier = modifier,
        colors = SliderDefaults.colors(
            thumbColor = MaterialTheme.colorScheme.primary,
            activeTrackColor = MaterialTheme.colorScheme.primary,
            inactiveTrackColor = Color.White.copy(alpha = 0.3f)
        )
    )
}

@Composable
fun ControlIcon(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onClick() }
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(28.dp)
        )
        Text(
            text = label,
            color = Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun SpeedDialog(
    currentSpeed: Float,
    onSpeedSelected: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    val speeds = listOf(0.05f, 0.1f, 0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
    
    Surface(
        modifier = Modifier
            .fillMaxWidth(0.8f)
            .padding(16.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Playback Speed",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))
            speeds.chunked(3).forEach { rowSpeeds ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    rowSpeeds.forEach { speed ->
                        val isSelected = speed == currentSpeed
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .padding(4.dp)
                                .clickable { onSpeedSelected(speed) },
                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                            shape = CircleShape,
                            border = if (!isSelected) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline) else null
                        ) {
                            Text(
                                text = "${speed}x",
                                modifier = Modifier.padding(8.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
            Text(
                text = "Dismiss",
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(top = 16.dp)
                    .clickable { onDismiss() },
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun AdjustmentsOverlay(
    brightness: Float,
    contrast: Float,
    onBrightnessChange: (Float) -> Unit,
    onContrastChange: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth(0.9f)
            .padding(16.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
        tonalElevation = 8.dp
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(
                text = "Video Adjustments",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            AdjustmentSlider(
                label = "Brightness",
                value = brightness,
                onValueChange = onBrightnessChange
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            AdjustmentSlider(
                label = "Contrast",
                value = contrast,
                onValueChange = onContrastChange
            )
            
            Text(
                text = "Done",
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(top = 16.dp)
                    .clickable { onDismiss() },
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun AdjustmentSlider(label: String, value: Float, onValueChange: (Float) -> Unit) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = label, style = MaterialTheme.typography.bodySmall)
            Text(text = String.format(Locale.US, "%.2f", value), style = MaterialTheme.typography.bodySmall)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = -1f..1f,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.secondary,
                activeTrackColor = MaterialTheme.colorScheme.secondary
            )
        )
    }
}

fun formatTime(ms: Long): String {
    val hours = TimeUnit.MILLISECONDS.toHours(ms)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(ms) % 60
    val seconds = TimeUnit.MILLISECONDS.toSeconds(ms) % 60
    return if (hours > 0) {
        String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}

@Composable
fun EmptyState() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Open a video to start",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
    }
}
