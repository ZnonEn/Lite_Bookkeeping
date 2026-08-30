package com.nonen.Bookkeeping.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(t: TransactionEntity): Long

    @Update
    suspend fun update(t: TransactionEntity)

    @Delete
    suspend fun delete(t: TransactionEntity)

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getById(id: Long): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE timestamp >= :start AND timestamp < :end ORDER BY timestamp DESC")
    fun observeRange(start: Long, end: Long): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE timestamp >= :start AND timestamp <= :end ORDER BY timestamp DESC")
    suspend fun getRange(start: Long, end: Long): List<TransactionEntity>

    /** 语义去重用：自动记账/导入来源里，与给定金额和时间都相近的记录数 */
    @Query(
        "SELECT COUNT(*) FROM transactions WHERE source IN ('auto', 'wechat', 'alipay') " +
            "AND abs(amount - :amount) < 0.005 AND timestamp BETWEEN :from AND :to"
    )
    suspend fun countSimilarAuto(amount: Double, from: Long, to: Long): Int

    @Query(
        """SELECT * FROM transactions WHERE
        (:keyword = '' OR note LIKE '%' || :keyword || '%' OR merchant LIKE '%' || :keyword || '%'
           OR category LIKE '%' || :keyword || '%' OR CAST(ABS(amount) AS TEXT) LIKE '%' || :keyword || '%')
        AND (:category IS NULL OR category = :category)
        AND (:type IS NULL OR (:type = 'income' AND amount > 0) OR (:type = 'expense' AND amount < 0))
        AND timestamp >= :start AND timestamp <= :end
        ORDER BY timestamp DESC LIMIT 500"""
    )
    suspend fun search(
        keyword: String,
        category: String?,
        type: String?,
        start: Long,
        end: Long,
    ): List<TransactionEntity>

    @Query("SELECT DISTINCT category FROM transactions ORDER BY category")
    suspend fun allCategories(): List<String>

    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    suspend fun getAll(): List<TransactionEntity>
}

@Dao
interface CategoryRuleDao {

    @Query("SELECT * FROM category_rules ORDER BY isCustom DESC, id ASC")
    fun observeAll(): Flow<List<CategoryRuleEntity>>

    @Query("SELECT * FROM category_rules")
    suspend fun getAll(): List<CategoryRuleEntity>

    @Query("SELECT * FROM category_rules WHERE keyword = :keyword LIMIT 1")
    suspend fun findByKeyword(keyword: String): CategoryRuleEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(rule: CategoryRuleEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(rules: List<CategoryRuleEntity>)

    @Query("DELETE FROM category_rules WHERE id = :id")
    suspend fun deleteById(id: Long)
}
