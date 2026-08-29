package com.nonen.Bookkeeping

import android.app.Application
import android.content.Context
import androidx.room.Room
import com.nonen.Bookkeeping.core.Categories
import com.nonen.Bookkeeping.core.RuleEngine
import com.nonen.Bookkeeping.data.db.AppDatabase
import com.nonen.Bookkeeping.data.db.CategoryRuleEntity
import com.nonen.Bookkeeping.data.prefs.SettingsStore
import com.nonen.Bookkeeping.data.repo.RuleRepository
import com.nonen.Bookkeeping.data.repo.TransactionRepository
import com.nonen.Bookkeeping.parse.BillImporter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class BookkeepingApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/**
 * 手动依赖注入容器：项目小，不引入 Hilt。
 */
class AppContainer(val appContext: Context) {

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val settings = SettingsStore(appContext)

    private val database = Room.databaseBuilder(appContext, AppDatabase::class.java, "bookkeeping.db")
        .build()

    val transactionDao get() = database.transactionDao()
    val ruleDao get() = database.categoryRuleDao()

    val ruleRepository = RuleRepository(ruleDao)
    val transactionRepository = TransactionRepository(transactionDao, ruleDao, settings)
    val ruleEngine = RuleEngine(ruleDao)
    val billImporter = BillImporter(transactionRepository, ruleEngine)

    init {
        // 首次启动播种内置分类规则
        applicationScope.launch {
            if (ruleDao.getAll().isEmpty()) {
                ruleDao.insertAll(
                    Categories.defaultRules().map { (keyword, category) ->
                        CategoryRuleEntity(keyword = keyword, category = category, isCustom = false)
                    }
                )
            }
        }
    }
}
