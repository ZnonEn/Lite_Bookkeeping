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
import com.nonen.Bookkeeping.debug.CaptureDebug
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
        // 恢复「抓取调试」开关（src/debug 真实实现；主源集为空操作）
        CaptureDebug.init(this)
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
        // 播种内置分类规则：首次启动全量；升级时移除被重新归类的旧映射（RETIRED_RULES），
        // 再增量补种新增关键词（不动用户删过的其他内置规则与自定义规则）
        applicationScope.launch {
            val existing = ruleDao.getAll().map { it.keyword to it.category }.toSet()
            Categories.RETIRED_RULES.forEach { (keyword, category) ->
                if ((keyword to category) in existing) ruleDao.deleteBuiltin(keyword, category)
            }
            val missing = Categories.defaultRules()
                .filter { (keyword, category) -> (keyword to category) !in existing }
                .map { (keyword, category) ->
                    CategoryRuleEntity(keyword = keyword, category = category, isCustom = false)
                }
            if (missing.isNotEmpty()) {
                ruleDao.insertAll(missing)
            }
        }
    }
}
