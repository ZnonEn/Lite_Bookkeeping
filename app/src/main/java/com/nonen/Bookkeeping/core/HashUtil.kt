package com.nonen.Bookkeeping.core

import java.security.MessageDigest
import java.util.Locale
import kotlin.math.abs

object HashUtil {

    fun md5(input: String): String =
        MessageDigest.getInstance("MD5").digest(input.toByteArray(Charsets.UTF_8))
            .joinToString("") { String.format(Locale.US, "%02x", it) }

    /**
     * 交易去重哈希：MD5(timestamp + 金额 + 商户 + 来源)。
     * 金额统一格式化为两位小数，保证「同一笔交易多次解析」得到相同哈希。
     */
    fun transactionHash(timestamp: Long, amount: Double, merchant: String?, source: String): String {
        val normalized = String.format(Locale.US, "%.2f", abs(amount))
        val input = "$timestamp|$normalized|${merchant?.trim().orEmpty()}|$source"
        return md5(input)
    }
}
