package dev.endlesssea.downloader.dash

/** A static DASH manifest reduced to the tracks this downloader can assemble. DRM and live streams are rejected. */
data class DashSegment(val idx: Int, val url: String)

data class DashTrack(
    val kind: String,
    val mime: String,
    val height: Int,
    val bandwidth: Long,
    val language: String?,
    val codecs: String,
    val initUrl: String?,
    val segments: List<DashSegment>,
) {
    val muxedAudio: Boolean
        get() = codecs.split(',').any { codec ->
            val c = codec.trim().lowercase()
            c.startsWith("mp4a") || c.startsWith("ac-3") || c.startsWith("ec-3") ||
                c.startsWith("opus") || c.startsWith("vorbis") || c.startsWith("mp3")
        }
}

data class DashPlan(
    val video: DashTrack,
    val audio: DashTrack?,
    val subtitles: List<DashTrack>,
)

class DashError(message: String) : Exception(message)

private const val MAX_SEGMENTS = 8_000

fun dashPlan(mpd: String, manifestUrl: String, requestedHeight: Int = 0): DashPlan {
    val root = runCatching { parseXml(mpd) }.getOrElse { throw DashError("Manifeste DASH illisible") }
    if (!root.name.equals("MPD", true)) throw DashError("Ce document n'est pas un manifeste DASH")
    if (root.attrs["type"].equals("dynamic", true) && isoDurationSeconds(root.attrs["mediaPresentationDuration"]) == null) {
        throw DashError("Flux DASH en direct : téléchargement impossible, lecture en ligne uniquement")
    }
    val presentation = isoDurationSeconds(root.attrs["mediaPresentationDuration"])
    val period = root.children.firstOrNull { it.name.equals("Period", true) }
        ?: throw DashError("Manifeste DASH sans période")
    val periodDuration = isoDurationSeconds(period.attrs["duration"]) ?: presentation
    val base = baseStack(manifestUrl, listOf(root, period))
    val sets = period.children.filter { it.name.equals("AdaptationSet", true) }
    val videos = mutableListOf<DashTrack>()
    val audios = mutableListOf<DashTrack>()
    val texts = mutableListOf<DashTrack>()
    sets.forEach { set ->
        val kind = adaptationKind(set)
        val setBase = baseStack(base, listOf(set))
        val template = inheritedTemplate(period, set)
        set.children.filter { it.name.equals("Representation", true) }.forEach { rep ->
            if (isProtected(set) || isProtected(rep)) return@forEach
            val track = representationTrack(kind, set, rep, template, setBase, periodDuration) ?: return@forEach
            when (kind) {
                "video" -> videos += track
                "audio" -> audios += track
                "text" -> texts += track
            }
        }
    }
    val video = selectVideo(videos, requestedHeight)
        ?: throw DashError(
            if (videos.isEmpty()) "Aucune piste vidéo DASH téléchargeable (DRM ou manifeste incomplet)"
            else "Aucune qualité ${requestedHeight}p identifiable. Choisis une autre qualité.",
        )
    val audio = if (video.muxedAudio) null else audios.maxByOrNull { it.bandwidth }
    return DashPlan(video, audio, texts.filter { it.segments.isNotEmpty() }.take(6))
}

private fun selectVideo(videos: List<DashTrack>, requestedHeight: Int): DashTrack? {
    if (videos.isEmpty()) return null
    if (requestedHeight <= 0) return videos.maxByOrNull { it.bandwidth }
    videos.firstOrNull { it.height == requestedHeight }?.let { return it }
    val lower = videos.filter { it.height in 1..requestedHeight }.maxByOrNull { it.height }
    return lower ?: videos.minByOrNull { kotlin.math.abs(it.height - requestedHeight) }
}

private fun representationTrack(
    kind: String,
    set: XmlNode,
    rep: XmlNode,
    parentTemplate: XmlNode?,
    setBase: String,
    periodDuration: Double?,
): DashTrack? {
    val mime = rep.attrs["mimeType"] ?: set.attrs["mimeType"] ?: defaultMime(kind)
    val codecs = rep.attrs["codecs"] ?: set.attrs["codecs"] ?: ""
    val height = (rep.attrs["height"] ?: set.attrs["height"])?.toIntOrNull() ?: 0
    val bandwidth = (rep.attrs["bandwidth"] ?: set.attrs["bandwidth"])?.toLongOrNull() ?: 0L
    val lang = rep.attrs["lang"] ?: set.attrs["lang"]
    val base = baseStack(setBase, listOf(rep))
    val template = rep.children.firstOrNull { it.name.equals("SegmentTemplate", true) } ?: parentTemplate
    val list = rep.children.firstOrNull { it.name.equals("SegmentList", true) }
        ?: set.children.firstOrNull { it.name.equals("SegmentList", true) }
    val segments = when {
        list != null -> segmentList(list, base)
        template != null -> segmentTemplate(template, rep, base, periodDuration)
        else -> {
            val url = rep.children.firstOrNull { it.name.equals("BaseURL", true) }?.text?.takeIf { it.isNotBlank() }
                ?: set.children.firstOrNull { it.name.equals("BaseURL", true) }?.text?.takeIf { it.isNotBlank() }
            url?.let { listOf(DashSegment(0, resolveUrl(base, it))) }
        }
    } ?: return null
    if (segments.isEmpty()) return null
    val init = template?.attrs?.get("initialization")?.let { substitute(it, rep, 0, 0) }?.let { resolveUrl(base, it) }
        ?: list?.children?.firstOrNull { it.name.equals("Initialization", true) }?.attrs?.get("sourceURL")
            ?.let { resolveUrl(base, it) }
    return DashTrack(kind, mime, height, bandwidth, lang, codecs, init, segments)
}

private fun segmentList(list: XmlNode, base: String): List<DashSegment> =
    list.children.filter { it.name.equals("SegmentURL", true) }.mapIndexed { index, node ->
        val media = node.attrs["media"] ?: node.text
        DashSegment(index, resolveUrl(base, media))
    }.take(MAX_SEGMENTS)

private fun segmentTemplate(template: XmlNode, rep: XmlNode, base: String, periodDuration: Double?): List<DashSegment> {
    val timeline = template.children.firstOrNull { it.name.equals("SegmentTimeline", true) }
    val timescale = template.attrs["timescale"]?.toLongOrNull()?.takeIf { it > 0 } ?: 1L
    val startNumber = template.attrs["startNumber"]?.toIntOrNull() ?: 1
    val media = template.attrs["media"] ?: return emptyList()
    if (timeline != null) {
        val out = mutableListOf<DashSegment>()
        var number = startNumber
        var time = 0L
        timeline.children.filter { it.name.equals("S", true) }.forEach { s ->
            if (out.size >= MAX_SEGMENTS) return@forEach
            s.attrs["t"]?.toLongOrNull()?.let { time = it }
            val duration = s.attrs["d"]?.toLongOrNull() ?: return@forEach
            val repeats = (s.attrs["r"]?.toIntOrNull() ?: 0).coerceIn(0, MAX_SEGMENTS)
            repeat(repeats + 1) {
                if (out.size >= MAX_SEGMENTS) return@repeat
                out += DashSegment(out.size, resolveUrl(base, substitute(media, rep, number, time)))
                number++
                time += duration
            }
        }
        return out
    }
    val segmentSeconds = (template.attrs["duration"]?.toDoubleOrNull() ?: return emptyList()) / timescale
    val total = periodDuration ?: return emptyList()
    if (segmentSeconds <= 0.05 || total <= 0) return emptyList()
    val count = kotlin.math.ceil(total / segmentSeconds).toInt().coerceIn(1, MAX_SEGMENTS)
    return (0 until count).map { index ->
        DashSegment(index, resolveUrl(base, substitute(media, rep, startNumber + index, (index * segmentSeconds * timescale).toLong())))
    }
}

private fun substitute(pattern: String, rep: XmlNode, number: Int, time: Long): String {
    var out = pattern
    out = Regex("""\${'$'}Number(?:%0(\d+)d)?\${'$'}""").replace(out) { match ->
        val width = match.groupValues[1].toIntOrNull()
        if (width == null) number.toString() else number.toString().padStart(width, '0')
    }
    out = out.replace("\$Time$", time.toString())
        .replace("\$RepresentationID$", rep.attrs["id"].orEmpty())
        .replace("\$Bandwidth$", rep.attrs["bandwidth"].orEmpty())
    return out
}

private fun inheritedTemplate(period: XmlNode, set: XmlNode): XmlNode? =
    set.children.firstOrNull { it.name.equals("SegmentTemplate", true) }
        ?: period.children.firstOrNull { it.name.equals("SegmentTemplate", true) }

private fun adaptationKind(set: XmlNode): String {
    val mime = (set.attrs["mimeType"] ?: "").lowercase()
    val content = (set.attrs["contentType"] ?: "").lowercase()
    return when {
        content == "audio" || mime.startsWith("audio/") -> "audio"
        content == "text" || mime.startsWith("text/") || mime.contains("ttml") || mime.contains("vtt") -> "text"
        else -> "video"
    }
}

private fun defaultMime(kind: String) = when (kind) {
    "audio" -> "audio/mp4"
    "text" -> "text/vtt"
    else -> "video/mp4"
}

private fun isProtected(node: XmlNode): Boolean =
    node.children.any { it.name.equals("ContentProtection", true) }

private fun baseStack(start: String, nodes: List<XmlNode>): String {
    var current = start
    nodes.forEach { node ->
        node.children.firstOrNull { it.name.equals("BaseURL", true) }?.text?.takeIf { it.isNotBlank() }?.let {
            current = resolveUrl(current, it)
        }
    }
    return current
}

internal fun resolveUrl(base: String, child: String): String {
    val value = child.trim()
    if (value.startsWith("http://") || value.startsWith("https://")) return value
    return runCatching { java.net.URL(java.net.URL(base), value).toString() }.getOrDefault(value)
}

internal fun isoDurationSeconds(raw: String?): Double? {
    if (raw.isNullOrBlank()) return null
    val match = Regex("""PT(?:(\d+(?:\.\d+)?)H)?(?:(\d+(?:\.\d+)?)M)?(?:(\d+(?:\.\d+)?)S)?""").matchEntire(raw.trim())
        ?: return null
    val hours = match.groupValues[1].toDoubleOrNull() ?: 0.0
    val minutes = match.groupValues[2].toDoubleOrNull() ?: 0.0
    val seconds = match.groupValues[3].toDoubleOrNull() ?: 0.0
    return (hours * 3600 + minutes * 60 + seconds).takeIf { it > 0 }
}

private data class XmlNode(
    val name: String,
    val attrs: Map<String, String>,
    val children: List<XmlNode>,
    val text: String,
)

private fun parseXml(raw: String): XmlNode = XmlReader(raw.removePrefix("\uFEFF")).parseRoot()

private class XmlReader(private val s: String) {
    private var i = 0

    fun parseRoot(): XmlNode {
        skipMisc()
        if (i >= s.length || s[i] != '<') throw DashError("Manifeste DASH vide")
        return parseElement()
    }

    private fun parseElement(): XmlNode {
        i++
        val name = readName()
        val attrs = linkedMapOf<String, String>()
        while (i < s.length) {
            skipWs()
            if (s.startsWith("/>", i)) {
                i += 2
                return XmlNode(local(name), attrs, emptyList(), "")
            }
            if (s.getOrNull(i) == '>') {
                i++
                break
            }
            val attr = readName()
            skipWs()
            if (s.getOrNull(i) == '=') {
                i++
                skipWs()
                attrs[local(attr)] = readQuoted()
            }
        }
        val children = mutableListOf<XmlNode>()
        val text = StringBuilder()
        while (i < s.length) {
            if (s.startsWith("</", i)) {
                i = s.indexOf('>', i).let { if (it < 0) s.length else it + 1 }
                break
            }
            if (s.startsWith("<!--", i)) {
                i = s.indexOf("-->", i).let { if (it < 0) s.length else it + 3 }
                continue
            }
            if (s.startsWith("<![CDATA[", i)) {
                val end = s.indexOf("]]>", i)
                text.append(s.substring(i + 9, if (end < 0) s.length else end))
                i = if (end < 0) s.length else end + 3
                continue
            }
            if (s[i] == '<') children += parseElement()
            else {
                val start = i
                while (i < s.length && s[i] != '<') i++
                text.append(s.substring(start, i))
            }
        }
        return XmlNode(local(name), attrs, children, unescape(text.toString().trim()))
    }

    private fun skipMisc() {
        while (i < s.length) {
            skipWs()
            when {
                s.startsWith("<?", i) -> i = s.indexOf("?>", i).let { if (it < 0) s.length else it + 2 }
                s.startsWith("<!--", i) -> i = s.indexOf("-->", i).let { if (it < 0) s.length else it + 3 }
                s.startsWith("<!", i) -> i = s.indexOf('>', i).let { if (it < 0) s.length else it + 1 }
                else -> return
            }
        }
    }

    private fun readName(): String {
        val start = i
        while (i < s.length && s[i] !in " \t\r\n=/>") i++
        return s.substring(start, i)
    }

    private fun readQuoted(): String {
        val quote = s.getOrNull(i) ?: return ""
        if (quote != '"' && quote != '\'') return ""
        i++
        val start = i
        while (i < s.length && s[i] != quote) i++
        val value = s.substring(start, i)
        if (i < s.length) i++
        return unescape(value)
    }

    private fun skipWs() {
        while (i < s.length && s[i].isWhitespace()) i++
    }
}

private fun local(name: String) = name.substringAfter(':')

private fun unescape(value: String) = value
    .replace("&lt;", "<")
    .replace("&gt;", ">")
    .replace("&quot;", "\"")
    .replace("&apos;", "'")
    .replace("&amp;", "&")
