package com.beto.app.scam

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScamMessageBufferTest {

    @Test
    fun `append concatenates multiple fragments per package`() {
        val buf = ScamMessageBuffer()
        buf.append(PKG_WA, "Hola abu, soy yo")
        val window = buf.append(PKG_WA, "cambié de número guardalo")
        assertEquals("Hola abu, soy yo cambié de número guardalo", window)
    }

    @Test
    fun `append separates packages independently`() {
        val buf = ScamMessageBuffer()
        buf.append(PKG_WA, "transferime ahora")
        buf.append(PKG_SMS, "AFIP multa pendiente")
        assertEquals("transferime ahora", buf.snapshot(PKG_WA))
        assertEquals("AFIP multa pendiente", buf.snapshot(PKG_SMS))
    }

    @Test
    fun `append dedupes immediate exact repeats`() {
        val buf = ScamMessageBuffer()
        buf.append(PKG_WA, "transferime 80 mil")
        val again = buf.append(PKG_WA, "transferime 80 mil")
        assertEquals("transferime 80 mil", again)
    }

    @Test
    fun `append trims to maxCharsPerPackage from the front`() {
        val buf = ScamMessageBuffer(maxCharsPerPackage = 20)
        buf.append(PKG_WA, "AAAAAAAAAA") // 10
        buf.append(PKG_WA, "BBBBBBBBBB") // +1 sep + 10 = 21 → cae primer char
        val window = buf.snapshot(PKG_WA)
        assertEquals(20, window.length)
        assertTrue("debe contener fragmento nuevo", window.endsWith("BBBBBBBBBB"))
    }

    @Test
    fun `evict drops least recently used package when over capacity`() {
        val buf = ScamMessageBuffer(maxPackages = 2)
        buf.append("a", "x")
        buf.append("b", "y")
        // touch 'a' to make 'b' the LRU
        buf.append("a", "z")
        buf.append("c", "w") // debe evictar 'b'
        assertEquals("", buf.snapshot("b"))
        assertTrue(buf.snapshot("a").isNotEmpty())
        assertTrue(buf.snapshot("c").isNotEmpty())
    }

    @Test
    fun `clear removes a single package`() {
        val buf = ScamMessageBuffer()
        buf.append(PKG_WA, "hola")
        buf.append(PKG_SMS, "afip")
        buf.clear(PKG_WA)
        assertEquals("", buf.snapshot(PKG_WA))
        assertEquals("afip", buf.snapshot(PKG_SMS))
    }

    @Test
    fun `clearAll removes everything`() {
        val buf = ScamMessageBuffer()
        buf.append(PKG_WA, "hola")
        buf.append(PKG_SMS, "afip")
        buf.clearAll()
        assertEquals("", buf.snapshot(PKG_WA))
        assertEquals("", buf.snapshot(PKG_SMS))
    }

    @Test
    fun `blank fragments are ignored`() {
        val buf = ScamMessageBuffer()
        buf.append(PKG_WA, "   ")
        assertEquals("", buf.snapshot(PKG_WA))
        buf.append(PKG_WA, "hola")
        buf.append(PKG_WA, "")
        assertEquals("hola", buf.snapshot(PKG_WA))
    }

    @Test
    fun `dedupe checks exact tail not arbitrary contains`() {
        val buf = ScamMessageBuffer()
        buf.append(PKG_WA, "soy yo")
        val window = buf.append(PKG_WA, "yo te aviso") // distinto, debe agregar
        assertTrue(window.contains("soy yo"))
        assertTrue(window.contains("yo te aviso"))
        assertFalse(window == "soy yo")
    }

    private companion object {
        const val PKG_WA = "com.whatsapp"
        const val PKG_SMS = "com.google.android.apps.messaging"
    }
}
