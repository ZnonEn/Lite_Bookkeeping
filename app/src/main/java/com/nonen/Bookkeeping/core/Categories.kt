package com.nonen.Bookkeeping.core

/** 预设收支分类与内置分类规则。 */
object Categories {
    const val TYPE_INCOME = "income"
    const val TYPE_EXPENSE = "expense"

    const val OTHER_EXPENSE = "其他"
    const val OTHER_INCOME = "其他收入"

    val expenseCategories = listOf("餐饮", "交通", "购物", "娱乐", "居住", "医疗", OTHER_EXPENSE)
    val incomeCategories = listOf("工资", "红包", "退款", "理财", OTHER_INCOME)
    val allCategories = expenseCategories + incomeCategories

    /** 内置关键词规则（关键词 → 分类），首次启动播种入库，用户可增删。 */
    fun defaultRules(): List<Pair<String, String>> = listOf(
        // 餐饮（含平台自带分类「餐饮美食」等）
        "餐饮" to "餐饮", "美团" to "餐饮", "饿了么" to "餐饮", "外卖" to "餐饮", "饭店" to "餐饮",
        "餐厅" to "餐饮", "咖啡" to "餐饮", "奶茶" to "餐饮", "肯德基" to "餐饮", "麦当劳" to "餐饮",
        "火锅" to "餐饮", "食品" to "餐饮", "小吃" to "餐饮",
        // 交通（含平台自带分类「交通出行」等）
        "交通" to "交通", "滴滴" to "交通", "地铁" to "交通", "公交" to "交通", "加油" to "交通",
        "停车" to "交通", "打车" to "交通", "火车" to "交通", "机票" to "交通", "航空" to "交通",
        "单车" to "交通", "出行" to "交通", "铁路" to "交通",
        // 购物
        "购物" to "购物", "淘宝" to "购物", "京东" to "购物", "拼多多" to "购物", "天猫" to "购物",
        "超市" to "购物", "便利店" to "购物", "商场" to "购物", "百货" to "购物", "服饰" to "购物",
        // 娱乐
        "娱乐" to "娱乐", "电影" to "娱乐", "KTV" to "娱乐", "游戏" to "娱乐", "旅游" to "娱乐",
        "健身" to "娱乐", "酒店" to "娱乐", "门票" to "娱乐",
        // 居住
        "居住" to "居住", "房租" to "居住", "水费" to "居住", "电费" to "居住", "燃气" to "居住",
        "物业" to "居住", "宽带" to "居住", "话费" to "居住",
        // 医疗
        "医疗" to "医疗", "医院" to "医疗", "药房" to "医疗", "药店" to "医疗", "诊所" to "医疗",
        "保险" to "医疗", "挂号" to "医疗", "健康" to "医疗",
        // 收入相关
        "工资" to "工资", "奖金" to "工资", "红包" to "红包", "退款" to "退款",
        "理财" to "理财", "利息" to "理财", "收益" to "理财", "分红" to "理财",
    )

    fun emoji(category: String): String = when (category) {
        "餐饮" -> "🍜"
        "交通" -> "🚇"
        "购物" -> "🛒"
        "娱乐" -> "🎮"
        "居住" -> "🏠"
        "医疗" -> "💊"
        "工资" -> "💰"
        "红包" -> "🧧"
        "退款" -> "💸"
        "理财" -> "📈"
        OTHER_INCOME -> "📥"
        else -> "📦"
    }
}
