package uk.gov.defra.mmocatchrecord.common.design

/** Picks the suggestion whose spelling is closest to the typed query, for the dropdown's blue highlight. */
object AutocompleteBestMatch {
    private const val RANK_EXACT = 0
    private const val RANK_PREFIX = 1
    private const val RANK_WORD_PREFIX = 2
    private const val RANK_CONTAINS = 3
    private const val RANK_OTHER = 4

    fun indexOf(
        query: String,
        labels: List<String>,
    ): Int? {
        val normalizedQuery = query.trim().lowercase()
        if (normalizedQuery.isEmpty() || labels.isEmpty()) return null
        return labels.indices.minWith(
            compareBy<Int>(
                { matchRank(normalizedQuery, labels[it].lowercase()) },
                { editDistance(normalizedQuery, labels[it].lowercase()) },
            ).thenBy { it },
        )
    }

    private fun matchRank(
        query: String,
        label: String,
    ): Int =
        when {
            label == query -> RANK_EXACT
            label.startsWith(query) -> RANK_PREFIX
            label.split(' ', '(', '-', '/').any { it.startsWith(query) } -> RANK_WORD_PREFIX
            label.contains(query) -> RANK_CONTAINS
            else -> RANK_OTHER
        }

    internal fun editDistance(
        a: String,
        b: String,
    ): Int {
        var previous = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val current = IntArray(b.length + 1)
            current[0] = i
            for (j in 1..b.length) {
                val substitution = previous[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1
                current[j] = minOf(previous[j] + 1, current[j - 1] + 1, substitution)
            }
            previous = current
        }
        return previous[b.length]
    }
}
