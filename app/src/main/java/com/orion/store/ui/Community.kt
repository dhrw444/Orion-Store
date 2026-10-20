package com.orion.store.ui

import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.PathEffect
import androidx.activity.compose.BackHandler
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.net.ConnectivityManager
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.animation.core.Animatable
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.animation.core.VectorConverter
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import coil3.compose.AsyncImage
import com.orion.store.data.*
import com.unity3d.ads.IUnityAdsInitializationListener
import com.unity3d.ads.IUnityAdsLoadListener
import com.unity3d.ads.IUnityAdsShowListener
import com.unity3d.ads.UnityAds
import com.unity3d.ads.UnityAdsShowOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.net.URI
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.ceil
import kotlin.math.min

@Composable
fun ErrorNote(msg: String) = AnimatedVisibility(msg.isNotEmpty()) {
    Surface(shape = RoundedCornerShape(16.dp), color = cs.errorContainer, contentColor = cs.onErrorContainer) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Sym.ErrorOutline, null, Modifier.size(20.dp)); Spacer(Modifier.width(10.dp)); Text(msg, style = ty.bodyMedium)
        }
    }
}

/** Choice tile whose container morphs Circle → [to] when selected. */
@Composable
private fun MorphPick(selected: Boolean, onClick: () -> Unit, size: androidx.compose.ui.unit.Dp = 56.dp, to: RoundedPolygon = MaterialShapes.Cookie9Sided, color: Color = cs.primaryContainer, content: @Composable BoxScope.() -> Unit) {
    val morph = remember(to) { Morph(MaterialShapes.Circle, to) }
    val p by animateFloatAsState(if (selected) 1f else 0f, MaterialTheme.motionScheme.fastSpatialSpec(), label = "pick")
    val bg by animateColorAsState(if (selected) color else cs.surfaceContainerHighest, MaterialTheme.motionScheme.fastEffectsSpec(), label = "pickc")
    Box(Modifier.size(size).clip(MorphShape(morph, p)).background(bg).clickable(onClick = onClick), contentAlignment = Alignment.Center, content = content)
}

@Composable
private fun Section(step: Int, title: String, subtitle: String? = null, done: Boolean = false, content: @Composable ColumnScope.() -> Unit) =
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(cs.surfaceContainer).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Numbered cookie per step; it fills and turns into a check once the step has what it needs.
            Box(Modifier.size(36.dp).clip(MaterialShapes.Cookie9Sided.toShape()).background(if (done) cs.primary else cs.primaryContainer), contentAlignment = Alignment.Center) {
                if (done) Icon(Sym.Check, "完成", Modifier.size(18.dp), tint = cs.onPrimary) else Text("$step", style = ty.titleSmallEmphasized, color = cs.onPrimaryContainer)
            }
            Spacer(Modifier.width(12.dp))
            Column { Text(title, style = ty.titleMediumEmphasized); subtitle?.let { Text(it, style = ty.bodySmall, color = cs.onSurfaceVariant) } }
        }
        content()
    }

@Composable
private fun fieldColors() = TextFieldDefaults.colors(focusedContainerColor = cs.surfaceContainerHighest, unfocusedContainerColor = cs.surfaceContainerHighest,
    disabledContainerColor = cs.surfaceContainerHigh, focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, disabledIndicatorColor = Color.Transparent)

@Composable
private fun Field(value: String, onChange: (String) -> Unit, label: String, placeholder: String, modifier: Modifier = Modifier, lines: Int = 1, mono: Boolean = false,
                  enabled: Boolean = true, readOnly: Boolean = false, uri: Boolean = false, supporting: String? = null, trailing: (@Composable () -> Unit)? = null, onDone: (() -> Unit)? = null,
                  leading: (@Composable () -> Unit)? = null) =
    TextField(value, onChange, modifier.fillMaxWidth(), label = { Text(label) }, placeholder = { Text(placeholder) }, singleLine = lines == 1, minLines = lines,
        enabled = enabled, readOnly = readOnly, shape = RoundedCornerShape(16.dp), colors = fieldColors(), leadingIcon = leading, trailingIcon = trailing, supportingText = supporting?.let { { Text(it) } },
        textStyle = if (mono) ty.bodySmall.copy(fontFamily = FontFamily.Monospace) else LocalTextStyle.current,
        keyboardOptions = KeyboardOptions(keyboardType = if (uri) KeyboardType.Uri else KeyboardType.Text, imeAction = if (onDone != null) ImeAction.Done else ImeAction.Default),
        keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }))

private tailrec fun Context.activity(): Activity? = when (this) { is Activity -> this; is ContextWrapper -> baseContext.activity(); else -> null }

// ---------------- Ad donation quest ----------------

private const val PLACEMENT = "Rewarded_Android"
private const val AD_PROBE = "https://pagead2.googlesyndication.com/pagead/js/adsbygoogle.js"
private val LOADING_MESSAGES = listOf("召唤英雄...", "磨剑...", "正在建立上行链路...", "正在缓冲视频流...", "生成地形...", "正在加载 Boss 资源…")
private data class Card(val lvl: Int, val title: String, val msg: String, val icon: ImageVector, val color: Color)
private val Indigo = Color(0xFF6366F1)
private val STORY = listOf(
    Card(1, "英雄觉醒", "你睁开眼睛。数字世界需要节约。你拿起一个生锈的键盘。", Sym.Shield, Blue),
    Card(2, "击败史莱姆", "野生故障史莱姆出现了！你用控制台日志粉碎了它。 +10 经验值。", Sym.BugReport, Green),
    Card(3, "傀儡被击败！", "BOSS 战：代码魔像崩溃了！支持者徽章已解锁！", Sym.Diamond, Cyan),
    Card(4, "发现战利品", "你找到了“语法之盾”。编译弹性+20%。", Sym.Shield, Yellow),
    Card(5, "巨魔被放逐", "论坛巨魔挡住了桥。你永久禁止他了。道路是清晰的。", Sym.Gavel, Red),
    Card(6, "蜂群清零！", "BOSS 战：你消灭了虫群女王！超新星徽章已解锁！", Sym.AutoAwesome, Pink),
    Card(7, "升级", "您学习了“深色模式视觉”。现在你可以清楚地看到深层代码了。", Sym.Contrast, Gray),
    Card(8, "地牢奔跑", "进入服务器机房地牢。天气又冷又吵。", Sym.Dns, Indigo),
    Card(9, "防火墙已建", "你加强了防御。入侵者不会在这里找到容身之所。", Sym.GppGood, Orange),
    Card(10, "稀有神器", "获得“正常运行时间水晶”。稳定性+500%。", Sym.ViewInAr, Purple),
    Card(11, "接近 Boss", "地面震动。有某种巨大的东西从虚空逼近……", Sym.Warning, Red),
    Card(12, "虚空龙斩！", "BOSS战：你让龙不复存在！虚空行者徽章已解锁！", Sym.DarkMode, Purple),
    Card(13, "Respite", "您在数字篝火旁休息。您的健康已恢复。", Sym.LocalFireDepartment, Orange),
    Card(14, "拯救村庄", "感谢你们，开源村才安全。", Sym.Home, Safe),
    Card(15, "新任务", "内核之王请求你的帮助。", Sym.HistoryEdu, Yellow),
    Card(16, "内存泄漏", "你修补了大坝的一个大漏洞。山谷得救了。", Sym.FormatColorFill, Blue),
    Card(17, "云城", "你上升到了云层。数据在这里自由流动。", Sym.Cloud, SkyC),
    Card(18, "闪电", "你利用了原始电力的力量。", Sym.Bolt, Yellow),
    Card(19, "时间旅行", "您恢复了错误的提交。历史已得到纠正。", Sym.History, Pink),
    Card(20, "最高等级", "您已达到 99 级。但最后的挑战还在等待着您。", Sym.ArrowUpward, Green),
    Card(21, "关卡之门", "站在熵之门前。", Sym.Fort, Gray),
    Card(22, "第一封印", "你打破了忽视封印。再次更新流程。", Sym.LinkOff, Red),
    Card(23, "第二印", "你打破了贪婪封印。一切仍然免费。", Sym.Paid, Yellow),
    Card(24, "最后的准备", "喝了药水。铠甲抛光。就是这样。", Sym.Science, Purple),
    Card(25, "熵被击败！", "最终 BOSS：你拯救了整个数字宇宙！宇宙守护者徽章已解锁！", Sym.RocketLaunch, Fuchsia),
    // Chapter 2 · The Forkening: a long road, beats spaced further apart the deeper it goes
    Card(26, "分叉", "熵的最后一口气将Orion分裂成一千个流氓分叉。他们每个人都发誓这是真正的Orion。", Sym.AccountTree, Indigo),
    Card(27, "叉侦察兵", "一个名为 orion-final-FINAL-v2 的小叉子试图咬你。你出于怜悯而给它加了星标。", Sym.Star, Yellow),
    Card(28, "分支迷宫", "每个分支看起来都像主分支。他们都不是主要的。您留下了提交哈希的痕迹。", Sym.Explore, Green),
    Card(30, "自述文件Oracle", "一个古老的自述文件说：“安装：它就可以工作。”它撒了谎。您将永远不会再相信自述文件。", Sym.MenuBook, Blue),
    Card(33, "依赖沼泽", "您费力地浏览了 4,000 个 node_modules。里面还有东西正在安装。", Sym.WaterDrop, Cyan),
    Card(36, "分号走私者", "发现一个妖精在 Kotlin 中偷偷使用分号。没收。编译器谢谢你。", Sym.Gavel, Red),
    Card(40, "九头蛇足迹", "有七个头的脚印，每个头都印有<<<<<<< HEAD。九头蛇已经很近了。", Sym.Warning, Orange),
    Card(44, "变基仪式", "你如此干净地重写了历史，甚至 git log 都忘记了发生了什么。", Sym.History, Purple),
    Card(48, "差异门", "只为干净的差异而打开的大门。您将 47 个提交压缩为一个。它吱吱地打开了。", Sym.Fort, Gray),
    Card(50, "九头蛇合并！", "BOSS战：消除了一场冲突，两场冲突又重新出现，所以你把它们全部合并了。合并驯兽师徽章已解锁！", Sym.CallMerge, Teal),
    Card(55, "Fork 营地", "你们联合起来的叉子一起做饭。没有人争论制表符与空格。一个奇迹。", Sym.LocalFireDepartment, Orange),
    Card(60, "溢流悬崖", "您可以使用标记为重复的答案进行攀爬。每个把手都被视为偏离主题而关闭。", Sym.TrendingUp, Yellow),
    Card(66, "缓存未命中峡谷", "你对着峡谷大喊。回声晚了 300 毫秒返回。经典的。", Sym.Speed, SkyC),
    Card(72, "古代评论", "// TODO: fix later (2009). You fixed it. Somewhere, a ghost finally rests.", Sym.HistoryEdu, Indigo),
    Card(80, "旧日深渊", "地图到这里就结束了。下面是自从 jQuery 诞生以来没人敢重构的代码。", Sym.Tsunami, Blue),
    Card(88, "面条触手", "有一些长长的、纠结的、整体性的东西缠绕在你的脚踝上。这是一个 9,000 行的函数。", Sym.Warning, Red),
    Card(95, "深呼吸", "你打开文件。滚动条高一像素。你把指关节弄裂了。", Sym.Psychology, Purple),
    Card(100, "利维坦解脱！", "BOSS 战：您将意大利面条利维坦重构为整洁的小功能。意大利面杀手徽章已解锁！", Sym.RamenDining, Caution),
    Card(110, "试验园", "您进行单元测试。绿色格纹随处绽放，覆盖蝴蝶翩翩飞过。", Sym.TaskAlt, Green),
    Card(125, "王位传闻", "日志中窃窃私语：有人扎根，现在称自己为空皇帝。", Sym.Campaign, Yellow),
    Card(140, "没有权限", "皇帝的卫兵封锁了每条道路，并写着“拒绝许可”。你微笑。你带来了ADB。", Sym.Lock, Red),
    Card(155, "贝壳风暴", "你输入的速度比守卫 chmod 的速度还要快。管道和 grep 像箭一样飞翔。", Sym.Terminal, Cyan),
    Card(170, "内核恐慌平原", "地面闪烁红色，并在你脚下重新启动。不管怎样你还是继续走吧。", Sym.Bolt, Orange),
    Card(185, "老朋友", "内核之王、村庄和你们团结起来的每一个岔路口都在你们身边。城堡的门在颤抖。", Sym.Group, Indigo),
    Card(195, "虚无王座", "虚无皇帝崛起。他的皇冠上写着 sudo。他的眼睛说着不确定。", Sym.Castle, Gray),
    Card(200, "根回收！", "最终BOSS：你废黜了虚无皇帝，将根权交还给了人民。须藤主权徽章解锁！", Sym.Crown, Color(0xFFE11D48)),
)
/** Between Chapter 2 beats the road still answers every ad, so the grind never shows a blank card. */
private val MARCH = listOf(
    Card(0, "流浪虫子", "一只流浪虫子横亘在你的路上。压扁了。 +5 XP。", Sym.BugReport, Green),
    Card(0, "补给下降", "一箱咖啡从云层掉下来。体力恢复了。", Sym.Coffee, Orange),
    Card(0, "支线任务", "您帮助丢失的拉取请求找到了其审阅者。", Sym.Forum, Blue),
    Card(0, "夜间巡逻", "你在岔路口巡逻。主干上一切都很安静。", Sym.Shield, Indigo),
    Card(0, "训练弧", "你练习快捷方式，直到你的手指发光。", Sym.Bolt, Yellow),
    Card(0, "幸运发现", "正则表达式第一次尝试就成功了。传说说这是不可能的。", Sym.Stars, Pink),
)
private val END_CARD = Card(999, "主权义务", "root 是安全的，领域是免费的。无论如何你还是要留意。开发者向你致敬。", Sym.Star, Indigo)
private fun storyCard(lvl: Int) = STORY.firstOrNull { it.lvl == lvl } ?: if (lvl <= QUEST_BOSSES.last().lvl) MARCH[lvl % MARCH.size] else END_CARD

/** Ad-quest bosses: the level that beats each one and the badge it drops. Shared by the quest, the About rank card and the badge archive. */
data class QuestBoss(val lvl: Int, val name: String, val badge: String, val rank: String = badge)
val QUEST_BOSSES = listOf(
    QuestBoss(3, "代码傀儡", "Backer"), QuestBoss(6, "虫群女王", "Supernova"), QuestBoss(12, "虚空龙", "虚空行者"),
    QuestBoss(25, "Entropy", "守护者", "宇宙守护者"),
    QuestBoss(50, "合并冲突九头蛇", "合并驯兽师"), QuestBoss(100, "意大利面利维坦", "意大利面杀手"), QuestBoss(200, "空皇", "须藤主权"),
)
/** Chapter 1 ends with Entropy; everything after it is Chapter 2. */
const val CHAPTER_ONE_END = 25
fun questChapter(ads: Int) = if (ads >= CHAPTER_ONE_END) 2 else 1

/** Progress from the last boss beaten to the next one, for the camp screen and victory cards. */
@Composable
private fun NextBoss(ads: Int) {
    val next = QUEST_BOSSES.firstOrNull { ads < it.lvl } ?: return
    val from = QUEST_BOSSES.lastOrNull { ads >= it.lvl }?.lvl ?: 0
    val target = (ads - from).toFloat() / (next.lvl - from)
    var shown by remember { mutableStateOf(false) }; LaunchedEffect(Unit) { shown = true }
    val p by animateFloatAsState(if (shown) target else 0f, MaterialTheme.motionScheme.slowSpatialSpec(), label = "boss")
    val (bg, fg) = tonal(BADGES[next.badge]?.second ?: cs.primary)
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(bg).padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(MaterialShapes.Cookie7Sided.toShape()).background(fg.copy(.14f)), contentAlignment = Alignment.Center) {
                Icon(BADGES[next.badge]?.first ?: Sym.Lock, null, Modifier.size(24.dp), tint = fg)
            }
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Text("下一个 Boss", style = ty.labelMedium, color = fg.copy(.8f))
                Text(next.name, style = ty.titleMediumEmphasized, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text("${next.lvl - ads} 出发", style = ty.labelLargeEmphasized, color = fg)
        }
        LinearWavyProgressIndicator({ p }, Modifier.fillMaxWidth(), color = fg, trackColor = fg.copy(.2f))
        Text("掉落${next.badge}徽章", style = ty.bodySmall, color = fg.copy(.85f))
    }
}

/** Unity starts on first use: initializing at boot spun up its WebView on the main thread and hitched the first seconds. */
private suspend fun unityLoad(ctx: Context) {
    if (!UnityAds.isInitialized) suspendCancellableCoroutine { c ->
        UnityAds.initialize(ctx.applicationContext, Defaults.UNITY_GAME_ID, false, object : IUnityAdsInitializationListener {
            override fun onInitializationComplete() { if (c.isActive) c.resume(Unit) }
            override fun onInitializationFailed(e: UnityAds.UnityAdsInitializationError?, m: String?) { if (c.isActive) c.resumeWithException(Exception(m ?: e?.name ?: "初始化失败")) }
        })
    }
    unityLoadAd()
}

private suspend fun unityLoadAd() = suspendCancellableCoroutine { c ->
    UnityAds.load(PLACEMENT, object : IUnityAdsLoadListener {
        override fun onUnityAdsAdLoaded(p: String?) { if (c.isActive) c.resume(Unit) }
        override fun onUnityAdsFailedToLoad(p: String?, e: UnityAds.UnityAdsLoadError?, m: String?) { if (c.isActive) c.resumeWithException(Exception(m ?: e?.name ?: "加载失败")) }
    })
}

private class AdSkipped : Exception("skipped")

private suspend fun unityShow(a: Activity) = suspendCancellableCoroutine { c ->
    UnityAds.show(a, PLACEMENT, UnityAdsShowOptions(), object : IUnityAdsShowListener {
        override fun onUnityAdsShowFailure(p: String?, e: UnityAds.UnityAdsShowError?, m: String?) { if (c.isActive) c.resumeWithException(Exception(m ?: e?.name ?: "显示失败")) }
        override fun onUnityAdsShowStart(p: String?) = Unit
        override fun onUnityAdsShowClick(p: String?) = Unit
        // A skipped ad funds nothing, so it earns nothing
        override fun onUnityAdsShowComplete(p: String?, s: UnityAds.UnityAdsShowCompletionState?) {
            if (!c.isActive) return
            if (s == UnityAds.UnityAdsShowCompletionState.COMPLETED) c.resume(Unit) else c.resumeWithException(AdSkipped())
        }
    })
}

@Composable
fun AdDonationSheet(vm: MainViewModel, onDismiss: () -> Unit) {
    val s = LocalS.current; val ctx = LocalContext.current; val scope = rememberCoroutineScope()
    var view by remember { mutableStateOf("checking") }; var reason by remember { mutableStateOf("") }
    var msg by remember { mutableIntStateOf(0) }
    val streak = s.adWatchCount; val result = maxOf(streak, 1)
    val card = storyCard(result); val prev = if (streak > 0) storyCard(streak) else null
    val boss = QUEST_BOSSES.any { it.lvl == result }
    LaunchedEffect(Unit) {
        reason = when {
            ctx.getSystemService(ConnectivityManager::class.java)?.activeNetwork == null -> "No Internet Connection"
            else -> {
                val sec = withContext(Dispatchers.IO) { runCatching { vm.tracker.networkSecurity() }.getOrNull() }
                when { sec?.isVpnActive == true -> "VPN Detected"; sec?.hasProxy == true -> "Proxy/Filter Detected"; !Net.alive(AD_PROBE, 3000) -> "Ad Blocker Detected"; else -> "" }
            }
        }
        val limit = vm.s.adBlockReason(System.currentTimeMillis())
        when {
            reason.isNotEmpty() -> view = "violation"
            limit != null -> { reason = limit; view = "noad" }
            else -> { launch { runCatching { unityLoad(ctx) } }; view = "intro" }
        }
    }
    LaunchedEffect(view) { if (view == "loading") { msg = 0; while (true) { delay(1200); msg = (msg + 1) % LOADING_MESSAGES.size } } }
    // Reaching Chapter 2 in the sheet counts as being told about it, so no "new mission" notice follows.
    // Only a fully watched ad pays out, and only within the daily limits: no ad, a skipped ad or a failed load earns nothing.
    fun complete() {
        val before = vm.s.adWatchCount
        vm.set { it.incrementAdWatch(System.currentTimeMillis()).let { n -> if (questChapter(n.adWatchCount) > n.questChapterSeen) n.copy(questChapterSeen = questChapter(n.adWatchCount)) else n } }
        if (vm.s.adWatchCount > before) { vm.haptic(H.SUCCESS); view = "success" } else { reason = vm.s.adBlockReason(System.currentTimeMillis()) ?: "稍后再试。"; view = "noad" }
    }
    fun noAd(e: Exception) {
        reason = if (e is AdSkipped) "广告被跳过。观看到底，为代码加油。" else "目前没有广告。几分钟后重试。"
        view = "noad"; vm.haptic(H.ERROR)
    }
    fun play() { scope.launch {
        val a = ctx.activity() ?: return@launch noAd(Exception())
        try { unityShow(a); complete() } catch (e: AdSkipped) { noAd(e) } catch (_: Exception) {
            view = "loading"
            try { unityLoad(ctx); delay(6000); unityShow(a); complete() } catch (e: Exception) { noAd(e) }
        }
    } }
    OrionSheet(onDismiss) {
        val close = LocalSheetClose.current
        val fx = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
        AnimatedContent(view to streak, transitionSpec = { fadeIn(fx) togetherWith fadeOut(fx) }, label = "quest") { (v, _) ->
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(top = 8.dp, bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (v) {
                    "checking" -> { LoadingIndicator(Modifier.size(96.dp)); Text("验证完整性...", style = ty.titleMediumEmphasized) }
                    "violation" -> {
                        Lead("Signal 卡住了", "为了保护经济免受农业影响，此任务不允许使用 VPN 和广告拦截器。请禁用它们并重试。", reason, cs.error, center = true) {
                            Blob(Sym.GppBad, cs.error, 88, MaterialShapes.Cookie7Sided)
                        }
                        CtaButton("了解并回报", close, tonal = true)
                    }
                    "intro" -> if (streak == 0) {
                        Lead("开发者的追求", "你是这个项目存在的原因。通过观看广告，您可以为代码提供燃料，保持服务器运行并支持开源。你太不可思议了。", "第一章", center = true) {
                            Blob(Sym.VolunteerActivism, cs.primary, 88, MaterialShapes.Sunny)
                        }
                        CtaButton("我是英雄", { view = "urgent"; vm.haptic(H.MEDIUM) }, icon = Sym.Bolt)
                        TextButton(close, Modifier.fillMaxWidth()) { Text("取消") }
                    } else if (streak == CHAPTER_ONE_END) {
                        var k by remember { mutableIntStateOf(0) }
                        LaunchedEffect(Unit) { delay(150); k = 1 }
                        MorphBlob(listOf(MaterialShapes.Circle, MaterialShapes.Cookie12Sided), k, 128.dp, tonal(Indigo).first) {
                            Icon(Sym.AccountTree, null, Modifier.size(56.dp), tint = tonal(Indigo).second)
                        }
                        Lead("分叉", "熵消失了，但它的最后一口气分裂了 Orion 成一千个流氓分叉。三位新老板守护着三枚新徽章。这条路还很长，飞行员。只有顽固的人才能登上王位。",
                            "第2章 新使命", Fuchsia, center = true)
                        NextBoss(streak)
                        CtaButton("开始第二章", ::play, icon = Sym.Swords)
                        TextButton(close, Modifier.fillMaxWidth()) { Text("还没有") }
                    } else {
                        Lead("营地休息", "你已经通过火恢复了健康。前方的旅程充满危险，但回报却是传奇般的。准备好继续了吗？",
                            "第${questChapter(streak)}·关$streak", Orange, center = true) {
                            Blob(Sym.LocalFireDepartment, Orange, 88, MaterialShapes.Cookie12Sided)
                        }
                        NextBoss(streak)
                        prev?.let { p ->
                            Surface(shape = RoundedCornerShape(20.dp), color = cs.surfaceContainerHigh) {
                                ListItem({ Text(p.title, fontWeight = FontWeight.SemiBold) }, overlineContent = { Text("之前") }, leadingContent = { Blob(p.icon, p.color, 40) },
                                    colors = ListItemDefaults.colors(containerColor = Color.Transparent))
                            }
                        }
                        CtaButton("继续旅程", ::play, icon = Sym.PlayArrow)
                        TextButton(close, Modifier.fillMaxWidth()) { Text("入住营地（关闭）") }
                    }
                    "urgent" -> {
                        RemoteIcon(Defaults.DEV_PROFILE.image.orEmpty(), "R", 96.dp, shape = MaterialShapes.Cookie9Sided.toShape())
                        Text("猎户座有危险！", style = ty.headlineSmallEmphasized, color = cs.error, textAlign = TextAlign.Center)
                        Text(buildAnnotatedString {
                            append("“听我说！熵军团已经突破了防火墙。他们正在破坏源代码！我一个人无法阻止他们。我需要你引导能量")
                            withStyle(SpanStyle(color = cs.error, fontWeight = FontWeight.Black)) { append("现在！") }; append("”")
                        }, style = ty.bodyLarge, fontStyle = FontStyle.Italic, textAlign = TextAlign.Center)
                        Text("RookieZ，首席开发人员", style = ty.labelLarge, color = cs.onSurfaceVariant)
                        CtaButton("接受任务", ::play, icon = Sym.Bolt)
                        TextButton(close, Modifier.fillMaxWidth()) { Text("我需要时间...") }
                    }
                    "loading" -> {
                        LoadingIndicator(Modifier.size(112.dp))
                        Text("与敌人交战", style = ty.headlineSmallEmphasized)
                        AnimatedContent(msg, label = "lm") { Text(LOADING_MESSAGES[it], style = ty.bodyLarge, color = cs.onSurfaceVariant) }
                        Text("（正在连接到 Unity 网络...）", style = ty.bodySmall, color = cs.onSurfaceVariant)
                    }
                    "noad" -> {
                        Lead("任务中止", reason, "这次没有XP", Caution, center = true) { Blob(Sym.Movie, Caution, 88, MaterialShapes.Cookie6Sided) }
                        CtaButton("回到营地", close, tonal = true)
                    }
                    else -> {
                        var k by remember { mutableIntStateOf(0) }
                        LaunchedEffect(Unit) { delay(120); k = 1 }
                        Box(contentAlignment = Alignment.BottomCenter) {
                            MorphBlob(listOf(MaterialShapes.Circle, if (boss) MaterialShapes.SoftBurst else MaterialShapes.Cookie9Sided), k, 136.dp, card.color.copy(.18f)) {
                                Icon(card.icon, null, Modifier.size(56.dp), tint = card.color)
                            }
                            if (boss) Surface(Modifier.offset(y = 10.dp), shape = Pill, color = cs.tertiary, contentColor = cs.onTertiary) {
                                Text("胜利", Modifier.padding(horizontal = 14.dp, vertical = 4.dp), style = ty.labelLargeEmphasized)
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(card.title, style = ty.headlineMediumEmphasized, textAlign = TextAlign.Center)
                        Text(card.msg, style = ty.bodyLarge, color = cs.onSurfaceVariant, textAlign = TextAlign.Center)
                        NextBoss(streak)
                        CtaButton("继续任务", ::play, icon = Sym.PlayArrow)
                        TextButton(close, Modifier.fillMaxWidth()) { Text("营地休息（关闭）") }
                    }
                }
            }
        }
    }
}

// ---------------- Profile & badges ----------------

private val AVATAR_CHOICES = CHARACTERS
private val DINO_BADGES = listOf(200L to "恐龙新秀", 600L to "恐龙大师", 1500L to "恐龙传奇")
/** Archive sections in display order; quest badges follow the boss order so the grind reads left to right. */
private val BADGE_GROUPS = listOf(
    "为代码加油" to QUEST_BOSSES.map { it.badge },
    "恐龙快跑" to DINO_BADGES.map { it.second },
    "飞行员等级" to TIER_BADGES.map { it.second },
    "Secrets" to listOf("Legend", "Dev Mode"),
)
private val PROFILE_BADGES = BADGE_GROUPS.flatMap { it.second }

private fun badgeUnlocked(id: String, s: Settings) = QUEST_BOSSES.firstOrNull { it.badge == id }?.let { s.adWatchCount >= it.lvl } ?: when (id) {
    "Legend" -> s.isLegend; "Dev Mode" -> s.isDevUnlocked; else -> id in s.unlockedBadges
}

/** Counted badges report (now, goal); secrets have none. */
private fun badgeProgress(id: String, s: Settings): Pair<Long, Long>? =
    QUEST_BOSSES.firstOrNull { it.badge == id }?.let { s.adWatchCount.toLong() to it.lvl.toLong() }
        ?: DINO_BADGES.firstOrNull { it.second == id }?.let { s.dinoHighScore to it.first }

private fun badgeHint(id: String) = QUEST_BOSSES.firstOrNull { it.badge == id }?.let { "Defeat the ${it.name} in Fuel the Code. Takes ${it.lvl} ads." }
    ?: DINO_BADGES.firstOrNull { it.second == id }?.let { "Score ${it.first} in a single Dino Run." }
    ?: TIER_BADGES.firstOrNull { it.second == id }?.let { "Reach level ${it.first}. Every ad, app and submission earns XP." }
    ?: when (id) { "Legend" -> "Finish Chapter 1 of Fuel the Code."; "Dev Mode" -> "Some version numbers love being tapped. A lot."; else -> "" }


@Composable
fun ProfileStatsSheet(vm: MainViewModel, onDismiss: () -> Unit) = OrionSheet(onDismiss) {
    val s = LocalS.current; val d = LocalD.current; val close = LocalSheetClose.current
    val select = remember { vm.profileView == "badges" }; val index = vm.badgeIndex
    var badges by rememberSaveable { mutableStateOf(select) }
    val sp = MaterialTheme.motionScheme.defaultSpatialSpec<androidx.compose.ui.unit.IntOffset>(); val fx = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    AnimatedContent(badges, transitionSpec = {
        val dir = if (targetState) 1 else -1
        (slideInHorizontally(sp) { it * dir } + fadeIn(fx)) togetherWith (slideOutHorizontally(sp) { -it * dir } + fadeOut(fx))
    }, label = "profile") { b ->
        if (b) BadgeArchive(vm, s, select, index, onBack = { if (select) close() else badges = false }, onPicked = close)
        else ProfileView(vm, s, d) { badges = true }
    }
}


/** Holographic pilot ID: tilts with the phone and under the finger, a rainbow foil and a specular glint follow the light. */
@Composable
private fun PilotCard(s: Settings, name: String, avatarUrl: String, onAvatar: () -> Unit) {
    val cs = MaterialTheme.colorScheme; val tilt = rememberTilt(); val scope = rememberCoroutineScope()
    val drag = remember { Animatable(Offset.Zero, Offset.VectorConverter) }; val back = MaterialTheme.motionScheme.slowSpatialSpec<Offset>()
    val still = s.disableAnimations
    val foil by rememberInfiniteTransition(label = "foil").animateFloat(0f, if (still) 0f else 1f, infiniteRepeatable(tween(7000, easing = LinearEasing)), label = "f")
    val xp = Xp.total(s, LocalD.current.appLibrary.size); val level = Xp.level(xp)
    val target = ((xp - Xp.start(level)).toFloat() / Xp.step(level)).coerceIn(0f, 1f)
    // Metal skins from the tier levels on; below Bronze the card wears the theme
    val skin = when {
        level >= 50 -> listOf(Color(0xFFFFE89A), Color(0xFFF2C14E), Color(0xFFC8962E)) to Color(0xFF2E2205)
        level >= 30 -> listOf(Color(0xFFF4F6F8), Color(0xFFC3C9D0), Color(0xFF8F979F)) to Color(0xFF1C2127)
        level >= 10 -> listOf(Color(0xFFF0B27A), Color(0xFFC07A3E), Color(0xFF8A5226)) to Color(0xFF2A1406)
        else -> listOf(cs.primaryContainer, androidx.compose.ui.graphics.lerp(cs.primaryContainer, cs.secondaryContainer, .55f), cs.primaryContainer) to cs.onPrimaryContainer
    }
    val on = skin.second
    var shown by remember { mutableStateOf(false) }; LaunchedEffect(Unit) { shown = true }
    val ring by animateFloatAsState(if (shown) target else 0f, MaterialTheme.motionScheme.slowSpatialSpec(), label = "ring")
    val shape = RoundedCornerShape(32.dp)
    val since = s.userProfile?.createdAt?.takeIf { it > 0 }?.let { java.text.SimpleDateFormat("MMM yyyy", java.util.Locale.getDefault()).format(java.util.Date(it)) } ?: "Today"
    val vm = LocalVm.current
    var flipped by remember { mutableStateOf(false) }
    val flip by animateFloatAsState(if (flipped) 180f else 0f, MaterialTheme.motionScheme.slowSpatialSpec(), label = "flip")
    // Signing needs a still card: no sensor tilt or drag tilt while the back is up
    val tiltNow = { if (flipped) Offset.Zero else tilt.value }
    val serial = remember(name, s.userProfile?.createdAt) { (name + s.userProfile?.createdAt).hashCode().toUInt().toString().padStart(10, '0').chunked(4).joinToString(" · ") }
    Box(Modifier.fillMaxWidth().aspectRatio(1.586f)
        .pointerInput(Unit) { detectTapGestures { vm.haptic(H.LIGHT); flipped = !flipped } }
        .pointerInput(flipped) {
            if (!flipped) detectDragGestures(onDragEnd = { scope.launch { drag.animateTo(Offset.Zero, back) } }, onDragCancel = { scope.launch { drag.animateTo(Offset.Zero, back) } }) { c, dv ->
                c.consume(); scope.launch { drag.snapTo(drag.value + dv) }
            }
        }
        .graphicsLayer {
            val t = tiltNow(); val dr = drag.value
            rotationY = flip + (t.x * 12f + dr.x / 10f).coerceIn(-22f, 22f); rotationX = (-t.y * 10f - dr.y / 10f).coerceIn(-22f, 22f)
            cameraDistance = 14 * density; shadowElevation = 18.dp.toPx(); this.shape = shape; clip = true
        }
        .background(Brush.linearGradient(skin.first))
        .drawWithContent {
            drawContent()
            val t = tiltNow(); val dr = drag.value
            val shift = (t.x + dr.x / 300f + foil * 2f) * size.width
            drawRect(Brush.linearGradient(listOf(Color(0x00FF6EC7), Color(0x66FF6EC7), Color(0x6600E5FF), Color(0x66B8FF5C), Color(0x66FFD25C), Color(0x00FFD25C)),
                start = Offset(shift - size.width, 0f), end = Offset(shift, size.height), tileMode = androidx.compose.ui.graphics.TileMode.Mirror),
                blendMode = androidx.compose.ui.graphics.BlendMode.Overlay)
            drawCircle(Brush.radialGradient(listOf(Color.White.copy(.35f), Color.Transparent), center = Offset(size.width * (.5f - t.x * .6f), size.height * (.3f + t.y * .6f)), radius = size.width * .55f),
                radius = size.width, center = Offset(size.width * (.5f - t.x * .6f), size.height * (.3f + t.y * .6f)))
        }) {
        Box(Modifier.align(Alignment.TopEnd).offset(60.dp, (-50).dp).size(200.dp).clip(MaterialShapes.Cookie12Sided.toShape()).background(on.copy(.06f)))
        CompositionLocalProvider(LocalContentColor provides on) {
            if (flip > 90f) SignatureSide(s, Modifier.fillMaxSize().graphicsLayer { rotationY = 180f }.padding(16.dp)) { vm.set(it) }
            else Column(Modifier.fillMaxSize().padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(com.orion.store.R.drawable.ic_splash_logo), null, Modifier.size(28.dp))
                    Text(if (level >= 10) "猎户座飞行员 · ${Xp.title(level).substringBefore(' ').uppercase()}" else "猎户座飞行员", Modifier.padding(start = 6.dp), style = ty.labelLarge, letterSpacing = 2.sp, fontWeight = FontWeight.Black)
                    Spacer(Modifier.weight(1f))
                    Surface(shape = Pill, color = on, contentColor = skin.first[0]) { Text("等级 $level", Modifier.padding(horizontal = 12.dp, vertical = 4.dp), style = ty.labelLargeEmphasized) }
                }
                Spacer(Modifier.weight(1f))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(78.dp).clip(CircleShape).clickable(onClick = onAvatar), contentAlignment = Alignment.Center) {
                        CircularWavyProgressIndicator({ ring }, Modifier.fillMaxSize(), color = on, trackColor = on.copy(.18f))
                        AsyncImage(pngAvatar(avatarUrl), "更改头像", Modifier.size(60.dp).clip(MaterialShapes.Cookie9Sided.toShape()).background(cs.surface.copy(.6f)), contentScale = ContentScale.Crop)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(name.ifBlank { "未透露姓名的飞行员" }, style = ty.headlineSmallEmphasized, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${Xp.title(level)}·$xpXP", style = ty.labelLarge, color = LocalContentColor.current.copy(.8f))
                    }
                }
                Spacer(Modifier.weight(1f))
                Row {
                    Text(serial, Modifier.weight(1f), style = ty.labelMedium, fontFamily = FontFamily.Monospace, color = LocalContentColor.current.copy(.75f))
                    Text("从$since开始", style = ty.labelMedium, color = LocalContentColor.current.copy(.75f))
                }
            }
        }
    }
}

private val INKS = listOf(Color(0xFF1B1B1F), Color(0xFF1D4ED8), Color(0xFFDC2626), Color(0xFF15803D), Color(0xFF7C3AED), Color(0xFFB45309))

/** Back of the pilot card: a signature strip on paper-white so every ink reads on every skin. */
@Composable
private fun SignatureSide(s: Settings, modifier: Modifier, update: ((Settings) -> Settings) -> Unit) = Column(modifier) {
    val ink = INKS[s.signatureInk.coerceIn(0, INKS.lastIndex)]
    // Every stroke, clear and ink change is one step: undo walks back through them, redo forward again until a new step is made
    val past = remember { mutableStateListOf<Pair<String, Int>>() }; val future = remember { mutableStateListOf<Pair<String, Int>>() }
    fun apply(to: Pair<String, Int>) = update { it.copy(pilotSignature = to.first, signatureInk = to.second) }
    fun step(to: Pair<String, Int>) { past += s.pilotSignature to s.signatureInk; future.clear(); apply(to) }
    fun undo() { future += s.pilotSignature to s.signatureInk; apply(past.removeAt(past.lastIndex)) }
    fun redo() { past += s.pilotSignature to s.signatureInk; apply(future.removeAt(future.lastIndex)) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("飞行员签名", Modifier.weight(1f), style = ty.labelLarge, letterSpacing = 2.sp, fontWeight = FontWeight.Black)
        IconButton(::undo, Modifier.size(32.dp), enabled = past.isNotEmpty()) { Icon(Sym.Undo, "Undo", Modifier.size(20.dp)) }
        IconButton(::redo, Modifier.size(32.dp), enabled = future.isNotEmpty()) { Icon(Sym.Redo, "Redo", Modifier.size(20.dp)) }
        if (s.pilotSignature.isNotEmpty()) Text("清除", Modifier.clip(Pill).clickable { step("" to s.signatureInk) }.padding(horizontal = 10.dp, vertical = 4.dp),
            style = ty.labelLargeEmphasized)
    }
    SignaturePad(s.pilotSignature, ink, Modifier.padding(vertical = 10.dp).weight(1f).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0xFFFAFAF7))) { sig ->
        step(sig to s.signatureInk)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        INKS.forEachIndexed { i, c ->
            val on = i == s.signatureInk
            Box(Modifier.size(28.dp).then(if (on) Modifier.border(2.dp, LocalContentColor.current, CircleShape) else Modifier).padding(if (on) 4.dp else 0.dp)
                .clip(CircleShape).background(c).clickable { if (!on) step(s.pilotSignature to i) }.semantics { contentDescription = "墨水${i + 1}"; selected = on })
        }
        Spacer(Modifier.weight(1f))
        Text("点击即可翻转", style = ty.labelSmall, color = LocalContentColor.current.copy(.7f))
    }
}

/** Finger-drawn strokes kept as normalized points; saved once per lifted stroke. */
@Composable
private fun SignaturePad(saved: String, ink: Color, modifier: Modifier, onSave: (String) -> Unit) {
    val strokes = remember(saved) { saved.split('|').filter { it.isNotBlank() }.map { st -> st.split(' ').mapNotNull { p -> p.split(',').takeIf { it.size == 2 }?.let { Offset(it[0].toFloat(), it[1].toFloat()) } } } }
    var live by remember { mutableStateOf(emptyList<Offset>()) }
    fun enc(all: List<List<Offset>>) = all.takeLast(40).joinToString("|") { st -> st.joinToString(" ") { "%.3f,%.3f".format(java.util.Locale.US, it.x, it.y) } }
    Canvas(modifier
        .pointerInput(saved) {
            fun n(p: Offset) = Offset((p.x / size.width).coerceIn(0f, 1f), (p.y / size.height).coerceIn(0f, 1f))
            detectDragGestures(onDragStart = { live = listOf(n(it)) }, onDragEnd = { if (live.size > 1) onSave(enc(strokes + listOf(live))); live = emptyList() },
                onDragCancel = { live = emptyList() }) { c, _ -> c.consume(); val p = n(c.position); if ((p - live.last()).getDistance() > .004f) live = live + p }
        }
        // Swallows taps so a stray dot on the strip does not flip the card
        .pointerInput(Unit) { detectTapGestures { } }) {
        val w = 3.dp.toPx()
        (strokes + listOf(live)).forEach { st ->
            if (st.size < 2) return@forEach
            val path = Path().apply { moveTo(st[0].x * size.width, st[0].y * size.height); st.drop(1).forEach { lineTo(it.x * size.width, it.y * size.height) } }
            drawPath(path, ink, style = Stroke(w, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        if (strokes.isEmpty() && live.isEmpty()) drawLine(ink.copy(.25f), Offset(16.dp.toPx(), size.height * .72f), Offset(size.width - 16.dp.toPx(), size.height * .72f), 1.dp.toPx())
    }
}

/** A stat that counts up when the sheet opens. */
@Composable
private fun CountStat(icon: androidx.compose.ui.graphics.vector.ImageVector, c: Color, value: Int, label: String, modifier: Modifier, suffix: String = "", prefix: String = "") {
    val (bg, fg) = tonal(c)
    var shown by remember { mutableStateOf(false) }; LaunchedEffect(Unit) { shown = true }
    val n by animateIntAsState(if (shown) value else 0, tween(1100, easing = FastOutSlowInEasing), label = "count")
    Row(modifier.clip(RoundedCornerShape(24.dp)).background(bg).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).clip(MaterialShapes.Cookie6Sided.toShape()).background(fg.copy(.14f)), contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(22.dp), tint = fg) }
        Spacer(Modifier.width(12.dp))
        Column {
            Text("$prefix$n$suffix", style = ty.titleLargeEmphasized, color = fg, maxLines = 1)
            Text(label, style = ty.labelMedium, color = fg.copy(.8f), maxLines = 1)
        }
    }
}

@Composable
private fun ProfileView(vm: MainViewModel, s: Settings, d: DataState, openBadges: () -> Unit) {
    val profile = s.userProfile
    var picker by remember { mutableStateOf(false) }; var temp by remember { mutableStateOf(AVATAR_CHOICES[0]) }; var name by rememberSaveable { mutableStateOf("") }
    val showcased = PROFILE_BADGES.filter { it in s.showcasedBadges && badgeUnlocked(it, s) }.take(3)
    val unlocked = PROFILE_BADGES.count { badgeUnlocked(it, s) }
    fun pick(a: Avatar) {
        if (profile != null) vm.set { it.withProfile(profile.copy(avatarId = a.id, avatarUrl = a.url), System.currentTimeMillis()) } else temp = a
        picker = false; vm.haptic(H.SELECTION)
    }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(top = 20.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        PilotCard(s, profile?.name ?: name, profile?.avatarUrl ?: temp.url) { picker = !picker; vm.haptic(H.LIGHT) }
        Text("倾斜或拖动卡片·点击卡片进行翻转并签名·点击头像进行更改", Modifier.fillMaxWidth(), style = ty.labelMedium, color = cs.onSurfaceVariant, textAlign = TextAlign.Center)
        AnimatedVisibility(picker, enter = expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) + fadeIn(), exit = shrinkVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) + fadeOut()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
                items(AVATAR_CHOICES, key = { it.id }) { a ->
                    MorphPick((profile?.avatarId ?: temp.id) == a.id, { pick(a) }, size = 64.dp) {
                        AsyncImage(pngAvatar(a.url), a.name, Modifier.fillMaxSize().padding(4.dp), contentScale = ContentScale.Fit)
                    }
                }
            }
        }
        // Top, not center: the field's supporting text would otherwise push the button below the field's line
        if (profile == null) Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(name, { name = it.take(15) }, Modifier.weight(1f), placeholder = { Text("设置你的外星人别名...") }, singleLine = true, shape = RoundedCornerShape(28.dp),
                supportingText = { Text("${name.trim().length}/15 · 至少 3") })
            Button({ vm.set { it.withProfile(UserProfile(name.trim(), temp.id, temp.url, System.currentTimeMillis()), System.currentTimeMillis()) }; picker = false; vm.haptic(H.SUCCESS) },
                modifier = Modifier.height(56.dp), enabled = name.trim().length >= 3, shapes = ButtonDefaults.shapes()) { Text("安全身份") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CountStat(Sym.Favorite, Pink, d.favorites.size, "Favorites", Modifier.weight(1f))
            CountStat(Sym.Download, Safe, d.appLibrary.size, "已下载", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CountStat(Sym.Bolt, Caution, Xp.total(s, d.appLibrary.size).toInt(), "获得 XP", Modifier.weight(1f))
            CountStat(Sym.EmojiEvents, Purple, Xp.level(Xp.total(s, d.appLibrary.size)), "Rank", Modifier.weight(1f), prefix = "Lvl ")
        }
        Column(Modifier.fillMaxWidth().springClick(RoundedCornerShape(28.dp)) { openBadges(); vm.haptic(H.LIGHT) }.background(cs.surfaceContainerHigh).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("徽章收藏", style = ty.titleLargeEmphasized)
                    Text("已解锁 $unlocked（共 ${PROFILE_BADGES.size}）", style = ty.bodySmall, color = cs.onSurfaceVariant)
                }
                FilledTonalIconButton({ openBadges() }, shapes = IconButtonDefaults.shapes()) { Icon(Sym.ArrowForward, "打开徽章档案") }
            }
            var shown by remember { mutableStateOf(false) }; LaunchedEffect(Unit) { shown = true }
            val p by animateFloatAsState(if (shown) unlocked / PROFILE_BADGES.size.toFloat() else 0f, MaterialTheme.motionScheme.slowSpatialSpec(), label = "badges")
            LinearWavyProgressIndicator({ p }, Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                repeat(3) { i ->
                    val id = showcased.getOrNull(i)
                    val (ic, c) = id?.let { BADGES[it] } ?: (Sym.Lock to cs.onSurfaceVariant)
                    val (bg, fg) = tonal(c)
                    Box(Modifier.size(56.dp).clip(MaterialShapes.Cookie12Sided.toShape()).background(if (id != null) bg else cs.surfaceContainerHighest), contentAlignment = Alignment.Center) {
                        Icon(ic, id, Modifier.size(26.dp), tint = if (id != null) fg else c.copy(.45f))
                    }
                }
            }
        }
    }
}

@Composable
private fun BadgeArchive(vm: MainViewModel, s: Settings, select: Boolean, index: Int?, onBack: () -> Unit, onPicked: () -> Unit) {
    val swap = select && index != null && index < s.showcasedBadges.size
    // Opens on the next badge worth chasing, so the grind is the first thing in view
    var focus by remember { mutableStateOf(PROFILE_BADGES.firstOrNull { !badgeUnlocked(it, s) && badgeProgress(it, s) != null } ?: PROFILE_BADGES.first()) }
    BackHandler { onBack() }
    val unlocked = PROFILE_BADGES.count { badgeUnlocked(it, s) }
    fun tap(id: String) {
        if (!select) { focus = id; vm.haptic(H.SELECTION); return }
        if (!badgeUnlocked(id, s) || id in s.showcasedBadges) { vm.haptic(H.ERROR); return }
        when { index != null -> vm.set { it.setShowcasedBadgeAt(index, id) }; s.showcasedBadges.size < 3 -> vm.set { it.toggleShowcasedBadge(id) }; else -> return }
        vm.haptic(H.SUCCESS); onPicked()
    }
    var shown by remember { mutableStateOf(false) }; LaunchedEffect(Unit) { shown = true }
    val total by animateFloatAsState(if (shown) unlocked / PROFILE_BADGES.size.toFloat() else 0f, MaterialTheme.motionScheme.slowSpatialSpec(), label = "all")
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 28.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.weight(1f))
            if (select && s.showcasedBadges.isNotEmpty()) TextButton({ vm.set { it.copy(showcasedBadges = emptyList()) }; vm.haptic(H.MEDIUM) }) { Text("重置") }
            Surface(Modifier.padding(end = 12.dp), shape = Pill, color = cs.primaryContainer, contentColor = cs.onPrimaryContainer) {
                Text("$unlocked/${PROFILE_BADGES.size}", Modifier.padding(horizontal = 12.dp, vertical = 4.dp), style = ty.labelLargeEmphasized)
            }
        }
        Column(Modifier.padding(horizontal = 24.dp)) {
            Text(if (select) (if (swap) "交换徽章" else "选择徽章") else "徽章档案", style = ty.displaySmallEmphasized)
            Text(if (select) (if (swap) "点击任何解锁的徽章即可立即替换所选的“关于”徽章。" else "点击任何解锁的徽章，它将立即添加到您的“关于”选项卡中。")
                else "您获得的每一个分数，以及通往您未获得的分数的道路。", Modifier.padding(top = 6.dp, bottom = 16.dp), style = ty.bodyLarge, color = cs.onSurfaceVariant)
            LinearWavyProgressIndicator({ total }, Modifier.fillMaxWidth())
        }
        if (!select) BadgeSpotlight(focus, s, Modifier.padding(horizontal = 16.dp).padding(top = 20.dp))
        var n = 0
        BADGE_GROUPS.forEach { (title, ids) ->
            Row(Modifier.padding(start = 24.dp, end = 24.dp, top = 28.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(title, Modifier.weight(1f), style = ty.titleMediumEmphasized)
                Text("${ids.count { badgeUnlocked(it, s) }}/${ids.size}", style = ty.labelLarge, color = cs.onSurfaceVariant)
            }
            FlowRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalArrangement = Arrangement.spacedBy(16.dp), maxItemsInEachRow = 4) {
                ids.forEach { id ->
                    Medal(id, s, focused = !select && focus == id, ring = select && id in s.showcasedBadges, Modifier.reveal(n++)) { tap(id) }
                }
            }
        }
    }
}

/** The focused badge, large: what it is, how to earn it and how far along the pilot is. */
@Composable
private fun BadgeSpotlight(id: String, s: Settings, modifier: Modifier = Modifier) {
    val fx = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    AnimatedContent(id, modifier, transitionSpec = { fadeIn(fx) togetherWith fadeOut(fx) }, label = "spot") { b ->
        val (icon, c) = BADGES[b] ?: return@AnimatedContent
        val on = badgeUnlocked(b, s); val (bg, fg) = tonal(c)
        val prog = badgeProgress(b, s)?.takeIf { !on }
        var k by remember { mutableIntStateOf(0) }; LaunchedEffect(Unit) { delay(80); k = 1 }
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(32.dp)).background(if (on) bg else cs.surfaceContainerHigh).padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            MorphBlob(listOf(MaterialShapes.Circle, if (on) MaterialShapes.SoftBurst else MaterialShapes.Cookie7Sided), k, 88.dp, if (on) fg.copy(.16f) else cs.surfaceContainerHighest) {
                Icon(icon, null, Modifier.size(40.dp), tint = if (on) fg else cs.onSurfaceVariant.copy(.6f))
            }
            Column(Modifier.weight(1f).padding(start = 18.dp)) {
                Text(if (on) "已解锁" else "未解锁", style = ty.labelLargeEmphasized, color = if (on) fg else cs.onSurfaceVariant)
                Text(b, style = ty.headlineSmallEmphasized, color = if (on) fg else cs.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(badgeHint(b), Modifier.padding(top = 4.dp), style = ty.bodyMedium, color = if (on) fg.copy(.85f) else cs.onSurfaceVariant)
                prog?.let { (now, goal) ->
                    val p by animateFloatAsState((now.toFloat() / goal).coerceIn(0f, 1f), MaterialTheme.motionScheme.slowSpatialSpec(), label = "spotp")
                    LinearWavyProgressIndicator({ p }, Modifier.fillMaxWidth().padding(top = 14.dp))
                    Text("${now.coerceAtMost(goal)} / $goal", Modifier.padding(top = 6.dp), style = ty.labelLarge, color = cs.onSurfaceVariant)
                }
            }
        }
    }
}

/** Earned medals are cookies in their own color; locked ones stay round and grey, ringed by their progress. Focus morphs to a burst. */
@Composable
private fun Medal(id: String, s: Settings, focused: Boolean, ring: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val (icon, c) = BADGES[id] ?: return
    val on = badgeUnlocked(id, s); val (bg, fg) = tonal(c)
    val prog = badgeProgress(id, s)?.takeIf { !on }?.let { (now, goal) -> (now.toFloat() / goal).coerceIn(0f, 1f) }
    val scale by animateFloatAsState(if (focused) 1.08f else 1f, MaterialTheme.motionScheme.fastSpatialSpec(), label = "mscale")
    Column(modifier.width(84.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(80.dp).graphicsLayer { scaleX = scale; scaleY = scale }.then(if (ring) Modifier.border(3.dp, cs.primary, CircleShape) else Modifier),
            contentAlignment = Alignment.Center) {
            prog?.let { CircularWavyProgressIndicator({ it }, Modifier.fillMaxSize(), trackColor = cs.surfaceContainerHighest) }
            MorphBlob(listOf(if (on) MaterialShapes.Cookie12Sided else MaterialShapes.Circle, MaterialShapes.SoftBurst), if (focused) 1 else 0, if (prog != null) 62.dp else 72.dp,
                if (on) bg else cs.surfaceContainerHighest, Modifier.springClick(CircleShape, onClick = onClick)) {
                Icon(icon, id, Modifier.size(30.dp), tint = if (on) fg else cs.onSurfaceVariant.copy(.45f))
            }
            if (!on) Box(Modifier.align(Alignment.BottomEnd).size(24.dp).clip(CircleShape).background(cs.surfaceContainerLow).padding(3.dp).clip(CircleShape)
                .background(cs.surfaceContainerHighest), contentAlignment = Alignment.Center) { Icon(Sym.Lock, null, Modifier.size(12.dp), tint = cs.onSurfaceVariant) }
        }
        Text(id, Modifier.padding(top = 8.dp), style = if (focused) ty.labelMediumEmphasized else ty.labelMedium, color = if (on) cs.onSurface else cs.onSurfaceVariant,
            maxLines = 2, textAlign = TextAlign.Center, minLines = 2)
    }
}

// ---------------- Custom bundle creator ----------------

private val ICON_CHOICES = listOf("fa-layer-group" to "Layers", "fa-cubes" to "Cubes", "fa-rocket" to "Rocket", "fa-bolt" to "Bolt", "fa-star" to "Star", "fa-heart" to "Heart",
    "fa-gamepad" to "Gaming", "fa-music" to "Music", "fa-palette" to "Creative", "fa-shield-halved" to "Privacy", "fa-graduation-cap" to "Learn", "fa-briefcase" to "Work")
private val COLOR_CHOICES = listOf("线性渐变（135deg，#6366f1，#ec4899）", "线性渐变（135deg，#0ea5e9，#6366f1）", "线性渐变（135deg，#10b981，#0ea5e9）",
    "线性渐变（135deg，#f59e0b，#ef4444）", "线性渐变（135deg，#8b5cf6，#ec4899）", "线性渐变（135deg，#111827，#4f46e5）")

fun bundleIssueBody(title: String, description: String, icon: String, color: String, appIds: List<String>, apps: List<AppItem>, submitter: String?): String {
    val lines = appIds.joinToString("\n") { id ->
        apps.firstOrNull { it.id == id }?.let { a -> "- **${a.name}** (`${a.id}`)${a.packageName?.takeIf { it.isNotEmpty() }?.let { " · `$it`" } ?: ""}" } ?: "- $id"
    }
    return listOf("## Custom Bundle Submission", "", "**Title:** $title", "**Description:** $description", "**Icon:** `$icon`", "**Color:** `$color`",
        submitter?.takeIf { it.isNotEmpty() }?.let { "**Submitted by:** $it" } ?: "", "", "### Apps (${appIds.size})", lines, "", "---",
        "_Submitted via Orion Store Custom Bundle Creator. Add the `approve` label to publish._").filter { it.isNotEmpty() }.joinToString("\n")
}

@Composable
fun CustomBundleSheet(vm: MainViewModel, onDismiss: () -> Unit) = OrionSheet(onDismiss) {
    val s = LocalS.current; val close = LocalSheetClose.current; val scope = rememberCoroutineScope()
    val platform = vm.tab.targetPlatform(); val all = remember { vm.allApps() }
    val pool = remember(all) { all.filter { it.platform.trim().equals(platform, true) } }
    var step by rememberSaveable { mutableIntStateOf(1) }
    var title by rememberSaveable { mutableStateOf("") }; var desc by rememberSaveable { mutableStateOf("") }
    var icon by rememberSaveable { mutableStateOf(ICON_CHOICES[0].first) }; var color by rememberSaveable { mutableIntStateOf(0) }
    var picked by rememberSaveable { mutableStateOf(listOf<String>()) }
    var saved by remember { mutableStateOf<String?>(null) }; var err by remember { mutableStateOf("") }
    val can = when (step) { 1 -> title.trim().length >= 3 && desc.trim().length >= 8; 2 -> picked.size in 2..24; else -> true }
    fun save(submitted: Boolean): String {
        val now = System.currentTimeMillis(); val id = "cb_${now.toString(36)}"
        vm.set { it.copy(customBundles = it.customBundles + CustomBundle(id, title.trim(), desc.trim(), icon, picked, now, if (submitted) now else null)) }
        return id
    }
    val stops = remember(color) { Regex("#[0-9a-fA-F]{6}").findAll(COLOR_CHOICES[color]).map { hex(it.value) }.toList().let { if (it.size == 1) it + it else it } }
    val pickedApps = remember(picked, pool) { val m = pool.associateBy { it.id }; picked.mapNotNull(m::get) }
    BundleSteps(step)
    AnimatedContent(step, Modifier.weight(1f, fill = false), label = "cb") { st ->
        when (st) {
            1 -> Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                BundleCard(title, desc, icon, stops, pickedApps)
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(cs.surfaceContainer).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Field(title, { title = it.take(48) }, "标题", "例如隐私要领", supporting = if (title.isNotEmpty() && title.trim().length < 3) "至少 3 个字符" else null)
                    Field(desc, { desc = it.take(180) }, "Description", "这个合集的简短介绍", lines = 3, supporting = "${desc.length}/180")
                }
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(cs.surfaceContainer).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("图标", style = ty.titleMediumEmphasized)
                    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        ICON_CHOICES.forEach { (id, label) ->
                            MorphPick(icon == id, { icon = id; vm.haptic(H.SELECTION) }, to = MaterialShapes.Cookie6Sided) {
                                Icon(faIcon(id), label, tint = if (icon == id) cs.onPrimaryContainer else cs.onSurfaceVariant)
                            }
                        }
                    }
                    Text("颜色", Modifier.padding(top = 4.dp), style = ty.titleMediumEmphasized)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        COLOR_CHOICES.forEachIndexed { i, g ->
                            val sw = Regex("#[0-9a-fA-F]{6}").findAll(g).map { hex(it.value) }.toList().let { if (it.size == 1) it + it else it }
                            val on = color == i
                            val k by animateFloatAsState(if (on) 1f else 0f, MaterialTheme.motionScheme.fastSpatialSpec(), label = "sw")
                            val morph = remember { Morph(MaterialShapes.Circle, MaterialShapes.Cookie9Sided) }
                            Box(Modifier.size(52.dp).springClick(MorphShape(morph, k)) { color = i }.graphicsLayer { rotationZ = k * 20f }.background(Brush.linearGradient(sw)),
                                contentAlignment = Alignment.Center) {
                                if (on) Icon(Sym.Check, "选定的颜色", Modifier.graphicsLayer { rotationZ = -k * 20f }, tint = Color.White)
                            }
                        }
                    }
                }
            }
            2 -> Column(Modifier.fillMaxWidth()) {
                // Picked tray: what the bundle holds so far, tap one to drop it.
                Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (picked.size < 2) "选择至少 2 个应用" else "合集中的 ${picked.size} 应用", Modifier.weight(1f), style = ty.titleSmallEmphasized)
                    Text("${picked.size}/24", style = ty.labelLargeEmphasized, color = if (picked.size in 2..24) cs.primary else cs.onSurfaceVariant)
                }
                androidx.compose.animation.AnimatedVisibility(pickedApps.isNotEmpty()) {
                    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(pickedApps, key = { it.id }) { a ->
                            Box(Modifier.animateItem().springClick(RoundedCornerShape(16.dp)) { picked = picked - a.id }) {
                                AppIcon(a, 52.dp)
                                Box(Modifier.align(Alignment.TopEnd).size(20.dp).clip(CircleShape).background(cs.surface).padding(2.dp).clip(CircleShape).background(cs.errorContainer),
                                    contentAlignment = Alignment.Center) { Icon(Sym.Close, "删除${a.name}", Modifier.size(12.dp), tint = cs.onErrorContainer) }
                            }
                        }
                    }
                }
                AppPickerList(pool, platform, selected = picked.toSet(), status = { v, f -> "显示 $f 中的 $v · 最多 24" }) { a ->
                    picked = if (a.id in picked) picked - a.id else if (picked.size < 24) picked + a.id else picked; vm.haptic(H.SELECTION)
                }
            }
            else -> Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                BundleCard(title, desc, icon, stops, pickedApps)
                Lead("已提交！", "您的合集已保存在本地，并且打开了 GitHub 问题。我们将在公共目录中对其进行审查。", center = true) {
                    Blob(Sym.TaskAlt, Safe, 72, MaterialShapes.Cookie9Sided)
                }
                Text("您保存的合集会显示在主页的合集行中。", style = ty.bodySmall, color = cs.onSurfaceVariant, textAlign = TextAlign.Center)
            }
        }
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 8.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ErrorNote(err)
        saved?.let { Tag(it, Safe, Sym.CheckCircle) }
        when (step) {
            1 -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(close, modifier = Modifier.weight(1f).height(56.dp), shapes = ButtonDefaults.shapes()) { Text("取消") }
                Button({ step = 2 }, modifier = Modifier.weight(1f).height(56.dp), enabled = can, shapes = ButtonDefaults.shapes()) {
                    Text("继续"); Spacer(Modifier.width(6.dp)); Icon(Sym.ArrowForward, null, Modifier.size(18.dp))
                }
            }
            2 -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                BackHandler { step = 1 }
                OutlinedButton({
                    err = ""; runCatching { save(false) }.onSuccess { saved = "保存到您的合集中"; vm.haptic(H.SUCCESS); scope.launch { delay(650); close() } }
                        .onFailure { err = it.message ?: "无法保存合集。" }
                }, modifier = Modifier.weight(1f).height(56.dp), enabled = can, shapes = ButtonDefaults.shapes()) { Text("保持本地化") }
                Button({
                    err = ""
                    runCatching {
                        save(true)
                        val body = bundleIssueBody(title.trim(), desc.trim(), icon, COLOR_CHOICES[color], picked, all, s.userProfile?.name)
                        vm.tracker.open("https://github.com/RookieEnough/Orion-Data/issues/new?title=${enc("[Bundle] ${title.trim()}")}&body=${enc(body)}&labels=bundle-submission")
                    }.onSuccess { step = 3; vm.haptic(H.SUCCESS) }.onFailure { err = it.message ?: "无法提交合集。再试一次。" }
                }, modifier = Modifier.weight(1f).height(56.dp), enabled = can, shapes = ButtonDefaults.shapes()) { Text("提交") }
            }
            else -> CtaButton("完成", close, icon = Sym.Check)
        }
    }
}

/** Three labelled step cookies joined by a track that fills as you go. */
@Composable
private fun BundleSteps(step: Int) = Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
    listOf("详情", "应用", "完成").forEachIndexed { i, label ->
        val n = i + 1; val on = n <= step
        val bg by animateColorAsState(if (on) cs.primary else cs.surfaceContainerHighest, MaterialTheme.motionScheme.defaultEffectsSpec(), label = "step")
        Box(Modifier.size(30.dp).clip(MaterialShapes.Cookie9Sided.toShape()).background(bg), contentAlignment = Alignment.Center) {
            if (n < step) Icon(Sym.Check, "完成", Modifier.size(16.dp), tint = cs.onPrimary)
            else Text("$n", style = ty.labelLargeEmphasized, color = if (on) cs.onPrimary else cs.onSurfaceVariant)
        }
        Text(label, Modifier.padding(start = 6.dp), style = if (n == step) ty.labelLargeEmphasized else ty.labelLarge, color = if (on) cs.onSurface else cs.onSurfaceVariant)
        if (i < 2) {
            val track by animateColorAsState(if (n < step) cs.primary else cs.surfaceContainerHighest, MaterialTheme.motionScheme.defaultEffectsSpec(), label = "track")
            Box(Modifier.weight(1f).padding(horizontal = 8.dp).height(4.dp).clip(Pill).background(track))
        }
    }
}

private val TILT = listOf(-8f, 6f, -3f, 9f)

/** Live preview of the bundle as the store will show it: its colour, icon, words and the first apps fanned out. */
@Composable
private fun BundleCard(title: String, desc: String, icon: String, stops: List<Color>, apps: List<AppItem>) =
    Box(Modifier.fillMaxWidth().height(168.dp).clip(RoundedCornerShape(32.dp)).background(Brush.linearGradient(stops))) {
        Box(Modifier.align(Alignment.TopEnd).offset(48.dp, (-40).dp).size(170.dp).clip(MaterialShapes.Cookie9Sided.toShape()).background(Color.White.copy(.12f)))
        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth(if (apps.isEmpty()) 1f else .58f).padding(20.dp)) {
            Box(Modifier.size(44.dp).clip(MaterialShapes.Cookie6Sided.toShape()).background(Color.White.copy(.22f)), contentAlignment = Alignment.Center) {
                Icon(faIcon(icon), null, tint = Color.White)
            }
            Text(title.trim().ifEmpty { "你的合集" }, Modifier.padding(top = 12.dp), style = ty.titleLargeEmphasized, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(desc.trim().ifEmpty { "是什么将这些应用联系在一起" }, style = ty.bodySmall, color = Color.White.copy(.85f), maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Box(Modifier.align(Alignment.CenterEnd).padding(end = 20.dp)) {
            apps.take(4).forEachIndexed { i, a ->
                AppIcon(a, 52.dp, Modifier.offset(x = (-(i % 2) * 44).dp, y = ((i / 2) * 44 - 22).dp).graphicsLayer { rotationZ = TILT[i]; shadowElevation = 8.dp.toPx(); shape = RoundedCornerShape(14.dp) })
            }
        }
    }

// ---------------- App submission ----------------

private val CATEGORIES = listOf("Utility", "Privacy", "Media", "Development", "Social", "Educational")
private const val KEYWORD_HELP = "1. 点击下方的“查看发布页面”。\n2.转到最新版本的“Assets”部分。\n3.查看APK文件名。\n4.从中复制一个唯一的单词。\n\n示例：\n文件：\"app-release-arm64.apk\"\n关键字：\"app-release\"\n\n如果文件只是\"app.apk\"，只需使用\"apk\"。"
private val pretty = Json { prettyPrint = true; @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class) prettyPrintIndent = "  " }

private data class SubDraft(
    val mode: String, val json: String = "", val shots: List<String> = emptyList(), val oIcon: String = "", val oKeyword: String = "", val oDesc: String = "",
    val manualKeyword: Boolean = false, val name: String = "", val description: String = "", val icon: String = "", val repoUrl: String = "", val keyword: String = "apk",
    val packageName: String = "", val category: String = "Utility", val author: String = "", val downloadUrl: String = "",
    val autoName: String = "", val autoDesc: String = "", val autoPkg: String = "", val autoStatus: String = "idle", val autoMsg: String = "",
)

// Note: draft lives for the process like the web's sessionStorage; persist to disk if users lose long drafts to process death.
private var draft: SubDraft? = null

private data class Detected(val name: String?, val description: String?, val packageName: String?, val author: String?)

private suspend fun detectRepo(src: RepoSource, token: String): Detected {
    val h = if (src.provider == "github" && token.isNotEmpty()) mapOf("Authorization" to "Bearer $token") else emptyMap()
    val api = when (src.provider) {
        "github" -> "https://api.github.com/repos/${src.owner}/${src.repo}"
        "gitlab" -> "https://${src.domain}/api/v4/projects/${enc(src.repoPath)}"
        else -> "https://codeberg.org/api/v1/repos/${src.owner}/${src.repo}"
    }
    val info = json.parseToJsonElement(Net.text(api, h)).jsonObject
    val desc = info["description"]?.jsonPrimitive?.contentOrNull.orEmpty(); val branch = info["default_branch"]?.jsonPrimitive?.contentOrNull ?: "main"
    val raw = when (src.provider) {
        "github" -> "https://raw.githubusercontent.com/${src.owner}/${src.repo}/$branch"
        "gitlab" -> "https://${src.domain}/${src.repoPath}/-/raw/$branch"
        else -> "https://codeberg.org/${src.owner}/${src.repo}/raw/branch/$branch"
    }
    val pkg = ANDROID_META_PATHS.firstNotNullOfOrNull { p -> runCatching { Net.text("$raw/$p", h) }.getOrNull()?.let(::extractPackageName) }
    return Detected(humanizeRepoName(src.repo), desc.ifEmpty { null }, pkg, src.owner.ifEmpty { null })
}

private fun autofillMessage(d: Detected) = when {
    d.name != null && d.packageName != null && d.description != null -> "App name, package name, and description detected automatically."
    d.name != null && d.packageName != null -> "App name and package name detected automatically."
    d.packageName != null -> "Package name detected. Add a custom description if needed."
    d.name != null -> "App name detected. Orion still needs Android metadata."
    d.description != null -> "Description detected. Package name still needs confirmation."
    else -> "已找到存储库，但Orion无法检测到Android元数据。"
}

@Composable
fun SubmissionSheet(vm: MainViewModel, onDismiss: () -> Unit) = OrionSheet(onDismiss) {
    val s = LocalS.current; val close = LocalSheetClose.current; val scope = rememberCoroutineScope(); val clip = LocalClipboardManager.current; val t = rememberSheetToast()
    val platform = vm.tab.targetPlatform(); val android = platform == Platform.ANDROID
    var f by remember { mutableStateOf(draft ?: SubDraft("manual")) }
    SideEffect { draft = f }
    var err by remember { mutableStateOf("") }; var shot by remember { mutableStateOf("") }; var issue by remember { mutableStateOf<String?>(null) }
    var help by remember { mutableStateOf(false) }; var badge by remember { mutableStateOf(false) }
    val obtainium = android && f.mode == "obtainium"
    val id = slugId(f.name)
    val cooldown = 180 - min(s.submissionCount * 15, 150)
    val lvl = Xp.level(Xp.total(s, LocalD.current.appLibrary.size)); val rank = "等级$lvl·${Xp.title(lvl)}"; val rankIcon = Sym.MilitaryTech
    val source = remember(f.repoUrl, f.mode) { if (android && f.mode == "manual" && f.repoUrl.isNotBlank()) parseRepoSource(f.repoUrl) else null }
    var lastKey by remember { mutableStateOf("") }
    LaunchedEffect(source) {
        val src = source ?: run { lastKey = ""; f = f.copy(autoStatus = "idle", autoMsg = ""); return@LaunchedEffect }
        val key = "${src.provider}:${src.domain.orEmpty()}:${src.repoPath}"; if (key == lastKey) return@LaunchedEffect
        f = f.copy(autoStatus = "loading", autoMsg = "正在从存储库中检测描述和包名称...")
        delay(650)
        runCatching { detectRepo(src, s.githubToken) }.onSuccess { d ->
            lastKey = key
            val p = f
            f = p.copy(
                author = if (d.author != null && p.author.isEmpty()) d.author else p.author,
                name = if (d.name != null && (p.name.isEmpty() || p.name == p.autoName)) d.name else p.name,
                description = if (d.description != null && (p.description.isEmpty() || p.description == p.autoDesc)) d.description else p.description,
                packageName = if (d.packageName != null && (p.packageName.isEmpty() || p.packageName == p.autoPkg)) d.packageName else p.packageName,
                autoName = d.name ?: p.autoName, autoDesc = d.description ?: p.autoDesc, autoPkg = d.packageName ?: p.autoPkg,
                autoStatus = if (d.packageName != null || d.description != null) "success" else "error", autoMsg = autofillMessage(d))
        }.onFailure { f = f.copy(autoStatus = "error", autoMsg = "目前无法自动检测存储库元数据。") }
    }
    val releases = f.repoUrl.trim().trimEnd('/').takeIf { it.isNotEmpty() }?.let { u ->
        when { "github.com" in u -> "$u/releases"; "gitlab" in u -> "$u/-/releases"; "codeberg.org" in u -> "$u/releases"; else -> null }
    }
    fun fail(m: String) { err = m; vm.haptic(H.ERROR) }
    fun addShot() {
        val u = shot.trim(); if (u.isEmpty()) return
        if (!u.startsWith("http")) { fail("Screenshot must be a valid URL starting with http/https"); scope.launch { delay(3000); err = "" }; return }
        f = f.copy(shots = f.shots + sanitizeGitHubImageUrl(u)); shot = ""
    }
    fun switchMode(m: String) {
        if (m == f.mode) return
        err = ""; issue = null; draft = null
        f = f.copy(mode = m, shots = emptyList(), oDesc = "", oIcon = "", oKeyword = "", json = "", autoName = "")
    }
    fun submit() {
        err = ""
        if (f.shots.size < 3) return fail("至少需要 3 张屏幕截图。")
        f.shots.forEachIndexed { i, u -> explainRejectedImageLink(u)?.let { return fail("截图#${i + 1}: $it") } }
        val shots = JsonArray(f.shots.map(::JsonPrimitive))
        val app: JsonObject = if (obtainium) {
            if (f.json.isBlank()) return fail("请粘贴JSON内容。")
            if (f.oIcon.isBlank()) return fail("图标 URL 为必填项。")
            explainRejectedImageLink(f.oIcon)?.let { return fail("图标：$it") }
            val parsed = runCatching { json.parseToJsonElement(f.json).jsonObject }.getOrElse { return fail("解析数据失败。请检查输入。") }
            JsonObject(parsed + mapOf("icon" to JsonPrimitive(sanitizeGitHubImageUrl(f.oIcon)), "releaseKeyword" to JsonPrimitive(f.oKeyword.trim().ifEmpty { "apk" }),
                "description" to JsonPrimitive(f.oDesc.trim()), "screenshots" to shots, "platform" to JsonPrimitive(Platform.ANDROID)))
        } else {
            if (f.name.isBlank()) return fail("应用名称为必填项。")
            if (f.description.isBlank()) return fail("需要描述。")
            if (f.icon.isBlank()) return fail("图标 URL 为必填项。")
            explainRejectedImageLink(f.icon)?.let { return fail("图标：$it") }
            if (android) {
                if (f.repoUrl.isBlank()) return fail("需要存储库 URL。")
                if (f.packageName.isBlank()) return fail("Package name is required.")
            } else if (f.downloadUrl.isBlank()) return fail("需要直接下载链接。")
            val url = f.repoUrl; val parts = runCatching { URI(url.trim()).path.split('/').filter { it.isNotEmpty() } }.getOrDefault(emptyList())
            val host = runCatching { URI(url.trim()).host.orEmpty() }.getOrDefault("")
            val gh = if (android && "github.com" in url) url.substringAfter("github.com/").split('/').takeIf { it.size >= 2 }?.let { "${it[0]}/${it[1].replace(".git", "").trimEnd('/')}" } else null
            val gl = android && gh == null && "gitlab" in url && parts.size >= 2; val cb = android && gh == null && !gl && "codeberg.org" in url && parts.size >= 2
            val owner = gh?.substringBefore('/') ?: parts.firstOrNull().takeIf { gl || cb }
            buildJsonObject {
                put("name", f.name); put("id", id); put("description", f.description); put("icon", sanitizeGitHubImageUrl(f.icon)); put("repoUrl", f.repoUrl)
                put("githubRepo", gh.orEmpty()); put("gitlabRepo", if (gl) parts.joinToString("/") else ""); put("gitlabDomain", if (gl) host else "")
                put("codebergRepo", if (cb) parts.joinToString("/") else "")
                put("releaseKeyword", if (android) (if (f.manualKeyword) f.keyword.trim().ifEmpty { "apk" } else "apk") else f.keyword)
                put("packageName", f.packageName); put("category", f.category); put("author", f.author.ifEmpty { owner.orEmpty() }); put("downloadUrl", f.downloadUrl)
                put("screenshots", shots); put("platform", platform)
            }
        }
        issue = submissionIssueUrl(pretty.encodeToString(JsonArray.serializer(), JsonArray(listOf(app))), 1, app["name"]?.jsonPrimitive?.contentOrNull, STORE_VERSION)
        vm.set { it.registerSubmission(System.currentTimeMillis()) }; vm.haptic(H.SUCCESS)
    }
    issue?.let { url ->
        AlertDialog({ issue = null }, icon = { Icon(Sym.Warning, null) }, title = { Text("继续之前") },
            text = { Text("Orion 已为您的提交生成了正确的 JSON。请不要在GitHub问题页面手动编辑JSON区块，否则提交可能会中断。") },
            confirmButton = { TextButton({ vm.tracker.open(url); draft = null; issue = null; close() }) { Text("打开GitHub问题") } },
            dismissButton = { TextButton({ issue = null }) { Text("回去") } })
    }
    SheetToastHost(t)
    val sectionsDone = if (obtainium) listOf(f.json.isNotBlank(), f.oIcon.isNotBlank(), f.shots.size >= 3)
        else listOf(if (android) f.repoUrl.isNotBlank() else f.downloadUrl.isNotBlank(), f.name.isNotBlank() && f.author.isNotBlank(), f.description.isNotBlank() && f.icon.isNotBlank()) +
            (if (android) listOf(f.packageName.isNotBlank()) else emptyList()) + (f.shots.size >= 3)
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SubmitHero(platform, rank, rankIcon, cooldown, sectionsDone.count { it }, sectionsDone.size)
        if (android) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ModeCard("From repo", "GitHub, GitLab or Codeberg", Sym.Code, MaterialShapes.Clover4Leaf, f.mode == "manual", Modifier.weight(1f)) { switchMode("manual") }
            ModeCard("Obtainium", "Paste an export", Sym.FileDownload, MaterialShapes.Cookie9Sided, f.mode == "obtainium", Modifier.weight(1f)) { switchMode("obtainium") }
        }
        ErrorNote(err)
        val screenshots: @Composable (Int) -> Unit = { n ->
            Section(n, "Screenshots", "至少 3 个来自自述文件的直接图像链接，Play Store 或 F-Droid。", f.shots.size >= 3) {
                Field(shot, { shot = it }, "Screenshot URL", "https://image.url/screenshot.jpg", uri = true, onDone = ::addShot,
                    trailing = { IconButton(::addShot, enabled = shot.isNotBlank()) { Icon(Sym.AddPhotoAlternate, "添加屏幕截图") } })
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val p by animateFloatAsState((f.shots.size / 3f).coerceAtMost(1f), MaterialTheme.motionScheme.defaultSpatialSpec(), label = "shots")
                    LinearWavyProgressIndicator({ p }, Modifier.weight(1f), color = if (f.shots.size >= 3) Safe else cs.primary)
                    Text("${f.shots.size}/3", Modifier.padding(start = 12.dp), style = ty.labelLargeEmphasized, color = if (f.shots.size >= 3) Safe else cs.onSurfaceVariant)
                }
                var viewing by remember { mutableStateOf<Int?>(null) }
                viewing?.let { Lightbox(f.shots, it) { viewing = null } }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    f.shots.forEachIndexed { i, u ->
                        Box(Modifier.size(84.dp, 150.dp).clip(RoundedCornerShape(16.dp)).background(cs.surfaceContainerHighest).clickable(onClickLabel = "查看截图${i + 1}") { viewing = i }) {
                            AsyncImage(u, "截图${i + 1}", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            Text("#${i + 1}", Modifier.align(Alignment.BottomStart).padding(6.dp).clip(Pill).background(cs.scrim.copy(.6f)).padding(horizontal = 8.dp, vertical = 2.dp), style = ty.labelSmall, color = Color.White)
                            FilledTonalIconButton({ f = f.copy(shots = f.shots.filterIndexed { j, _ -> j != i }) }, modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(32.dp)) {
                                Icon(Sym.Close, "删除截图${i + 1}", Modifier.size(16.dp))
                            }
                        }
                    }
                    repeat(maxOf(3 - f.shots.size, 1)) { k ->
                        val line = cs.outlineVariant
                        Box(Modifier.size(84.dp, 150.dp).drawBehind {
                            drawRoundRect(line, cornerRadius = CornerRadius(16.dp.toPx()), style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))))
                        }, contentAlignment = Alignment.Center) {
                            if (k == 0) Icon(Sym.AddPhotoAlternate, null, tint = cs.onSurfaceVariant) else Text("${f.shots.size + k + 1}", style = ty.titleLarge, color = cs.onSurfaceVariant.copy(.5f))
                        }
                    }
                }
            }
        }
        if (obtainium) {
            Section(1, "Obtainium 出口", "粘贴此应用的 JSON Obtainium 导出。", f.json.isNotBlank()) {
                Field(f.json, { f = f.copy(json = it) }, "Obtainium JSON", "{\"apps\": [{\"url\": \"https://gitlab.com/...\"}]}", lines = 6, mono = true)
            }
            Section(2, "看起来如何", "图标是必需的，其余的是可选的。", f.oIcon.isNotBlank()) {
                Field(f.oIcon, { f = f.copy(oIcon = it) }, "Icon URL *", "https://...", uri = true, leading = iconPreview(f.oIcon))
                Field(f.oKeyword, { f = f.copy(oKeyword = it) }, "发布关键词", "例如应用发布")
                Field(f.oDesc, { f = f.copy(oDesc = it) }, "Description", "这个应用有什么作用？", lines = 3)
            }
            screenshots(3)
        } else {
            Section(1, "Source", if (android) "发布的地方。 Orion 从存储库中读取其余部分。" else "人们下载它的地方。", sectionsDone[0]) {
                if (android) {
                    Field(f.repoUrl, { f = f.copy(repoUrl = it) }, "Repo URL *", "https://github.com/owner/repo", uri = true)
                    if (f.repoUrl.isNotBlank()) {
                        val (c, ic) = when (f.autoStatus) { "error" -> cs.error to Sym.Warning; "success" -> Safe to Sym.CheckCircle; else -> cs.onSurfaceVariant to Sym.AutoFixHigh }
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.copy(.1f)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (f.autoStatus == "loading") LoadingIndicator(Modifier.size(20.dp)) else Icon(ic, null, Modifier.size(18.dp), tint = c)
                            Spacer(Modifier.width(10.dp))
                            Text(f.autoMsg.ifEmpty { "Orion 将尝试从此存储库中检测包名称和描述。" }, style = ty.bodySmall, color = c)
                        }
                    }
                } else Field(f.downloadUrl, { f = f.copy(downloadUrl = it) }, "Direct download link *", "https://example.com/download", uri = true)
            }
            Section(2, "详情", null, sectionsDone[1]) {
                Field(f.name, { f = f.copy(name = it) }, "姓名 *", "应用名称")
                var open by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(open, { open = it }) {
                    TextField(f.category, {}, Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable), readOnly = true, label = { Text("类别 *") },
                        shape = RoundedCornerShape(16.dp), colors = fieldColors(), trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(open) })
                    ExposedDropdownMenu(open, { open = false }, shape = RoundedCornerShape(20.dp)) {
                        CATEGORIES.forEach { c -> DropdownMenuItem({ Text(c) }, { f = f.copy(category = c); open = false; vm.haptic(H.SELECTION) }) }
                    }
                }
                Field(f.author, { f = f.copy(author = it) }, "作者 *", "开发商或发行商")
                Field(id, {}, "应用 ID", "auto-generated", readOnly = true, mono = true)
            }
            Section(3, "商品详情", "人们首先看到的。", sectionsDone[2]) {
                Field(f.description, { f = f.copy(description = it) }, "描述 *", "用户将首先阅读简短的摘要。", lines = 3)
                Field(f.icon, { f = f.copy(icon = it) }, "Icon URL *", "https://...", uri = true, leading = iconPreview(f.icon))
            }
            if (android) Section(4, "Release", "Orion 如何从每个版本中选择正确的 APK。", sectionsDone[3]) {
                Field(f.packageName, { f = f.copy(packageName = it) }, "Package name *", "com.example.app", mono = true)
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(cs.surfaceContainerHighest).padding(start = 16.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("发布关键词", style = ty.titleSmall)
                        Text(if (f.manualKeyword) "手册：匹配您的关键字" else "自动：版本中的第一个 APK", style = ty.bodySmall, color = cs.onSurfaceVariant)
                    }
                    IconButton({ help = !help }) { Icon(Sym.Info, "帮助：释放关键字") }
                    Switch(f.manualKeyword, { f = f.copy(manualKeyword = it); vm.haptic(H.SELECTION) })
                }
                AnimatedVisibility(help) {
                    Surface(shape = RoundedCornerShape(16.dp), color = cs.secondaryContainer) { Text(KEYWORD_HELP, Modifier.padding(14.dp), style = ty.bodySmall) }
                }
                AnimatedVisibility(f.manualKeyword) { Field(f.keyword, { f = f.copy(keyword = it) }, "Keyword", "apk") }
                releases?.let { r ->
                    FilledTonalButton({ vm.tracker.open(r) }, shapes = ButtonDefaults.shapes()) { Icon(Sym.OpenInNew, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("检查发布页面") }
                }
            }
            screenshots(if (android) 5 else 4)
        }
        Column(Modifier.padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            CtaButton("生成GitHub问题", ::submit, icon = Sym.OpenInNew)
            Text("在浏览器中打开 GitHub 问题并填写所有内容。", style = ty.bodySmall, color = cs.onSurfaceVariant)
        }
        val md = "[![在 Orion Store](https://raw.githubusercontent.com/RookieEnough/Orion-Store/refs/heads/main/assets/orion-badge.png)](https://rookieenough.github.io/Orion-Data/redirect.html?id=${enc(id)})"
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(cs.surfaceContainer).clickable { badge = !badge; vm.haptic(H.SELECTION) }
            .animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(36.dp).clip(MaterialShapes.Sunny.toShape()).background(cs.tertiaryContainer), contentAlignment = Alignment.Center) {
                    Icon(Sym.Verified, null, Modifier.size(18.dp), tint = cs.onTertiaryContainer)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("您的自述文件的徽章", style = ty.titleMediumEmphasized)
                    Text("在 Orion 中将人们直接链接到此应用", style = ty.bodySmall, color = cs.onSurfaceVariant)
                }
                Icon(if (badge) Sym.ExpandLess else Sym.ExpandMore, null)
            }
            if (badge) {
                Eyebrow("Markdown", cs.onSurfaceVariant)
                Text(md, Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(cs.surfaceContainerHighest).padding(12.dp), style = ty.bodySmall, fontFamily = FontFamily.Monospace)
                FilledTonalButton({ clip.setText(AnnotatedString(md)); t.show("已复制 Markdown"); vm.haptic(H.SUCCESS) }, shapes = ButtonDefaults.shapes()) {
                    Icon(Sym.ContentCopy, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("复制 Markdown")
                }
                Eyebrow("深层链接", cs.onSurfaceVariant)
                Text("orionstore://app/${enc(id)}", style = ty.bodySmall, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

/** Slim submission header: title, contributor rank, cooldown and live step progress. No hero box, the form is the point. */
@Composable
private fun SubmitHero(platform: String, rank: String, rankIcon: ImageVector, cooldown: Int, done: Int, total: Int) = Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
    Text("Submit ${if (platform == Platform.ANDROID) "an app" else if (platform == Platform.TV) "a TV app" else "software"}", style = ty.headlineSmallEmphasized)
    Text("在GitHub进行了评论，然后它就登陆了商店。", style = ty.bodyMedium, color = cs.onSurfaceVariant)
    Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Tag(rank, cs.tertiary, rankIcon); Tag("${cooldown / 60}h ${cooldown % 60}m 冷却时间", cs.onSurfaceVariant, Sym.Timer)
        Spacer(Modifier.weight(1f))
        Text("$done/$total", style = ty.labelLargeEmphasized, color = cs.primary)
    }
    val p by animateFloatAsState(done / total.toFloat().coerceAtLeast(1f), MaterialTheme.motionScheme.defaultSpatialSpec(), label = "steps")
    LinearWavyProgressIndicator({ p }, Modifier.fillMaxWidth().padding(top = 10.dp))
}

/** Submission mode as a pair of selectable tiles: the selected one fills and its shape turns primary. */
@Composable
private fun ModeCard(title: String, desc: String, icon: ImageVector, shape: RoundedPolygon, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val fx = MaterialTheme.motionScheme.defaultEffectsSpec<Color>()
    val bg by animateColorAsState(if (on) cs.secondaryContainer else cs.surfaceContainer, fx, label = "bg")
    Column(modifier.springClick(RoundedCornerShape(24.dp), onClick = onClick).background(bg).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(shape.toShape()).background(if (on) cs.primary else cs.surfaceContainerHighest), contentAlignment = Alignment.Center) {
                Icon(icon, null, Modifier.size(22.dp), tint = if (on) cs.onPrimary else cs.onSurfaceVariant)
            }
            Spacer(Modifier.weight(1f))
            AnimatedVisibility(on, enter = scaleIn(MaterialTheme.motionScheme.fastSpatialSpec()) + fadeIn(), exit = scaleOut() + fadeOut()) { Icon(Sym.CheckCircle, "已选中", tint = cs.primary) }
        }
        Text(title, Modifier.padding(top = 12.dp), style = ty.titleSmallEmphasized)
        Text(desc, style = ty.bodySmall, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Live preview of a pasted icon link, shown inside the field. */
private fun iconPreview(url: String): (@Composable () -> Unit)? = url.trim().takeIf { it.startsWith("http") }?.let { u ->
    { AsyncImage(sanitizeGitHubImageUrl(u), "图标预览", Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop) }
}
