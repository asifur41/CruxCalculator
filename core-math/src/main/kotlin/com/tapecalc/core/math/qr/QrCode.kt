package com.tapecalc.core.math.qr

/** Error-correction level. L recovers about 7 % damage, M about 15 % (M scans better when printed or photographed). */
enum class QrEcc(internal val formatBits: Int, internal val index: Int) {
    L(1, 0), M(0, 1)
}

/**
 * A finished QR code: [size] x [size] modules, `true` = dark. The quiet zone (the 4-module white border the
 * standard asks for) is not included; the screen that draws the code adds it.
 */
class QrMatrix(val size: Int, private val dark: BooleanArray, val version: Int = (size - 17) / 4) {

    init {
        require(dark.size == size * size) { "Matrix data has the wrong length" }
    }

    /** `true` when the module in column [x], row [y] (0 = top-left) is dark; outside the code it is light. */
    operator fun get(x: Int, y: Int): Boolean = x in 0 until size && y in 0 until size && dark[y * size + x]
}

/**
 * QR Code (Model 2) encoder: byte mode (UTF-8), error-correction levels L and M, versions 1 to 25.
 * Pure Kotlin, no dependencies, so it works offline and is unit-testable without Android.
 *
 *   val code = QrCode.encode("12 km to mi = 7.45645 mi")
 *   code[x, y]   // is this module dark?
 */
object QrCode {
    private const val MIN_VERSION = 1
    private const val MAX_VERSION = 25
    private const val PAD0 = 0xEC
    private const val PAD1 = 0x11

    // Index = version (0 is unused). Values from ISO/IEC 18004, table 9 (verified against OpenCV's encoder).
    private val ECC_PER_BLOCK = arrayOf(
        intArrayOf(-1, 7, 10, 15, 20, 26, 18, 20, 24, 30, 18, 20, 24, 26, 30, 22, 24, 28, 30, 28, 28, 28, 28, 30, 30, 26),
        intArrayOf(-1, 10, 16, 26, 18, 24, 16, 18, 22, 22, 26, 30, 22, 22, 24, 24, 28, 28, 26, 26, 26, 26, 28, 28, 28, 28)
    )
    private val BLOCKS = arrayOf(
        intArrayOf(-1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 4, 4, 4, 4, 4, 6, 6, 6, 6, 7, 8, 8, 9, 9, 10, 12),
        intArrayOf(-1, 1, 1, 1, 2, 2, 4, 4, 4, 5, 5, 5, 8, 9, 9, 10, 10, 11, 13, 14, 16, 17, 17, 18, 20, 21)
    )

    /** Largest number of UTF-8 bytes one code can hold at this level (version 25). */
    fun maxBytes(ecc: QrEcc): Int = capacityBytes(MAX_VERSION, ecc)

    fun fits(text: String, ecc: QrEcc = QrEcc.M): Boolean {
        val n = text.toByteArray(Charsets.UTF_8).size
        return n in 1..maxBytes(ecc)
    }

    /** Encodes [text] with the smallest version that holds it and the best of the 8 masks. */
    fun encode(text: String, ecc: QrEcc = QrEcc.M): QrMatrix = encodeInternal(text, ecc, null, null)

    /** Test hook: forces [version] and/or [mask] (0..7) so the output can be compared with another encoder. */
    internal fun encodeInternal(text: String, ecc: QrEcc, forcedVersion: Int?, forcedMask: Int?): QrMatrix {
        val bytes = text.toByteArray(Charsets.UTF_8)
        require(bytes.isNotEmpty()) { "Nothing to encode" }
        require(bytes.size <= maxBytes(ecc)) { "Too much data for one QR code (max ${maxBytes(ecc)} bytes)" }
        val version = forcedVersion ?: (MIN_VERSION..MAX_VERSION).first { capacityBytes(it, ecc) >= bytes.size }
        require(version in MIN_VERSION..MAX_VERSION && capacityBytes(version, ecc) >= bytes.size) {
            "Too much data for version $version"
        }
        val data = buildDataCodewords(bytes, version, ecc)
        val all = addEccAndInterleave(data, version, ecc)
        return Builder(version, ecc).build(all, forcedMask)
    }

    // ---- capacity ----------------------------------------------------------------------------------------

    private fun rawDataModules(version: Int): Int {
        var result = (16 * version + 128) * version + 64
        if (version >= 2) {
            val numAlign = version / 7 + 2
            result -= (25 * numAlign - 10) * numAlign - 55
            if (version >= 7) result -= 36
        }
        return result
    }

    private fun dataCodewords(version: Int, ecc: QrEcc): Int =
        rawDataModules(version) / 8 - ECC_PER_BLOCK[ecc.index][version] * BLOCKS[ecc.index][version]

    private fun countBits(version: Int): Int = if (version <= 9) 8 else 16

    private fun capacityBytes(version: Int, ecc: QrEcc): Int =
        (dataCodewords(version, ecc) * 8 - 4 - countBits(version)) / 8

    // ---- data bits ---------------------------------------------------------------------------------------

    private class BitBuffer {
        val bits = ArrayList<Boolean>()
        fun append(value: Int, length: Int) {
            for (i in length - 1 downTo 0) bits.add(((value ushr i) and 1) != 0)
        }
    }

    private fun buildDataCodewords(bytes: ByteArray, version: Int, ecc: QrEcc): ByteArray {
        val capacityBits = dataCodewords(version, ecc) * 8
        val buf = BitBuffer()
        buf.append(0x4, 4)                       // byte mode
        buf.append(bytes.size, countBits(version))
        for (b in bytes) buf.append(b.toInt() and 0xFF, 8)
        buf.append(0, minOf(4, capacityBits - buf.bits.size))   // terminator
        while (buf.bits.size % 8 != 0) buf.bits.add(false)
        var pad = PAD0
        while (buf.bits.size < capacityBits) {
            buf.append(pad, 8)
            pad = if (pad == PAD0) PAD1 else PAD0
        }
        val out = ByteArray(buf.bits.size / 8)
        for (i in buf.bits.indices) if (buf.bits[i]) out[i ushr 3] = (out[i ushr 3].toInt() or (1 shl (7 - (i and 7)))).toByte()
        return out
    }

    // ---- Reed-Solomon over GF(256) with polynomial 0x11D ----------------------------------------------------

    private fun gfMultiply(x: Int, y: Int): Int {
        var z = 0
        for (i in 7 downTo 0) {
            z = (z shl 1) xor ((z ushr 7) * 0x11D)
            z = z xor (((y ushr i) and 1) * x)
        }
        return z
    }

    private fun rsDivisor(degree: Int): IntArray {
        val result = IntArray(degree)
        result[degree - 1] = 1
        var root = 1
        for (i in 0 until degree) {
            for (j in 0 until degree) {
                result[j] = gfMultiply(result[j], root)
                if (j + 1 < degree) result[j] = result[j] xor result[j + 1]
            }
            root = gfMultiply(root, 0x02)
        }
        return result
    }

    private fun rsRemainder(data: IntArray, divisor: IntArray): IntArray {
        val result = IntArray(divisor.size)
        for (b in data) {
            val factor = b xor result[0]
            System.arraycopy(result, 1, result, 0, result.size - 1)
            result[result.size - 1] = 0
            for (i in result.indices) result[i] = result[i] xor gfMultiply(divisor[i], factor)
        }
        return result
    }

    private fun addEccAndInterleave(data: ByteArray, version: Int, ecc: QrEcc): IntArray {
        val numBlocks = BLOCKS[ecc.index][version]
        val eccLen = ECC_PER_BLOCK[ecc.index][version]
        val rawCodewords = rawDataModules(version) / 8
        val numShort = numBlocks - rawCodewords % numBlocks
        val shortLen = rawCodewords / numBlocks
        val divisor = rsDivisor(eccLen)

        val dataBlocks = ArrayList<IntArray>()
        val eccBlocks = ArrayList<IntArray>()
        var k = 0
        for (i in 0 until numBlocks) {
            val len = shortLen - eccLen + (if (i < numShort) 0 else 1)
            val block = IntArray(len) { data[k + it].toInt() and 0xFF }
            k += len
            dataBlocks.add(block)
            eccBlocks.add(rsRemainder(block, divisor))
        }
        val result = ArrayList<Int>()
        val longest = shortLen - eccLen + 1
        for (i in 0 until longest) for (b in dataBlocks) if (i < b.size) result.add(b[i])
        for (i in 0 until eccLen) for (b in eccBlocks) result.add(b[i])
        return result.toIntArray()
    }

    // ---- drawing -----------------------------------------------------------------------------------------

    private class Builder(val version: Int, val ecc: QrEcc) {
        val size = version * 4 + 17
        val modules = BooleanArray(size * size)
        val isFunction = BooleanArray(size * size)

        fun set(x: Int, y: Int, dark: Boolean) {
            modules[y * size + x] = dark
            isFunction[y * size + x] = true
        }

        fun build(codewords: IntArray, forcedMask: Int?): QrMatrix {
            drawFunctionPatterns()
            drawData(codewords)
            var best = forcedMask ?: 0
            if (forcedMask == null) {
                var bestPenalty = Int.MAX_VALUE
                for (m in 0 until 8) {
                    applyMask(m)
                    drawFormatBits(m)
                    val p = penalty()
                    if (p < bestPenalty) {
                        bestPenalty = p
                        best = m
                    }
                    applyMask(m)   // masking is its own inverse
                }
            }
            applyMask(best)
            drawFormatBits(best)
            return QrMatrix(size, modules, version)
        }

        private fun drawFunctionPatterns() {
            for (i in 0 until size) {
                set(6, i, i % 2 == 0)
                set(i, 6, i % 2 == 0)
            }
            drawFinder(3, 3)
            drawFinder(size - 4, 3)
            drawFinder(3, size - 4)
            val pos = alignmentPositions()
            val last = pos.size - 1
            for (i in pos.indices) for (j in pos.indices) {
                if ((i == 0 && j == 0) || (i == 0 && j == last) || (i == last && j == 0)) continue
                drawAlignment(pos[i], pos[j])
            }
            drawFormatBits(0)   // reserves the area; rewritten once the mask is known
            drawVersion()
        }

        private fun drawFinder(cx: Int, cy: Int) {
            for (dy in -4..4) for (dx in -4..4) {
                val dist = maxOf(Math.abs(dx), Math.abs(dy))
                val x = cx + dx
                val y = cy + dy
                if (x in 0 until size && y in 0 until size) set(x, y, dist != 2 && dist != 4)
            }
        }

        private fun drawAlignment(cx: Int, cy: Int) {
            for (dy in -2..2) for (dx in -2..2) set(cx + dx, cy + dy, maxOf(Math.abs(dx), Math.abs(dy)) != 1)
        }

        private fun alignmentPositions(): IntArray {
            if (version == 1) return IntArray(0)
            val numAlign = version / 7 + 2
            val step = if (version == 32) 26 else (version * 4 + numAlign * 2 + 1) / (numAlign * 2 - 2) * 2
            val result = IntArray(numAlign)
            result[0] = 6
            var pos = size - 7
            for (i in numAlign - 1 downTo 1) {
                result[i] = pos
                pos -= step
            }
            return result
        }

        private fun bit(value: Int, i: Int): Boolean = ((value ushr i) and 1) != 0

        private fun drawFormatBits(mask: Int) {
            val data = (ecc.formatBits shl 3) or mask
            var rem = data
            repeat(10) { rem = (rem shl 1) xor ((rem ushr 9) * 0x537) }
            val bits = ((data shl 10) or rem) xor 0x5412
            for (i in 0..5) set(8, i, bit(bits, i))
            set(8, 7, bit(bits, 6))
            set(8, 8, bit(bits, 7))
            set(7, 8, bit(bits, 8))
            for (i in 9..14) set(14 - i, 8, bit(bits, i))
            for (i in 0..7) set(size - 1 - i, 8, bit(bits, i))
            for (i in 8..14) set(8, size - 15 + i, bit(bits, i))
            set(8, size - 8, true)   // the always-dark module
        }

        private fun drawVersion() {
            if (version < 7) return
            var rem = version
            repeat(12) { rem = (rem shl 1) xor ((rem ushr 11) * 0x1F25) }
            val bits = (version shl 12) or rem
            for (i in 0 until 18) {
                val a = size - 11 + i % 3
                val b = i / 3
                set(a, b, bit(bits, i))
                set(b, a, bit(bits, i))
            }
        }

        private fun drawData(codewords: IntArray) {
            var i = 0
            var right = size - 1
            while (right >= 1) {
                if (right == 6) right = 5
                for (vert in 0 until size) {
                    for (j in 0..1) {
                        val x = right - j
                        val upward = ((right + 1) and 2) == 0
                        val y = if (upward) size - 1 - vert else vert
                        if (!isFunction[y * size + x] && i < codewords.size * 8) {
                            modules[y * size + x] = bit(codewords[i ushr 3], 7 - (i and 7))
                            i++
                        }
                    }
                }
                right -= 2
            }
        }

        private fun applyMask(mask: Int) {
            for (y in 0 until size) for (x in 0 until size) {
                val invert = when (mask) {
                    0 -> (x + y) % 2 == 0
                    1 -> y % 2 == 0
                    2 -> x % 3 == 0
                    3 -> (x + y) % 3 == 0
                    4 -> (x / 3 + y / 2) % 2 == 0
                    5 -> x * y % 2 + x * y % 3 == 0
                    6 -> (x * y % 2 + x * y % 3) % 2 == 0
                    else -> ((x + y) % 2 + x * y % 3) % 2 == 0
                }
                if (invert && !isFunction[y * size + x]) modules[y * size + x] = !modules[y * size + x]
            }
        }

        private fun at(x: Int, y: Int): Boolean = modules[y * size + x]

        /** The four standard penalty rules from ISO/IEC 18004 section 7.8.3. */
        private fun penalty(): Int {
            var total = 0
            // Rule 1: runs of five or more equal modules in a row or column.
            for (pass in 0..1) for (a in 0 until size) {
                var run = 1
                for (b in 1 until size) {
                    val same = if (pass == 0) at(b, a) == at(b - 1, a) else at(a, b) == at(a, b - 1)
                    if (same) {
                        run++
                    } else {
                        if (run >= 5) total += run - 2
                        run = 1
                    }
                }
                if (run >= 5) total += run - 2
            }
            // Rule 2: 2x2 blocks of one colour.
            for (y in 0 until size - 1) for (x in 0 until size - 1) {
                val c = at(x, y)
                if (c == at(x + 1, y) && c == at(x, y + 1) && c == at(x + 1, y + 1)) total += 3
            }
            // Rule 3: 1:1:3:1:1 finder-like pattern with four light modules on one side.
            val pattern = booleanArrayOf(true, false, true, true, true, false, true)
            for (pass in 0..1) for (a in 0 until size) for (b in 0..size - 7) {
                fun cell(i: Int) = if (pass == 0) at(b + i, a) else at(a, b + i)
                var match = true
                for (i in 0 until 7) if (cell(i) != pattern[i]) { match = false; break }
                if (!match) continue
                fun light(from: Int, to: Int): Boolean {
                    if (from < 0 || to > size) return false
                    for (i in from until to) if (if (pass == 0) at(i, a) else at(a, i)) return false
                    return true
                }
                if (light(b - 4, b)) total += 40
                if (light(b + 7, b + 11)) total += 40
            }
            // Rule 4: balance of dark and light modules.
            var dark = 0
            for (m in modules) if (m) dark++
            val totalModules = size * size
            val k = (Math.abs(dark * 20 - totalModules * 10) + totalModules - 1) / totalModules - 1
            total += k * 10
            return total
        }
    }
}
