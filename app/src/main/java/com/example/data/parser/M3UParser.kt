package com.example.data.parser

import com.example.data.model.PlaylistItem
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader

object M3UParser {

    /**
     * Parse an M3U playlist from an input stream.
     * Extracts tags such as tvg-logo, group-title, tvg-name, and applies content-type heuristics.
     */
    fun parse(inputStream: InputStream, playlistSource: String, onProgress: (Int) -> Unit): List<PlaylistItem> {
        val reader = BufferedReader(InputStreamReader(inputStream))
        val items = mutableListOf<PlaylistItem>()

        var line: String?
        var currentMetaData: String? = null
        
        // Count total lines to estimate progress
        // IPTV files can be huge, let's do a fast estimation loop or fixed steps
        var processedLines = 0
        var totalEstimatedLines = 20000 // default fallback
        
        // If we can estimate dimensions, let's do so. But we want a performant pass.
        while (reader.readLine().also { line = it } != null) {
            val currentLine = line!!.trim()
            processedLines++
            
            if (currentLine.isEmpty()) continue

            if (currentLine.startsWith("#EXTM3U")) {
                // Ignore header
                continue
            } else if (currentLine.startsWith("#EXTINF:")) {
                currentMetaData = currentLine
            } else if (!currentLine.startsWith("#") && currentMetaData != null) {
                // This is the URL line matching the previous metadata
                val item = parseItem(currentMetaData, currentLine, playlistSource)
                items.add(item)
                currentMetaData = null
                
                // Emitting progress at regular intervals
                if (items.size % 400 == 0) {
                    val progress = (items.size * 100 / (items.size + 1000)).coerceAtMost(99)
                    onProgress(progress)
                }
            }
        }
        
        reader.close()
        onProgress(100)
        return items
    }

    private fun parseItem(metadataLine: String, streamUrl: String, playlistSource: String): PlaylistItem {
        // Extract display name (last part of metadata after comma)
        val commaIndex = metadataLine.lastIndexOf(',')
        var displayName = if (commaIndex != -1 && commaIndex < metadataLine.length - 1) {
            metadataLine.substring(commaIndex + 1).trim()
        } else {
            "Untitled Stream"
        }

        // Extract attributes
        val logoUrl = extractAttribute(metadataLine, "tvg-logo") ?: extractAttribute(metadataLine, "logo")
        var category = extractAttribute(metadataLine, "group-title") ?: "Canais Gerais"
        
        // Sanitize category
        if (category.trim().isEmpty()) {
            category = "Canais Gerais"
        }

        val tvgName = extractAttribute(metadataLine, "tvg-name")
        if (tvgName != null && displayName == "Untitled Stream") {
            displayName = tvgName
        }

        // Determine content-type (Ao Vivo, Filmes, Séries)
        val contentType = determineType(displayName, category, streamUrl)

        // Check for adult content
        val isAdult = isAdultContent(displayName, category)

        return PlaylistItem(
            name = displayName,
            url = streamUrl,
            logoUrl = logoUrl,
            category = category,
            contentType = contentType.name,
            isAdult = isAdult,
            playlistSource = playlistSource
        )
    }

    private fun extractAttribute(line: String, attrName: String): String? {
        val target = "$attrName=\""
        var index = line.indexOf(target)
        if (index != -1) {
            val start = index + target.length
            val end = line.indexOf("\"", start)
            if (end != -1) {
                return line.substring(start, end)
            }
        }
        // Fallback without quotes (e.g. tvg-logo=http://url)
        val targetFallback = "$attrName="
        index = line.indexOf(targetFallback)
        if (index != -1) {
            val start = index + targetFallback.length
            var end = line.indexOf(" ", start)
            if (end == -1) {
                end = line.indexOf(",", start)
            }
            if (end == -1) {
                end = line.length
            }
            if (end > start) {
                return line.substring(start, end).replace("\"", "").trim()
            }
        }
        return null
    }

    private fun determineType(name: String, category: String, url: String): com.example.data.model.ContentType {
        val uppercaseName = name.uppercase()
        val uppercaseCategory = category.uppercase()
        val uppercaseUrl = url.uppercase()

        // Explicit movie keywords
        val movieCategories = listOf(
            "FILMES", "MOVIES", "VOD", "CINEMA", "BLOCKBUSTER", "LANCAMENTOS", "LANÇAMENTOS",
            "PREMIUM FILMES", "CINE", "ACTION", "COMEDY", "DRAMA", "HORROR", "TERROR"
        )
        // Explicit series keywords
        val seriesCategories = listOf(
            "SERIES", "SÉRIES", "SERIADOS", "SEASON", "TEMPORADA", "EPISODIOS", "EPISÓDIOS",
            "ANIME", "ANIMES", "NOVELAS", "NOVELA"
        )

        // Look in category first
        if (movieCategories.any { uppercaseCategory.contains(it) }) {
            return com.example.data.model.ContentType.MOVIE
        }
        if (seriesCategories.any { uppercaseCategory.contains(it) }) {
            return com.example.data.model.ContentType.SERIES
        }

        // Fallbacks based on URL extensions
        if (uppercaseUrl.endsWith(".MP4") || uppercaseUrl.endsWith(".MKV") || uppercaseUrl.endsWith(".AVI")) {
            return if (uppercaseUrl.contains("/SERIES/") || uppercaseUrl.contains("/EPISODES/") || uppercaseUrl.contains("S0") || uppercaseUrl.contains("E0")) {
                com.example.data.model.ContentType.SERIES
            } else {
                com.example.data.model.ContentType.MOVIE
            }
        }

        // Live matches (often ending with .m3u8, .ts, or has /live/)
        if (uppercaseUrl.contains("/LIVE/") || uppercaseUrl.endsWith(".M3U8") || uppercaseUrl.endsWith(".TS") || uppercaseUrl.contains(".TS?")) {
            return com.example.data.model.ContentType.LIVE
        }

        return com.example.data.model.ContentType.LIVE // default is Ao Vivo
    }

    private fun isAdultContent(name: String, category: String): Boolean {
        val pattern = listOf(
            "18+", "ADULTO", "ADULT", "XXX", "SEXY", "PLAYBOY", "PENTHOUSE", "VENUS", "HOT ", "HUSTLER", "FORBIDDEN", "FORA DA LEI", "S0X"
        )
        val upperName = name.uppercase()
        val upperCategory = category.uppercase()
        return pattern.any { upperName.contains(it) || upperCategory.contains(it) }
    }
}
