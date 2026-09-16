package com.nonen.Bookkeeping.core

/**
 * 平台自带分类 → 本应用分类的映射。
 *
 * 微信/支付宝的账单文件里各自带一列权威分类（支付宝「交易分类」、微信「交易类型」），
 * 这是平台自己维护的体系，覆盖面天然大于内置关键词表，且零猜测成本，故优先级高于关键词匹配。
 *
 * 两条纪律：
 * 1. **只映射有把握的值**。平台值过于宽泛（微信「商户消费」「扫二维码」只说明支付场景，
 *    不含消费领域）时返回 null，交给关键词规则继续判断——宁可不映射，也不要错误兜底。
 * 2. **方向相关**。同一个平台值在收/支两个方向可能落不同分类
 *    （「转账红包」支出记人情、收入记红包），故按方向分别给出。
 */
object PlatformCategories {

    /** 某个平台值在两个方向上的落点；null 表示该方向不映射 */
    private data class Mapping(val expense: String? = null, val income: String? = null)

    /** 支付宝「交易分类」列取值 */
    private val ALIPAY: Map<String, Mapping> = mapOf(
        "交通出行" to Mapping(expense = "交通"),
        "餐饮美食" to Mapping(expense = "餐饮"),
        "服饰装扮" to Mapping(expense = "服饰美容"),
        "美容美发" to Mapping(expense = "服饰美容"),
        "日用百货" to Mapping(expense = "购物"),
        "数码电器" to Mapping(expense = "购物"),
        "医疗健康" to Mapping(expense = "医疗"),
        "住房物业" to Mapping(expense = "居住"),
        "文化休闲" to Mapping(expense = "娱乐"),
        "运动户外" to Mapping(expense = "娱乐"),
        "教育培训" to Mapping(expense = "教育"),
        "酒店旅游" to Mapping(expense = "旅行"),
        "通讯物流" to Mapping(expense = "通讯"),
        "充值缴费" to Mapping(expense = "通讯"),
        "宠物" to Mapping(expense = "宠物"),
        "母婴亲子" to Mapping(expense = "母婴"),
        "保险" to Mapping(expense = "金融"),
        "信用借还" to Mapping(expense = "金融"),
        "投资理财" to Mapping(expense = "金融", income = "理财"),
        "亲友代付" to Mapping(expense = "人情"),
        "转账红包" to Mapping(expense = "人情", income = "红包"),
        "退款" to Mapping(income = "退款"),
        // 未列出：生活服务、公共服务、其他 —— 语义过泛，交给关键词规则
    )

    /** 微信「交易类型」列取值 */
    private val WECHAT: Map<String, Mapping> = mapOf(
        "微信红包" to Mapping(expense = "人情", income = "红包"),
        "转账" to Mapping(expense = "人情", income = "其他收入"),
        "微信转账" to Mapping(expense = "人情", income = "其他收入"),
        "群收款" to Mapping(expense = "人情", income = "其他收入"),
        "退款" to Mapping(income = "退款"),
        "红包" to Mapping(expense = "人情", income = "红包"),
        // 未列出：商户消费、扫二维码、其他 —— 只说明支付场景，不含消费领域
    )

    /**
     * 解析平台分类值。
     * @param platformValue 账单里的原始分类值（支付宝「交易分类」/ 微信「交易类型」）
     * @param isIncome 收支方向
     * @return 命中的本应用分类；无把握映射时返回 null
     */
    fun resolve(platformValue: String?, isIncome: Boolean): String? {
        val value = platformValue?.trim().orEmpty()
        if (value.isEmpty()) return null
        val mapping = ALIPAY[value] ?: WECHAT[value] ?: return null
        return if (isIncome) mapping.income else mapping.expense
    }

    /** 供单测校验：所有映射目标都必须是本应用已定义的合法分类 */
    internal fun allMappedCategories(): List<String> =
        (ALIPAY.values + WECHAT.values).flatMap { listOfNotNull(it.expense, it.income) }

    /** 供单测校验：映射表的全部原始键（两个平台可能重名，取并集） */
    internal fun allPlatformKeys(): Set<String> = ALIPAY.keys + WECHAT.keys

    /**
     * 判断某个分类名是否属于指定方向。
     * 平台映射与关键词规则都要用它过滤，避免把收入类的词归到支出上。
     */
    fun allowedCategories(isIncome: Boolean): Set<String> =
        if (isIncome) Categories.incomeCategories.toSet() else Categories.expenseCategories.toSet()
}
