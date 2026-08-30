package com.nonen.Bookkeeping.service

import com.nonen.Bookkeeping.data.prefs.Packages
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 抓取调试：无障碍服务把最近收到的事件与扫描结论写进这里（环形缓冲），
 * 设置页的「抓取调试」面板展示并可一键复制，用于判断是「抓不到内容」还是「解析规则不匹配」。
 * 位于 src/debug 源集：仅 test 分支存在，debug 构建参与编译，release 构建不含此文件。
 */
object AutoRecordDebugStore {

    const val MAX_ENTRIES = 24

    data class Entry(
        val time: Long,
        val pkg: String,
        val origin: String,
        val result: String,
        val texts: List<String>,
    )

    @Volatile var eventCount: Long = 0
        private set
    @Volatile var lastEventAt: Long = 0
        private set
    @Volatile var lastEventPkg: String = ""
        private set

    private val entries = ArrayDeque<Entry>()
    private var lastThrottledAt = 0L

    /** 目标应用每来一个无障碍事件记一次心跳 */
    fun onEvent(pkg: String) {
        synchronized(this) {
            eventCount++
            lastEventAt = System.currentTimeMillis()
            lastEventPkg = pkg
        }
    }

    fun record(pkg: String, origin: String, result: String, texts: List<String>) {
        synchronized(entries) {
            entries.addFirst(Entry(System.currentTimeMillis(), pkg, origin, result, texts))
            while (entries.size > MAX_ENTRIES) entries.removeLast()
        }
    }

    /** 同类失败信息限流（3 秒一条），避免滚动聊天页时刷屏淹没有效记录 */
    fun recordThrottled(pkg: String, origin: String, result: String, texts: List<String>) {
        val now = System.currentTimeMillis()
        synchronized(this) {
            if (now - lastThrottledAt < 3_000L) return
            lastThrottledAt = now
        }
        record(pkg, origin, result, texts)
    }

    fun snapshot(): List<Entry> = synchronized(entries) { entries.toList() }

    fun clear() {
        synchronized(entries) { entries.clear() }
        synchronized(this) {
            eventCount = 0
            lastEventAt = 0
            lastEventPkg = ""
        }
    }

    fun appNameOf(pkg: String): String = when (pkg) {
        Packages.WECHAT -> "微信"
        Packages.ALIPAY -> "支付宝"
        else -> pkg.substringAfterLast('.').take(10)
    }

    fun formatTime(ts: Long): String =
        DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(ts))

    fun buildReport(serviceEnabled: Boolean): String {
        val sb = StringBuilder()
        sb.append("无障碍服务：").append(if (serviceEnabled) "已开启" else "未开启").append('\n')
        sb.append("收到监听事件：")
        if (lastEventAt == 0L) {
            sb.append("从未（服务可能未绑定，或被系统省电策略休眠）")
        } else {
            sb.append(appNameOf(lastEventPkg)).append(" 最后于 ").append(formatTime(lastEventAt))
                .append("，累计 ").append(eventCount).append(" 次")
        }
        sb.append('\n')
        val list = snapshot()
        if (list.isEmpty()) {
            sb.append("暂无扫描记录：去微信/支付宝操作一笔，稍等 1-2 秒回来查看")
        } else {
            sb.append("—— 最近扫描（新在上） ——")
            list.forEach { e ->
                sb.append('\n').append('[').append(formatTime(e.time)).append("] ")
                    .append(appNameOf(e.pkg)).append('·').append(e.origin).append("：").append(e.result)
                if (e.texts.isNotEmpty()) {
                    sb.append('\n').append("  文本：").append(e.texts.joinToString(" | ") { it.take(20) })
                }
            }
        }
        return sb.toString()
    }
}
