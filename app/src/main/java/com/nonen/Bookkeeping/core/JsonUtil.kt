package com.nonen.Bookkeeping.core

/** 轻量 JSON 构造（仅用于 rawData 与备份导出，避免引入序列化框架）。 */
object JsonUtil {

    fun escape(s: String): String = buildString {
        for (c in s) when (c) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> append(c)
        }
    }

    fun value(v: Any?): String = when (v) {
        null -> "null"
        is Double -> if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()
        is Number, is Boolean -> v.toString()
        else -> "\"${escape(v.toString())}\""
    }

    fun obj(vararg pairs: Pair<String, Any?>): String =
        pairs.joinToString(",", "{", "}") { (k, v) -> "\"${escape(k)}\":${value(v)}" }
}
