package com.nonen.Bookkeeping

import com.nonen.Bookkeeping.core.HashUtil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class HashUtilTest {

    @Test
    fun `md5 known vector`() {
        assertEquals("900150983cd24fb0d6963f7d28e17f72", HashUtil.md5("abc"))
    }

    @Test
    fun `same input produces same hash`() {
        assertEquals(
            HashUtil.transactionHash(1000L, 25.0, "美团", "manual"),
            HashUtil.transactionHash(1000L, 25.0, "美团", "manual"),
        )
    }

    @Test
    fun `amount is normalized to two decimals`() {
        assertEquals(
            HashUtil.transactionHash(1L, 25.0, null, "auto"),
            HashUtil.transactionHash(1L, 25.0001, null, "auto"),
        )
    }

    @Test
    fun `different inputs produce different hashes`() {
        val base = HashUtil.transactionHash(1L, 10.0, "A", "wechat")
        assertNotEquals(base, HashUtil.transactionHash(2L, 10.0, "A", "wechat"))
        assertNotEquals(base, HashUtil.transactionHash(1L, 20.0, "A", "wechat"))
        assertNotEquals(base, HashUtil.transactionHash(1L, 10.0, "B", "wechat"))
        assertNotEquals(base, HashUtil.transactionHash(1L, 10.0, "A", "alipay"))
    }
}
