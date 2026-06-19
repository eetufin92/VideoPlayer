package com.eetu.videoplayer

import android.content.Intent
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.Effect
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Log
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.effect.Brightness
import androidx.media3.effect.Contrast
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
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
        const val FIVE_MINUTES_MS = 300_000
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
            // 1. Set the Forward Buffer (The +5 minutes)
            .setBufferDurationsMs(
                FIVE_MINUTES_MS, // Min buffer: Try to always keep 5 mins ahead loaded
                FIVE_MINUTES_MS, // Max buffer: Don't load more than 5 mins ahead to save RAM
                100,             // Min buffer to start playback (100ms for instant start)
                100              // Min buffer to resume playback after seek
            )

            // 2. Set the Back Buffer (The -5 minutes)
            // The second parameter 'true' is critical: it ensures the back buffer
            // aligns specifically with keyframes.
            .setBackBuffer(FIVE_MINUTES_MS, true)

            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val renderersFactory = DefaultRenderersFactory(this).apply {
            setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF)
            forceEnableMediaCodecAsynchronousQueueing()
        }

        // 4. Build the Player
        val player = ExoPlayer.Builder(this, renderersFactory)
            .setMediaSourceFactory(DefaultMediaSourceFactory(this).setDataSourceFactory(cacheDataSourceFactory))
            .setLoadControl(loadControl)
            .build()

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

    private inner class MediaSessionCallback : MediaSession.Callback {

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
                        // Ensure we use CLOSEST_SYNC for fast scrubbing
                        player.setSeekParameters(
                            if (mode == SEEK_MODE_FAST) SeekParameters.CLOSEST_SYNC else SeekParameters.DEFAULT
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