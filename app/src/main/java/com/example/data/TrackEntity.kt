package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tracks")
data class TrackEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,
    val filePath: String,
    val format: String,
    val lrcContent: String? = null,
    val plainContent: String? = null,
    val isSample: Boolean = false,
    val lastSynced: Long = 0
)
