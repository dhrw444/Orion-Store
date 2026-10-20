package com.orion.store.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.orion.store.data.*

private val Sky = Color(0xFF0EA5E9)
private fun filterIcon(f: MyAppsFilter) = when (f) {
    MyAppsFilter.ALL -> Sym.Inventory2; MyAppsFilter.INSTALLED -> Sym.CheckCircle
    MyAppsFilter.DOWNLOADED -> Sym.Download; MyAppsFilter.UPDATES -> Sym.ArrowUpward
}
private val EMPTY = mapOf(MyAppsFilter.ALL to "还没有应用", MyAppsFilter.INSTALLED to "没有安装的应用", MyAppsFilter.DOWNLOADED to "没有下载的应用", MyAppsFilter.UPDATES to "没有待处理的更新")

@Composable
private fun tone(r: MyAppRow): Pair<ImageVector, Color> = when {
    r.status == "downloading" -> Sym.ArrowDownward to MaterialTheme.colorScheme.primary
    r.status == "ready" -> Sym.Inventory to Safe
    r.hasUpdate -> Sym.ArrowUpward to Caution
    r.isInstalledNow -> Sym.CheckCircle to Sky
    r.libraryStatus == "installed" -> Sym.History to MaterialTheme.colorScheme.onSurfaceVariant
    else -> Sym.FileDownload to MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
fun MyAppsTab(vm: MainViewModel, padding: PaddingValues) {
    val s = LocalS.current; val d = LocalD.current
    var filter by rememberSaveable { mutableStateOf(MyAppsFilter.ALL) }; val query = vm.libraryQuery
    val updateIds = vm.updates.map { it.id }.toSet()
    val rows = remember(d, s.installedVersions, s.lastRemoteVersions, updateIds) { buildMyAppsRows(vm.allApps(), s, d, updateIds) }
    val pending = rows.filter { it.hasUpdate && it.status != "downloading" }
    val visible = rows.filter { it.matches(filter) && it.matchesQuery(query) }
    val queue = d.activeDownloads.size + d.readyToInstall.size
    val cs = MaterialTheme.colorScheme
    LazyColumn(Modifier.fillMaxSize().readableWidth(), contentPadding = padding) {
        item("header") { Header(vm) }
        item {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                item {
                    FilledTonalButton({ vm.haptic(H.SELECTION); vm.openSettings("queue") }, shapes = ButtonDefaults.shapes(), contentPadding = ButtonDefaults.SmallContentPadding) {
                        Icon(Sym.Downloading, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("队列")
                        if (queue > 0) { Spacer(Modifier.width(6.dp)); Badge { Text("$queue") } }
                    }
                }
                item { VerticalDivider(Modifier.height(28.dp).padding(horizontal = 4.dp), color = cs.outlineVariant) }
                items(MyAppsFilter.entries) { f -> Choice(filter == f, "${f.label} · ${rows.count { it.matches(f) }}", filterIcon(f)) { filter = f } }
            }
        }
        if (filter == MyAppsFilter.UPDATES && pending.isNotEmpty()) item {
            CtaButton("Update all (${pending.size})", { vm.haptic(H.MEDIUM); pending.forEach { vm.download(it.app) } }, Modifier.padding(horizontal = 16.dp, vertical = 4.dp), Sym.SystemUpdate)
        }
        if (vm.loading) item("loading") { StoreSkeleton(false) }
        else if (visible.isEmpty()) item {
            if (query.isNotBlank()) Column(Modifier.fillMaxWidth().padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Sym.SearchOff, null, Modifier.size(48.dp), tint = cs.onSurfaceVariant)
                Text("没有匹配“${query}”的应用", Modifier.padding(top = 12.dp), style = MaterialTheme.typography.titleMedium)
                TextButton({ vm.libraryQuery = "" }) { Text("清除搜索") }
            } else EmptyState(filterIcon(filter), EMPTY.getValue(filter), if (filter == MyAppsFilter.ALL) "Apps you download from Orion show up here." else null, Modifier.padding(top = 32.dp))
        }
        itemsIndexed(visible, key = { _, it -> it.app.id }) { i, r -> LibraryRow(vm, r, Modifier.animateItem().reveal(i)) }
        // Ignored updates: listed with a one-tap undo, under All and Updates where a missing update would be noticed
        val now = System.currentTimeMillis()
        val ignored = if (filter == MyAppsFilter.ALL || filter == MyAppsFilter.UPDATES)
            s.ignoredUpdates.keys.mapNotNull { id -> vm.allApps().firstOrNull { it.id == id }?.takeIf { isUpdateIgnored(it, s, now) } } else emptyList()
        if (ignored.isNotEmpty()) item("ignored-title") {
            Text("忽略更新 · ${ignored.size}", Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 8.dp), style = MaterialTheme.typography.titleMediumEmphasized)
        }
        itemsIndexed(ignored, key = { _, a -> "ignored-${a.id}" }) { i, a ->
            val until = when (s.ignoredUpdates[a.id]?.type) { "week" -> "Hidden for a week"; "version" -> "Hidden until the next version"; else -> "Hidden for good" }
            ListItem({ Text(a.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                Modifier.animateItem().padding(horizontal = 16.dp).padding(bottom = 2.dp).clip(rowShape(i, ignored.size)).clickable { vm.selectApp(a) },
                supportingContent = { Text(until) }, leadingContent = { AppIcon(a, 44.dp) },
                trailingContent = { FilledTonalButton({ vm.set { it.copy(ignoredUpdates = it.ignoredUpdates - a.id) }; vm.haptic(H.SUCCESS) }, shapes = ButtonDefaults.shapes(),
                    contentPadding = ButtonDefaults.SmallContentPadding) { Text("取消忽略") } },
                colors = ListItemDefaults.colors(containerColor = cs.surfaceContainer))
        }
    }
}

@Composable
private fun LibraryRow(vm: MainViewModel, r: MyAppRow, modifier: Modifier) {
    val (icon, c) = tone(r); val cs = MaterialTheme.colorScheme
    Column(modifier.fillMaxWidth().padding(horizontal = 8.dp).springClick { vm.selectApp(r.app, "lib") }.animateContentSize().padding(horizontal = 12.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIcon(r.app, 52.dp, state = true, from = "lib")
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(r.app.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${r.app.author}·${r.app.platform}·${r.versionDetail}", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, null, Modifier.size(14.dp), tint = c); Spacer(Modifier.width(4.dp))
                    Text(r.statusLabel, style = MaterialTheme.typography.labelMedium, color = c, maxLines = 1)
                }
            }
            val action = r.primaryAction
            if (r.hasSavedApk && action in setOf("install", "update", "download")) IconButton({ vm.deleteLibraryApk(r.app) }) { Icon(Sym.DeleteOutline, "删除 APK") }
            @Composable fun Primary(label: String, onClick: () -> Unit) =
                FilledTonalButton(onClick, shapes = ButtonDefaults.shapes(), contentPadding = ButtonDefaults.SmallContentPadding) { Text(label) }
            when {
                r.status == "downloading" -> FilledTonalIconButton({ vm.cancelDownload(r.app.id) }, shapes = IconButtonDefaults.shapes()) { Icon(Sym.Close, "取消下载") }
                action == "install" -> Primary("安装") { r.savedFile?.let { vm.installFile(r.app, it) } }
                action == "update" -> Primary("更新") { vm.download(r.app) }
                action == "download" -> Primary("下载") { vm.download(r.app) }
                action == "delete" -> IconButton({ vm.deleteLibraryApk(r.app) }) { Icon(Sym.DeleteOutline, "删除 APK") }
                else -> Icon(Sym.ChevronRight, "打开详情", tint = cs.onSurfaceVariant)
            }
        }
        if (r.status == "downloading") Row(Modifier.padding(start = 68.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            val p by animateFloatAsState(r.progress / 100f, MaterialTheme.motionScheme.defaultSpatialSpec(), label = "p")
            LinearWavyProgressIndicator({ p }, Modifier.weight(1f)); Spacer(Modifier.width(12.dp))
            Text("${Math.round(r.progress)}%", style = MaterialTheme.typography.labelLarge)
        }
    }
}
