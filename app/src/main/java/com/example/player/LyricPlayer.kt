package com.example.player

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class PlaybackState {
    object Idle : PlaybackState()
    object Loading : PlaybackState()
    object Playing : PlaybackState()
    object Paused : PlaybackState()
    data class Error(val message: String) : PlaybackState()
}

class LyricPlayer(private val context: Context) {
    private val TAG = "LyricPlayer"
    private var mediaPlayer: MediaPlayer? = null
    private var volume: Float = 0.8f
    
    private val _playbackState = MutableStateFlow<PlaybackState>(PlaybackState.Idle)
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private var activeTrackId: Long? = null
    private var trackingJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    fun playTrack(trackId: Long, sourcePath: String) {
        stop()
        _playbackState.value = PlaybackState.Loading
        activeTrackId = trackId

        try {
            mediaPlayer = MediaPlayer().apply {
                setVolume(volume, volume)
                if (sourcePath.startsWith("http")) {
                    setDataSource(sourcePath)
                } else {
                    setDataSource(context, Uri.parse(sourcePath))
                }
                
                setOnPreparedListener { mp ->
                    _durationMs.value = mp.duration.toLong()
                    mp.start()
                    _playbackState.value = PlaybackState.Playing
                    startTrackingPosition()
                }

                setOnCompletionListener {
                    _playbackState.value = PlaybackState.Paused
                    seekTo(0)
                }

                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "MediaPlayer Error: what=$what, extra=$extra")
                    _playbackState.value = PlaybackState.Error("Error playing file ($what, $extra)")
                    false
                }

                prepareAsync()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play track: ${e.message}", e)
            _playbackState.value = PlaybackState.Error("Failed to load file: ${e.localizedMessage}")
        }
    }

    fun togglePlayPause() {
        val player = mediaPlayer ?: return
        val currentState = _playbackState.value

        try {
            if (currentState is PlaybackState.Playing) {
                player.pause()
                _playbackState.value = PlaybackState.Paused
                stopTrackingPosition()
            } else if (currentState is PlaybackState.Paused) {
                player.start()
                _playbackState.value = PlaybackState.Playing
                startTrackingPosition()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in togglePlayPause: ${e.message}")
        }
    }

    fun play() {
        val player = mediaPlayer ?: return
        if (_playbackState.value is PlaybackState.Paused) {
            player.start()
            _playbackState.value = PlaybackState.Playing
            startTrackingPosition()
        }
    }

    fun pause() {
        val player = mediaPlayer ?: return
        if (_playbackState.value is PlaybackState.Playing) {
            player.pause()
            _playbackState.value = PlaybackState.Paused
            stopTrackingPosition()
        }
    }

    fun seekTo(positionMs: Long) {
        val player = mediaPlayer ?: return
        try {
            player.seekTo(positionMs.toInt())
            _currentPositionMs.value = positionMs
        } catch (e: Exception) {
            Log.e(TAG, "Error seeking: ${e.message}")
        }
    }

    fun stop() {
        stopTrackingPosition()
        try {
            mediaPlayer?.apply {
                if (isPlaying) {
                    stop()
                }
                release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping player: ${e.message}")
        } finally {
            mediaPlayer = null
            activeTrackId = null
            _playbackState.value = PlaybackState.Idle
            _currentPositionMs.value = 0L
            _durationMs.value = 0L
        }
    }

    private fun startTrackingPosition() {
        stopTrackingPosition()
        trackingJob = scope.launch {
            while (true) {
                mediaPlayer?.let { mp ->
                    if (mp.isPlaying) {
                        _currentPositionMs.value = mp.currentPosition.toLong()
                    }
                }
                delay(50) // Tick every 50ms for ultra-smooth karaoke tracking
            }
        }
    }

    private fun stopTrackingPosition() {
        trackingJob?.cancel()
        trackingJob = null
    }

    fun getActiveTrackId(): Long? = activeTrackId

    fun setVolume(vol: Float) {
        volume = vol.coerceIn(0f, 1f)
        try {
            mediaPlayer?.setVolume(volume, volume)
        } catch (e: Exception) {
            Log.e(TAG, "Error setting volume in MediaPlayer: ${e.message}")
        }
    }

    fun getVolume(): Float = volume
}
