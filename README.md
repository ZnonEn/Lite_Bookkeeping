# 轻记账

本地优先的 Android 个人记账 App。手动记账 + 自动记账，微信 / 支付宝账单一键导入，数据全部保存在本机，**不请求任何网络权限**。

## 功能

### 记账
- 手动记一笔：支出 / 收入、分类、商户、备注、自定义时间
- 主页概览：当月收支总览、近 7 日收支迷你柱状图、按日分组流水
- 月份快速切换：左右箭头逐月，点击月份弹出日历直接选月

### 自动记账（实验性）
- 基于**无障碍服务**监听支付页面、**通知使用权**监听支付结果通知，付完款自动记录
- 本地规则引擎自动分类，手动改过分类会自动学习
- 支付页面按窗口归属精确判定，跨通道自动去重，抓不到的页面宁可不记

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
- 深色 / 浅色模式，Apple 风格界面与转场动效

## 隐私

- 不声明 `INTERNET` 权限，所有数据仅存于本机 Room 数据库
- 自动记账依赖的无障碍 / 通知使用权仅用于读取支付结果文本，不做任何点击、不联网

## 技术栈

| 项 | 说明 |
| --- | --- |
| 语言 | Kotlin 2.2 |
| UI | Jetpack Compose（Material 3，Apple HIG 风格定制） |
| 存储 | Room + DataStore |
| 架构 | 单 Activity + Navigation Compose，MVVM，Coroutines / Flow |
| 构建 | AGP 9、Gradle 9、KSP（已配置阿里云 / 腾讯国内镜像） |

最低支持 Android 8.0（API 26）。

## 构建

1. 安装最新版 Android Studio 与 JDK 17+
2. 克隆本仓库后用 Android Studio 打开，等待 Gradle Sync 完成
3. `./gradlew assembleDebug` 构建调试包，或直接在 Android Studio 中运行

```bash
git clone https://github.com/ZnonEn/Bookkeeping.git
cd Bookkeeping
./gradlew assembleDebug
```

## 项目结构

```
app/src/main/java/com/nonen/Bookkeeping/
├── core/          # 分类规则引擎、哈希、通用工具
├── data/
│   ├── db/        # Room 实体与 DAO
│   ├── prefs/     # DataStore 设置
│   └── repo/      # 仓库层
├── parse/         # 微信/支付宝账单解析、窗口/通知文本解析、导入器
├── service/       # 无障碍自动记账、通知使用权监听
├── ui/
│   ├── components/  # 通用组件（分段控件、账单行等）
│   ├── motion/      # 动效（弹簧、按压缩放）
│   ├── screens/     # 主页 / 统计 / 设置 / 记一笔 / 搜索 / 规则
│   └── theme/       # 配色与字体（深浅双主题）
└── export/        # JSON 备份导出
```

## 分支说明

- `main`：稳定分支，日常使用构建此分支
- `test`：调试分支，包含自动记账的抓取调试面板等排查工具

## 状态

v0.1beta，个人项目，仍在迭代中。

## 许可

仅供个人学习与使用。
