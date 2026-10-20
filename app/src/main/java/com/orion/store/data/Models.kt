package com.orion.store.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; explicitNulls = false; isLenient = true; encodeDefaults = true }

object Platform { const val ANDROID = "Android"; const val PC = "PC"; const val TV = "电视" }

enum class SortOption(val label: String) {
    HOME("主页"), NEWEST("最近添加"), OLDEST("最旧添加"), NAME_ASC("姓名（A-Z）"),
    NAME_DESC("姓名（Z-A）"), SIZE_ASC("尺寸（最小）"), SIZE_DESC("尺寸（最大）")
}

enum class Tab(val platform: String?) { ANDROID(Platform.ANDROID), PC(Platform.PC), TV(Platform.TV), MYAPPS(null), ABOUT(null), UPDATES(null) }

val STREAMS = listOf("Stable", "Beta", "Alpha", "Nightly")

@Serializable data class AppVariant(val arch: String = "", val url: String = "", val size: Long? = null)

@Serializable data class VersionOption(val type: String = "Stable", val version: String = "", val date: String = "", val variants: List<AppVariant> = emptyList())

@Serializable data class AppItem(
    val id: String = "",
    val name: String = "未知应用",
    val description: String = "",
    val icon: String = "",
    val version: String = "Latest",
    val latestVersion: String = "Latest",
    val downloadUrl: String = "#",
    val variants: List<AppVariant> = emptyList(),
    val availableVersions: List<VersionOption> = emptyList(),
    val repoUrl: String? = null,
    val githubRepo: String? = null,
    val gitlabRepo: String? = null,
    val codebergRepo: String? = null,
    val gitlabDomain: String? = null,
    val releaseKeyword: String? = null,
    val packageName: String? = null,
    val category: String = "Utility",
    val tags: List<String> = emptyList(),
    val platform: String = Platform.ANDROID,
    val size: String = "",
    val author: String = "未知",
    val screenshots: List<String> = emptyList(),
    val officialSite: String? = null,
    val patches: List<String> = emptyList(),
)

@Serializable data class SocialLinks(val github: String = "", val x: String = "", val discord: String = "", val coffee: String = "")
@Serializable data class DonationConfig(val kofiUrl: String? = null, val upiId: String? = null, val paypalUrl: String? = null)
@Serializable data class DevProfile(val name: String = "", val image: String? = null, val bio: String = "")
@Serializable data class FAQItem(val question: String = "", val answer: String = "", val icon: String = "")
@Serializable data class Notice(val id: String = "", val title: String = "", val message: String = "", val show: Boolean = false)
@Serializable data class PillConfig(val id: String? = null, val label: String? = null, val appId: String? = null, val tone: String? = null)
@Serializable data class ModuleBundleConfig(val id: String = "", val title: String = "", val description: String? = null, val appIds: List<String> = emptyList(), val color: String? = null, val badge: String? = null, val icon: String? = null, val monogram: String? = null)
@Serializable data class ModuleConfig(
    val id: String = "", val type: String = "", val title: String = "", val subtitle: String? = null, val platform: String? = null,
    val insertAfterCategory: Double? = null, val animation: String? = null, val appIds: List<String>? = null,
    val pills: List<PillConfig>? = null, val bundles: List<ModuleBundleConfig>? = null,
)

@Serializable data class StoreConfig(
    val appsJsonUrl: String = "",
    val mirrorJsonUrl: String? = null,
    val maintenanceMode: Boolean = false,
    val maintenanceMessage: String? = null,
    val announcement: String? = null,
    val notice: Notice? = null,
    val minStoreVersion: String? = null,
    val latestStoreVersion: String? = null,
    val storeDownloadUrl: String? = null,
    val socials: SocialLinks? = null,
    val donation: DonationConfig? = null,
    val devProfile: DevProfile? = null,
    val faqs: List<FAQItem>? = null,
    val supportEmail: String? = null,
    val easterEggUrl: String? = null,
    val leaderboardUrl: String? = null,
    val storefrontModules: List<ModuleConfig>? = null,
)

@Serializable data class LeaderboardEntry(val username: String = "", val xp: Long = 0, val level: Int = 0, val title: String = "", val `class`: String = "Hybrid", val avatar_url: String = "", val rank: Int? = null)

data class BundleItem(val id: String, val title: String, val description: String, val appIds: List<String>, val apps: List<AppItem> = emptyList(), val color: String? = null, val badge: String? = null, val icon: String? = null, val monogram: String? = null)
data class CategoryCard(val id: String, val label: String, val icon: String, val count: Int, val accent: String, val gradient: List<String>)

enum class CollectionType { HERO, SWIMLANE, BUNDLE, RECOMMENDATION_BUNDLES, CATEGORY_CARDS, SORTED_GRID }

data class StoreCollection(
    val id: String, val title: String, val type: CollectionType, val subtitle: String? = null, val filter: String? = null,
    val animation: String? = null, val apps: List<AppItem> = emptyList(), val bundles: List<BundleItem> = emptyList(),
    val categoryCards: List<CategoryCard> = emptyList(), val totalAppCount: Int? = null,
)

object Defaults {
    const val CACHE_VERSION = "v1_6"
    const val NETWORK_TIMEOUT_MS = 8000L
    const val RELEASES_API = "https://api.github.com/repos/RookieEnough/Orion-Store/releases?per_page=8"
    const val MIRROR_JSON = "https://raw.githubusercontent.com/RookieEnough/Orion-Data/data/mirror.json"
    val CONFIG_URLS = listOf(
        "https://raw.githubusercontent.com/RookieEnough/Orion-Data/main/config.json",
        "https://gitlab.com/RookieEnough/Orion-Data/-/raw/main/config.json",
        "https://codeberg.org/RookieEnough/Orion-Data/raw/branch/main/config.json",
        "https://cdn.jsdelivr.net/gh/RookieEnough/Orion-Data@main/config.json",
    )
    val APPS_URLS = CONFIG_URLS.map { it.replace("config.json", "apps.json") }
    val MIRROR_LABELS = listOf("GitHub", "GitLab", "Codeberg", "JSDelivr")
    val MICROG_DEPENDENT_APPS = setOf("youtube-revanced", "yt-music-revanced", "google-photos-revanced")
    const val MICROG_INFO_URL = "https://github.com/microg/GmsCore/wiki"
    val DEV_SOCIALS = SocialLinks("https://github.com/RookieEnough", "https://x.com/_Rookie_Z", "https://discord.com/invite/CrM6y4ujnq", "https://www.buymeacoffee.com/rookiez")
    val DONATION = DonationConfig("https://ko-fi.com/rookie_z", "rookiez@ptyes", "paypal.me/RookieEnough")
    val DEV_PROFILE = DevProfile("RookieZ", "https://i.pinimg.com/originals/12/79/48/127948a3253396796874286570740594.jpg", "Building the open web, one commit at a time. No ads, no tracking, just code.")
    const val SUPPORT_EMAIL = "orionstoredev@gmail.com"
    const val EASTER_EGG = "https://www.youtube.com/watch?v=dQw4w9WgXcQ"
    const val SELF_PACKAGE = "com.orion.store"
    const val UNITY_GAME_ID = "5996387"
    val PACKAGE_SUFFIXES = listOf(".preview", ".debug", ".test", ".beta", ".canary", ".dev", ".alpha", ".nightly", ".staging", ".release")
    val FONTS = listOf(
        "spaceGrotesk" to "Space Grotesk", "inter" to "Inter", "poppins" to "Poppins", "manrope" to "Manrope", "outfit" to "Outfit",
        "dmSans" to "DM Sans", "plusJakartaSans" to "加上 Jakarta Sans", "rubik" to "Rubik", "nunitoSans" to "努尼托桑斯",
        "publicSans" to "公共字体", "systemDefault" to "系统默认值",
    )
    val PTR_CHARACTERS = listOf("cat", "dog", "pokeball", "shield", "owl", "robot", "ghost", "kitty", "bunny", "batman")
    val FAQS = listOf(
        FAQItem("为什么扫描仪应用说 Orion 有追踪器？", "AppManager 等扫描仪应用将 Unity Ads SDK 标记在 Orion 内。该 SDK 的存在正是为了实现一项功能：“关于”选项卡中的可选广告观看（Fuel the Code），观看广告可以为我提供支持，并为您赢得 XP 并解锁徽章。没有分析，没有数据挖掘，没有数据销售。该应用是开源的，因此任何人都可以验证它，广告是 100% 可选的，后台没有运行任何内容。", "fa-magnifying-glass"),
        FAQItem("Orion Store安全吗？", "绝对地。 Orion Store 完全开源。这意味着我们的代码在 GitHub 上公开，任何人都可以审核。我们相信透明度：没有隐藏的跟踪器，没有数据挖掘，只有一个干净的应用网关。", "fa-shield-cat"),
        FAQItem("Orion 上的应用安全吗？", "是的。我亲自使用官方存储库上提供的工具对它们进行审查和修改，以确保它们在到达这里之前安全、实用且尊重隐私。", "fa-check-double"),
        FAQItem("ReVanced 构建安全吗？", "是的。这些构建是通过 GitHub Actions 机器人自动进行的，而不是手动修补。他们使用官方 ReVanced CLI 以及来自 APKMirror、Uptodown 和 APKPure 的 APK 补丁。该流程每天早上 6 点运行。整个构建管道是透明且开源的：https://github.com/RookieEnough/Revanced-AutoBuilds", "fa-robot"),
        FAQItem("安装失败？", "如果您看到“应用未安装”或“包冲突”，则可能是签名不匹配。您无法在原始 Play Store 版本或其他 mod 上安装修改后的应用。首先卸载现有应用，然后安装 Orion 中的应用。", "fa-file-signature"),
        FAQItem("下载了不行？", "不要恐慌！只需前往应用的详细信息页面，然后单击右上角的“报告”图标 (⚠️)。它会预先填写一封电子邮件，以便我可以尽快修复它。", "fa-bug"),
        FAQItem("会有更多应用吗？", "是的，如果有更多有趣的应用可以添加的话。只要我找到值得关注的开源或有用的工具，该库就会不断增长。", "fa-layer-group"),
        FAQItem("我该如何支持？", "通过 ko-fi 捐赠。代码为商店提供动力，但咖啡为开发提供动力！您可以在社交部分找到该链接。", "fa-heart"),
        FAQItem("难道还有隐藏的彩蛋吗？", "建筑师凝视的地方，秘密就沉睡着。\n\n数一数蜘蛛的腿。数出立方体的顶点。\n\n多次打击面容。\n\n黄金真理等待着那些了解规则的人……我也是。", "fa-user-secret"),
    )
}
