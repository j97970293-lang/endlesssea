package dev.endlesssea.downloader.hls

/** Observed-rate envelope, not a statistical confidence interval or a guaranteed ceiling. */
data class HlsSizeEstimate(
    val bytes: Long,
    val indicativeLowBytes: Long,
    val indicativeHighBytes: Long,
    val measuredAll: Boolean,
    val sampledSegments: Int,
    val totalSegments: Int,
)

object HlsSizeMath {
    private const val SAMPLE_LIMIT = 12
    private fun groups(count: Int): List<IntRange> {
        if (count <= 0) return emptyList()
        val samples = minOf(count, SAMPLE_LIMIT)
        return (0 until samples).map { i ->
            (i.toLong() * count / samples).toInt() until ((i + 1L) * count / samples).toInt()
        }
    }
    fun sampleIndices(count: Int): List<Int> = groups(count).mapIndexed { i, range ->
        when (i) {
            0 -> range.first
            minOf(count, SAMPLE_LIMIT) - 1 -> range.last
            else -> range.first + (range.last - range.first) / 2
        }
    }

    fun estimate(durations: List<Double?>, sizes: Map<Int, Long>, initBytes: Long, finite: Boolean): HlsSizeEstimate? {
        val count = durations.size
        if (!finite || count == 0 || initBytes < 0) return null
        val indices = sampleIndices(count)
        if (indices.any { (sizes[it] ?: 0) <= 0 }) return null
        if ((0 until count).all { (sizes[it] ?: 0) > 0 }) {
            val sum = try { (0 until count).fold(initBytes) { total, i -> Math.addExact(total, sizes.getValue(i)) } }
                catch (_: ArithmeticException) { return null }
            return HlsSizeEstimate(sum, sum, sum, true, count, count)
        }
        if (durations.any { it == null || !it.isFinite() || it <= 0 }) return null
        val seconds = durations.map { it!! }
        val totalDuration = seconds.sum()
        val rates = indices.map { sizes.getValue(it).toDouble() / seconds[it] }
        // One representative per region, weighted by that region's real duration.
        val total = initBytes + groups(count).mapIndexed { i, range -> rates[i] * range.sumOf { seconds[it] } }.sum()
        val low = initBytes + rates.min() * totalDuration
        val high = initBytes + rates.max() * totalDuration
        if (listOf(total, low, high).any { !it.isFinite() || it <= 0 || it >= Long.MAX_VALUE.toDouble() }) return null
        return HlsSizeEstimate(total.toLong(), minOf(low.toLong(), total.toLong()), maxOf(high.toLong(), total.toLong()),
            false, indices.size, count)
    }
}
