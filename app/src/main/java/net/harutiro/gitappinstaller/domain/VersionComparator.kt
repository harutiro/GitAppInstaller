package net.harutiro.gitappinstaller.domain

object VersionComparator : Comparator<String> {
    override fun compare(a: String, b: String): Int {
        val (numA, preA) = split(a)
        val (numB, preB) = split(b)
        val len = maxOf(numA.size, numB.size)
        for (i in 0 until len) {
            val x = numA.getOrElse(i) { 0 }
            val y = numB.getOrElse(i) { 0 }
            if (x != y) return x.compareTo(y)
        }
        return comparePre(preA, preB)
    }

    private fun split(raw: String): Pair<List<Int>, String?> {
        val v = raw.trim().removePrefix("v").removePrefix("V")
        val dashIdx = v.indexOf('-')
        val numericPart = if (dashIdx >= 0) v.substring(0, dashIdx) else v
        val prePart = if (dashIdx >= 0) v.substring(dashIdx + 1) else null
        val nums = numericPart.split('.').map { it.toIntOrNull() ?: 0 }
        return nums to prePart
    }

    private fun rank(pre: String?): Int = when {
        pre == null -> Int.MAX_VALUE
        pre.startsWith("rc", ignoreCase = true) -> 3
        pre.startsWith("beta", ignoreCase = true) -> 2
        pre.startsWith("alpha", ignoreCase = true) -> 1
        else -> 0
    }

    private fun comparePre(a: String?, b: String?): Int {
        val ra = rank(a)
        val rb = rank(b)
        if (ra != rb) return ra.compareTo(rb)
        if (a == null && b == null) return 0
        if (a == null) return 1
        if (b == null) return -1
        return a.compareTo(b)
    }

    fun isNewer(latest: String, current: String): Boolean = compare(latest, current) > 0
}
