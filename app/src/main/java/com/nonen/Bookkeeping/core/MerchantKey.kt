package com.nonen.Bookkeeping.core

/**
 * 商户名归一化：把「同一家店的不同写法」收敛成同一个键，供商户记忆匹配使用。
 *
 * 微信/支付宝的商户名带大量噪声（分店括号、空白、大小写），
 * 直接比较会失配；归一化后「肯德基（XX路店）」与「肯德基(浦东店)」归为同一个键。
 */
object MerchantKey {

    /** 括号及其内容（中英文方括号与圆括号）：「肯德基（XX路店）」→「肯德基」 */
    private val BRACKETS = Regex("[（(【\\[][^）)】\\]]*[）)】\\]]")
    private val WHITESPACE = Regex("\\s+")

    /** 包含匹配所需的最短长度：低于该长度（如「超市」）包含匹配会大面积误伤 */
    const val MIN_CONTAINMENT_LENGTH = 3

    fun normalize(raw: String?): String? {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isEmpty()) return null
        val key = BRACKETS.replace(trimmed, " ").replace(WHITESPACE, "").lowercase()
        return key.ifEmpty { null }
    }

    /**
     * 两个已归一化的键是否指同一商户：完全相等，或较短一方长度达标时互相包含。
     * 包含关系用于兜住「星巴克臻选上海南京西路」与「星巴克」这类带后缀的写法。
     */
    fun matches(stored: String, candidate: String): Boolean {
        if (stored == candidate) return true
        val shorter = if (stored.length <= candidate.length) stored else candidate
        if (shorter.length < MIN_CONTAINMENT_LENGTH) return false
        return stored.contains(candidate) || candidate.contains(stored)
    }
}
