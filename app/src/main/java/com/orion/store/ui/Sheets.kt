package com.orion.store.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.orion.store.data.*
import com.orion.store.platform.ScanTarget
import com.orion.store.platform.ScanTargetType
import com.orion.store.platform.VtResult
import com.orion.store.platform.VtScan
import com.orion.store.platform.VtView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.io.File
import kotlin.math.min
import kotlin.math.roundToInt

val ty: Typography @Composable @ReadOnlyComposable get() = MaterialTheme.typography
val cs: ColorScheme @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme

/** Vertical segmented list corners: 20dp at group ends, 4dp between rows. */
fun colShape(i: Int, n: Int) = RoundedCornerShape(if (i == 0) 20.dp else 4.dp, if (i == 0) 20.dp else 4.dp, if (i == n - 1) 20.dp else 4.dp, if (i == n - 1) 20.dp else 4.dp)

@Composable
fun Eyebrow(text: String, c: Color = cs.primary, modifier: Modifier = Modifier) =
    Text(text.uppercase(), modifier, style = ty.labelMedium, fontWeight = FontWeight.Bold, color = c, letterSpacing = 1.2.sp)

/** Sheet lead block: optional visual, eyebrow, emphasized headline, supporting body. */
@Composable
fun Lead(title: String, body: String? = null, eyebrow: String? = null, eyebrowColor: Color = cs.primary, center: Boolean = false, top: (@Composable () -> Unit)? = null) {
    val align = if (center) TextAlign.Center else TextAlign.Start
    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalAlignment = if (center) Alignment.CenterHorizontally else Alignment.Start) {
        top?.let { it(); Spacer(Modifier.height(16.dp)) }
        eyebrow?.let { Eyebrow(it, eyebrowColor) }
        Text(title, style = ty.headlineSmallEmphasized, textAlign = align)
        body?.let { Text(it, Modifier.padding(top = 6.dp), style = ty.bodyMedium, color = cs.onSurfaceVariant, textAlign = align) }
    }
}

@Composable
fun PickerHeader(title: String, subtitle: String?, onBack: () -> Unit) =
    Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        BackHandler(onBack = onBack)
        Column(Modifier.weight(1f)) {
            Text(title, style = ty.headlineSmallEmphasized)
            subtitle?.let { Text(it, style = ty.bodySmall, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }

/** Pulsing placeholder that keeps the final card geometry. */
@Composable
fun Skeleton(modifier: Modifier, shape: Shape = RoundedCornerShape(20.dp)) {
    val a by rememberInfiniteTransition(label = "sk").animateFloat(.45f, .9f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "a")
    Box(modifier.clip(shape).graphicsLayer { alpha = a }.background(cs.surfaceContainerHighest))
}

/** Searchable, category-filtered, paged app list shared by the bundle editor and creator. */
@Composable
fun AppPickerList(pool: List<AppItem>, platform: String, modifier: Modifier = Modifier, selected: Set<String> = emptySet(), status: ((Int, Int) -> String)? = null, onPick: (AppItem) -> Unit) {
    var q by rememberSaveable { mutableStateOf("") }; var cat by rememberSaveable { mutableStateOf("All") }; var limit by rememberSaveable { mutableIntStateOf(20) }
    val cats = remember(pool) { pool.groupingBy { it.category }.eachCount().toSortedMap() }
    val filtered = remember(pool, q, cat) {
        val t = q.trim()
        pool.filter { (cat == "All" || it.category == cat) && (t.isEmpty() || it.name.contains(t, true) || it.author.contains(t, true) || it.packageName?.contains(t, true) == true) }
    }
    val shown = filtered.take(limit); val left = filtered.size - shown.size
    LazyColumn(modifier.fillMaxWidth(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp)) {
        item {
            OutlinedTextField(q, { q = it; limit = 20 }, Modifier.fillMaxWidth(), placeholder = { Text("搜索$platform应用...") }, singleLine = true, shape = RoundedCornerShape(28.dp),
                leadingIcon = { Icon(Sym.Search, null) },
                trailingIcon = { if (q.isNotEmpty()) IconButton({ q = ""; limit = 20 }) { Icon(Sym.Close, "清除搜索") } })
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (listOf("All" to pool.size) + cats.toList()).forEach { (k, n) ->
                    FilterChip(cat == k, { cat = k; limit = 20 }, { Text("$k ($n)") }, leadingIcon = if (cat == k) ({ Icon(Sym.Check, null, Modifier.size(18.dp)) }) else null)
                }
            }
            Text(status?.invoke(shown.size, filtered.size) ?: "显示${shown.size}/${filtered.size}", Modifier.padding(start = 4.dp, bottom = 8.dp), style = ty.labelMedium, color = cs.onSurfaceVariant)
        }
        if (filtered.isEmpty()) item { EmptyState(Sym.SearchOff, "没有匹配的应用。") }
        itemsIndexed(shown, key = { _, a -> a.id }) { i, a ->
            val on = a.id in selected
            GroupItem(i, shown.size, selected = on, onClick = { onPick(a) }) {
                ListItem({ Text(a.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = if (on) ty.titleMediumEmphasized else ty.titleMedium) },
                    supportingContent = { Text("${a.category}·${a.author}", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingContent = { AppIcon(a, 40.dp) },
                    trailingContent = { Icon(if (on) Sym.CheckCircle else Sym.AddCircleOutline, null, tint = if (on) cs.primary else cs.onSurfaceVariant) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent))
            }
        }
        if (left > 0) item {
            FilledTonalButton({ limit += 20 }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), shapes = ButtonDefaults.shapes()) { Text("加载${min(20, left)} 更多（$left 剩余）") }
        }
    }
}

// ---------------- Notice ----------------

@Composable
fun NoticeSheet(vm: MainViewModel, onDismiss: () -> Unit) {
    val n = vm.config?.notice
    if (n == null) { LaunchedEffect(Unit) { onDismiss() }; return }
    OrionSheet(onDismiss) {
        val close = LocalSheetClose.current
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
            Lead(n.title, n.message, "通知", center = true) { Blob(Sym.Campaign, cs.primary, 80, MaterialShapes.Sunny) }
            Spacer(Modifier.height(16.dp))
            CtaButton("知道了", close, icon = Sym.Check)
        }
    }
}

// ---------------- FAQ ----------------

@Composable
fun FaqSheet(vm: MainViewModel, onDismiss: () -> Unit) = OrionSheet(onDismiss) {
    val faqs = vm.config?.faqs?.takeIf { it.isNotEmpty() } ?: Defaults.FAQS
    var open by rememberSaveable { mutableIntStateOf(-1) }
    SheetTitle("常见问题", Sym.HelpOutline, "常见问题和秘密")
    LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 32.dp)) {
        itemsIndexed(faqs) { i, f ->
            val on = open == i
            GroupItem(i, faqs.size, selected = on, onClick = { open = if (on) -1 else i; vm.haptic(H.SELECTION) }) {
                Column(Modifier.animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec()).padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(40.dp).clip(if (on) MaterialShapes.Cookie6Sided.toShape() else CircleShape).background(if (on) cs.primary else cs.surfaceContainerHighest), contentAlignment = Alignment.Center) {
                            Icon(faIcon(f.icon, Sym.QuestionMark), null, Modifier.size(20.dp), tint = if (on) cs.onPrimary else cs.onSurfaceVariant)
                        }
                        Spacer(Modifier.width(14.dp))
                        Text(f.question, Modifier.weight(1f), style = if (on) ty.titleSmallEmphasized else ty.titleSmall)
                        Icon(if (on) Sym.ExpandLess else Sym.ExpandMore, null, tint = cs.onSurfaceVariant)
                    }
                    if (on) Text(f.answer, Modifier.padding(start = 54.dp, top = 10.dp), style = ty.bodyMedium)
                }
            }
        }
    }
}

// ---------------- What's new (GitHub Releases) ----------------

/** What's new reads GitHub Releases directly: publishing a release is the only step, no JSON to maintain. Cached for offline and rate limits. */
@Composable
fun ReleaseNotesSheet(vm: MainViewModel, onDismiss: () -> Unit) = OrionSheet(onDismiss) {
    val ctx = LocalContext.current
    var releases by remember { mutableStateOf<List<Release>?>(null) }; var failed by remember { mutableStateOf(false) }
    var open by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        val f = File(ctx.filesDir, "releases.json")
        withContext(Dispatchers.IO) { runCatching { parseReleases(f.readText()) }.getOrNull() }?.let { releases = it }
        // GitHub allows 60 unauthenticated calls an hour per network, so a fresh cache is used as-is
        if (releases == null || System.currentTimeMillis() - f.lastModified() > 30 * 60_000L) runCatching {
            val raw = Net.text(Defaults.RELEASES_API, mapOf("Accept" to "application/vnd.github+json"))
            releases = parseReleases(raw); withContext(Dispatchers.IO) { f.writeText(raw) }
        }.onFailure { if (releases == null) failed = true }
    }
    SheetTitle("什么是新的", Sym.NewReleases, "直接来自 Orion 的发行版")
    val list = releases
    LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        when {
            list == null && !failed -> items(2) { Skeleton(Modifier.fillMaxWidth().height(140.dp)) }
            list.isNullOrEmpty() -> item {
                EmptyState(Sym.CloudOff, "无法到达GitHub", "上线后即会加载发行说明。")
                TextButton({ vm.tracker.open("https://github.com/RookieEnough/Orion-Store/releases") }, Modifier.fillMaxWidth()) { Text("在 GitHub 打开 Releases") }
            }
            else -> itemsIndexed(list, key = { _, r -> r.version }) { i, r ->
                ReleaseCard(vm, r, latest = i == 0, expanded = (open ?: list[0].version) == r.version) { open = if ((open ?: list[0].version) == r.version) "" else r.version }
            }
        }
    }
}

/** Collapsed: version, title, when. Open: section titles with one-line headlines, and a link to the full notes. */
@Composable
private fun ReleaseCard(vm: MainViewModel, r: Release, latest: Boolean, expanded: Boolean, toggle: () -> Unit) {
    val installed = isSameVersion(r.version, STORE_VERSION)
    val fg = if (latest) cs.onPrimaryContainer else cs.onSurface
    Surface(toggle, shape = RoundedCornerShape(if (expanded) 28.dp else 20.dp), color = if (latest) cs.primaryContainer else cs.surfaceContainerHigh, contentColor = fg) {
        Column(Modifier.fillMaxWidth().animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec()).padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("v${r.version}", Modifier.weight(1f), style = ty.titleLargeEmphasized)
                if (installed) Tag("已安装", if (latest) cs.primary else Safe) else if (latest) Tag("Latest", cs.primary)
            }
            if (r.name.isNotEmpty()) Text(r.name, style = ty.titleMedium, fontWeight = FontWeight.SemiBold)
            val count = r.sections.sumOf { it.items.size }
            val whenText = if (r.publishedAt > 0) android.text.format.DateUtils.getRelativeTimeSpanString(r.publishedAt).toString() else ""
            Text(listOf(whenText, if (count > 0) "$count变化" else "").filter { it.isNotEmpty() }.joinToString(" · "), style = ty.labelMedium, color = fg.copy(.7f))
            if (expanded) {
                r.sections.forEach { sec ->
                    if (sec.title.isNotEmpty()) Text(sec.title, Modifier.padding(top = 16.dp, bottom = 4.dp), style = ty.titleSmallEmphasized, color = cs.primary)
                    sec.items.forEach { line ->
                        Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.Top) {
                            Box(Modifier.padding(top = 8.dp).size(6.dp).clip(CircleShape).background(fg.copy(.5f)))
                            Spacer(Modifier.width(10.dp))
                            Text(line, style = ty.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                if (r.url.isNotEmpty()) TextButton({ vm.tracker.open(r.url) }, Modifier.padding(top = 8.dp)) { Text("GitHub 的完整注释"); Spacer(Modifier.width(6.dp)); Icon(Sym.OpenInNew, null, Modifier.size(16.dp)) }
            }
        }
    }
}

// ---------------- Tutorial (Modern UI intro + feature tour) ----------------

private data class Step(val icon: ImageVector, val title: String, val body: String)

private val HERO_SHAPES = listOf(MaterialShapes.Cookie9Sided, MaterialShapes.Sunny, MaterialShapes.Clover4Leaf, MaterialShapes.SoftBurst, MaterialShapes.Cookie12Sided)
private val UI_STEPS = listOf(
    Step(Sym.AutoAwesome, "尝试新用户界面", "Orion 拥有全新的现代用户界面，包含精心策划的收藏、英雄轮播和漂亮的新布局。"),
    Step(Sym.Settings, "打开设置", "点击右上角的齿轮图标"),
    Step(Sym.Palette, "进入界面", "在设置中找到界面部分"),
    Step(Sym.ToggleOn, "启用现代 UI", "切换“现代 UI”以解锁新布局"),
)
private val TOUR_STEPS = listOf(
    Step(Sym.Terminal, "开发者菜单", "点击“开发”选项卡即可查看底层的新增内容。"),
    Step(Sym.VolunteerActivism, "支持Orion", "我们添加了新的捐赠方式。检查一下！"),
    Step(Sym.Favorite, "谢谢你！", "您现在可以通过 Ko-fi、UPI 或 PayPal 支持 Orion。"),
)

/** Hero moment: the container morphs to a new MaterialShape and turns a quarter on every step. */
@Composable
private fun StepHero(steps: List<Step>, i: Int, dots: IntRange?) {
    val fx = MaterialTheme.motionScheme.defaultEffectsSpec<Float>(); val sf = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    val sp = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        MorphBlob(HERO_SHAPES, i, 136.dp, cs.primaryContainer) {
            AnimatedContent(i, transitionSpec = { (fadeIn(fx) + scaleIn(sf, .6f)) togetherWith (fadeOut(fx) + scaleOut(sf, .6f)) }, label = "icon") {
                Icon(steps[it].icon, null, Modifier.size(56.dp), tint = cs.onPrimaryContainer)
            }
        }
        Spacer(Modifier.height(24.dp))
        AnimatedContent(i, transitionSpec = {
            val dir = if (targetState > initialState) 1 else -1
            (fadeIn(fx) + slideInHorizontally(sp) { it / 4 * dir }) togetherWith (fadeOut(fx) + slideOutHorizontally(sp) { -it / 4 * dir })
        }, label = "text") {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(steps[it].title, style = ty.headlineMediumEmphasized, textAlign = TextAlign.Center)
                Text(steps[it].body, Modifier.padding(top = 8.dp), style = ty.bodyLarge, color = cs.onSurfaceVariant, textAlign = TextAlign.Center)
            }
        }
        dots?.let { r ->
            Row(Modifier.padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                r.forEach { d ->
                    val w by animateDpAsState(if (d == i) 24.dp else 8.dp, MaterialTheme.motionScheme.fastSpatialSpec(), label = "dw")
                    val col by animateColorAsState(if (d == i) cs.primary else cs.outlineVariant, MaterialTheme.motionScheme.fastEffectsSpec(), label = "dc")
                    Box(Modifier.height(8.dp).width(w).clip(Pill).background(col))
                }
            }
        }
    }
}

@Composable
fun TutorialSheet(vm: MainViewModel, onDismiss: () -> Unit) = if (vm.tour) TourSheet(vm, onDismiss) else ModernUiSheet(vm, onDismiss)

@Composable
private fun ModernUiSheet(vm: MainViewModel, onDismiss: () -> Unit) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    var enable by remember { mutableStateOf(false) }; var settings by remember { mutableStateOf(false) }
    OrionSheet({
        vm.set { it.copy(hasSeenModernUITutorial = true, storeLayout = if (enable) "modern" else it.storeLayout) }
        onDismiss(); if (settings) vm.openSettings()
    }) {
        val close = LocalSheetClose.current
        val now = { enable = true; vm.haptic(H.SUCCESS); close() }
        StepHero(UI_STEPS, step, if (step > 0) 1..3 else null)
        Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when (step) {
                0 -> {
                    CtaButton("立即启用", now, icon = Sym.Bolt)
                    CtaButton("如何启用", { step = 1; vm.haptic(H.SELECTION) }, icon = Sym.Lightbulb, tonal = true)
                    TextButton(close, Modifier.fillMaxWidth()) { Text("跳过") }
                }
                UI_STEPS.lastIndex -> {
                    CtaButton("立即启用", now, icon = Sym.Bolt)
                    CtaButton("打开设置", { settings = true; close() }, icon = Sym.Settings, tonal = true)
                }
                else -> {
                    CtaButton("下一步", { step++; vm.haptic(H.SELECTION) }, icon = Sym.ArrowForward)
                    TextButton(close, Modifier.fillMaxWidth()) { Text("跳过") }
                }
            }
        }
    }
}

@Composable
private fun TourSheet(vm: MainViewModel, onDismiss: () -> Unit) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    OrionSheet({ vm.setFlag("tour", "1"); onDismiss() }) {
        val close = LocalSheetClose.current
        StepHero(TOUR_STEPS, step, TOUR_STEPS.indices)
        Column(Modifier.fillMaxWidth().padding(24.dp)) {
            CtaButton(if (step == TOUR_STEPS.lastIndex) "Got it!" else "下一步", {
                vm.haptic(H.SELECTION)
                when (step) {
                    0 -> { vm.tab = Tab.ABOUT; step = 1 }
                    1 -> { vm.tab = Tab.ABOUT; vm.donateOpen = true; step = 2 }
                    else -> close()
                }
            }, icon = if (step == TOUR_STEPS.lastIndex) Sym.Check else Sym.ArrowForward)
        }
    }
}

// ---------------- Bundle preview / editor ----------------

@Composable
fun BundlePreviewSheet(vm: MainViewModel, b: BundleItem, onDismiss: () -> Unit) = OrionSheet(onDismiss) {
    val s = LocalS.current; val d = LocalD.current; val close = LocalSheetClose.current
    val removed = s.removedBundleApps[b.id].orEmpty(); val extras = s.extraBundleApps[b.id].orEmpty()
    val all = remember(d.apps, d.importedApps) { (d.apps + d.importedApps).distinctBy { it.id } }
    val byId = remember(all) { all.associateBy { it.id } }
    val apps = (b.apps.filter { it.id !in removed } + extras.mapNotNull { byId[it] }).distinctBy { it.id }
    val present = apps.map { it.id }.toSet()
    val local = b.badge == "Local"; val customized = removed.isNotEmpty() || extras.isNotEmpty()
    val first = remember { apps.firstOrNull()?.id }
    var picking by rememberSaveable { mutableStateOf(false) }; var ask by remember { mutableStateOf<String?>(null) }
    ConfirmDialog(ask, "删除", onConfirm = {
        vm.set { st -> st.resetBundle(b.id).copy(customBundles = st.customBundles.filterNot { it.id == b.id }) }; vm.haptic(H.WARNING); close()
    }, onDismiss = { ask = null })
    AnimatedContent(picking, label = "bundle") { pick ->
        if (pick) Column {
            val platform = vm.tab.targetPlatform()
            PickerHeader("添加到合集", b.title) { picking = false }
            AppPickerList(all.filter { it.platform == platform && it.id !in present }, platform) { a ->
                vm.set { it.addAppToBundle(b.id, a.id) }; vm.haptic(H.SUCCESS); picking = false
            }
        } else LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp)) {
            item {
                Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(64.dp).clip(MaterialShapes.Cookie9Sided.toShape()).background(cs.tertiaryContainer), contentAlignment = Alignment.Center) {
                        if (b.icon != null) Icon(faIcon(b.icon), null, Modifier.size(30.dp), tint = cs.onTertiaryContainer)
                        else Text((b.monogram ?: b.title).take(2).uppercase(), style = ty.titleLarge, fontWeight = FontWeight.Black, color = cs.onTertiaryContainer)
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Eyebrow("推荐合集")
                        Text(b.title, style = ty.headlineSmallEmphasized, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text("${apps.size} app${if (apps.size == 1) "" else "s"}${b.badge?.let { " · $it" } ?: ""}", style = ty.bodySmall, color = cs.onSurfaceVariant)
                    }
                }
                if (b.description.isNotBlank()) Text(b.description, Modifier.padding(horizontal = 8.dp), style = ty.bodyMedium, color = cs.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))
                CtaButton("下载", { vm.bundleDownload(b) }, icon = Sym.Download, enabled = apps.isNotEmpty())
                Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton({ picking = true }, modifier = Modifier.weight(1f), shapes = ButtonDefaults.shapes()) {
                        Icon(Sym.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("添加应用")
                    }
                    if (customized) FilledTonalButton({ vm.set { it.resetBundle(b.id) }; vm.haptic(H.MEDIUM) }, modifier = Modifier.weight(1f), shapes = ButtonDefaults.shapes()) {
                        Icon(Sym.RestartAlt, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("重置")
                    }
                    if (local) OutlinedButton({ ask = "删除这个合集？" }, modifier = Modifier.weight(1f), shapes = ButtonDefaults.shapes(), colors = ButtonDefaults.outlinedButtonColors(contentColor = cs.error)) {
                        Icon(Sym.Delete, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("删除")
                    }
                }
            }
            if (apps.isEmpty()) item { EmptyState(Sym.Inventory2, "Bundle is empty", "Add apps with the button above.") }
            itemsIndexed(apps, key = { _, a -> a.id }) { i, a ->
                BundleRow(vm, a, colShape(i, apps.size), a.id in extras, !s.installedVersions[a.id].isNullOrEmpty(), a.id == first, onOpen = { vm.selectApp(a) }) {
                    vm.set { st ->
                        var x = st
                        if (a.id in extras) x = x.removeAppFromBundleExtras(b.id, a.id)
                        if (b.apps.any { it.id == a.id }) x = x.toggleBundleAppRemoval(b.id, a.id)
                        x
                    }
                }
            }
        }
    }
}

@Composable
private fun BundleRow(vm: MainViewModel, a: AppItem, shape: Shape, extra: Boolean, installed: Boolean, hint: Boolean, onOpen: () -> Unit, onRemove: () -> Unit) {
    SwipeRow(shape, Modifier.padding(bottom = 2.dp), right = SwipeAction(Sym.Delete, "从合集中删除", cs.error, cs.onError, removes = true, run = onRemove), hint = hint) {
        ListItem({ Text(a.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold) }, Modifier.clickable(onClick = onOpen),
            supportingContent = {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(a.category, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, false))
                    if (installed) Tag("已安装", Safe)
                    if (extra) Tag("Added", cs.tertiary)
                }
            },
            leadingContent = { AppIcon(a, 44.dp) }, trailingContent = { Icon(Sym.ChevronRight, null) },
            colors = ListItemDefaults.colors(containerColor = cs.surfaceContainerHigh))
    }
}

// ---------------- VirusTotal ----------------

private val MOD_PAGES = listOf(
    Step(Sym.Extension, "修补的应用需要上下文", "Morphe 构建修改原始应用代码。即使构建是干净的，防病毒引擎也可以标记这些更改。"),
    Step(Sym.Warning, "可能会发生误报", "Heuristic detections may react to patches, signature changes, or bundled compatibility code. Read the result; do not panic from one flag."),
    Step(Sym.Build, "最佳验证路径", "如果标志看起来可疑，请使用 Morphe Manager 自行修补应用，保持补丁更新，然后在 VirusTotal 上手动重新扫描。"),
)
private val MOD_SHAPES = listOf(MaterialShapes.Cookie4Sided, MaterialShapes.SoftBurst, MaterialShapes.Gem)
private val VT_TIPS = listOf("提示：仅扫描您计划安装的应用。", "Orion技巧：在安装修改版本之前检查补丁标签。",
    "有趣的事实：对修改后的应用进行 1-4 次检测可能是误报。", "优点：打开完整的VirusTotal报告并读取引擎名称。")
private const val VT_KEY_TUTORIAL = "https://www.youtube.com/shorts/Ynqw4dgH5ko"

private data class VtRisk(val label: String, val color: Color, val icon: ImageVector, val note: String)
private fun risk(flagged: Int) = when {
    flagged == 0 -> VtRisk("Clean", Safe, Sym.VerifiedUser, "0 detections. This is the best VirusTotal outcome.")
    flagged == 1 -> VtRisk("Low Signal", Blue, Sym.Info, "1 detection. Often a false positive, especially for modded apps.")
    flagged < 5 -> VtRisk("Review", Caution, Sym.QueryStats, "2-4 次检测。检查引擎名称和应用源。")
    else -> VtRisk("高度警惕", Red, Sym.GppBad, "5+ 检测。在手动验证之前，将其视为有风险。")
}

/** Downloaded APK first, then the installed APK, then a direct remote APK link. Android only. */
fun scanTargetFor(app: AppItem, s: Settings, d: DataState): ScanTarget? {
    if (app.platform != Platform.ANDROID) return null
    (d.readyToInstall[app.id] ?: d.pendingCleanup[app.id]?.fileName)?.let { return ScanTarget(ScanTargetType.DOWNLOADED_APK, "已下载APK", it) }
    if (!app.packageName.isNullOrEmpty() && !s.installedVersions[app.id].isNullOrEmpty()) return ScanTarget(ScanTargetType.APK, "Installed APK", app.packageName)
    val variant = (app.availableVersions.firstOrNull()?.variants?.firstOrNull()?.url ?: app.variants.firstOrNull()?.url)?.takeIf { it.isNotEmpty() }
    val url = variant ?: app.downloadUrl
    val direct = variant != null || Regex("\\.(apk|apks|xapk|zip)(\\?|$)", RegexOption.IGNORE_CASE).containsMatchIn(url)
    return if (url.startsWith("http") && url != "#" && direct) ScanTarget(ScanTargetType.REMOTE_APK, "APK File", url) else null
}

@Composable
fun VirusTotalSheet(vm: MainViewModel, app: AppItem, onDismiss: () -> Unit) = OrionSheet(onDismiss) {
    val s = LocalS.current; val d = LocalD.current; val close = LocalSheetClose.current
    val scope = rememberCoroutineScope(); val t = rememberSheetToast()
    val vt = remember { VtScan(vm.tracker, app.id, app.name, app.packageName, s.virusTotalApiKey) }
    val target = scanTargetFor(app, s, d)
    val modded = remember { listOf(app.name, app.author, app.id).any { it.contains("revanced", true) || it.contains("morphe", true) } }
    var page by rememberSaveable { mutableIntStateOf(if (modded) 0 else MOD_PAGES.size) }
    val fx = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    fun revoke() { vm.set { it.copy(virusTotalApiKey = "") }; vt.reset(); vt.view = VtView.KEY; vm.haptic(H.SUCCESS) }
    fun scan() { scope.launch { vt.start(target, s.virusTotalApiKey, vm::hapticStr) } }
    SheetTitle("VirusTotal", Sym.Security, app.name)
    SheetToastHost(t)
    AnimatedContent(page.coerceAtMost(MOD_PAGES.size) to vt.view, Modifier.fillMaxWidth(), transitionSpec = { fadeIn(fx) togetherWith fadeOut(fx) }, label = "vt") { (pg, view) ->
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(top = 8.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (pg < MOD_PAGES.size) ModWarning(pg) { page++; vm.haptic(H.SELECTION) }
            else when (view) {
                VtView.KEY -> VtKey(vm, vt, s.virusTotalApiKey, ::revoke)
                VtView.READY -> VtReady(target, s.virusTotalApiKey, ::scan, ::revoke)
                VtView.SCANNING -> VtScanning(vt)
                VtView.RESULTS -> vt.result?.let { r ->
                    VtResults(r, onCopy = { t.show("SHA-256 已复制") }, onReport = { r.permalink?.let(vm.tracker::open) }, onAgain = { vt.reset(); vt.view = VtView.READY }, onClose = close)
                }
                VtView.ERROR -> {
                    Lead("需要注意", vt.error.ifEmpty { "出了点问题。" }, "扫描已停止", cs.error) { Blob(Sym.ErrorOutline, cs.error, 72, MaterialShapes.Cookie7Sided) }
                    CtaButton("再次扫描", { vt.reset(); vt.view = if (s.virusTotalApiKey.isNotEmpty()) VtView.READY else VtView.KEY }, icon = Sym.Refresh, tonal = true)
                    TextButton(::revoke, Modifier.fillMaxWidth(), colors = ButtonDefaults.textButtonColors(contentColor = cs.error)) { Text("撤销密钥") }
                }
            }
        }
    }
}

@Composable
private fun ModWarning(page: Int, onNext: () -> Unit) {
    var left by remember { mutableIntStateOf(10) }
    LaunchedEffect(Unit) { while (left > 0) { delay(1000); left-- } }
    val p = MOD_PAGES[page]
    Lead(p.title, p.body, "注意${MOD_PAGES.size}的${page + 1}", Caution, center = true) { Blob(p.icon, Caution, 88, MOD_SHAPES[page]) }
    LinearWavyProgressIndicator({ (10 - left) / 10f }, Modifier.fillMaxWidth(), color = Caution, trackColor = Caution.copy(.2f))
    CtaButton(if (left > 0) "Continue in ${left}s" else if (page < MOD_PAGES.lastIndex) "继续" else "I Understand", onNext, enabled = left == 0)
}

@Composable
private fun VtKey(vm: MainViewModel, vt: VtScan, saved: String, revoke: () -> Unit) {
    var key by rememberSaveable { mutableStateOf(saved) }; var show by remember { mutableStateOf(false) }; var err by remember { mutableStateOf("") }
    Lead("连接VirusTotal", "您的密钥保留在该设备上。 Orion 仅在开始扫描时使用它。", "自带密钥设置")
    OutlinedTextField(key, { key = it; err = "" }, Modifier.fillMaxWidth(), label = { Text("API 密钥") }, placeholder = { Text("粘贴您的 VirusTotal API 密钥") },
        singleLine = true, isError = err.isNotEmpty(), supportingText = if (err.isNotEmpty()) ({ Text(err) }) else null, shape = RoundedCornerShape(16.dp),
        visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        leadingIcon = { Icon(Sym.Key, null) },
        trailingIcon = { IconButton({ show = !show }) { Icon(if (show) Sym.VisibilityOff else Sym.Visibility, if (show) "隐藏密钥" else "显示密钥") } })
    CtaButton("保存密钥", {
        if (key.isBlank()) { err = "首先粘贴您的 VirusTotal API 密钥。"; vm.haptic(H.ERROR) }
        else { vm.set { it.copy(virusTotalApiKey = key.trim()) }; vt.view = VtView.READY; vm.haptic(H.SUCCESS) }
    }, icon = Sym.Save)
    if (saved.isNotEmpty()) TextButton(revoke, Modifier.fillMaxWidth(), colors = ButtonDefaults.textButtonColors(contentColor = cs.error)) { Text("删除保存的密钥") }
    Column {
        GroupItem(0, 2, onClick = { vm.tracker.open(VT_KEY_TUTORIAL) }) {
            ListItem({ Text("观看按键教程", fontWeight = FontWeight.SemiBold) }, supportingContent = { Text("快速帮助您找到 VirusTotal 钥匙。") },
                leadingContent = { Blob(Sym.PlayCircle, Red, 40) }, trailingContent = { Icon(Sym.OpenInNew, null) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent))
        }
        GroupItem(1, 2) {
            ListItem({ Text("配额提示", fontWeight = FontWeight.SemiBold) }, supportingContent = { Text("免费 VirusTotal 密钥通常限制为每分钟 4 次请求。") },
                leadingContent = { Blob(Sym.Speed, Caution, 40) }, colors = ListItemDefaults.colors(containerColor = Color.Transparent))
        }
    }
}

@Composable
private fun VtReady(target: ScanTarget?, key: String, scan: () -> Unit, revoke: () -> Unit) {
    val masked = if (key.length > 8) key.take(4) + "••••" + key.takeLast(4) else "••••"
    Lead("使用VirusTotal扫描", "首先进行哈希查找。仅当 VirusTotal 尚无 APK 报告时才会进行上传。", "Ready")
    Column {
        GroupItem(0, 2) {
            ListItem({ Text(target?.label ?: "未找到扫描目标", fontWeight = FontWeight.SemiBold) },
                supportingContent = target?.let { { Text(it.detail, maxLines = 2, overflow = TextOverflow.Ellipsis) } },
                leadingContent = { Blob(if (target == null) Sym.Block else Sym.Android, if (target == null) cs.error else Safe, 40) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent))
        }
        GroupItem(1, 2) {
            ListItem({ Text(masked, fontFamily = FontFamily.Monospace) }, supportingContent = { Text("限制小费") }, leadingContent = { Blob(Sym.Key, cs.primary, 40) },
                trailingContent = { Tag("谨慎使用", Caution) }, colors = ListItemDefaults.colors(containerColor = Color.Transparent))
        }
    }
    CtaButton("Start VirusTotal Scan", scan, icon = Sym.Radar, enabled = target != null)
    TextButton(revoke, Modifier.fillMaxWidth(), colors = ButtonDefaults.textButtonColors(contentColor = cs.error)) { Text("撤销密钥") }
    Text("提示：一次扫描可能需要报告、上传和轮询调用。为您实际计划安装的应用保留免费配额。", style = ty.bodySmall, color = cs.onSurfaceVariant)
}

@Composable
private fun VtScanning(vt: VtScan) {
    var tip by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(2600); tip = (tip + 1) % VT_TIPS.size } }
    val p by animateFloatAsState(vt.progress, MaterialTheme.motionScheme.defaultSpatialSpec(), label = "vtp")
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        LoadingIndicator(Modifier.size(112.dp))
        Text("轻轻扫过……", style = ty.titleLargeEmphasized)
        Text(vt.note, style = ty.bodyMedium, color = cs.onSurfaceVariant, textAlign = TextAlign.Center)
        LinearWavyProgressIndicator({ p }, Modifier.fillMaxWidth())
        Surface(shape = RoundedCornerShape(16.dp), color = cs.secondaryContainer) {
            AnimatedContent(tip, Modifier.fillMaxWidth().padding(14.dp), label = "tip") { Text(VT_TIPS[it], style = ty.bodyMedium, textAlign = TextAlign.Center) }
        }
        Text("保持这个打开状态。当新的 APK 需要分析时，VirusTotal 可能需要一段时间。", style = ty.bodySmall, color = cs.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
private fun VtResults(r: VtResult, onCopy: () -> Unit, onReport: () -> Unit, onAgain: () -> Unit, onClose: () -> Unit) {
    val clip = LocalClipboardManager.current
    val st = r.stats; val total = st.total; val rate = if (total > 0) (st.flagged * 100f / total).roundToInt() else 0
    val k = risk(st.flagged)
    val detections = r.engines.filter { it.category == "malicious" || it.category == "suspicious" }.take(8)
    Lead(k.label, k.note, "VirusTotal 结果", k.color, center = true) { Blob(k.icon, k.color, 88, MaterialShapes.Cookie9Sided) }
    Surface(shape = RoundedCornerShape(28.dp), color = k.color.copy(.12f)) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Text("有害率", style = ty.labelLarge, color = cs.onSurfaceVariant)
            Text("$rate%", style = ty.displayMediumEmphasized, color = k.color)
            LinearProgressIndicator({ rate / 100f }, Modifier.fillMaxWidth().padding(top = 8.dp), color = k.color, trackColor = k.color.copy(.2f))
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        listOf(Triple("Flagged", st.flagged, Red), Triple("Clean", st.harmless, Safe), Triple("Engines", total, cs.onSurface)).forEachIndexed { i, (l, v, col) ->
            Surface(Modifier.weight(1f), shape = rowShape(i, 3), color = cs.surfaceContainerHigh) {
                Column(Modifier.padding(vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$v", style = ty.titleLargeEmphasized, color = col)
                    Text(l.uppercase(), style = ty.labelSmall, color = cs.onSurfaceVariant)
                }
            }
        }
    }
    Surface(shape = RoundedCornerShape(20.dp), color = cs.surfaceContainerHigh) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Eyebrow("如何阅读", cs.onSurfaceVariant)
            Text("0 次检测：总体安全。", style = ty.bodyMedium)
            Text("1-4 次检测：经常出现误报。", style = ty.bodyMedium)
            Text("5+ 检测：高度谨慎。", style = ty.bodyMedium)
        }
    }
    if (detections.isNotEmpty()) Column {
        Eyebrow("标记引擎", cs.onSurfaceVariant, Modifier.padding(start = 4.dp, bottom = 8.dp))
        detections.forEachIndexed { i, e ->
            GroupItem(i, detections.size) {
                ListItem({ Text(e.engine, fontWeight = FontWeight.SemiBold) }, supportingContent = { Text(e.result ?: e.category, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    trailingContent = { Tag(e.category, if (e.category == "malicious") Red else Caution) }, colors = ListItemDefaults.colors(containerColor = Color.Transparent))
            }
        }
    }
    if (r.hash != null) CtaButton("复制 SHA-256", { clip.setText(AnnotatedString(r.hash)); onCopy() }, icon = Sym.ContentCopy, tonal = true)
    if (r.permalink != null) CtaButton("Open VirusTotal Report", onReport, icon = Sym.OpenInNew)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onAgain, modifier = Modifier.weight(1f), shapes = ButtonDefaults.shapes()) { Text("再次扫描") }
        TextButton(onClose, modifier = Modifier.weight(1f), shapes = ButtonDefaults.shapes()) { Text("关闭") }
    }
}

// ---------------- App change request ----------------

@Composable
fun ChangeRequestSheet(vm: MainViewModel, app: AppItem, onDismiss: () -> Unit) = OrionSheet(onDismiss) {
    val close = LocalSheetClose.current
    val fields = remember(app.platform) { changeFields(app.platform) }
    var key by rememberSaveable { mutableStateOf("name") }
    val f = fields.firstOrNull { it.key == key } ?: fields.first()
    var value by rememberSaveable { mutableStateOf("") }; var reason by rememberSaveable { mutableStateOf("") }; var ref by rememberSaveable { mutableStateOf("") }
    var view by rememberSaveable { mutableStateOf("form") }; var issueUrl by rememberSaveable { mutableStateOf("") }
    var err by remember { mutableStateOf("") }; var showCurrent by remember { mutableStateOf(false) }
    val current = remember(key) { currentFieldValue(app, key) }
    fun submit() {
        vm.refreshCooldown()
        vm.submissionCooldown?.let { err = "下一个请求可在 $it 中完成。"; vm.haptic(H.WARNING); return }
        validateChange(f, value).takeIf { it.isNotEmpty() }?.let { err = it; vm.haptic(H.ERROR); return }
        if (ref.isNotBlank()) urlError(ref).takeIf { it.isNotEmpty() }?.let { err = "证据链接：$it"; vm.haptic(H.ERROR); return }
        val v: Any = if (f.kind == "urls") splitUrls(value) else value.trim()
        issueUrl = appUpdateIssueUrl(app, f.label, current, value, reason, ref, appUpdatePayloadJson(app, f.key, v))
        err = ""; view = "ready"; vm.haptic(H.SUCCESS)
    }
    if (view != "pick") SheetTitle("${app.platform} App Update", Sym.EditNote, app.name)
    AnimatedContent(view, label = "cr") { v ->
        when (v) {
            "pick" -> Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
                PickerHeader("选择一个字段", "更正应用详细信息") { view = "form" }
                fields.groupBy { it.group }.forEach { (g, list) ->
                    Eyebrow(g, modifier = Modifier.padding(start = 28.dp, top = 12.dp, bottom = 8.dp))
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        list.forEachIndexed { i, x ->
                            GroupItem(i, list.size, selected = x.key == key, onClick = { if (x.key != key) { key = x.key; value = "" }; err = ""; view = "form"; vm.haptic(H.SELECTION) }) {
                                ListItem({ Text(x.label, style = if (x.key == key) ty.titleMediumEmphasized else ty.titleMedium) }, supportingContent = { Text(x.hint) },
                                    trailingContent = { if (x.key == key) Icon(Sym.CheckCircle, null, tint = cs.primary) }, colors = ListItemDefaults.colors(containerColor = Color.Transparent))
                            }
                        }
                    }
                }
            }
            "ready" -> Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(top = 8.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Lead("更新问题已准备就绪", "该问题包含应用更新工作流程在审核后合并到 apps.json 中的确切 JSON。", f.label, center = true) {
                    Blob(Sym.TaskAlt, Safe, 88, MaterialShapes.Cookie9Sided)
                }
                CtaButton("打开GitHub问题", {
                    vm.tracker.open(issueUrl); vm.set { it.registerChangeRequest(System.currentTimeMillis()) }; vm.haptic(H.SUCCESS); close()
                }, icon = Sym.OpenInNew)
                CtaButton("编辑请求", { view = "form" }, icon = Sym.Edit, tonal = true)
            }
            else -> Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(top = 8.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Column {
                    Eyebrow("纠正什么", modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
                    GroupItem(0, 2, onClick = { view = "pick" }) {
                        ListItem({ Text(f.label, style = ty.titleMediumEmphasized) }, supportingContent = { Text(f.hint) }, trailingContent = { Icon(Sym.UnfoldMore, "更改字段") },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent))
                    }
                    GroupItem(1, 2, onClick = { showCurrent = !showCurrent }) {
                        Column(Modifier.animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec()).padding(horizontal = 16.dp, vertical = 12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("当前值", Modifier.weight(1f), style = ty.labelLarge, color = cs.onSurfaceVariant)
                                Icon(if (showCurrent) Sym.ExpandLess else Sym.ExpandMore, null, tint = cs.onSurfaceVariant)
                            }
                            if (showCurrent) Text(current.take(1000), Modifier.padding(top = 6.dp), style = ty.bodySmall, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
                val multi = f.kind == "textarea" || f.kind == "urls"; val urlKind = f.kind == "url" || f.kind == "urls"
                OutlinedTextField(value, { value = it.take(4000); err = "" }, Modifier.fillMaxWidth(), label = { Text("正确值") }, shape = RoundedCornerShape(16.dp),
                    placeholder = { Text(if (f.kind == "urls") "https://image.example/one.png\nhttps://image.example/two.png" else f.hint) },
                    singleLine = !multi, minLines = if (multi) 4 else 1, keyboardOptions = KeyboardOptions(keyboardType = if (urlKind) KeyboardType.Uri else KeyboardType.Text))
                OutlinedTextField(reason, { reason = it.take(4000) }, Modifier.fillMaxWidth(), label = { Text("原因") }, placeholder = { Text("为什么需要进行此修正？") },
                    minLines = 3, shape = RoundedCornerShape(16.dp))
                OutlinedTextField(ref, { ref = it; err = "" }, Modifier.fillMaxWidth(), label = { Text("证据链接（可选）") }, placeholder = { Text("发布页面或官方来源") },
                    singleLine = true, shape = RoundedCornerShape(16.dp), leadingIcon = { Icon(Sym.Link, null) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
                AnimatedVisibility(err.isNotEmpty()) {
                    Surface(shape = RoundedCornerShape(16.dp), color = cs.errorContainer, contentColor = cs.onErrorContainer) {
                        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Sym.ErrorOutline, null, Modifier.size(20.dp)); Spacer(Modifier.width(10.dp)); Text(err, style = ty.bodyMedium)
                        }
                    }
                }
                CtaButton("准备应用更新问题", ::submit, icon = Sym.Description)
                Text("+50 XP 进入排行榜", Modifier.fillMaxWidth(), style = ty.labelLarge, color = cs.tertiary, textAlign = TextAlign.Center)
            }
        }
    }
}

// ---------------- 2.0 welcome ----------------

private val WELCOME_POINTS = listOf(
    Triple(Sym.Speed, "原生速度", "使用 Jetpack Compose 构建，看不到网页视图"),
    Triple(Sym.Palette, "材料 3 表现力", "弹性运动、形状变形和 Material You"),
    Triple(Sym.Bolt, "免去24小时的等待", "内置无线安装，一次设置"),
    Triple(Sym.MilitaryTech, "等级和飞行员卡", "赚取 XP 并攀登铜牌、银牌和金牌"),
)

/** One-time hero for the native switch, shown after updating over the web app and on a fresh install alike. */
@Composable
fun WelcomeSheet(vm: MainViewModel) = OrionSheet(vm::finishWelcome) {
    val cs = MaterialTheme.colorScheme; val ty = MaterialTheme.typography; val close = LocalSheetClose.current
    val still = LocalS.current.disableAnimations
    var k by remember { mutableIntStateOf(0) }
    LaunchedEffect(still) { delay(250); k = 1; while (!still) { delay(2200); k++ } }
    val pulse by rememberInfiniteTransition(label = "re").animateFloat(1f, if (still) 1f else 1.08f,
        infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "p")
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.BottomCenter) {
            MorphBlob(listOf(MaterialShapes.Circle, MaterialShapes.Cookie12Sided, MaterialShapes.SoftBurst, MaterialShapes.Flower), k, 168.dp, cs.primaryContainer) {
                Icon(androidx.compose.ui.res.painterResource(com.orion.store.R.drawable.ic_splash_logo), null, Modifier.size(84.dp), tint = cs.onPrimaryContainer)
            }
            Surface(Modifier.offset(y = 10.dp).graphicsLayer { scaleX = pulse; scaleY = pulse }, shape = Pill, color = cs.tertiary, contentColor = cs.onTertiary) {
                Text("RE", Modifier.padding(horizontal = 18.dp, vertical = 4.dp), style = ty.labelLargeEmphasized, letterSpacing = 3.sp)
            }
        }
        Text("Orion Store 2.0 · 原生", Modifier.padding(top = 28.dp).reveal(0), style = ty.labelLarge, color = cs.primary)
        Text("终于本土了", Modifier.reveal(1), style = ty.displaySmallEmphasized, textAlign = TextAlign.Center)
        Text("您所知道的网络应用已从头开始重建为真正的 Android 应用。同样的Orion，全新的引擎。",
            Modifier.padding(top = 8.dp).reveal(2), style = ty.bodyLarge, color = cs.onSurfaceVariant, textAlign = TextAlign.Center)
        Column(Modifier.padding(top = 20.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            WELCOME_POINTS.forEachIndexed { i, (icon, title, desc) ->
                ListItem({ Text(title, style = ty.titleMedium) }, Modifier.reveal(3 + i).clip(rowShape(i, WELCOME_POINTS.size)),
                    supportingContent = { Text(desc) }, leadingContent = { TonalIcon(icon, cs.primary) },
                    colors = ListItemDefaults.colors(containerColor = cs.surfaceContainer))
            }
        }
        AnimatedVisibility(vm.migratedFromWeb) {
            Row(Modifier.padding(top = 12.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(cs.tertiaryContainer).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Sym.CheckCircle, null, tint = cs.onTertiaryContainer); Spacer(Modifier.width(12.dp))
                Text("您的个人资料、徽章和设置来自网络应用。", style = ty.bodyMedium, color = cs.onTertiaryContainer)
            }
        }
        CtaButton("开始吧", close, Modifier.padding(top = 20.dp).reveal(8), icon = Sym.RocketLaunch)
    }
}
