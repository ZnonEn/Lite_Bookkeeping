package com.nonen.Bookkeeping.data.repo

import com.nonen.Bookkeeping.core.RuleEngine
import com.nonen.Bookkeeping.data.db.CategoryRuleDao
import com.nonen.Bookkeeping.data.db.TransactionDao
import com.nonen.Bookkeeping.data.db.TransactionEntity
import com.nonen.Bookkeeping.data.prefs.SettingsStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.time.YearMonth
import java.time.ZoneId

class TransactionRepository(
    private val dao: TransactionDao,
    private val ruleDao: CategoryRuleDao,
    private val settings: SettingsStore,
) {
    fun observeMonth(month: YearMonth): Flow<List<TransactionEntity>> {
        val zone = ZoneId.systemDefault()
        val start = month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return dao.observeRange(start, end)
    }

    suspend fun getById(id: Long): TransactionEntity? = dao.getById(id)

    fun observeRange(start: Long, end: Long): Flow<List<TransactionEntity>> = dao.observeRange(start, end)

    /**
     * 语义去重：自动记账/导入来源里是否已存在「金额相同且时间相近」的记录。
     * 同一笔交易在成功页与详情页的商户写法可能不同（科蕊小吃店 vs 苍南县科蕊小吃店），
     * 不能依赖商户一致，用金额+时间判同一笔。
     */
    suspend fun hasSimilar(amount: Double, timestamp: Long, windowMs: Long = 120_000): Boolean =
        dao.countSimilarAuto(amount, timestamp - windowMs, timestamp + windowMs) > 0

    /** @return false 表示 hash 重复，已存在相同记录 */
    suspend fun insertIfNew(entity: TransactionEntity): Boolean = dao.insert(entity) != -1L

    suspend fun update(entity: TransactionEntity) =
        dao.update(entity.copy(updatedAt = System.currentTimeMillis()))

    suspend fun delete(entity: TransactionEntity) = dao.delete(entity)

    suspend fun search(
        keyword: String,
        category: String?,
        type: String?,
        start: Long,
        end: Long,
    ): List<TransactionEntity> = dao.search(keyword.trim(), category, type, start, end)

    suspend fun allCategories(): List<String> = dao.allCategories()

    suspend fun getAll(): List<TransactionEntity> = dao.getAll()

    suspend fun getRange(start: Long, end: Long): List<TransactionEntity> = dao.getRange(start, end)

    /**
     * 按当前分类规则重算全部历史账单的分类（会覆盖手动改过的分类，自定义学习规则优先生效）。
     * @return 实际改动了分类的条数
     */
    suspend fun reclassifyAll(): Int {
        val rules = ruleDao.getAll()
        var updated = 0
        for (t in dao.getAll()) {
            val text = listOfNotNull(t.merchant, t.note).joinToString(" ")
            val category = RuleEngine.matchRules(text, t.amount > 0, rules)
            if (category != t.category) {
                dao.update(t.copy(category = category, updatedAt = System.currentTimeMillis()))
                updated++
            }
        }
        return updated
    }

    /**
     * 用户手动修改某笔交易分类时，把该笔交易的商户/备注关键词学习为自定义规则，
     * 下次遇到相同关键词自动归入同类（自动分类的本地优化）。
     */
    suspend fun learnFromEdit(entity: TransactionEntity, oldCategory: String?) {
        if (entity.category == oldCategory) return
        if (!settings.learnOnEdit.first()) return
        val keyword = keywordOf(entity) ?: return
        if (keyword.length < 2) return
        ruleDao.insert(
            com.nonen.Bookkeeping.data.db.CategoryRuleEntity(
                keyword = keyword,
                category = entity.category,
                isCustom = true,
            )
        )
    }

    private fun keywordOf(entity: TransactionEntity): String? {
        val raw = entity.merchant?.takeIf { it.isNotBlank() }
            ?: entity.note?.takeIf { it.isNotBlank() }
            ?: return null
        // 去掉括号里的分店/编号等修饰，如「肯德基（XX路店）」→「肯德基」
        var keyword = raw.trim().replace(Regex("[（(【\\[].*?[）)】\\]]"), "").trim()
        if (keyword.isEmpty()) keyword = raw.trim()
        return keyword.take(20)
    }
}
