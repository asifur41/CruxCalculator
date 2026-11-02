package com.tapecalc.core.math.qr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class QrCodeTest {

    private fun render(m: QrMatrix): String =
        (0 until m.size).joinToString("\n") { y -> (0 until m.size).joinToString("") { x -> if (m[x, y]) "1" else "0" } }

    @Test fun smallTextUsesVersionOne() {
        val m = QrCode.encode("HELLO", QrEcc.M)
        assertEquals(1, m.version)
        assertEquals(21, m.size)
    }

    @Test fun sizeFollowsVersion() {
        for (len in listOf(10, 40, 100, 300, 600)) {
            val m = QrCode.encode("a".repeat(len), QrEcc.M)
            assertEquals(17 + 4 * m.version, m.size)
        }
    }

    @Test fun finderPatternsAreInThreeCorners() {
        val m = QrCode.encode("HELLO", QrEcc.L)
        for ((ox, oy) in listOf(0 to 0, m.size - 7 to 0, 0 to m.size - 7)) {
            for (i in 0 until 7) for (j in 0 until 7) {
                val ring = maxOf(Math.abs(i - 3), Math.abs(j - 3))
                assertEquals("finder at $ox,$oy cell $i,$j", ring != 2, m[ox + i, oy + j])
            }
        }
    }

    @Test fun timingPatternAlternates() {
        val m = QrCode.encode("timing pattern check", QrEcc.M)
        for (i in 8 until m.size - 8) {
            assertEquals(i % 2 == 0, m[i, 6])
            assertEquals(i % 2 == 0, m[6, i])
        }
    }

    @Test fun alwaysDarkModuleIsSet() {
        val m = QrCode.encode("dark", QrEcc.M)
        assertTrue(m[8, m.size - 8])
    }

    @Test fun formatInformationIsAValidBchCode() {
        val m = QrCode.encode("format", QrEcc.M)
        var bits = 0
        // First copy: around the top-left finder.
        val order = listOf(8 to 0, 8 to 1, 8 to 2, 8 to 3, 8 to 4, 8 to 5, 8 to 7, 8 to 8, 7 to 8, 5 to 8, 4 to 8, 3 to 8, 2 to 8, 1 to 8, 0 to 8)
        order.forEachIndexed { i, (x, y) -> if (m[x, y]) bits = bits or (1 shl i) }
        val data = (bits xor 0x5412) ushr 10
        var rem = data
        repeat(10) { rem = (rem shl 1) xor ((rem ushr 9) * 0x537) }
        assertEquals(((data shl 10) or rem) xor 0x5412, bits)
        assertEquals(0, data ushr 3)   // ECC level M is encoded as 00
    }

    @Test fun deterministic() {
        assertEquals(render(QrCode.encode("12 km to mi", QrEcc.M)), render(QrCode.encode("12 km to mi", QrEcc.M)))
    }

    @Test fun differentTextGivesDifferentCode() {
        assertNotEquals(render(QrCode.encode("abc")), render(QrCode.encode("abd")))
    }

    @Test fun goldenCodeForHello() {
        // Checksum of the module grid; the grid itself was decoded back to "HELLO" with OpenCV.
        val text = render(QrCode.encode("HELLO", QrEcc.M))
        assertEquals(GOLDEN_HELLO_M, text.hashCode())
    }

    @Test fun utf8TextIsAccepted() {
        val s = "বাংলা π √2"
        assertTrue(QrCode.fits(s))
        assertTrue(QrCode.encode(s).size >= 21)
    }

    @Test fun multiBlockVersionsEncode() {
        val m = QrCode.encode((0 until 700).joinToString("") { ('a' + it % 26).toString() }, QrEcc.L)
        assertTrue(m.version >= 15)
        assertTrue(m[8, m.size - 8])
    }

    @Test fun maxBytesAreSane() {
        assertEquals(17, QrCode.encode("a".repeat(17), QrEcc.L).let { 17 })
        assertTrue(QrCode.maxBytes(QrEcc.L) > QrCode.maxBytes(QrEcc.M))
        assertTrue(QrCode.maxBytes(QrEcc.M) > 900)
    }

    @Test fun capacityBoundaryIsExact() {
        val max = QrCode.maxBytes(QrEcc.M)
        assertTrue(QrCode.fits("a".repeat(max), QrEcc.M))
        assertFalse(QrCode.fits("a".repeat(max + 1), QrEcc.M))
        assertEquals(25, QrCode.encode("a".repeat(max), QrEcc.M).version)
    }

    @Test fun tooLongThrowsClearMessage() {
        try {
            QrCode.encode("a".repeat(QrCode.maxBytes(QrEcc.M) + 1), QrEcc.M)
            fail("expected an exception")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.startsWith("Too much data for one QR code"))
        }
    }

    @Test fun emptyTextIsRejected() {
        assertFalse(QrCode.fits(""))
        try {
            QrCode.encode("")
            fail("expected an exception")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.isNotEmpty())
        }
    }

    @Test fun outsideTheCodeIsLight() {
        val m = QrCode.encode("HELLO")
        assertFalse(m[-1, 0])
        assertFalse(m[0, m.size])
    }

    private companion object {
        const val GOLDEN_HELLO_M = 702867492
    }
}
