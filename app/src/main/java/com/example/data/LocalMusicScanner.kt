package com.example.data

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import android.util.Log

object LocalMusicScanner {
    private const val TAG = "LocalMusicScanner"

    /**
     * Scans the MediaStore for MP3 and FLAC audio files.
     * Returns a list of TrackEntity representing local music files.
     */
    fun scanLocalMedia(context: Context): List<TrackEntity> {
        val tracks = mutableListOf<TrackEntity>()
        
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.MIME_TYPE
        )

        // Only search for audio formats like MP3 and FLAC
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND (${MediaStore.Audio.Media.MIME_TYPE} LIKE ? OR ${MediaStore.Audio.Media.MIME_TYPE} LIKE ?)"
        val selectionArgs = arrayOf("audio/mpeg", "audio/flac")
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        try {
            val queryUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            } else {
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            }

            context.contentResolver.query(
                queryUri,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                val mimeTypeColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val title = cursor.getString(titleColumn) ?: "Unknown Track"
                    val artist = cursor.getString(artistColumn) ?: "Unknown Artist"
                    val album = cursor.getString(albumColumn) ?: "Unknown Album"
                    val duration = cursor.getLong(durationColumn)
                    val filePath = cursor.getString(dataColumn) ?: ""
                    val mimeType = cursor.getString(mimeTypeColumn) ?: ""
                    val format = if (mimeType.contains("flac")) "FLAC" else "MP3"

                    // Use Content URI instead of raw filePath on modern Android versions for streaming playback compatibility
                    val contentUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id).toString()

                    tracks.add(
                        TrackEntity(
                            title = title,
                            artist = artist,
                            album = album,
                            duration = duration,
                            filePath = contentUri, // Content URI allows MediaPlayer to play standard media files easily
                            format = format,
                            isSample = false
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error scanning local MediaStore: ${e.message}", e)
        }

        return tracks
    }
}
