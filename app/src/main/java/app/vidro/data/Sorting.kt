package app.vidro.data

import java.text.Collator

/**
 * Orders names the way people expect: locale-aware letters, and numbers by value,
 * so "Episode 2" comes before "Episode 10".
 */
object NaturalOrder : Comparator<String> {

    private val collator = ThreadLocal.withInitial {
        Collator.getInstance().apply { strength = Collator.SECONDARY }
    }

    override fun compare(a: String, b: String): Int {
        val collator = collator.get()!!
        var i = 0
        var j = 0
        while (i < a.length && j < b.length) {
            val aDigit = a[i].isDigit()
            val bDigit = b[j].isDigit()
            val si = i
            val sj = j
            while (i < a.length && a[i].isDigit() == aDigit) i++
            while (j < b.length && b[j].isDigit() == bDigit) j++
            val partA = a.substring(si, i)
            val partB = b.substring(sj, j)
            val result = if (aDigit && bDigit) {
                val na = partA.trimStart('0')
                val nb = partB.trimStart('0')
                if (na.length != nb.length) na.length - nb.length else na.compareTo(nb)
            } else {
                collator.compare(partA, partB)
            }
            if (result != 0) return result
        }
        return (a.length - i) - (b.length - j)
    }
}

fun List<VideoFolder>.sortedFolders(order: SortOrder): List<VideoFolder> {
    val comparator: Comparator<VideoFolder> = when (order.field) {
        SortField.Date -> compareBy { it.lastModified }
        SortField.Size -> compareBy { it.totalSize }
        SortField.Count -> compareBy { it.videos.size }
        SortField.Name, SortField.Duration -> Comparator { a, b -> NaturalOrder.compare(a.name, b.name) }
    }
    return sortedWith(if (order.ascending) comparator else comparator.reversed())
}

fun List<Video>.sortedVideos(order: SortOrder): List<Video> {
    val comparator: Comparator<Video> = when (order.field) {
        SortField.Date -> compareBy { it.dateModified }
        SortField.Size -> compareBy { it.size }
        SortField.Duration -> compareBy { it.durationMs }
        SortField.Name, SortField.Count -> Comparator { a, b -> NaturalOrder.compare(a.name, b.name) }
    }
    return sortedWith(if (order.ascending) comparator else comparator.reversed())
}
