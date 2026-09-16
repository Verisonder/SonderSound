package com.verisonder.sondersound.detect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AgreementTest {
    @Test
    fun oneTakeHasNothingToAgreeWith() {
        assertNull(Matcher.agreement(listOf(floatArrayOf(1f, 0f))))
    }

    @Test
    fun identicalTakesAgreeFully() {
        val a = Matcher.agreement(List(5) { floatArrayOf(1f, 1f, 0f) })!!
        a.forEach { assertEquals(1f, it, 1e-5f) }
    }

    @Test
    fun theOddTakeOutScoresLowest() {
        val takes = List(4) { floatArrayOf(1f, 0.1f * it, 0f) } + listOf(floatArrayOf(0f, 0f, 1f))
        val a = Matcher.agreement(takes)!!
        val worst = a.indices.minByOrNull { a[it] }
        assertEquals(4, worst)
        assertTrue(a[4] < 0.1f)
    }
}
