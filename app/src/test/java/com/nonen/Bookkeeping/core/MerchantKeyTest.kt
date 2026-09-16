package com.nonen.Bookkeeping.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MerchantKeyTest {

    @Test
    fun `normalize strips branch brackets and whitespace`() {
        assertEquals("肯德基", MerchantKey.normalize("肯德基（XX路店）"))
        assertEquals("肯德基", MerchantKey.normalize("肯德基(浦东店)"))
        assertEquals("肯德基", MerchantKey.normalize(" 肯 德 基 "))
        assertEquals("星巴克", MerchantKey.normalize("星巴克【臻选店】"))
    }

    @Test
    fun `normalize is case insensitive for latin names`() {
        assertEquals("starbucks", MerchantKey.normalize("Starbucks"))
        assertEquals(MerchantKey.normalize("Starbucks"), MerchantKey.normalize("STARBUCKS"))
    }

    @Test
    fun `normalize returns null for blank or bracket-only input`() {
        assertNull(MerchantKey.normalize(null))
        assertNull(MerchantKey.normalize(""))
        assertNull(MerchantKey.normalize("   "))
        assertNull(MerchantKey.normalize("（）"))
    }

    @Test
    fun `matches treats different branch names of same brand as equal`() {
        val a = MerchantKey.normalize("肯德基（XX路店）")!!
        val b = MerchantKey.normalize("肯德基(浦东店)")!!
        assertTrue(MerchantKey.matches(a, b))
    }

    @Test
    fun `matches accepts containment for long enough keys`() {
        val stored = MerchantKey.normalize("星巴克臻选上海南京西路")!!
        val candidate = MerchantKey.normalize("星巴克")!!
        assertTrue(MerchantKey.matches(stored, candidate))
    }

    @Test
    fun `matches refuses containment for very short keys`() {
        // 「超市」两个字太泛，包含匹配会大面积误伤（永辉超市、便利超市…）
        val stored = MerchantKey.normalize("超市")!!
        val candidate = MerchantKey.normalize("永辉超市")!!
        assertFalse(MerchantKey.matches(stored, candidate))
    }

    @Test
    fun `matches rejects different merchants`() {
        val a = MerchantKey.normalize("肯德基")!!
        val b = MerchantKey.normalize("麦当劳")!!
        assertFalse(MerchantKey.matches(a, b))
    }
}
