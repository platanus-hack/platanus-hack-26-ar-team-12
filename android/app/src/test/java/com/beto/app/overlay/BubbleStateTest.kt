package com.beto.app.overlay

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BubbleStateTest {

    @Test
    fun `every state has a ring color`() {
        BubbleState.values().forEach {
            assertNotNull("$it must have ring color", it.ringColorRes)
            assertTrue("$it ring color should be a real resource id", it.ringColorRes != 0)
        }
    }

    @Test
    fun `idle has no center icon`() {
        // El logo central debe ser estable en IDLE — sin badge superpuesto (D-13)
        assertNotNull(BubbleState.IDLE)
        assertTrue(BubbleState.IDLE.centerIconRes == null)
    }

    @Test
    fun `non-idle states have a center icon`() {
        BubbleState.values().filter { it != BubbleState.IDLE }.forEach {
            assertNotNull("$it must have a center icon", it.centerIconRes)
        }
    }

    @Test
    fun `idle has no animation`() {
        assertTrue(BubbleState.IDLE.animation == BubbleAnimation.NONE)
    }

    @Test
    fun `non-idle states have an animation`() {
        BubbleState.values().filter { it != BubbleState.IDLE }.forEach {
            assertTrue(
                "$it should have non-NONE animation",
                it.animation != BubbleAnimation.NONE,
            )
        }
    }

    @Test
    fun `error uses shake animation`() {
        assertTrue(BubbleState.ERROR.animation == BubbleAnimation.SHAKE)
    }

    @Test
    fun `transitions idle to listening is legal`() {
        assertTrue(BubbleStateTransitions.isLegal(BubbleState.IDLE, BubbleState.LISTENING))
    }

    @Test
    fun `transitions listening to thinking is legal`() {
        assertTrue(BubbleStateTransitions.isLegal(BubbleState.LISTENING, BubbleState.THINKING))
    }

    @Test
    fun `transitions thinking to speaking is legal`() {
        assertTrue(BubbleStateTransitions.isLegal(BubbleState.THINKING, BubbleState.SPEAKING))
    }

    @Test
    fun `transitions speaking to idle is legal`() {
        assertTrue(BubbleStateTransitions.isLegal(BubbleState.SPEAKING, BubbleState.IDLE))
    }

    @Test
    fun `transitions any active state to error is legal (failsafe)`() {
        listOf(BubbleState.LISTENING, BubbleState.THINKING, BubbleState.SPEAKING).forEach {
            assertTrue("$it -> ERROR must be legal", BubbleStateTransitions.isLegal(it, BubbleState.ERROR))
        }
    }

    @Test
    fun `transitions error can clear to idle`() {
        assertTrue(BubbleStateTransitions.isLegal(BubbleState.ERROR, BubbleState.IDLE))
    }

    @Test
    fun `transitions error cannot fall through to thinking directly`() {
        // ERROR es failsafe — solo debe limpiar a IDLE o reanudar LISTENING.
        assertFalse(BubbleStateTransitions.isLegal(BubbleState.ERROR, BubbleState.THINKING))
        assertFalse(BubbleStateTransitions.isLegal(BubbleState.ERROR, BubbleState.SPEAKING))
    }

    @Test
    fun `transitions same state is always legal (no-op)`() {
        BubbleState.values().forEach {
            assertTrue("$it -> $it should be legal (no-op)", BubbleStateTransitions.isLegal(it, it))
        }
    }
}
