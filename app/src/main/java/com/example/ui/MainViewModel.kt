package com.example.ui

import android.app.Application
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.LocalMusicScanner
import com.example.data.LyricLine
import com.example.data.LyricParser
import com.example.data.LyricsService
import com.example.data.SampleTracks
import com.example.data.TrackEntity
import com.example.player.LyricPlayer
import com.example.player.PlaybackState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val TAG = "MainViewModel"
    private val database = AppDatabase.getDatabase(application)
    private val trackDao = database.trackDao()
    val lyricPlayer = LyricPlayer(application)

    // Tracks flow from database
    val tracks: StateFlow<List<TrackEntity>> = trackDao.getAllTracks()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _selectedTrack = MutableStateFlow<TrackEntity?>(null)
    val selectedTrack: StateFlow<TrackEntity?> = _selectedTrack.asStateFlow()

    // Parse LRC lines dynamically when selected track changes
    val currentLrcLines: StateFlow<List<LyricLine>> = _selectedTrack
        .map { track -> LyricParser.parseLrc(track?.lrcContent) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val playbackState: StateFlow<PlaybackState> = lyricPlayer.playbackState
    val currentPositionMs: StateFlow<Long> = lyricPlayer.currentPositionMs
    val durationMs: StateFlow<Long> = lyricPlayer.durationMs

    private val _isSearchingLyrics = MutableStateFlow(false)
    val isSearchingLyrics: StateFlow<Boolean> = _isSearchingLyrics.asStateFlow()

    private val _isScanningMedia = MutableStateFlow(false)
    val isScanningMedia: StateFlow<Boolean> = _isScanningMedia.asStateFlow()

    private val _themeDarkMode = MutableStateFlow(true) // Default to beautiful dark mode
    val themeDarkMode: StateFlow<Boolean> = _themeDarkMode.asStateFlow()

    private val _playbackVolume = MutableStateFlow(0.8f)
    val playbackVolume: StateFlow<Float> = _playbackVolume.asStateFlow()

    private val _metadataArtistAndAlbum = MutableStateFlow<Pair<String?, String?>>(Pair(null, null))
    val metadataArtistAndAlbum: StateFlow<Pair<String?, String?>> = _metadataArtistAndAlbum.asStateFlow()

    init {
        // Preload sample tracks if the database is completely empty
        viewModelScope.launch {
            tracks.collect { list ->
                if (list.isEmpty()) {
                    preloadSampleSongs()
                } else if (_selectedTrack.value == null) {
                    // Auto-select the first track on launch
                    val first = list.firstOrNull()
                    if (first != null) {
                        _selectedTrack.value = first
                        fetchTrackMetadataFromFile(application, first)
                    }
                }
            }
        }
    }

    private suspend fun preloadSampleSongs() {
        withContext(Dispatchers.IO) {
            Log.d(TAG, "Preloading sample songs into empty database...")
            trackDao.insertTracks(SampleTracks.list)
        }
    }

    fun toggleTheme() {
        _themeDarkMode.value = !_themeDarkMode.value
    }

    fun setPlaybackVolume(volume: Float) {
        _playbackVolume.value = volume
        lyricPlayer.setVolume(volume)
    }

    /**
     * Extracts artist and album from the local music file's metadata dynamically.
     */
    fun fetchTrackMetadataFromFile(context: Context, track: TrackEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            var artist: String? = null
            var album: String? = null
            if (!track.isSample && track.filePath.isNotEmpty()) {
                val retriever = android.media.MediaMetadataRetriever()
                try {
                    val uri = android.net.Uri.parse(track.filePath)
                    retriever.setDataSource(context, uri)
                    artist = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_ARTIST)
                    album = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_ALBUM)
                    Log.d(TAG, "Retrieved local metadata: Artist = $artist, Album = $album")
                } catch (e: Exception) {
                    Log.e(TAG, "Error extracting file metadata: ${e.message}")
                } finally {
                    try {
                        retriever.release()
                    } catch (e: Exception) {}
                }
            }
            // Update state flow with extracted metadata, fallback to track entity info
            _metadataArtistAndAlbum.value = Pair(artist ?: track.artist, album ?: track.album)
        }
    }

    fun selectTrack(track: TrackEntity) {
        _selectedTrack.value = track
        // Play the track automatically when selected
        lyricPlayer.playTrack(track.id, track.filePath)
        fetchTrackMetadataFromFile(getApplication(), track)
    }

    /**
     * Scans local files on the device using MediaStore.
     */
    fun scanLocalMusic(context: Context) {
        viewModelScope.launch {
            _isScanningMedia.value = true
            try {
                val scannedTracks = withContext(Dispatchers.IO) {
                    LocalMusicScanner.scanLocalMedia(context)
                }
                
                if (scannedTracks.isNotEmpty()) {
                    withContext(Dispatchers.IO) {
                        trackDao.insertTracks(scannedTracks)
                    }
                    Toast.makeText(context, "Scanned and added ${scannedTracks.size} local music files!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "No local MP3/FLAC files found on device.", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed scanning local files: ${e.message}", e)
                Toast.makeText(context, "Error scanning: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            } finally {
                _isScanningMedia.value = false
            }
        }
    }

    /**
     * Searches and syncs lyrics from Musixmatch & Gemini.
     */
    fun searchAndSyncLyrics(context: Context, track: TrackEntity, customTitle: String? = null, customArtist: String? = null) {
        viewModelScope.launch {
            _isSearchingLyrics.value = true
            val title = customTitle?.trim() ?: track.title
            val artist = customArtist?.trim() ?: track.artist
            
            try {
                Toast.makeText(context, "Syncing lyrics for '$title'...", Toast.LENGTH_SHORT).show()
                
                // Prioritize local cached lyrics or fallback if network is offline or api limit reached
                val lyricsResult = try {
                    LyricsService.searchLyrics(title, artist)
                } catch (e: Exception) {
                    if (track.lrcContent != null) {
                        Log.d(TAG, "API call failed, but offline cached lyrics are available. Prioritizing cache.")
                        track.lrcContent
                    } else {
                        throw e
                    }
                }
                
                val updatedTrack = track.copy(
                    title = title,
                    artist = artist,
                    lrcContent = lyricsResult,
                    lastSynced = System.currentTimeMillis()
                )
                
                withContext(Dispatchers.IO) {
                    trackDao.updateTrack(updatedTrack)
                }
                
                // If the selected track is the one we updated, refresh the view
                if (_selectedTrack.value?.id == track.id) {
                    _selectedTrack.value = updatedTrack
                }
                
                Toast.makeText(context, "Lyrics synced successfully!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Log.e(TAG, "Lyrics search failed: ${e.message}", e)
                Toast.makeText(context, "Failed to sync lyrics: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            } finally {
                _isSearchingLyrics.value = false
            }
        }
    }

    /**
     * Caches lyrics locally in the Room database.
     */
    fun cacheLyrics(context: Context, track: TrackEntity, lyrics: String) {
        viewModelScope.launch {
            val updated = track.copy(
                lrcContent = lyrics,
                lastSynced = System.currentTimeMillis()
            )
            withContext(Dispatchers.IO) {
                trackDao.updateTrack(updated)
            }
            if (_selectedTrack.value?.id == track.id) {
                _selectedTrack.value = updated
            }
            Toast.makeText(context, "Lyrics cached locally for offline use!", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Removes cached lyrics from the database.
     */
    fun clearLyricsCache(context: Context, track: TrackEntity) {
        viewModelScope.launch {
            val updated = track.copy(
                lrcContent = null,
                lastSynced = 0L
            )
            withContext(Dispatchers.IO) {
                trackDao.updateTrack(updated)
            }
            if (_selectedTrack.value?.id == track.id) {
                _selectedTrack.value = updated
            }
            Toast.makeText(context, "Local lyrics cache cleared.", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Integrated Metadata Tagging: Update song info inside Room.
     */
    fun updateTrackMetadata(context: Context, track: TrackEntity, newTitle: String, newArtist: String, newAlbum: String, newFormat: String) {
        viewModelScope.launch {
            val updated = track.copy(
                title = newTitle.trim(),
                artist = newArtist.trim(),
                album = newAlbum.trim(),
                format = newFormat.trim().uppercase()
            )
            
            withContext(Dispatchers.IO) {
                trackDao.updateTrack(updated)
            }
            
            if (_selectedTrack.value?.id == track.id) {
                _selectedTrack.value = updated
            }
            
            Toast.makeText(context, "Metadata tags updated!", Toast.LENGTH_SHORT).show()
            
            // Auto re-search lyrics on metadata tag change to ensure lyrics are accurate!
            searchAndSyncLyrics(context, updated)
        }
    }

    /**
     * Export option to export current lyrics as a standard LRC/TXT file or share it.
     */
    fun exportLyrics(context: Context, track: TrackEntity, format: String) {
        val lyrics = track.lrcContent ?: LyricsService.generateLocalMockLrc(track.title, track.artist)
        val fileExtension = if (format.lowercase() == "lrc") "lrc" else "txt"
        val fileName = "${track.title.replace(" ", "_")}_Lyrics.$fileExtension"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Modern Scoped Storage writing via MediaStore to the Downloads directory (No permissions required!)
            viewModelScope.launch {
                try {
                    val resolver = context.contentResolver
                    val contentValues = ContentValues().apply {
                        put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                        put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                        put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    }
                    val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                    if (uri != null) {
                        resolver.openOutputStream(uri)?.use { outputStream ->
                            outputStream.write(lyrics.toByteArray())
                        }
                        Toast.makeText(context, "Exported successfully to Downloads folder as $fileName", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(context, "Failed to insert MediaStore record", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Export via MediaStore failed: ${e.message}", e)
                    Toast.makeText(context, "Export failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            // Legacy Storage writing (requires permissions, fall back to sharing if permission not granted)
            try {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!downloadsDir.exists()) {
                    downloadsDir.mkdirs()
                }
                val file = File(downloadsDir, fileName)
                FileOutputStream(file).use { out ->
                    out.write(lyrics.toByteArray())
                }
                Toast.makeText(context, "Saved to Downloads: ${file.absolutePath}", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Log.e(TAG, "Legacy file export failed. Falling back to Share...", e)
                shareLyrics(context, track)
            }
        }
    }

    /**
     * Share option to share raw lyrics as plain text.
     */
    fun shareLyrics(context: Context, track: TrackEntity) {
        val lyrics = track.lrcContent ?: LyricsService.generateLocalMockLrc(track.title, track.artist)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "${track.title} - ${track.artist} Lyrics")
            putExtra(Intent.EXTRA_TEXT, "Lyrics for ${track.title} by ${track.artist}:\n\n$lyrics")
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share Lyrics via"))
    }

    override fun onCleared() {
        super.onCleared()
        lyricPlayer.stop()
    }
}
