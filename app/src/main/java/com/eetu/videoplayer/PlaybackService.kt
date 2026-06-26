package com.eetu.videoplayer

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.util.EventLogger
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.mkv.MatroskaExtractor
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import java.io.File

@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    private var simpleCache: SimpleCache? = null

    companion object {
        const val ACTION_SET_VIDEO_EFFECTS = "SET_VIDEO_EFFECTS"
        const val ACTION_SET_SEEK_PARAMETERS = "SET_SEEK_PARAMETERS"
        const val KEY_BRIGHTNESS = "KEY_BRIGHTNESS"
        const val KEY_CONTRAST = "KEY_CONTRAST"
        const val KEY_SEEK_MODE = "KEY_SEEK_MODE"
        const val SEEK_MODE_EXACT = 0
        const val SEEK_MODE_FAST = 1
        const val MAX_BUFFER_MS = 50_000
        const val MIN_BUFFER_MS = 5_000
        const val BACK_BUFFER_MS = 30_000
        const val BUFFER_FOR_PLAYBACK_MS = 250
        const val BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS = 500
    }

    override fun onCreate() {
        super.onCreate()

        // 1. Setup a massive local disk cache (1GB limit for large NAS files)
        val cacheDir = File(cacheDir, "exoplayer_video_cache")
        val cacheEvictor = LeastRecentlyUsedCacheEvictor(1024 * 1024 * 1024)
        val databaseProvider = StandaloneDatabaseProvider(this)
        simpleCache = SimpleCache(cacheDir, cacheEvictor, databaseProvider)

        // 2. Setup the Data Source for content:// URIs
        // DefaultDataSource natively uses ContentDataSource for file managers
        val upstreamFactory = DefaultDataSource.Factory(this)

        // Wrap it in the cache layer
        val cacheDataSourceFactory = CacheDataSource.Factory()
            .setCache(simpleCache!!)
            .setUpstreamDataSourceFactory(upstreamFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)


        val loadControl = DefaultLoadControl.Builder()
            // 1. Set the Forward Buffer
            .setBufferDurationsMs(
                MIN_BUFFER_MS,
                MAX_BUFFER_MS,
                BUFFER_FOR_PLAYBACK_MS,
                BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS
            )

            // 2. Set the Back Buffer
            .setBackBuffer(BACK_BUFFER_MS, true)

            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val renderersFactory = DefaultRenderersFactory(this).apply {
            setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF)
        }

        val extractorsFactory = DefaultExtractorsFactory()

        Log.d("VideoPlayerDebug", "Building ExoPlayer with default ExtractorsFactory")

        val player = ExoPlayer.Builder(this, renderersFactory)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(this, extractorsFactory)
                    .setDataSourceFactory(cacheDataSourceFactory)
            )
            .setLoadControl(loadControl)
            .build()

        player.addAnalyticsListener(EventLogger("VideoPlayerDebug"))

        mediaSession = MediaSession.Builder(this, player)
            .setCallback(MediaSessionCallback())
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        // Return the active session to allow the UI to connect.
        return mediaSession
    }

    // Ensure the service shuts down properly when the app is removed from recent apps.
    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player != null) {
            if (!player.playWhenReady || player.mediaItemCount == 0) {
                stopSelf()
            }
        }
    }

    private class MediaSessionCallback : MediaSession.Callback {

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {

                ACTION_SET_SEEK_PARAMETERS -> {
                    val mode = args.getInt(KEY_SEEK_MODE, SEEK_MODE_EXACT)
                    val player = session.player
                    if (player is ExoPlayer) {
                        // Use PREVIOUS_SYNC for even faster scrubbing in high-res
                        player.setSeekParameters(
                            if (mode == SEEK_MODE_FAST) SeekParameters.PREVIOUS_SYNC else SeekParameters.DEFAULT
                        )
                    }
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
            }
            return super.onCustomCommand(session, controller, customCommand, args)
        }
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        // Essential: Release the cache when the service dies
        simpleCache?.release()
        super.onDestroy()
    }
}