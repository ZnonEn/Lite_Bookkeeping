package com.nonen.Bookkeeping.data.repo

import com.nonen.Bookkeeping.data.db.CategoryRuleDao
import com.nonen.Bookkeeping.data.db.CategoryRuleEntity
import com.nonen.Bookkeeping.data.db.MerchantCategoryDao
import kotlinx.coroutines.flow.Flow

class RuleRepository(
    private val dao: CategoryRuleDao,
    private val merchantDao: MerchantCategoryDao,
) {

    fun observeAll(): Flow<List<CategoryRuleEntity>> = dao.observeAll()

    /** 已学习的商户记忆条数（规则页展示 + 清空入口用） */
    fun observeMerchantCount(): Flow<Int> = merchantDao.observeCount()

    /**
     * 添加自定义关键词规则。
     * @return false 表示关键词为空，或同方向下该关键词已有等价规则
     */
    suspend fun add(keyword: String, category: String, isIncome: Boolean): Boolean {
        val k = keyword.trim()
        if (k.isEmpty()) return false
        if (k in existingKeywords(isIncome)) return false
        dao.insert(CategoryRuleEntity(keyword = k, category = category, isCustom = true))
        return true
    }

    suspend fun delete(id: Long) = dao.deleteById(id)

    /** 清空全部商户记忆（关键词规则不受影响） */
    suspend fun clearMerchantMemory() = merchantDao.clearAll()

    /**
     * 同方向下已存在的关键词（含内置）。
     * 用于「添加规则」的查重：唯一约束是 (keyword, category)，
     * 同一关键词在不同分类下可以共存，因此需要按方向+分类判断是否等价。
     */
    private suspend fun existingKeywords(isIncome: Boolean): Set<String> {
        val allowed = com.nonen.Bookkeeping.core.PlatformCategories.allowedCategories(isIncome)
        return dao.getAll().filter { it.category in allowed }.map { it.keyword }.toSet()
    }
}
