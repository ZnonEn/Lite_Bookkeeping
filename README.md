# 轻记账

本地优先的 Android 个人记账 App。手动记账 + 自动记账，微信 / 支付宝账单一键导入，数据全部保存在本机，**不请求任何网络权限**。

## 功能

### 记账
- 手动记一笔：支出 / 收入、分类、商户、备注、自定义时间
- 主页概览：当月收支总览、近 7 日收支迷你柱状图、按日分组流水
- 月份快速切换：左右箭头逐月，点击月份弹出日历直接选月

### 自动记账（实验性）
半自动模式：检测到交易时在屏幕下方弹出**悬浮确认卡片**（方向可切换，金额 / 对方 / 分类 / 时间一目了然），点「记一笔」才入库，「忽略」后同一笔不再打扰：
- **无障碍服务**：专版识别支付成功页、微信/支付宝账单详情页、红包与转账收款详情页（规则参考 Tally 与 AutoRule 项目）
- **通知使用权**：支付完成后的系统通知（带金额）直接解析
- **OCR 屏幕识别**：页面对无障碍隐藏内容时（如支付宝扫码），抓屏后用 ML Kit 中文识别兜底（需授权屏幕录制，重启后需重新授权）
- 微信 8.0.52+ 会对第三方无障碍服务隐藏页面内容，本项目使用与系统「随选朗读」一致的服务类名以获取完整节点（社区通行做法）
- 确认卡片需要「显示在其他应用上层」权限；本地规则引擎自动分类，手动改过分类会自动学习；跨通道自动去重
- 已知限制：微信发出红包/转账的过程中没有金额信息，无法自动记录

### 统计
- 本周 / 本月 / 本年 / 自定义区间四种周期，年→月、月→周逐级下钻
- 趋势柱状图 / 占比环图、环比上期、日均、分类排行榜

### 账单导入
- 微信支付账单（.xlsx）与支付宝交易明细（.csv）
- 按交易哈希自动去重，重复导入不产生重复记录
- 只保留收支类型、收支方、金额、时间与商品说明，订单号等隐私字段不入库

### 其他
- 搜索：关键词、收支类型、分类、时间范围多条件过滤
- 分类规则管理：自定义关键词 → 分类映射
- 数据备份：一键导出 JSON
- 检查更新：应用不联网，通过系统浏览器跳转 GitHub Releases 查看新版本
- 深色 / 浅色模式，Apple 风格界面与转场动效

## 下载

从 [GitHub Releases](https://github.com/ZnonEn/Lite_Bookkeeping/releases/latest) 下载最新的 APK 安装。

## 隐私

- 不声明 `INTERNET` 权限，所有数据仅存于本机 Room 数据库
- 无障碍 / 通知使用权 / 屏幕录制仅用于读取支付结果文本或画面，不做任何点击、不联网
- OCR 使用 ML Kit 离线中文模型，识别在本机完成

## 技术栈

| 项 | 说明 |
| --- | --- |
| 语言 | Kotlin 2.2 |
| UI | Jetpack Compose（Material 3，Apple HIG 风格定制） |
| 存储 | Room + DataStore |
| 识别 | ML Kit 文本识别·中文（离线模型，不依赖 GMS） |
| 架构 | 单 Activity + Navigation Compose，MVVM，Coroutines / Flow |
| 构建 | AGP 9、Gradle 9、KSP（已配置阿里云 / 腾讯国内镜像） |

最低支持 Android 8.0（API 26）。

## 构建

1. 安装最新版 Android Studio（内置的 JDK 21 即可）
2. 克隆本仓库后用 Android Studio 打开，等待 Gradle Sync 完成
3. `./gradlew assembleDebug` 构建调试包，或直接在 Android Studio 中运行

```bash
git clone https://github.com/ZnonEn/Lite_Bookkeeping.git
cd Lite_Bookkeeping
./gradlew assembleDebug
```

## 项目结构

```
app/src/main/java/
├── com/google/android/accessibility/selecttospeak/
│                 # 无障碍服务本体（类名伪装为系统「随选朗读」，勿改）
└── com/nonen/Bookkeeping/
    ├── core/        # 分类规则引擎、哈希、通用工具
    ├── data/
    │   ├── db/      # Room 实体与 DAO
    │   ├── prefs/   # DataStore 设置
    │   └── repo/    # 仓库层
    ├── debug/       # 抓取调试统一入口（主源集为空实现，test 分支 src/debug 源集提供真实实现）
    ├── parse/       # 微信/支付宝账单解析、支付成功页文本解析、导入器
    ├── service/     # 无障碍自动记账、通知使用权监听、OCR 屏幕识别
    ├── ui/
    │   ├── components/  # 通用组件（分段控件、账单行等）
    │   ├── motion/      # 动效（弹簧、按压缩放）
    │   ├── screens/     # 主页 / 统计 / 设置 / 记一笔 / 搜索 / 规则
    │   └── theme/       # 配色与字体（深浅双主题）
    └── export/      # JSON 备份导出
```

## 分支说明

- `main`：稳定分支，日常使用构建此分支，不含任何调试功能
- `test`：`main` + `app/src/debug/` 源集形式的抓取调试模块（记录无障碍事件心跳、各通道抓取文本与解析结论，设置页实时报告）
  - 调试模块只在 **debug 构建**（`./gradlew assembleDebug` 或 Android Studio Run）生效，release 构建不包含
  - 合并方向保持 `main` → `test`，不要把 `test` 合回 `main`；两分支共享代码保持完全一致

## 状态

v0.2pre，个人项目，仍在迭代中。

## 许可

仅供个人学习与使用。
