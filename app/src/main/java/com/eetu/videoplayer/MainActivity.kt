package com.eetu.videoplayer

import android.app.Activity
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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.BrightnessLow
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
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
    var isMuted by remember { mutableStateOf(false) }
    var screenBrightness by remember { mutableFloatStateOf(-1f) }
    var currentTracks by remember { mutableStateOf(Tracks.EMPTY) }
    
    // Zoom/Pan State
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    // UI State
    var showControls by remember { mutableStateOf(true) }
    var showSpeedDialog by remember { mutableStateOf(false) }
    var showAdjustments by remember { mutableStateOf(false) }
    var showTrackSelection by remember { mutableStateOf(false) }

    // Gesture State
    var seekDragDelta by remember { mutableLongStateOf(0L) }
    var initialSeekPosition by remember { mutableLongStateOf(0L) }
    var isSeeking by remember { mutableStateOf(false) }
    var wasPlayingBeforeSeek by remember { mutableStateOf(false) }
    var lastSeekUpdateTime by remember { mutableLongStateOf(0L) }
    var isBrightnessDragging by remember { mutableStateOf(false) }
    var isHoldingSpeedBoost by remember { mutableStateOf(false) }
    var lastPinchEndTime by remember { mutableLongStateOf(0L) }

    var playerController by remember { mutableStateOf<MediaController?>(null) }

    val activity = context as? Activity
    LaunchedEffect(screenBrightness) {
        if (screenBrightness >= 0f) {
            activity?.window?.attributes = activity?.window?.attributes?.apply {
                this.screenBrightness = screenBrightness.coerceIn(0.01f, 1.0f)
            }
        }
    }

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
            isMuted = controller.volume == 0f
            currentTracks = controller.currentTracks
            
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
                override fun onTracksChanged(tracks: Tracks) {
                    currentTracks = tracks
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
        if (showControls && !showSpeedDialog && !showAdjustments && !showTrackSelection) {
            delay(3000)
            showControls = false
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.Black
    ) { innerPadding ->
        val configuration = LocalConfiguration.current
        val touchSlop = with(LocalDensity.current) { 16.dp.toPx() } // Standard touch slop
        
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color.Black)
                .pointerInput(playerController, duration) {
                    if (isInPiPMode) return@pointerInput
                    
                    awaitEachGesture {
                        val firstDown = awaitFirstDown(requireUnconsumed = false)
                        var dragConsumed = false
                        var isLongPress = false
                        val startPos = firstDown.position
                        val startTime = System.currentTimeMillis()
                        
                        do {
                            val event = awaitPointerEvent()
                            val changes = event.changes
                            
                            if (changes.size == 1) {
                                // Potential single-finger gesture
                                val change = changes[0]
                                if (change.pressed) {
                                    val timeSincePinch = System.currentTimeMillis() - lastPinchEndTime
                                    if (timeSincePinch < 300) { // 300ms cooldown after pinch
                                        change.consume()
                                        continue
                                    }

                                    val dragAmount = change.position - startPos
                                    val timeElapsed = System.currentTimeMillis() - startTime
                                    
                                    if (!dragConsumed && !isLongPress) {
                                        if (dragAmount.getDistance() > touchSlop) {
                                            dragConsumed = true
                                            if (kotlin.math.abs(dragAmount.x) > kotlin.math.abs(dragAmount.y)) {
                                                isSeeking = true
                                                wasPlayingBeforeSeek = playerController?.isPlaying == true
                                                initialSeekPosition = playerController?.currentPosition ?: 0L
                                                playerController?.pause()
                                            } else if (startPos.x > size.width / 2) {
                                                isBrightnessDragging = true
                                            }
                                        } else if (timeElapsed > 500) {
                                            isLongPress = true
                                            isHoldingSpeedBoost = true
                                            playerController?.playbackParameters = PlaybackParameters(2.0f)
                                            showControls = false
                                        }
                                    }

                                    if (isSeeking) {
                                        val velocityX = if (timeElapsed > 0) kotlin.math.abs(dragAmount.x) / timeElapsed else 0f
                                        // Granularity: even higher. 
                                        // User wants "slow long swipe like 5sec".
                                        // Base sensitivity: 5s per screen width.
                                        val velocityFactor = (1f + (velocityX / 1f)).coerceAtMost(20f)
                                        val baseSensitivity = 5000f / size.width
                                        seekDragDelta = (dragAmount.x * baseSensitivity * velocityFactor).toLong()
                                        val newPos = (initialSeekPosition + seekDragDelta).coerceIn(0L, duration)
                                        
                                        // Throttle seek updates to once per 1000ms
                                        val currentTime = System.currentTimeMillis()
                                        if (currentTime - lastSeekUpdateTime > 1000) {
                                            playerController?.seekTo(newPos)
                                            lastSeekUpdateTime = currentTime
                                        }
                                        currentPosition = newPos
                                        change.consume()
                                    } else if (isBrightnessDragging) {
                                        // User wants "50% vertical screen is the range for 1 to 100"
                                        // sensitivity = 1.0 / (0.5 * size.height)
                                        val delta = -dragAmount.y / (size.height * 0.5f)
                                        screenBrightness = (screenBrightness.takeIf { it >= 0 } ?: 0.5f) + delta
                                        screenBrightness = screenBrightness.coerceIn(0.01f, 1f)
                                        change.consume()
                                    } else if (isHoldingSpeedBoost) {
                                        change.consume()
                                    }
                                }
                            } else if (changes.size >= 2) {
                                // Multi-finger (Zoom/Pan)
                                // Cancel any single finger gesture if a second finger is added
                                if (isSeeking) {
                                    isSeeking = false
                                    if (wasPlayingBeforeSeek) playerController?.play()
                                }
                                isBrightnessDragging = false
                                if (isHoldingSpeedBoost) {
                                    isHoldingSpeedBoost = false
                                    playerController?.playbackParameters = PlaybackParameters(playbackSpeed)
                                }
                                
                                val zoom = event.calculateZoom()
                                val pan = event.calculatePan()

                                if (zoom != 1f || pan != Offset.Zero) {
                                    scale = (scale * zoom).coerceIn(0.1f, 10f)
                                    val maxOffsetH = (size.width * scale + size.width) / 2
                                    val maxOffsetV = (size.height * scale + size.height) / 2
                                    offset = Offset(
                                        (offset.x + pan.x).coerceIn(-maxOffsetH, maxOffsetH),
                                        (offset.y + pan.y).coerceIn(-maxOffsetV, maxOffsetV)
                                    )
                                    changes.forEach { it.consume() }
                                    lastPinchEndTime = System.currentTimeMillis()
                                }
                            }
                        } while (event.changes.any { it.pressed })
                        
                        // Release / End of gesture
                        if (isSeeking) {
                            val finalPos = (initialSeekPosition + seekDragDelta).coerceIn(0L, duration)
                            playerController?.seekTo(finalPos)
                            isSeeking = false
                            if (wasPlayingBeforeSeek) playerController?.play()
                            seekDragDelta = 0
                            lastSeekUpdateTime = 0
                        }
                        isBrightnessDragging = false
                        if (isHoldingSpeedBoost) {
                            isHoldingSpeedBoost = false
                            playerController?.playbackParameters = PlaybackParameters(playbackSpeed)
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

                // Overlays
                if (isSeeking) {
                    SeekOverlay(
                        currentPos = currentPosition,
                        delta = seekDragDelta
                    )
                }

                if (isBrightnessDragging) {
                    BrightnessOverlay(brightness = screenBrightness)
                }

                if (isHoldingSpeedBoost) {
                    Box(modifier = Modifier.fillMaxSize().padding(top = 32.dp), contentAlignment = Alignment.TopCenter) {
                        SpeedBoostOverlay()
                    }
                }

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
                        onAdjustmentsClick = { showAdjustments = true },
                        isMuted = isMuted,
                        onMuteToggle = {
                            playerController?.let {
                                if (isMuted) {
                                    it.volume = 1f
                                    isMuted = false
                                } else {
                                    it.volume = 0f
                                    isMuted = true
                                }
                            }
                        },
                        onTrackSelectionClick = { showTrackSelection = true }
                    )
                }

                // Sub-menus
                if (showTrackSelection && !isInPiPMode) {
                    TrackSelectionDialog(
                        tracks = currentTracks,
                        onTrackSelected = { group, trackIndex ->
                            playerController?.let { controller ->
                                val parameters = controller.trackSelectionParameters
                                    .buildUpon()
                                    .setOverrideForType(
                                        TrackSelectionOverride(group.mediaTrackGroup, trackIndex)
                                    )
                                    .build()
                                controller.trackSelectionParameters = parameters
                            }
                        },
                        onDisableType = { type ->
                            playerController?.let { controller ->
                                val parameters = controller.trackSelectionParameters
                                    .buildUpon()
                                    .setTrackTypeDisabled(type, true)
                                    .build()
                                controller.trackSelectionParameters = parameters
                            }
                        },
                        onEnableType = { type ->
                            playerController?.let { controller ->
                                val parameters = controller.trackSelectionParameters
                                    .buildUpon()
                                    .setTrackTypeDisabled(type, false)
                                    .build()
                                controller.trackSelectionParameters = parameters
                            }
                        },
                        onDismiss = { showTrackSelection = false }
                    )
                }
                
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
fun SeekOverlay(currentPos: Long, delta: Long) {
    Box(
        modifier = Modifier
            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = formatTime(currentPos),
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = if (delta >= 0) "[+${formatTime(delta)}]" else "[-${formatTime(-delta)}]",
                color = if (delta >= 0) Color.Green else Color.Red,
                fontSize = 16.sp
            )
        }
    }
}

@Composable
fun BrightnessOverlay(brightness: Float) {
    Box(
        modifier = Modifier
            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.BrightnessLow,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            val displayBrightness = if (brightness < 0) 0.5f else brightness
            Text(
                text = "${(displayBrightness * 100).toInt()}%",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun SpeedBoostOverlay() {
    Box(
        modifier = Modifier
            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Speed,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "2X Speed",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
fun TrackSelectionDialog(
    tracks: Tracks,
    onTrackSelected: (Tracks.Group, Int) -> Unit,
    onDisableType: (Int) -> Unit,
    onEnableType: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth(0.9f)
            .height(400.dp)
            .padding(16.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Tracks",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            
            LazyColumn(modifier = Modifier.weight(1f)) {
                item {
                    Text("Audio", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
                }
                val audioGroups = tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
                items(audioGroups) { group ->
                    for (i in 0 until group.length) {
                        TrackItem(
                            label = group.mediaTrackGroup.getFormat(i).language ?: "Unknown",
                            isSelected = group.isTrackSelected(i),
                            onClick = { onTrackSelected(group, i) }
                        )
                    }
                }
                
                item {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Text("Subtitles", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
                    TrackItem(
                        label = "None",
                        isSelected = !tracks.groups.any { it.type == C.TRACK_TYPE_TEXT && it.isSelected },
                        onClick = { onDisableType(C.TRACK_TYPE_TEXT) }
                    )
                }
                
                val subtitleGroups = tracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }
                items(subtitleGroups) { group ->
                    for (i in 0 until group.length) {
                        TrackItem(
                            label = group.mediaTrackGroup.getFormat(i).language ?: "Unknown",
                            isSelected = group.isTrackSelected(i),
                            onClick = { 
                                onEnableType(C.TRACK_TYPE_TEXT)
                                onTrackSelected(group, i) 
                            }
                        )
                    }
                }
            }
            
            Text(
                text = "Close",
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(top = 8.dp)
                    .clickable { onDismiss() },
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun TrackItem(label: String, isSelected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 4.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (label == "None") Icons.Default.VolumeOff else if (label.length > 3) Icons.Default.Subtitles else Icons.Default.Audiotrack,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.bodyLarge,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun PlayerControls(
    isPlaying: Boolean,
    currentPosition: Long,
    duration: Long,
    playbackSpeed: Float,
    isMuted: Boolean,
    onPlayPauseToggle: () -> Unit,
    onSeek: (Long) -> Unit,
    onSpeedClick: () -> Unit,
    onAdjustmentsClick: () -> Unit,
    onMuteToggle: () -> Unit,
    onTrackSelectionClick: () -> Unit
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
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ControlIcon(
                    icon = if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                    label = if (isMuted) "Unmute" else "Mute",
                    onClick = onMuteToggle
                )
                Spacer(modifier = Modifier.width(16.dp))
                ControlIcon(
                    icon = Icons.Default.Subtitles,
                    label = "Tracks",
                    onClick = onTrackSelectionClick
                )
                Spacer(modifier = Modifier.width(16.dp))
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
    val speeds = listOf(0.01f, 0.05f, 0.1f, 0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f, 4.0f, 8.0f)
    
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
