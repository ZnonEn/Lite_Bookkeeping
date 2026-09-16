package com.nonen.Bookkeeping.data.repo

import com.nonen.Bookkeeping.core.CategorizationRules
import com.nonen.Bookkeeping.core.MerchantKey
import com.nonen.Bookkeeping.core.PlatformCategories
import com.nonen.Bookkeeping.core.RuleEngine
import com.nonen.Bookkeeping.data.db.CategoryRuleDao
import com.nonen.Bookkeeping.data.db.MerchantCategoryDao
import com.nonen.Bookkeeping.data.db.MerchantCategoryEntity
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
    private val merchantDao: MerchantCategoryDao,
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
     * 按当前分类依据重算全部历史账单的分类（含商户记忆；会覆盖手动改过的分类）。
     * 每笔的商户若已有记忆，以记忆为准。
     *
     * @return 实际改动了分类的条数
     */
    suspend fun reclassifyAll(): Int {
        val rules = CategorizationRules(rules = ruleDao.getAll(), merchants = merchantDao.getAll())
        var updated = 0
        for (t in dao.getAll()) {
            val text = listOfNotNull(t.merchant, t.note).joinToString(" ")
            val category = RuleEngine.classify(
                text = text,
                isIncome = t.amount > 0,
                rules = rules,
                merchant = t.merchant,
            )
            if (category != t.category) {
                dao.update(t.copy(category = category, updatedAt = System.currentTimeMillis()))
                updated++
            }
        }
        return updated
    }

    /**
     * 用户手动修改某笔交易分类时学习：记下「这个商户属于这个分类」。
     *
     * 学习结果写入 merchant_categories（按归一化商户名精确/包含匹配），
     * 而不是把商户名截断成关键词塞进 category_rules——后者走子串匹配，
     * 长商户名（「星巴克臻选上海南京西路店」）截断后几乎再也匹配不到，
     * 短商户名又容易误伤。商户记忆是最具体的一层依据，优先于所有关键词规则。
     */
    suspend fun learnFromEdit(entity: TransactionEntity, oldCategory: String?) {
        if (entity.category == oldCategory) return
        if (!settings.learnOnEdit.first()) return
        learnMerchant(entity.merchant, entity.category, entity.amount > 0)
    }

    /**
     * 自动记账确认卡片里用户确认分类时学习——这是最高价值的学习时刻：
     * 用户亲手把某个商户归到了某个分类，下次同一商户应直接命中。
     */
    suspend fun learnFromConfirm(merchant: String?, category: String, isIncome: Boolean) {
        if (!settings.learnOnEdit.first()) return
        learnMerchant(merchant, category, isIncome)
    }

    /** 记下「该商户在此方向上是这个分类」；商户为空或分类不属于该方向时忽略 */
    private suspend fun learnMerchant(merchant: String?, category: String, isIncome: Boolean) {
        val key = MerchantKey.normalize(merchant) ?: return
        if (category !in PlatformCategories.allowedCategories(isIncome)) return
        val existing = merchantDao.find(key, isIncome)
        merchantDao.upsert(
            MerchantCategoryEntity(
                id = existing?.id ?: 0L,
                merchantKey = key,
                category = category,
                isIncome = isIncome,
                hits = (existing?.hits ?: 0) + 1,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }
}
