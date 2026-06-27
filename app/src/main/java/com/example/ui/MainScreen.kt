package com.example.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import kotlin.math.abs
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.LyricLine
import com.example.data.TrackEntity
import com.example.data.LyricsService
import com.example.player.PlaybackState
import kotlinx.coroutines.launch

// Color Palette Definition for Clean Minimalism Theme (Dark)
val CleanBg = Color(0xFF1C1B1F)
val CleanCard = Color(0xFF2B2930)
val CleanBorder = Color(0xFF49454F)
val CleanAccent = Color(0xFFD0BCFF)
val CleanDeepAccent = Color(0xFF381E72)
val CleanText = Color(0xFFE6E1E5)
val CleanMuted = Color(0xFF938F99)
val CleanSecondaryText = Color(0xFFCAC4D0)
val CleanBadgeBg = Color(0xFFEADDFF)
val CleanBadgeText = Color(0xFF21005D)
val CleanGreen = Color(0xFF00E676)

// Color Palette Definition for Clean Minimalism Theme (Light)
val CleanBgLight = Color(0xFFF4F3F6)
val CleanCardLight = Color(0xFFFFFFFF)
val CleanBorderLight = Color(0xFFE4E2E6)
val CleanAccentLight = Color(0xFF6750A4)
val CleanDeepAccentLight = Color(0xFFEADDFF)
val CleanTextLight = Color(0xFF1C1B1F)
val CleanMutedLight = Color(0xFF79747E)
val CleanSecondaryTextLight = Color(0xFF49454F)
val CleanBadgeBgLight = Color(0xFFEADDFF)
val CleanBadgeTextLight = Color(0xFF21005D)
val CleanGreenLight = Color(0xFF2E7D32)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // State Collectors
    val tracks by viewModel.tracks.collectAsStateWithLifecycle()
    val selectedTrack by viewModel.selectedTrack.collectAsStateWithLifecycle()
    val lrcLines by viewModel.currentLrcLines.collectAsStateWithLifecycle()
    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
    val currentPositionMs by viewModel.currentPositionMs.collectAsStateWithLifecycle()
    val durationMs by viewModel.durationMs.collectAsStateWithLifecycle()
    val isSearchingLyrics by viewModel.isSearchingLyrics.collectAsStateWithLifecycle()
    val isScanningMedia by viewModel.isScanningMedia.collectAsStateWithLifecycle()
    val themeDarkMode by viewModel.themeDarkMode.collectAsStateWithLifecycle()
    val metadataArtistAndAlbum by viewModel.metadataArtistAndAlbum.collectAsStateWithLifecycle()
    val playbackVolume by viewModel.playbackVolume.collectAsStateWithLifecycle()

    // Dialog & UI states
    var showEditMetadataDialog by remember { mutableStateOf(false) }
    var showExportMenuDialog by remember { mutableStateOf(false) }
    var searchLibraryQuery by remember { mutableStateOf("") }
    var showManualSearchDialog by remember { mutableStateOf(false) }

    // Dynamic Colors based on theme mode
    val currentBgColor = if (themeDarkMode) CleanBg else CleanBgLight
    val currentCardColor = if (themeDarkMode) CleanCard else CleanCardLight
    val currentTextColor = if (themeDarkMode) CleanText else CleanTextLight
    val currentMutedColor = if (themeDarkMode) CleanMuted else CleanMutedLight
    val currentAccentColor = if (themeDarkMode) CleanAccent else CleanAccentLight
    val currentSecondaryAccent = if (themeDarkMode) CleanAccent else CleanAccentLight
    val currentDeepAccent = if (themeDarkMode) CleanDeepAccent else CleanDeepAccentLight
    val currentBorderColor = if (themeDarkMode) CleanBorder else CleanBorderLight
    val currentSecondaryText = if (themeDarkMode) CleanSecondaryText else CleanSecondaryTextLight
    val currentBadgeBg = if (themeDarkMode) CleanBadgeBg else CleanBadgeBgLight
    val currentBadgeText = if (themeDarkMode) CleanBadgeText else CleanBadgeTextLight
    val currentGreen = if (themeDarkMode) CleanGreen else CleanGreenLight

    // Audio Library scanning permissions helper
    val audioPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.scanLocalMusic(context)
        } else {
            Toast.makeText(context, "Storage permission is required to sync local files.", Toast.LENGTH_SHORT).show()
        }
    }

    fun requestAndScan() {
        val check = ContextCompat.checkSelfPermission(context, audioPermission)
        if (check == PackageManager.PERMISSION_GRANTED) {
            viewModel.scanLocalMusic(context)
        } else {
            permissionLauncher.launch(audioPermission)
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag("main_scaffold"),
        containerColor = currentBgColor
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(currentBgColor)
        ) {
            // --- HEADER ACTION BAR ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(currentBorderColor)
                            .testTag("app_logo"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MusicNote,
                            contentDescription = "LyricSync Icon",
                            tint = currentAccentColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "SyncLyrics",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium,
                            color = currentTextColor,
                        )
                        Text(
                            text = "MUSIXMATCH PRO",
                            color = currentAccentColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Dark mode toggle button
                    IconButton(
                        onClick = { viewModel.toggleTheme() },
                        modifier = Modifier.testTag("theme_toggle_btn")
                    ) {
                        Icon(
                            imageVector = if (themeDarkMode) Icons.Outlined.LightMode else Icons.Outlined.DarkMode,
                            contentDescription = "Toggle Theme",
                            tint = currentTextColor
                        )
                    }

                    // Scan local library button
                    IconButton(
                        onClick = { requestAndScan() },
                        modifier = Modifier.testTag("scan_music_btn")
                    ) {
                        if (isScanningMedia) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = currentAccentColor,
                                strokeWidth = 2.5.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.Sync,
                                contentDescription = "Scan Local Files",
                                tint = currentAccentColor
                            )
                        }
                    }
                }
            }

            // --- MAIN INTERACTIVE SURFACE ---
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Current Song details banner
                    selectedTrack?.let { track ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                                .testTag("song_details_card"),
                            colors = CardDefaults.cardColors(containerColor = currentCardColor),
                            shape = RoundedCornerShape(24.dp),
                            border = BorderStroke(1.dp, currentBorderColor),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Rounded album container
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            Brush.linearGradient(
                                                colors = listOf(
                                                    currentAccentColor,
                                                    currentDeepAccent
                                                )
                                            )
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Album,
                                        contentDescription = "Playing Disk",
                                        tint = Color.White,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(16.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = track.title,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 16.sp,
                                        color = currentTextColor,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${track.artist} • ${track.album}",
                                        fontSize = 14.sp,
                                        color = currentSecondaryText,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(top = 4.dp)
                                    ) {
                                        Surface(
                                            color = currentBadgeBg,
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = track.format.uppercase(),
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = currentBadgeText,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = if (track.format.uppercase() == "FLAC") "24-bit / 44.1kHz" else "320kbps / 44.1kHz",
                                            fontSize = 10.sp,
                                            color = currentMutedColor
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Icon(
                                            imageVector = if (track.lrcContent != null) Icons.Filled.Verified else Icons.Outlined.Info,
                                            contentDescription = "Sync Status Icon",
                                            tint = if (track.lrcContent != null) currentGreen else currentMutedColor,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }

                                // Quick actions for active song
                                Row {
                                    IconButton(
                                        onClick = { showEditMetadataDialog = true },
                                        modifier = Modifier.testTag("edit_tags_btn")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Edit,
                                            contentDescription = "Edit Metadata Tags",
                                            tint = currentAccentColor
                                        )
                                    }
                                    IconButton(
                                        onClick = { showExportMenuDialog = true },
                                        modifier = Modifier.testTag("export_lyrics_btn")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Download,
                                            contentDescription = "Export Lyrics",
                                            tint = currentAccentColor
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // --- KARAOKE LYRICS PANEL (SCROLLING ENGINE) ---
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        // Prominent Local File Metadata & Offline Lyric Caching status card
                        selectedTrack?.let { track ->
                            val (metadataArtist, metadataAlbum) = metadataArtistAndAlbum
                            val displayArtist = metadataArtist ?: track.artist
                            val displayAlbum = metadataAlbum ?: track.album
                            val isCached = track.lrcContent != null
                            
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 6.dp)
                                    .testTag("metadata_and_cache_card"),
                                colors = CardDefaults.cardColors(containerColor = currentCardColor.copy(alpha = 0.5f)),
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(1.dp, currentBorderColor.copy(alpha = 0.5f))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "LOCAL FILE METADATA",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = currentAccentColor,
                                            letterSpacing = 1.sp
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = displayArtist,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = currentTextColor,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = displayAlbum,
                                            fontSize = 11.sp,
                                            color = currentMutedColor,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    
                                    Spacer(modifier = Modifier.width(8.dp))
                                    
                                    Column(
                                        horizontalAlignment = Alignment.End,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(bottom = 4.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (isCached) Icons.Filled.CloudDone else Icons.Filled.CloudOff,
                                                contentDescription = "Cache Status Indicator",
                                                tint = if (isCached) currentGreen else currentMutedColor,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = if (isCached) "Lyrics Cached" else "No Local Cache",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isCached) currentGreen else currentMutedColor
                                            )
                                        }
                                        
                                        if (isCached) {
                                            TextButton(
                                                onClick = { viewModel.clearLyricsCache(context, track) },
                                                contentPadding = PaddingValues(horizontal = 8.dp),
                                                modifier = Modifier.height(28.dp)
                                            ) {
                                                Text(
                                                    text = "Clear Cache",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = currentAccentColor
                                                )
                                            }
                                        } else {
                                            Button(
                                                onClick = {
                                                    // Cache the active lyrics locally (or fallback template if none parsed)
                                                    val rawLrc = track.lrcContent ?: LyricsService.generateLocalMockLrc(track.title, track.artist)
                                                    viewModel.cacheLyrics(context, track, rawLrc)
                                                },
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = currentDeepAccent,
                                                    contentColor = currentAccentColor
                                                ),
                                                contentPadding = PaddingValues(horizontal = 10.dp),
                                                shape = RoundedCornerShape(10.dp),
                                                modifier = Modifier.height(28.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.Save,
                                                    contentDescription = "Cache Offline icon",
                                                    modifier = Modifier.size(11.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = "Cache Offline",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.ExtraBold
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Lyrics Scroller Box
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            if (lrcLines.isNotEmpty()) {
                                KaraokeLyricsScroller(
                                    lrcLines = lrcLines,
                                    currentPositionMs = currentPositionMs,
                                    textColor = currentTextColor,
                                    mutedColor = currentMutedColor,
                                    accentColor = currentAccentColor,
                                    onLineClicked = { line ->
                                        viewModel.lyricPlayer.seekTo(line.timestampMs)
                                    }
                                )
                            } else {
                                // Blank state / fetching lyric helper
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Search,
                                            contentDescription = "No Lyrics icon",
                                            tint = currentMutedColor,
                                            modifier = Modifier.size(64.dp)
                                        )
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Text(
                                            text = "No Synchronized Lyrics Loaded",
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = currentTextColor,
                                            textAlign = TextAlign.Center
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = "Tap sync to lookup this song on Musixmatch or auto-generate complete LRC lyrics with Gemini AI!",
                                            fontSize = 12.sp,
                                            color = currentMutedColor,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.padding(horizontal = 16.dp)
                                        )
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Button(
                                            onClick = {
                                                selectedTrack?.let {
                                                    viewModel.searchAndSyncLyrics(context, it)
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = currentDeepAccent, contentColor = currentAccentColor),
                                            shape = RoundedCornerShape(16.dp),
                                            modifier = Modifier.testTag("sync_lyrics_action_btn")
                                        ) {
                                            if (isSearchingLyrics) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(18.dp),
                                                    color = Color.White,
                                                    strokeWidth = 2.dp
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text("Searching Musixmatch...")
                                            } else {
                                                Icon(
                                                    imageVector = Icons.Filled.Sync,
                                                    contentDescription = "Sync icon",
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text("Sync Lyrics Now")
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Subtle, animated audio visualizer reacting to playback volume & state
                        AudioVisualizer(
                            isPlaying = playbackState is PlaybackState.Playing,
                            volume = playbackVolume,
                            accentColor = currentAccentColor,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                        )
                    }

                    // --- TRACK SELECTOR PANEL (ACCORDION STYLE LIBRARY) ---
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .testTag("library_card"),
                        colors = CardDefaults.cardColors(containerColor = currentCardColor),
                        shape = RoundedCornerShape(24.dp),
                        border = BorderStroke(1.dp, currentBorderColor)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            // Library Header
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Filled.LibraryMusic,
                                        contentDescription = "Library icon",
                                        tint = currentAccentColor,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Your Music Library",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = currentTextColor
                                    )
                                }

                                Surface(
                                    color = currentTextColor.copy(alpha = 0.08f),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text(
                                        text = "${tracks.size} tracks",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = currentMutedColor,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Search Library Filter
                            OutlinedTextField(
                                value = searchLibraryQuery,
                                onValueChange = { searchLibraryQuery = it },
                                placeholder = { Text("Filter songs by title/artist...", fontSize = 13.sp) },
                                leadingIcon = { Icon(Icons.Filled.Search, "Search", modifier = Modifier.size(18.dp)) },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("library_search_input"),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = currentAccentColor,
                                    unfocusedBorderColor = currentBorderColor,
                                    focusedTextColor = currentTextColor,
                                    unfocusedTextColor = currentTextColor,
                                    focusedLeadingIconColor = currentAccentColor,
                                    unfocusedLeadingIconColor = currentMutedColor
                                )
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            // Tracks list
                            val filteredTracks = tracks.filter {
                                it.title.contains(searchLibraryQuery, ignoreCase = true) ||
                                        it.artist.contains(searchLibraryQuery, ignoreCase = true)
                            }

                            if (filteredTracks.isNotEmpty()) {
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(140.dp)
                                ) {
                                    itemsIndexed(filteredTracks) { _, track ->
                                        val isCurrent = selectedTrack?.id == track.id
                                        val rowBg = if (isCurrent) currentAccentColor.copy(alpha = 0.12f) else Color.Transparent

                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(rowBg)
                                                .clickable { viewModel.selectTrack(track) }
                                                .padding(horizontal = 10.dp, vertical = 8.dp)
                                                .testTag("track_row_${track.id}"),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = if (isCurrent) Icons.Filled.PlayArrow else Icons.Outlined.MusicNote,
                                                contentDescription = "Track indicator",
                                                tint = if (isCurrent) currentAccentColor else currentMutedColor,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = track.title,
                                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                                    fontSize = 14.sp,
                                                    color = if (isCurrent) currentAccentColor else currentTextColor,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = track.artist,
                                                    fontSize = 11.sp,
                                                    color = currentMutedColor,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(8.dp))
                                            
                                            // Format indicator
                                            Surface(
                                                color = currentTextColor.copy(alpha = 0.05f),
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    text = track.format,
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = currentMutedColor,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                )
                                            }

                                            Spacer(modifier = Modifier.width(8.dp))

                                            // Sync Status Tag
                                            if (track.lrcContent != null) {
                                                Icon(
                                                    imageVector = Icons.Filled.Check,
                                                    contentDescription = "Synced",
                                                    tint = currentGreen,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            } else {
                                                Icon(
                                                    imageVector = Icons.Filled.SyncProblem,
                                                    contentDescription = "Not Synced",
                                                    tint = currentMutedColor.copy(alpha = 0.5f),
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(100.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "No songs match filter.",
                                        color = currentMutedColor,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // --- BOTTOM PLAYER CONTROLLER CONTROLS ---
            selectedTrack?.let { track ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .testTag("media_controller_panel"),
                    colors = CardDefaults.cardColors(containerColor = currentCardColor),
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.dp, currentBorderColor),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Progress Slider Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = formatTime(currentPositionMs),
                                fontSize = 11.sp,
                                color = currentMutedColor,
                                fontWeight = FontWeight.Bold
                            )
                            
                            Slider(
                                value = currentPositionMs.toFloat(),
                                onValueChange = { viewModel.lyricPlayer.seekTo(it.toLong()) },
                                valueRange = 0f..(if (durationMs > 0) durationMs.toFloat() else 100f),
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 12.dp)
                                    .testTag("progress_slider"),
                                colors = SliderDefaults.colors(
                                    thumbColor = currentAccentColor,
                                    activeTrackColor = currentAccentColor,
                                    inactiveTrackColor = currentMutedColor.copy(alpha = 0.25f)
                                )
                            )
                            
                            Text(
                                text = formatTime(durationMs),
                                fontSize = 11.sp,
                                color = currentMutedColor,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Playback Control Button Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Back seek button
                            IconButton(
                                onClick = { viewModel.lyricPlayer.seekTo((currentPositionMs - 5000).coerceAtLeast(0L)) },
                                modifier = Modifier.size(44.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Replay5,
                                    contentDescription = "Rewind 5 Seconds",
                                    tint = currentTextColor,
                                    modifier = Modifier.size(26.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(20.dp))

                            // Play/Pause FAB style button
                            val isPlaying = playbackState is PlaybackState.Playing
                            Box(
                                modifier = Modifier
                                    .size(62.dp)
                                    .clip(CircleShape)
                                    .background(currentDeepAccent)
                                    .clickable { viewModel.lyricPlayer.togglePlayPause() }
                                    .testTag("play_pause_fab"),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                    contentDescription = if (isPlaying) "Pause" else "Play",
                                    tint = currentAccentColor,
                                    modifier = Modifier.size(36.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(20.dp))

                            // Forward seek button
                            IconButton(
                                onClick = { viewModel.lyricPlayer.seekTo((currentPositionMs + 5000).coerceAtMost(durationMs)) },
                                modifier = Modifier.size(44.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Forward5,
                                    contentDescription = "Forward 5 Seconds",
                                    tint = currentTextColor,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Compact Volume Control Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth(0.85f)
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = if (playbackVolume == 0f) Icons.Filled.VolumeOff else if (playbackVolume < 0.4f) Icons.Filled.VolumeDown else Icons.Filled.VolumeUp,
                                contentDescription = "Volume Icon",
                                tint = currentMutedColor,
                                modifier = Modifier
                                    .size(18.dp)
                                    .clickable {
                                        if (playbackVolume > 0f) {
                                            viewModel.setPlaybackVolume(0f)
                                        } else {
                                            viewModel.setPlaybackVolume(0.8f)
                                        }
                                    }
                            )
                            
                            Slider(
                                value = playbackVolume,
                                onValueChange = { viewModel.setPlaybackVolume(it) },
                                valueRange = 0f..1f,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 8.dp)
                                    .testTag("volume_slider"),
                                colors = SliderDefaults.colors(
                                    thumbColor = currentAccentColor,
                                    activeTrackColor = currentAccentColor,
                                    inactiveTrackColor = currentMutedColor.copy(alpha = 0.2f)
                                )
                            )

                            Text(
                                text = "${(playbackVolume * 100).toInt()}%",
                                fontSize = 11.sp,
                                color = currentMutedColor,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.width(32.dp),
                                textAlign = TextAlign.End
                            )
                        }
                    }
                }
            }
        }
    }

    // --- INTEGRATED METADATA TAGGING DIALOG ---
    if (showEditMetadataDialog && selectedTrack != null) {
        val track = selectedTrack!!
        var editTitle by remember { mutableStateOf(track.title) }
        var editArtist by remember { mutableStateOf(track.artist) }
        var editAlbum by remember { mutableStateOf(track.album) }
        var editFormat by remember { mutableStateOf(track.format) }

        Dialog(onDismissRequest = { showEditMetadataDialog = false }) {
            Card(
                colors = CardDefaults.cardColors(containerColor = currentCardColor),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, currentBorderColor),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .testTag("metadata_dialog")
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Filled.Label,
                        contentDescription = "Tagging icon",
                        tint = currentAccentColor,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Edit Song Metadata",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = currentTextColor
                    )
                    Text(
                        text = "Modify file tags to get the most accurate Musixmatch sync. Saving will automatically trigger a new lyrics sync.",
                        fontSize = 11.sp,
                        color = currentMutedColor,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 6.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = editTitle,
                        onValueChange = { editTitle = it },
                        label = { Text("Song Title") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("edit_title_input"),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = currentAccentColor, focusedLabelColor = currentAccentColor)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = editArtist,
                        onValueChange = { editArtist = it },
                        label = { Text("Artist Name") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("edit_artist_input"),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = currentAccentColor, focusedLabelColor = currentAccentColor)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = editAlbum,
                        onValueChange = { editAlbum = it },
                        label = { Text("Album Name") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("edit_album_input"),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = currentAccentColor, focusedLabelColor = currentAccentColor)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Format Radio Selectors (MP3 vs FLAC)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = editFormat.uppercase() == "MP3",
                                onClick = { editFormat = "MP3" },
                                colors = RadioButtonDefaults.colors(selectedColor = currentAccentColor)
                            )
                            Text("MP3 Format", color = currentTextColor, fontSize = 13.sp)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = editFormat.uppercase() == "FLAC",
                                onClick = { editFormat = "FLAC" },
                                colors = RadioButtonDefaults.colors(selectedColor = currentAccentColor)
                            )
                            Text("FLAC Format", color = currentTextColor, fontSize = 13.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Button actions
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showEditMetadataDialog = false }) {
                            Text("Cancel", color = currentMutedColor)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                viewModel.updateTrackMetadata(context, track, editTitle, editArtist, editAlbum, editFormat)
                                showEditMetadataDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = currentDeepAccent, contentColor = currentAccentColor),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.testTag("save_metadata_btn")
                        ) {
                            Text("Save & Sync")
                        }
                    }
                }
            }
        }
    }

    // --- LYRIC EXPORT AND OFFLINE OFFLINE OPTION MENU ---
    if (showExportMenuDialog && selectedTrack != null) {
        val track = selectedTrack!!
        Dialog(onDismissRequest = { showExportMenuDialog = false }) {
            Card(
                colors = CardDefaults.cardColors(containerColor = currentCardColor),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, currentBorderColor),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .testTag("export_dialog")
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Filled.FileDownload,
                        contentDescription = "Export icon",
                        tint = currentAccentColor,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Export Offline Lyrics",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = currentTextColor
                    )
                    Text(
                        text = "Save this track's lyrics file into your device's Downloads directory for completely offline viewing on other devices.",
                        fontSize = 11.sp,
                        color = currentMutedColor,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 6.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Option 1: Standard Plain Text .txt file
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.exportLyrics(context, track, "txt")
                                showExportMenuDialog = false
                            },
                        colors = CardDefaults.cardColors(containerColor = currentCardColor),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, currentBorderColor)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Description, "Txt", tint = currentAccentColor)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Plain Text File (.TXT)", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = currentTextColor)
                                Text("Cleans all timing stamps. Best for basic reading.", fontSize = 11.sp, color = currentMutedColor)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Option 2: Full Synchronized .LRC file
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.exportLyrics(context, track, "lrc")
                                showExportMenuDialog = false
                            },
                        colors = CardDefaults.cardColors(containerColor = currentCardColor),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, currentBorderColor)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Timelapse, "Lrc", tint = currentAccentColor)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Synced Lyrics File (.LRC)", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = currentTextColor)
                                Text("Preserves karaoke-style timestamps metadata.", fontSize = 11.sp, color = currentMutedColor)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Option 3: Quick Native Share
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.shareLyrics(context, track)
                                showExportMenuDialog = false
                            },
                        colors = CardDefaults.cardColors(containerColor = currentCardColor),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, currentBorderColor)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Share, "Share", tint = currentGreen)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Share Lyrics", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = currentTextColor)
                                Text("Copy or share instantly with other messaging apps.", fontSize = 11.sp, color = currentMutedColor)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    TextButton(
                        onClick = { showExportMenuDialog = false },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Dismiss", color = currentMutedColor, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/**
 * Super smooth Karaoke Scrolling List Component.
 * Automatically centers on the active line matching the current timestamp position.
 */
@Composable
fun KaraokeLyricsScroller(
    lrcLines: List<LyricLine>,
    currentPositionMs: Long,
    textColor: Color,
    mutedColor: Color,
    accentColor: Color,
    onLineClicked: (LyricLine) -> Unit
) {
    val lazyListState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Find active lyric index matching current playback position
    val activeLineIndex = lrcLines.indexOfLast { currentPositionMs >= it.timestampMs }

    // Auto centering scroll engine with elegant spring feedback
    LaunchedEffect(activeLineIndex) {
        if (activeLineIndex >= 0) {
            scope.launch {
                lazyListState.animateScrollToItem(
                    index = activeLineIndex,
                    scrollOffset = -180 // Centers the active item beautifully in the scroller
                )
            }
        }
    }

    LazyColumn(
        state = lazyListState,
        contentPadding = PaddingValues(top = 100.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .fillMaxSize()
            .testTag("lyrics_list")
    ) {
        itemsIndexed(lrcLines) { index, line ->
            val isActive = index == activeLineIndex
            
            // Interactive Animations for high-fidelity karaoke glow feel
            val lyricAlpha by animateFloatAsState(
                targetValue = if (isActive) 1.0f else 0.45f,
                animationSpec = spring(dampingRatio = 0.8f, stiffness = 150f),
                label = "AlphaAnim"
            )
            
            val lyricScale by animateFloatAsState(
                targetValue = if (isActive) 1.12f else 0.95f,
                animationSpec = spring(dampingRatio = 0.7f, stiffness = 120f),
                label = "ScaleAnim"
            )
            
            val lyricColor by animateColorAsState(
                targetValue = if (isActive) accentColor else mutedColor,
                animationSpec = spring(dampingRatio = 0.8f, stiffness = 150f),
                label = "ColorAnim"
            )

            val lyricFontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null // Quiet click to prevent annoying flash background
                    ) { onLineClicked(line) }
                    .padding(horizontal = 24.dp)
                    .scale(lyricScale)
                    .testTag("lyric_line_$index"),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = line.text,
                    fontSize = if (isActive) 26.sp else 18.sp,
                    fontWeight = lyricFontWeight,
                    color = lyricColor.copy(alpha = lyricAlpha),
                    textAlign = TextAlign.Center,
                    lineHeight = if (isActive) 34.sp else 26.sp
                )
                
                // Subtle timestamp tag under each line for diagnostic karaoke help
                if (isActive) {
                    Text(
                        text = formatTime(line.timestampMs),
                        fontSize = 10.sp,
                        color = accentColor,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}

/**
 * Format timestamp (ms) to string representation e.g. "04:12"
 */
fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}

/**
 * A beautiful, highly reactive dynamic audio visualizer bar.
 */
@Composable
fun AudioVisualizer(
    isPlaying: Boolean,
    volume: Float,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val barCount = 20
    val infiniteTransition = rememberInfiniteTransition(label = "VisualizerTransition")
    
    // Smooth progress indicator to drive organic wave movement continuously
    val animationProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "VisualizerProgress"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 24.dp, vertical = 6.dp)
            .testTag("audio_visualizer"),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 0 until barCount) {
            // Compute a phase shift based on index to create a wave propagation effect
            val phaseOffset = (i * 2f * Math.PI.toFloat() / barCount)
            
            // Compose multiple sine waves for beautiful, organic hardware-like spectrum motion
            val firstWave = kotlin.math.sin(animationProgress + phaseOffset)
            val secondWave = kotlin.math.cos(animationProgress * 2.3f - phaseOffset * 0.5f)
            val combinedWave = (firstWave * 0.6f + secondWave * 0.4f)
            
            // Restrict height mapping to [0, 1] range smoothly
            val normalizedAmplitude = abs(combinedWave)
            
            // Base rest height: 4.dp
            // Max height: 44.dp
            val targetHeight = if (isPlaying) {
                // Height reacts dynamically to volume! At 0 volume, it behaves as idle / low movement.
                val maxVolumeBoost = 40.dp * volume
                4.dp + (maxVolumeBoost * normalizedAmplitude)
            } else {
                4.dp
            }

            val animatedHeight by animateDpAsState(
                targetValue = targetHeight,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow
                ),
                label = "VisualizerBarHeight"
            )

            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(animatedHeight)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                accentColor,
                                accentColor.copy(alpha = 0.35f)
                            )
                        )
                    )
            )
        }
    }
}
