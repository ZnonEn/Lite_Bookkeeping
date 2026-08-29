package com.nonen.Bookkeeping.data.repo

import com.nonen.Bookkeeping.data.db.CategoryRuleDao
import com.nonen.Bookkeeping.data.db.CategoryRuleEntity
import kotlinx.coroutines.flow.Flow

class RuleRepository(private val dao: CategoryRuleDao) {

    fun observeAll(): Flow<List<CategoryRuleEntity>> = dao.observeAll()

    /** @return false 表示关键词为空或规则已存在 */
    suspend fun add(keyword: String, category: String): Boolean {
        val k = keyword.trim()
        if (k.isEmpty()) return false
        if (dao.findByKeyword(k) != null) return false
        dao.insert(CategoryRuleEntity(keyword = k, category = category, isCustom = true))
        return true
    }

    suspend fun delete(id: Long) = dao.deleteById(id)
}
