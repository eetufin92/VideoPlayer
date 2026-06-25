@file:OptIn(UnstableApi::class)

package com.eetu.videoplayer

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.BrightnessLow
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.pow
import android.media.MediaMetadataRetriever

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")
private val SEEK_SENSITIVITY = floatPreferencesKey("seek_sensitivity")
private val GESTURE_SENSITIVITY = floatPreferencesKey("gesture_sensitivity")
private val AUTO_LOAD_SUBTITLES = booleanPreferencesKey("auto_load_subtitles")
private val PREFERRED_AUDIO_LANG = stringPreferencesKey("preferred_audio_lang")
private val PREFERRED_SUBTITLE_LANG = stringPreferencesKey("preferred_subtitle_lang")

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

                VideoPlayerScreen(
                    videoUri = videoUri,
                    isInPiPMode = isInPiPMode,
                    onIsPlayingChanged = { isPlayingState = it },
                    onClose = {
                        if (intent?.action == Intent.ACTION_VIEW) {
                            finish() // Close the app completely
                        } else {
                            videoUri = null // Return to EmptyState
                        }
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == Intent.ACTION_VIEW) {
            setContent {
                VideoPlayerTheme {
                    VideoPlayerScreen(
                        videoUri = intent.data,
                        isInPiPMode = isInPiPMode,
                        onIsPlayingChanged = { isPlayingState = it },
                        onClose = {
                            finish()
                        }
                    )
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
        val params = PictureInPictureParams.Builder()
            .setAspectRatio(Rational(16, 9))
            .build()
        enterPictureInPictureMode(params)
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isInPiPMode = isInPictureInPictureMode
    }
}

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerScreen(
    videoUri: Uri?,
    isInPiPMode: Boolean,
    onIsPlayingChanged: (Boolean) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    var playerController by remember { mutableStateOf<MediaController?>(null) }

    // Player State
    var isPlaying by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }
    var brightness by remember { mutableFloatStateOf(0.0f) }
    var contrast by remember { mutableFloatStateOf(0.0f) }
    var isMuted by remember { mutableStateOf(false) }
    var screenBrightness by remember { mutableFloatStateOf(-1f) }
    var currentTracks by remember { mutableStateOf(Tracks.EMPTY) }
    var hasVideoTrack by remember { mutableStateOf(true) }
    val videoFrameRate = remember(currentTracks) {
        currentTracks.groups
            .find { it.type == C.TRACK_TYPE_VIDEO }
            ?.let { group ->
                (0 until group.length)
                    .map { group.mediaTrackGroup.getFormat(it).frameRate }
                    .firstOrNull { it > 0f }
            } ?: 30f
    }

    // Zoom/Pan State
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    // UI State
    var showControls by remember { mutableStateOf(true) }
    var isSliderScrubbing by remember { mutableStateOf(false) }
    var showSpeedDialog by remember { mutableStateOf(false) }
    var showAdjustments by remember { mutableStateOf(false) }
    var showTrackSelection by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var wasPlayingBeforeSliderScrub by remember { mutableStateOf(false) }

    // Sensitivity Settings
    var seekSensitivity by remember { mutableFloatStateOf(1.0f) }
    var gestureSensitivity by remember { mutableFloatStateOf(1.0f) }
    var autoLoadSubtitles by remember { mutableStateOf(true) }
    var prefAudioLang by remember { mutableStateOf("") }
    var prefSubLang by remember { mutableStateOf("") }

    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        context.dataStore.data.map { it[SEEK_SENSITIVITY] ?: 1.0f }.first()
            .let { seekSensitivity = it }
        context.dataStore.data.map { it[GESTURE_SENSITIVITY] ?: 1.0f }.first()
            .let { gestureSensitivity = it }
        context.dataStore.data.map { it[AUTO_LOAD_SUBTITLES] ?: true }.first()
            .let { autoLoadSubtitles = it }
        context.dataStore.data.map { it[PREFERRED_AUDIO_LANG] ?: "" }.first()
            .let { prefAudioLang = it }
        context.dataStore.data.map { it[PREFERRED_SUBTITLE_LANG] ?: "" }.first()
            .let { prefSubLang = it }
    }

    // Apply preferred languages when they change or player is ready
    LaunchedEffect(prefAudioLang, prefSubLang, autoLoadSubtitles, playerController) {
        playerController?.let { controller ->
            val parameters = controller.trackSelectionParameters
                .buildUpon()
                .setPreferredAudioLanguage(prefAudioLang.takeIf { it.isNotBlank() })
                .setPreferredTextLanguage(prefSubLang.takeIf { it.isNotBlank() })
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !autoLoadSubtitles)
                .build()
            controller.trackSelectionParameters = parameters
        }
    }

    // Only intercept the back button if a video is currently loaded
    BackHandler(enabled = videoUri != null) {
        playerController?.let {
            it.stop() // Stops the background service playback
            it.clearMediaItems() // Flushes the active stream
        }
        onClose() // Tells the Activity to finish or reset
    }

    // Gesture State
    var isSeeking by remember { mutableStateOf(false) }

    // Multi-Tap State
    var tapJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var tapCount by remember { mutableIntStateOf(0) }
    var tapOverlayIsRight by remember { mutableStateOf(true) }
    var tapOverlayVisible by remember { mutableStateOf(false) }
    var tapOverlayText by remember { mutableStateOf("") }

    var isBrightnessDragging by remember { mutableStateOf(false) }
    var isHoldingSpeedBoost by remember { mutableStateOf(false) }
    var lastPinchEndTime by remember { mutableLongStateOf(0L) }


    val activity = context as? Activity
    LaunchedEffect(screenBrightness) {
        if (screenBrightness >= 0f) {
            activity?.window?.attributes = activity.window?.attributes?.apply {
                this.screenBrightness = screenBrightness.coerceIn(0.01f, 1.0f)
            }
        }
    }

    DisposableEffect(context) {
        val sessionToken =
            SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val controllerFuture: ListenableFuture<MediaController> =
            MediaController.Builder(context, sessionToken).buildAsync()

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
                    isLoading = state == Player.STATE_BUFFERING
                    if (state == Player.STATE_READY) {
                        duration = controller.duration
                    }
                }

                override fun onTracksChanged(tracks: Tracks) {
                    currentTracks = tracks
                    hasVideoTrack = tracks.groups.any { it.type == C.TRACK_TYPE_VIDEO }
                }
            })
        }, MoreExecutors.directExecutor())

        onDispose {
            MediaController.releaseFuture(controllerFuture)
        }
    }

    var thumbnails by remember { mutableStateOf<Map<Long, ImageBitmap>>(emptyMap()) }

    LaunchedEffect(videoUri) {
        if (videoUri != null) {
            thumbnails = emptyMap()
            withContext(Dispatchers.IO) {
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(context, videoUri)
                    val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0L
                    if (durationMs > 0) {
                        val newThumbnails = mutableMapOf<Long, ImageBitmap>()
                        
                        // Binary splitting sequence: Generate 0.5, then 0.25 & 0.75, etc.
                        // This ensures we have broad coverage quickly.
                        val points = mutableListOf<Double>()
                        val queue: java.util.Queue<Pair<Double, Double>> = java.util.LinkedList()
                        
                        points.add(0.0)
                        points.add(1.0)
                        queue.add(0.0 to 1.0)
                        
                        while (points.size < 64 && queue.isNotEmpty()) {
                            val (low, high) = queue.poll()!!
                            val mid = (low + high) / 2.0
                            if (mid !in points) {
                                points.add(mid)
                                queue.add(low to mid)
                                queue.add(mid to high)
                            }
                        }

                        for ((index, ratio) in points.withIndex()) {
                            val timeMs = (ratio * durationMs).toLong()
                            val bitmap = retriever.getScaledFrameAtTime(
                                timeMs * 1000,
                                MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                                160,
                                90
                            )
                            bitmap?.let {
                                newThumbnails[timeMs] = it.asImageBitmap()
                                // Update UI frequently for the first few, then periodically
                                if (index < 10 || index % 5 == 0) {
                                    val currentMap = newThumbnails.toMap()
                                    withContext(Dispatchers.Main) {
                                        thumbnails = currentMap
                                    }
                                }
                            }
                        }
                        withContext(Dispatchers.Main) {
                            thumbnails = newThumbnails
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    retriever.release()
                }
            }
        } else {
            thumbnails = emptyMap()
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
                // Reset speed to 1.0x for new files
                playbackSpeed = 1.0f
                it.playbackParameters = PlaybackParameters(1.0f)
                it.prepare()
                it.playWhenReady = true
            }
        }
    }

    // Auto-hide controls
    LaunchedEffect(
        showControls,
        isSliderScrubbing,
        isSeeking,
        showSpeedDialog,
        showAdjustments,
        showTrackSelection
    ) {
        if (showControls && !showSpeedDialog && !showAdjustments && !showTrackSelection && !isSliderScrubbing && !isSeeking) {
            delay(3000)
            showControls = false
        }
    }

    fun setSeekMode(fast: Boolean) {
        playerController?.let { controller ->
            val args = Bundle().apply {
                putInt(
                    PlaybackService.KEY_SEEK_MODE,
                    if (fast) PlaybackService.SEEK_MODE_FAST else PlaybackService.SEEK_MODE_EXACT
                )
            }
            controller.sendCustomCommand(
                SessionCommand(PlaybackService.ACTION_SET_SEEK_PARAMETERS, Bundle.EMPTY),
                args
            )
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.Black
    ) { innerPadding ->
        val touchSlop = with(LocalDensity.current) { 16.dp.toPx() } // Standard touch slop

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color.Black)
                .pointerInput(
                    playerController,
                    duration,
                    showSpeedDialog,
                    showAdjustments,
                    showTrackSelection,
                    showSettings
                ) {
                    if (isInPiPMode || showSpeedDialog || showAdjustments || showTrackSelection || showSettings) return@pointerInput

                    awaitEachGesture {
                        val firstDown = awaitFirstDown(requireUnconsumed = false)

                        // Let the slider handle its own touches
                        if (firstDown.isConsumed) return@awaitEachGesture

                        // UX Dead Zone: Ignore top and bottom areas if controls are visible to let them handle touches
                        val isTouchInBottomZone = firstDown.position.y > size.height * 0.65f
                        val isTouchInTopZone = firstDown.position.y < size.height * 0.20f
                        if (showControls && (isTouchInBottomZone || isTouchInTopZone)) return@awaitEachGesture

                        var dragConsumed = false
                        var isLongPress = false
                        var isPinching = false
                        var isHorizontalSwipe = false
                        var hasChildConsumed = false
                        var swipeDeltaX = 0f

                        val startPos = firstDown.position
                        val startTime = System.currentTimeMillis()
                        var lastPosition = startPos

                        do {
                            val event = awaitPointerEvent()
                            if (event.changes.any { it.isConsumed }) {
                                hasChildConsumed = true
                                break
                            }

                            val changes = event.changes
                            if (changes.isNotEmpty()) lastPosition = changes.last().position

                            if (changes.size == 1) {
                                val change = changes[0]
                                if (change.pressed) {
                                    val timeSincePinch = System.currentTimeMillis() - lastPinchEndTime
                                    if (timeSincePinch < 300) {
                                        change.consume()
                                        continue
                                    }

                                    val dragAmount = change.position - startPos
                                    val timeElapsed = System.currentTimeMillis() - startTime

                                    if (!dragConsumed && !isLongPress) {
                                        if (dragAmount.getDistance() > touchSlop) {
                                            dragConsumed = true
                                            // Detect Swipe Direction
                                            if (kotlin.math.abs(dragAmount.x) > kotlin.math.abs(dragAmount.y)) {
                                                isHorizontalSwipe = true
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

                                    if (isHorizontalSwipe) {
                                        // Just track the distance, do nothing until finger lifts
                                        swipeDeltaX = change.position.x - startPos.x
                                        change.consume()
                                    } else if (isBrightnessDragging) {
                                        val dragDeltaY = change.position.y - change.previousPosition.y
                                        val delta = (-dragDeltaY / size.height) * gestureSensitivity
                                        screenBrightness = (screenBrightness.takeIf { it >= 0 } ?: 0.5f) + delta
                                        screenBrightness = screenBrightness.coerceIn(0.01f, 1f)
                                        change.consume()
                                    } else if (isHoldingSpeedBoost) {
                                        change.consume()
                                    }
                                }
                            } else if (changes.size >= 2) {
                                isBrightnessDragging = false
                                isHorizontalSwipe = false // Cancel swipe if they pinch
                                if (isHoldingSpeedBoost) {
                                    isHoldingSpeedBoost = false
                                    playerController?.playbackParameters = PlaybackParameters(playbackSpeed)
                                }

                                val zoom = event.calculateZoom()
                                val pan = event.calculatePan()

                                if (zoom != 1f || pan != Offset.Zero) {
                                    isPinching = true
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

                        // --- GESTURES ENDED (Finger Lifted) ---
                        isBrightnessDragging = false
                        if (isHoldingSpeedBoost) {
                            isHoldingSpeedBoost = false
                            playerController?.playbackParameters = PlaybackParameters(playbackSpeed)
                        }

                        val totalTime = System.currentTimeMillis() - startTime
                        val totalDistance = (lastPosition - startPos).getDistance()

                        // --- QUICK SWIPE EXECUTION ---
                        // If it was a horizontal drag and it was relatively quick (under 500ms)
                        if (!hasChildConsumed && isHorizontalSwipe && totalTime < 500) {
                            val direction = if (swipeDeltaX > 0) 1 else -1
                            val baseMs = 5000L
                            val scaledMs = (baseMs * playbackSpeed).toLong()
                            val frameDurationMs = (1000f / videoFrameRate).toLong()
                            val finalMs = maxOf(scaledMs, frameDurationMs)

                            // Show Top Popup
                            tapOverlayIsRight = direction == 1
                            val finalSeconds = finalMs / 1000f
                            val text = if (finalSeconds < 1f) "${finalMs}ms" else String.format(Locale.US, "%.1fs", finalSeconds)
                            tapOverlayText = if (direction == 1) "+$text" else "-$text"
                            tapOverlayVisible = true

                            // Execute Jump
                            playerController?.let {
                                val newPos = (it.currentPosition + (finalMs * direction)).coerceIn(0L, duration)

                                // Use EXACT mode for these scaled jumps
                                val exactArgs = Bundle().apply { putInt(PlaybackService.KEY_SEEK_MODE, PlaybackService.SEEK_MODE_EXACT) }
                                it.sendCustomCommand(SessionCommand(PlaybackService.ACTION_SET_SEEK_PARAMETERS, Bundle.EMPTY), exactArgs)

                                it.seekTo(newPos)
                                currentPosition = newPos

                                scope.launch {
                                    delay(800) // Keep popup visible briefly
                                    tapOverlayVisible = false
                                }
                            }
                        }
                        // --- TAP ACCUMULATOR LOGIC ---
                        // If it wasn't a drag, swipe, long press, or pinch, it's a tap!
                        else if (!hasChildConsumed && !dragConsumed && !isLongPress && !isPinching && totalDistance < touchSlop && totalTime < 300) {
                            val tapOnRight = startPos.x > size.width / 2

                            if (tapCount > 0 && tapOverlayIsRight != tapOnRight) {
                                tapCount = 1
                                tapOverlayIsRight = tapOnRight
                            } else {
                                tapCount++
                                tapOverlayIsRight = tapOnRight
                            }

                            if (tapCount >= 2) {
                                val baseSeconds = when (tapCount) {
                                    2 -> 5
                                    3 -> 10
                                    else -> 30
                                }
                                val scaledSeconds = baseSeconds * playbackSpeed
                                val frameDurationSeconds = 1f / videoFrameRate
                                val finalSeconds = maxOf(scaledSeconds, frameDurationSeconds)

                                val text = if (finalSeconds < 1f) "${(finalSeconds * 1000).toInt()}ms" else String.format(Locale.US, "%.1fs", finalSeconds)
                                tapOverlayText = if (tapOnRight) "+$text" else "-$text"
                                tapOverlayVisible = true
                            }

                            tapJob?.cancel()
                            tapJob = scope.launch {
                                delay(300)

                                if (tapCount == 1) {
                                    showControls = !showControls
                                } else {
                                    val baseMs = when (tapCount) {
                                        2 -> 5000L
                                        3 -> 10000L
                                        else -> 30000L
                                    }
                                    val scaledMs = (baseMs * playbackSpeed).toLong()
                                    val frameDurationMs = (1000f / videoFrameRate).toLong()
                                    val finalMs = maxOf(scaledMs, frameDurationMs)
                                    val direction = if (tapOverlayIsRight) 1 else -1

                                    playerController?.let {
                                        val newPos = (it.currentPosition + (finalMs * direction)).coerceIn(0L, duration)

                                        // Use EXACT mode for these scaled jumps
                                        val exactArgs = Bundle().apply { putInt(PlaybackService.KEY_SEEK_MODE, PlaybackService.SEEK_MODE_EXACT) }
                                        it.sendCustomCommand(SessionCommand(PlaybackService.ACTION_SET_SEEK_PARAMETERS, Bundle.EMPTY), exactArgs)

                                        it.seekTo(newPos)
                                        currentPosition = newPos
                                    }
                                }

                                tapCount = 0
                                delay(400)
                                tapOverlayVisible = false
                            }
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            if (videoUri != null && playerController != null) {
                if (hasVideoTrack) {
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
                } else {
                    AudioOnlyState(isPlaying = isPlaying)
                }

                if (isLoading && !isSliderScrubbing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(48.dp),
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }

                AnimatedVisibility(
                    visible = tapOverlayVisible,
                    enter = fadeIn() + androidx.compose.animation.expandVertically(expandFrom = Alignment.Top),
                    exit = fadeOut() + androidx.compose.animation.shrinkVertically(shrinkTowards = Alignment.Top),
                    modifier = Modifier.align(Alignment.TopCenter)
                ) {
                    TopSeekPopup(
                        text = tapOverlayText,
                        isRight = tapOverlayIsRight
                    )
                }

                if (isBrightnessDragging) {
                    BrightnessOverlay(brightness = screenBrightness)
                }

                if (isHoldingSpeedBoost) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = 32.dp),
                        contentAlignment = Alignment.TopCenter
                    ) {
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
                        thumbnails = thumbnails,
                        onPlayPauseToggle = {
                            playerController?.let {
                                if (it.isPlaying) it.pause() else it.play()
                            }
                        },
                        onSeek = { position ->
                            playerController?.let {
                                it.seekTo(position)
                                currentPosition = position
                            }
                        },
                        onScrubStart = {
                            isSliderScrubbing = true
                            wasPlayingBeforeSliderScrub = playerController?.playWhenReady == true
                            setSeekMode(true)
                            playerController?.pause()
                        },
                        onScrubEnd = {
                            isSliderScrubbing = false
                            setSeekMode(false)
                            if (wasPlayingBeforeSliderScrub) playerController?.play()
                        },
                        onRewind = {
                            playerController?.let {
                                val newPos = (it.currentPosition - 10000).coerceAtLeast(0)
                                it.seekTo(newPos)
                                currentPosition = newPos
                            }
                        },
                        onForward = {
                            playerController?.let {
                                val newPos = (it.currentPosition + 10000).coerceAtMost(duration)
                                it.seekTo(newPos)
                                currentPosition = newPos
                            }
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
                        onSettingsClick = {
                            showAdjustments = false
                            showSettings = true
                        },
                        onDismiss = { showAdjustments = false }
                    )
                }

                if (showSettings && !isInPiPMode) {
                    SettingsDialog(
                        seekSensitivity = seekSensitivity,
                        gestureSensitivity = gestureSensitivity,
                        autoLoadSubtitles = autoLoadSubtitles,
                        prefAudioLang = prefAudioLang,
                        prefSubLang = prefSubLang,
                        onSeekSensitivityChange = {
                            seekSensitivity = it
                            scope.launch {
                                context.dataStore.edit { settings ->
                                    settings[SEEK_SENSITIVITY] = it
                                }
                            }
                        },
                        onGestureSensitivityChange = {
                            gestureSensitivity = it
                            scope.launch {
                                context.dataStore.edit { settings ->
                                    settings[GESTURE_SENSITIVITY] = it
                                }
                            }
                        },
                        onAutoLoadSubtitlesChange = {
                            autoLoadSubtitles = it
                            scope.launch {
                                context.dataStore.edit { settings ->
                                    settings[AUTO_LOAD_SUBTITLES] = it
                                }
                            }
                        },
                        onPrefAudioLangChange = {
                            prefAudioLang = it
                            scope.launch {
                                context.dataStore.edit { settings ->
                                    settings[PREFERRED_AUDIO_LANG] = it
                                }
                            }
                        },
                        onPrefSubLangChange = {
                            prefSubLang = it
                            scope.launch {
                                context.dataStore.edit { settings ->
                                    settings[PREFERRED_SUBTITLE_LANG] = it
                                }
                            }
                        },
                        onDismiss = { showSettings = false }
                    )
                }

            } else if (videoUri == null) {
                EmptyState()
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
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
                    Text(
                        "Audio",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.secondary
                    )
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
                    Text(
                        "Subtitles",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.secondary
                    )
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
                imageVector = if (label == "None") Icons.AutoMirrored.Filled.VolumeOff else if (label.length > 3) Icons.Default.Subtitles else Icons.Default.Audiotrack,
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
    thumbnails: Map<Long, ImageBitmap> = emptyMap(),
    onPlayPauseToggle: () -> Unit,
    onSeek: (Long) -> Unit,
    onScrubStart: () -> Unit,
    onScrubEnd: () -> Unit,
    onRewind: () -> Unit,
    onForward: () -> Unit,
    onSpeedClick: () -> Unit,
    onAdjustmentsClick: () -> Unit,
    onMuteToggle: () -> Unit,
    onTrackSelectionClick: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ControlIcon(
                icon = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                label = "",
                onClick = onMuteToggle
            )
            Spacer(modifier = Modifier.width(20.dp))
            ControlIcon(
                icon = Icons.Default.Speed,
                label = String.format(Locale.US, "%.1fx", playbackSpeed),
                onClick = onSpeedClick
            )
        }

        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ControlIcon(
                icon = Icons.Default.Subtitles,
                label = "",
                onClick = onTrackSelectionClick
            )
            Spacer(modifier = Modifier.width(20.dp))
            ControlIcon(
                icon = Icons.Default.Settings,
                label = "",
                onClick = onAdjustmentsClick
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 32.dp, start = 16.dp, end = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatTime(currentPosition),
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )

                CustomSlider(
                    value = currentPosition.toFloat(),
                    onValueChange = { onSeek(it.toLong()) },
                    valueRange = 0f..duration.coerceAtLeast(0).toFloat(),
                    thumbnails = thumbnails,
                    modifier = Modifier
                        .weight(1f)
                        .height(16.dp)
                        .padding(horizontal = 8.dp),
                    onScrubStart = onScrubStart,
                    onScrubEnd = onScrubEnd,
                )

                Text(
                    text = formatTime(duration),
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Box(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    IconButton(onClick = onRewind) {
                        Icon(
                            Icons.Default.Replay10,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                    IconButton(
                        onClick = onPlayPauseToggle,
                        modifier = Modifier.size(64.dp)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(56.dp),
                            tint = Color.White
                        )
                    }
                    IconButton(onClick = onForward) {
                        Icon(
                            Icons.Default.Forward10,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CustomSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    thumbnails: Map<Long, ImageBitmap> = emptyMap(),
    onScrubStart: () -> Unit = {},
    onScrubEnd: () -> Unit = {},
) {
    var isScrubbing by remember { mutableStateOf(false) }
    var sliderValue by remember { mutableFloatStateOf(value) }
    var lastSeekTime by remember { mutableLongStateOf(0L) }

    LaunchedEffect(value) {
        if (!isScrubbing) {
            sliderValue = value
        }
    }

    Box(modifier = modifier, contentAlignment = Alignment.TopCenter) {
        if (isScrubbing) {
            Column(
                modifier = Modifier.offset(y = (-140).dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Background Thumbnail Cache Preview
                val nearestTime = thumbnails.keys.minByOrNull { abs(it - sliderValue.toLong()) }
                val thumbnail = nearestTime?.let { thumbnails[it] }

                Surface(
                    modifier = Modifier
                        .width(160.dp)
                        .aspectRatio(16f / 9f),
                    color = Color.DarkGray,
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                ) {
                    if (thumbnail != null) {
                        Image(
                            bitmap = thumbnail,
                            contentDescription = null,
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(12.dp),
                    tonalElevation = 8.dp
                ) {
                    Text(
                        text = "${formatTime(sliderValue.toLong())} / ${formatTime(valueRange.endInclusive.toLong())}",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }

        Slider(
            value = sliderValue,
            onValueChange = { newValue ->
                sliderValue = newValue
                if (!isScrubbing) {
                    isScrubbing = true
                    onScrubStart()
                }

                val currentTime = System.currentTimeMillis()
                if (currentTime - lastSeekTime > 50) { // Reduced throttle for smoother visual scrub
                    onValueChange(newValue)
                    lastSeekTime = currentTime
                }
            },
            onValueChangeFinished = {
                onValueChange(sliderValue)
                isScrubbing = false
                onScrubEnd()
            },
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = Color.White.copy(alpha = 0.3f)
            )
        )
    }
}

@Composable
fun AudioOnlyState(isPlaying: Boolean) {
    // Subtle pulsing animation when playing
    val infiniteTransition = rememberInfiniteTransition(label = "audio_pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isPlaying) 1.15f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "audio_pulse_scale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212)), // Slightly lighter than pure black for contrast
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .graphicsLayer(scaleX = scale, scaleY = scale)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Audiotrack,
                    contentDescription = "Audio Playing",
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Audio Playback",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White.copy(alpha = 0.7f)
            )
        }
    }
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
                            border = if (!isSelected) androidx.compose.foundation.BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.outline
                            ) else null
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
    onSettingsClick: () -> Unit,
    onDismiss: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth(0.9f)
            .padding(16.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { },
        shape = MaterialTheme.shapes.extraLarge,
        color = Color.Black.copy(alpha = 0.95f),
        tonalElevation = 12.dp
    ) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Video Adjustments",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold
                )
                IconButton(onClick = onSettingsClick) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            AdjustmentSlider(
                label = "Brightness",
                value = brightness,
                range = -1f..1f,
                onValueChange = onBrightnessChange
            )

            Spacer(modifier = Modifier.height(24.dp))

            AdjustmentSlider(
                label = "Contrast",
                value = contrast,
                range = -1f..1f,
                onValueChange = onContrastChange
            )

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "DONE",
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(8.dp)
                    .clickable { onDismiss() },
                color = Color.White,
                fontWeight = FontWeight.Black,
                fontSize = 16.sp
            )
        }
    }
}

@Composable
fun SettingsDialog(
    seekSensitivity: Float,
    gestureSensitivity: Float,
    autoLoadSubtitles: Boolean,
    prefAudioLang: String,
    prefSubLang: String,
    onSeekSensitivityChange: (Float) -> Unit,
    onGestureSensitivityChange: (Float) -> Unit,
    onAutoLoadSubtitlesChange: (Boolean) -> Unit,
    onPrefAudioLangChange: (String) -> Unit,
    onPrefSubLangChange: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth(0.9f)
            .height(500.dp)
            .padding(16.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = Color.Black.copy(alpha = 0.95f),
        tonalElevation = 12.dp
    ) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "Global Settings",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.ExtraBold
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text("GESTURES", style = MaterialTheme.typography.labelLarge, color = Color.Gray)
            Spacer(modifier = Modifier.height(8.dp))

            AdjustmentSlider(
                label = "Seek Sensitivity",
                value = seekSensitivity,
                range = 0.01f..100f,
                onValueChange = onSeekSensitivityChange
            )

            Spacer(modifier = Modifier.height(24.dp))

            AdjustmentSlider(
                label = "Brightness Gesture",
                value = gestureSensitivity,
                range = 0.01f..100f,
                onValueChange = onGestureSensitivityChange
            )

            Spacer(modifier = Modifier.height(32.dp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.2f))
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                "TRACK PREFERENCES",
                style = MaterialTheme.typography.labelLarge,
                color = Color.Gray
            )
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Auto-load Subtitles", color = Color.White)
                Switch(
                    checked = autoLoadSubtitles,
                    onCheckedChange = onAutoLoadSubtitlesChange
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            LanguageField(
                label = "Preferred Audio Language (ISO-639)",
                value = prefAudioLang,
                onValueChange = onPrefAudioLangChange
            )

            Spacer(modifier = Modifier.height(16.dp))

            LanguageField(
                label = "Preferred Subtitle Language",
                value = prefSubLang,
                onValueChange = onPrefSubLangChange
            )

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "CLOSE",
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(8.dp)
                    .clickable { onDismiss() },
                color = Color.White,
                fontWeight = FontWeight.Black,
                fontSize = 16.sp
            )
        }
    }
}

@Composable
fun LanguageField(label: String, value: String, onValueChange: (String) -> Unit) {
    Column {
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = Color.LightGray)
        Spacer(modifier = Modifier.height(4.dp))
        TextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("e.g. eng, fin", color = Color.DarkGray) },
            singleLine = true,
            colors = TextFieldDefaults.colors(
                unfocusedContainerColor = Color.White.copy(alpha = 0.1f),
                focusedContainerColor = Color.White.copy(alpha = 0.15f),
                unfocusedTextColor = Color.White,
                focusedTextColor = Color.White
            )
        )
    }
}

@Composable
fun TopSeekPopup(text: String, isRight: Boolean) {
    Box(
        modifier = Modifier
            // Push it down slightly from the very top edge
            .padding(top = 48.dp)
            .background(
                color = Color.Black.copy(alpha = 0.75f),
                shape = RoundedCornerShape(24.dp) // Pill shape
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (isRight) Icons.Default.Forward10 else Icons.Default.Replay10,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = text,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        }
    }
}

@Composable
fun AdjustmentSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float> = -1f..1f,
    onValueChange: (Float) -> Unit
) {
    val useLogScale = range.start > 0 && (range.endInclusive / range.start) >= 100f

    val sliderValue = if (useLogScale) log10(value) else value
    val sliderRange = if (useLogScale) {
        log10(range.start)..log10(range.endInclusive)
    } else {
        range
    }

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Text(
                text = String.format(Locale.US, "%.2f", value),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Slider(
            value = sliderValue,
            onValueChange = {
                val newValue = if (useLogScale) 10f.pow(it) else it
                onValueChange(newValue)
            },
            valueRange = sliderRange,
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color.White,
                inactiveTrackColor = Color.White.copy(alpha = 0.3f)
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