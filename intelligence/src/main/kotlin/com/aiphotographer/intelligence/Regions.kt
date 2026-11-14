package com.aiphotographer.intelligence

/** Helpers for reading the frame's brightness grid. */
internal object Regions {

    fun mean(luma: LumaStats, indices: List<Int>): Float {
        if (indices.isEmpty()) return luma.mean
        var sum = 0f
        var count = 0
        for (i in indices) {
            if (i in luma.regions.indices) {
                sum += luma.regions[i]
                count++
            }
        }
        return if (count == 0) luma.mean else sum / count
    }

    /**
     * Indices of the grid cells whose CENTRE falls inside [box].
     *
     * Centre-based membership is what makes subject/background separation
     * meaningful. A box spanning 0.3..0.7 overlaps every column of a 3-column
     * grid but only genuinely occupies the middle of the frame.
     */
    fun cellsInBox(box: Box, grid: Int): List<Int> {
        val out = ArrayList<Int>()
        for (row in 0 until grid) {
            val cy = (row + 0.5f) / grid
            if (cy < box.top || cy > box.bottom) continue
            for (col in 0 until grid) {
                val cx = (col + 0.5f) / grid
                if (cx in box.left..box.right) out.add(row * grid + col)
            }
        }
        if (out.isEmpty()) {
            // Box smaller than a single cell: use the cell holding its centre.
            val row = (box.centerY * grid).toInt().coerceIn(0, grid - 1)
            val col = (box.centerX * grid).toInt().coerceIn(0, grid - 1)
            out.add(row * grid + col)
        }
        return out
    }

    fun columnIndices(grid: Int, columns: IntRange): List<Int> {
        val out = ArrayList<Int>()
        for (row in 0 until grid) for (col in columns) {
            if (col in 0 until grid) out.add(row * grid + col)
        }
        return out
    }

    fun rowIndices(grid: Int, rows: IntRange): List<Int> {
        val out = ArrayList<Int>()
        for (row in rows) {
            if (row !in 0 until grid) continue
            for (col in 0 until grid) out.add(row * grid + col)
        }
        return out
    }
}
