package com.example.data

data class LyricLine(
    val timestampMs: Long,
    val text: String
)

object LyricParser {
    /**
     * Parses standard LRC format strings into a sorted list of LyricLines.
     * Supports formats: [mm:ss], [mm:ss.x], [mm:ss.xx], [mm:ss.xxx]
     */
    fun parseLrc(lrcContent: String?): List<LyricLine> {
        if (lrcContent.isNullOrBlank()) return emptyList()
        val lines = lrcContent.split("\n")
        val result = mutableListOf<LyricLine>()
        // Regex matches [minutes:seconds] or [minutes:seconds.decimals]
        val regex = Regex("\\[(\\d+):(\\d+(?:\\.\\d+)?)\\](.*)")
        
        for (line in lines) {
            val trimmedLine = line.trim()
            val matchResult = regex.find(trimmedLine)
            if (matchResult != null) {
                val min = matchResult.groupValues[1].toLongOrNull() ?: 0L
                val secStr = matchResult.groupValues[2]
                val sec = secStr.toDoubleOrNull() ?: 0.0
                val text = matchResult.groupValues[3].trim()
                
                val totalMs = (min * 60 * 1000) + (sec * 1000).toLong()
                result.add(LyricLine(totalMs, text))
            } else if (trimmedLine.isNotEmpty() && !trimmedLine.startsWith("[")) {
                // Handle plain lines as placeholder timings distributed evenly across the track
                // if we don't have tags, but usually LRC will have them.
            }
        }
        
        // If there are no timestamps found but plain lyrics exist, generate simple 4-second paced timestamps
        if (result.isEmpty()) {
            val plainLines = lines.map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("[") }
            if (plainLines.isNotEmpty()) {
                var currentMs = 2000L // start at 2 seconds
                for (line in plainLines) {
                    result.add(LyricLine(currentMs, line))
                    currentMs += 4000L // 4 seconds per line
                }
            }
        }
        
        return result.sortedBy { it.timestampMs }
    }
}
