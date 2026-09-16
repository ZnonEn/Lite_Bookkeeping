# 轻记账 R8 规则。
#
# 本应用不联网、无反射框架，规则只需守住两类不可混淆的契约。

# ── 1. 无障碍服务：类名即协议，绝不能混淆或改名 ──────────────────────────────
# 微信 8.0.52+ 按无障碍服务的完整类名做白名单匹配，只有与系统「随选朗读」
# 同名的服务才会拿到完整节点树（详见 SelectToSpeakService 的类注释）。
# 一旦被 R8 重命名，微信会重新隐藏支付页内容，自动记账直接失效。
-keep class com.google.android.accessibility.selecttospeak.SelectToSpeakService { *; }
-keepnames class com.google.android.accessibility.selecttospeak.SelectToSpeakService

# 其余在清单里声明的组件（服务、Activity）由 R8 自动保留，此处显式加固，
# 避免将来有人移动包名或改动清单时静默丢失。
-keep class com.nonen.Bookkeeping.MainActivity { *; }
-keep class com.nonen.Bookkeeping.BookkeepingApp { *; }
-keep class com.nonen.Bookkeeping.service.PaymentNotificationListener { *; }

# ── 2. Room ────────────────────────────────────────────────────────────────
# Room 生成的实现类与实体通过反射/注解处理器协作，按官方建议保留。
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep @androidx.room.Entity class * { *; }
-dontwarn androidx.room.paging.**

# ── 3. DataStore / Kotlin 元数据 ───────────────────────────────────────────
# DataStore 的 Preferences 序列化依赖注解与内部类名。
-keepclassmembers class * extends androidx.datastore.core.DataStore { *; }
-keep class androidx.datastore.preferences.** { *; }

# Kotlin 协程与 Flow 的正常代码路径不依赖类名，但保留元数据便于排查线上问题。
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
