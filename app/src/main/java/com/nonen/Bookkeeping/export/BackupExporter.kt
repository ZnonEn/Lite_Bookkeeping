package com.nonen.Bookkeeping.export

import com.nonen.Bookkeeping.core.MiniXlsx
import com.nonen.Bookkeeping.data.db.TransactionEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Excel 备份导出/导入（本应用专属格式，可被 Excel/WPS 打开查看，也能原样导回）。
 * 列：记账时间 | 类型 | 金额 | 分类 | 商户/交易对象 | 备注 | 来源 | 校验码。
 * 导入时按「校验码」（入库时的去重哈希）原样回灌，重复导入自动去重；
 * 手工编辑过的文件缺校验码时按 时间+金额+商户+来源 重算。
 */
object BackupExporter {

    const val HEADER_TIME = "记账时间"
    const val HEADER_TYPE = "类型"
    const val HEADER_AMOUNT = "金额"
    const val HEADER_CATEGORY = "分类"
    const val HEADER_MERCHANT = "商户/交易对象"
    const val HEADER_NOTE = "备注"
    const val HEADER_SOURCE = "来源"
    const val HEADER_HASH = "校验码"

    private val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    fun buildXlsx(transactions: List<TransactionEntity>): ByteArray {
        val rows: List<List<Any?>> = buildList(transactions.size + 1) {
            add(
                listOf(
                    HEADER_TIME, HEADER_TYPE, HEADER_AMOUNT, HEADER_CATEGORY,
                    HEADER_MERCHANT, HEADER_NOTE, HEADER_SOURCE, HEADER_HASH,
                ),
            )
            transactions.forEach { t ->
                add(
                    listOf(
                        timeFormat.format(Date(t.timestamp)),
                        if (t.amount >= 0) "收入" else "支出",
                        kotlin.math.abs(t.amount),
                        t.category,
                        t.merchant.orEmpty(),
                        t.note.orEmpty(),
                        t.source,
                        t.hash,
                    ),
                )
            }
        }
        return MiniXlsx.write(rows)
    }
}
