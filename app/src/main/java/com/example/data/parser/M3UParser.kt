package com.example.data.parser

import com.example.data.model.PlaylistItem
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.net.URI

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
        var currentGroup: String? = null
        var processedLines = 0
        var firstLine: String? = null
        
        while (reader.readLine().also { line = it } != null) {
            var currentLine = line!!.trim()
            processedLines++
            
            // Check and strip UTF-8 BOM if present on the very first read lines or general lines
            if (currentLine.startsWith("\uFEFF")) {
                currentLine = currentLine.substring(1).trim()
            }
            
            if (currentLine.isEmpty()) continue

            if (firstLine == null) {
                firstLine = currentLine
            }

            if (currentLine.startsWith("#EXTM3U", ignoreCase = true)) {
                // Ignore header
                continue
            } else if (currentLine.startsWith("#EXTINF", ignoreCase = true)) {
                currentMetaData = currentLine
            } else if (currentLine.startsWith("#EXTGRP:", ignoreCase = true)) {
                currentGroup = currentLine.substring(8).trim()
            } else if (!currentLine.startsWith("#")) {
                // This is a stream URL line!
                if (currentMetaData != null) {
                    val item = parseItem(currentMetaData, currentLine, playlistSource, currentGroup)
                    items.add(item)
                    currentMetaData = null
                    currentGroup = null
                } else if (isValidUrl(currentLine)) {
                    // Fallback: parse plain URL without metadata
                    val item = parseUrlOnly(currentLine, playlistSource)
                    items.add(item)
                }

                // Emitting progress at regular intervals
                if (items.size % 400 == 0) {
                    val progress = (items.size * 100 / (items.size + 1000)).coerceAtMost(99)
                    onProgress(progress)
                }
            }
        }
        
        reader.close()
        onProgress(100)

        // If no items were parsed, diagnose why (e.g. server returned an HTML failure portal)
        if (items.isEmpty()) {
            val diagnosis = diagnoseContent(firstLine)
            if (diagnosis != null) {
                throw Exception(diagnosis)
            }
        }

        return items
    }

    private fun isValidUrl(url: String): Boolean {
        val lower = url.lowercase()
        return lower.startsWith("http://") || 
               lower.startsWith("https://") || 
               lower.startsWith("rtmp://") || 
               lower.startsWith("rtsp://") || 
               lower.startsWith("mms://")
    }

    private fun parseUrlOnly(streamUrl: String, playlistSource: String): PlaylistItem {
        val uri = try {
            URI(streamUrl)
        } catch (e: Exception) {
            null
        }
        val path = uri?.path ?: streamUrl
        val lastSegment = path.substringAfterLast('/')
        val displayName = if (lastSegment.isNotEmpty()) {
            lastSegment.substringBeforeLast('.')
        } else {
            "Canal Manual"
        }
        val category = "Canais Gerais"
        val contentType = determineType(displayName, category, streamUrl)
        return PlaylistItem(
            name = displayName.ifEmpty { "Canal Manual" },
            url = streamUrl,
            logoUrl = null,
            category = category,
            contentType = contentType.name,
            isAdult = isAdultContent(displayName, category),
            playlistSource = playlistSource
        )
    }

    private fun parseItem(metadataLine: String, streamUrl: String, playlistSource: String, fallbackGroup: String? = null): PlaylistItem {
        // Extract display name (last part of metadata after comma)
        val commaIndex = metadataLine.lastIndexOf(',')
        var displayName = if (commaIndex != -1 && commaIndex < metadataLine.length - 1) {
            metadataLine.substring(commaIndex + 1).trim()
        } else {
            "Untitled Stream"
        }

        // Extract attributes case-insensitively
        val logoUrl = extractAttribute(metadataLine, "tvg-logo") ?: extractAttribute(metadataLine, "logo")
        var category = extractAttribute(metadataLine, "group-title") ?: fallbackGroup ?: "Canais Gerais"
        
        // Sanitize category
        if (category.trim().isEmpty()) {
            category = fallbackGroup ?: "Canais Gerais"
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
        val lineLower = line.lowercase()
        val attrNameLower = attrName.lowercase()
        
        val target = "$attrNameLower=\""
        var index = lineLower.indexOf(target)
        if (index != -1) {
            val start = index + target.length
            val end = line.indexOf("\"", start)
            if (end != -1) {
                return line.substring(start, end)
            }
        }
        
        // Fallback without quotes (e.g. tvg-logo=http://url)
        val targetFallback = "$attrNameLower="
        index = lineLower.indexOf(targetFallback)
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

    private fun diagnoseContent(firstLine: String?): String? {
        if (firstLine == null) return null
        val lower = firstLine.lowercase()
        
        // HTML check
        if (lower.startsWith("<html") || lower.startsWith("<!doc") || lower.contains("<html>")) {
            return "O servidor retornou uma página HTML ao invés da lista. Verifique se o usuário/senha estão corretos ou se o servidor está funcionando."
        }
        
        // JSON check
        if (lower.startsWith("{") || lower.startsWith("[")) {
            if (lower.contains("message") || lower.contains("error") || lower.contains("status")) {
                return "O servidor retornou um erro em formato JSON. Verifique seus dados de acesso."
            }
        }
        
        // Common plain text auth errors
        if (lower.contains("invalid username or password") || 
            lower.contains("authorization failed") || 
            lower.contains("auth failed") || 
            lower.contains("invalid credentials") ||
            lower.contains("usuario invalido") ||
            lower.contains("senha incorreta") ||
            lower.contains("credenciais incorretas") ||
            lower.contains("acesso negado") ||
            lower.contains("unauthorized")) {
            return "Usuário ou senha inválidos no servidor contratado."
        }
        
        if (lower.contains("account expired") || 
            lower.contains("expired") || 
            lower.contains("vencido") || 
            lower.contains("expirou") || 
            lower.contains("vencida")) {
            return "Sua conta de IPTV expirou ou está inativa no servidor."
        }
        
        if (lower.contains("limit reached") || 
            lower.contains("too many connections") || 
            lower.contains("limite de conex") || 
            lower.contains("max connections")) {
            return "Limite de conexões simultâneas atingido no servidor."
        }
        
        return null
    }
}
