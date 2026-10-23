package com.umutk.pulse.extras.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.PField
import com.umutk.pulse.T
import com.umutk.pulse.tr
import kotlinx.coroutines.delay
import kotlin.math.abs

/** One thing on the home grid: an app or a folder of apps. */
internal sealed class HomeItem(val key: String) {
    class App(val app: LaunchApp) : HomeItem("app:" + app.pkg)
    class Folder(val id: String, val name: String, val apps: List<LaunchApp>) : HomeItem("f:$id")
}


/** "name|pkg,pkg" of a folder entry. */
private fun parseFolder(v: String): Pair<String, List<String>> = v.substringBefore('|') to v.substringAfter('|', "").split(',').filter { it.isNotBlank() }

/** The home items in the saved order: folders and apps; an app that is in no folder and not in the order yet follows at the end in its usual place. */
internal fun resolveHome(visible: List<LaunchApp>): List<HomeItem> {
    val byPkg = visible.associateBy { it.pkg }
    val folders = LauncherPrefs.folders.filterKeys { it !in LauncherPrefs.folderOff }.mapValues { parseFolder(it.value) }
    val inFolder = folders.values.flatMap { it.second }.toSet()
    val out = ArrayList<HomeItem>(); val used = HashSet<String>()
    fun folder(id: String): HomeItem.Folder? = folders[id]?.let { (n, p) -> p.mapNotNull { byPkg[it] }.takeIf { it.isNotEmpty() }?.let { HomeItem.Folder(id, n, it) } }
    for (k in LauncherPrefs.homeOrder) {
        if (k.startsWith("app:")) { val p = k.removePrefix("app:"); val a = byPkg[p]; if (a != null && p !in inFolder && used.add(k)) out += HomeItem.App(a) }
        else if (k.startsWith("f:")) { folder(k.removePrefix("f:"))?.let { if (used.add(k)) out += it } }
    }
    folders.keys.forEach { id -> if (used.add("f:$id")) folder(id)?.let { out += it } }
    visible.forEach { if (it.pkg !in inFolder && used.add("app:" + it.pkg)) out += HomeItem.App(it) }
    return out
}

private fun commit(ctx: android.content.Context, order: List<String>, folders: Map<String, String> = LauncherPrefs.folders) {
    LauncherPrefs.homeOrder = order
    LauncherPrefs.folders = folders
    LauncherPrefs.save(ctx)
}

/**
 * The app grid of the home, in pages: a tap opens the app or the folder; a long press picks an item up (a press that does not move opens its menu), and
 * it can be put on another place, on another page (hold it at the screen edge), or on another app to make a folder, or on a folder to add it.
 */
@Composable
internal fun HomeGrid(look: LauncherLook, state: LauncherState, visible: List<LaunchApp>, columns: Int, iconDp: Int, edge: androidx.compose.ui.unit.Dp, open: (LaunchApp) -> Unit) {
    val ctx = LocalContext.current
    val view = LocalView.current
    val density = LocalDensity.current.density
    val items = remember(visible, LauncherPrefs.homeOrder, LauncherPrefs.folders, LauncherPrefs.folderOff) { resolveHome(visible) }
    val perPage = columns * LauncherPrefs.rows
    var dragKey by remember { mutableStateOf<String?>(null) }
    var finger by remember { mutableStateOf(Offset.Zero) }
    var moved by remember { mutableFloatStateOf(0f) }
    var rootPos by remember { mutableStateOf(Offset.Zero) }
    var rootW by remember { mutableFloatStateOf(0f) }
    var openFolder by remember { mutableStateOf<String?>(null) }
    val rects = remember { HashMap<String, Rect>() }
    // while something is held, one empty page waits at the end so it can be put there
    val basePages = items.chunked(perPage)
    val pages: List<List<HomeItem>> = if (dragKey != null && basePages.isNotEmpty()) basePages + listOf(emptyList<HomeItem>()) else basePages.ifEmpty { listOf(emptyList<HomeItem>()) }
    val pager = rememberPagerState(pageCount = { pages.size })
    val side = when {
        dragKey == null -> 0
        finger.x - rootPos.x < 40 * density -> -1
        finger.x - rootPos.x > rootW - 40 * density -> 1
        else -> 0
    }
    LaunchedEffect(side) {
        while (side != 0) {
            delay(650)
            val t = pager.currentPage + side
            if (t in 0 until pager.pageCount) pager.animateScrollToPage(t) else break
        }
    }

    fun drop() {
        val dk = dragKey ?: return
        dragKey = null
        val dragged = items.firstOrNull { it.key == dk } ?: return
        // a press that did not move: the app's menu
        if (moved < 12f * density) { if (dragged is HomeItem.App) state.longPress(dragged.app); return }
        val target = rects.entries.firstOrNull { (k, r) -> k != dk && r.contains(finger) }?.key
        val order = items.map { it.key }.toMutableList()
        val targetItem = items.firstOrNull { it.key == target }
        // the middle of a cell is "onto it" (a folder), the rim is "next to it" (a new place)
        val onto = target != null && rects[target]!!.let { r -> abs(finger.x - r.center.x) < r.width * .26f && abs(finger.y - r.center.y) < r.height * .26f }
        when {
            onto && targetItem is HomeItem.Folder && dragged is HomeItem.App -> {
                val v = LauncherPrefs.folders[targetItem.id].orEmpty(); val (n, p) = parseFolder(v)
                order.remove(dk)
                commit(ctx, order, LauncherPrefs.folders + (targetItem.id to "$n|${(p + dragged.app.pkg).joinToString(",")}"))
            }
            onto && targetItem is HomeItem.App && dragged is HomeItem.App -> {
                val id = System.currentTimeMillis().toString(36)
                order[order.indexOf(target!!)] = "f:$id"; order.remove(dk)
                commit(ctx, order, LauncherPrefs.folders + (id to tr("Folder", "Klasör") + "|" + targetItem.app.pkg + "," + dragged.app.pkg))
            }
            else -> {
                order.remove(dk)
                val at = if (target != null) order.indexOf(target) else (pager.currentPage * perPage + (pages.getOrNull(pager.currentPage)?.count { it.key != dk } ?: 0)).coerceAtMost(order.size)
                order.add(at.coerceIn(0, order.size), dk)
                commit(ctx, order)
            }
        }
    }

    Box(Modifier.fillMaxSize().onGloballyPositioned { rootPos = it.positionInRoot(); rootW = it.size.width.toFloat() }) {
        HorizontalPager(pager, Modifier.fillMaxSize(), userScrollEnabled = dragKey == null) { p ->
            Column(Modifier.fillMaxSize().padding(horizontal = maxOf(8.dp, edge), vertical = 8.dp).pageEffect(pager, p, LauncherPrefs.pageEffect)) {
                pages.getOrElse(p) { emptyList() }.chunked(columns).forEach { row ->
                    Row(Modifier.fillMaxWidth()) {
                        row.forEach { item ->
                            val k = item.key
                            DisposableEffect(k) { onDispose { rects.remove(k) } }
                            Box(
                                Modifier.weight(1f).onGloballyPositioned { rects[k] = it.boundsInRoot() }.alpha(if (dragKey == k) .25f else 1f)
                                    .pointerInput(k) { detectTapGestures(onTap = { if (item is HomeItem.App) open(item.app) else if (item is HomeItem.Folder) openFolder = item.id }) }
                                    .pointerInput(k) {
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = { o ->
                                                if (LauncherPrefs.haptic) view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                                                dragKey = k; moved = 0f; finger = (rects[k]?.topLeft ?: Offset.Zero) + o
                                            },
                                            onDrag = { c, d -> c.consume(); finger += d; moved += abs(d.x) + abs(d.y) },
                                            onDragEnd = { drop() },
                                            onDragCancel = { dragKey = null },
                                        )
                                    },
                            ) {
                                when (item) {
                                    is HomeItem.App -> look.IconCell(item.app, iconDp, Modifier.fillMaxWidth())
                                    is HomeItem.Folder -> FolderCell(item, iconDp)
                                }
                            }
                        }
                        repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
        if (pages.size > 1 && LauncherPrefs.pageDots) PageDots(pager.currentPage, pages.size, Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp))
        // the held item follows the finger
        dragKey?.let { k ->
            val sz = (iconDp * 1.2f)
            Box(Modifier.offset { IntOffset((finger.x - rootPos.x - sz * density / 2f).toInt(), (finger.y - rootPos.y - sz * density / 2f).toInt()) }.size(sz.dp).alpha(.92f)) {
                when (val it = items.firstOrNull { x -> x.key == k }) {
                    is HomeItem.App -> LaunchAppPicture(it.app, sz.dp, T.panel, T.text)
                    is HomeItem.Folder -> FolderBadge(it, sz)
                    null -> {}
                }
            }
        }
    }
    openFolder?.let { id -> items.firstOrNull { it is HomeItem.Folder && it.id == id }?.let { FolderDialog(it as HomeItem.Folder, state, open) { openFolder = null } } ?: run { openFolder = null } }
}

@Composable
private fun FolderBadge(f: HomeItem.Folder, size: Float) {
    val shape = RoundedCornerShape((size * .28f).dp)
    Box(Modifier.size(size.dp).clip(shape).background(Color.White.copy(alpha = .22f)).border(1.dp, Color.White.copy(alpha = .35f), shape).padding((size * .09f).dp)) {
        when (LauncherPrefs.folderStyle) {
            // three in a row, the first biggest
            "line" -> Row(Modifier.align(Alignment.Center), horizontalArrangement = Arrangement.spacedBy((-size * .08f).dp)) { f.apps.take(3).forEach { LaunchAppPicture(it, (size * .4f).dp, T.panel, T.text) } }
            // piled up: each a little further to the corner
            "stack" -> f.apps.take(3).reversed().forEachIndexed { i, a -> Box(Modifier.align(Alignment.Center).offset(((i - 1) * size * .09f).dp, ((i - 1) * size * .09f).dp)) { LaunchAppPicture(a, (size * .5f).dp, T.panel, T.text) } }
            else -> Column(verticalArrangement = Arrangement.spacedBy((size * .04f).dp)) {
                f.apps.take(4).chunked(2).forEach { r ->
                    Row(horizontalArrangement = Arrangement.spacedBy((size * .04f).dp)) { r.forEach { LaunchAppPicture(it, (size * .38f).dp, T.panel, T.text) } }
                }
            }
        }
    }
}

@Composable
private fun FolderCell(f: HomeItem.Folder, iconDp: Int) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        FolderBadge(f, iconDp.toFloat())
        if (LauncherPrefs.labels) Text(f.name, color = Color.White, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
            style = TextStyle(shadow = Shadow(Color.Black.copy(alpha = .7f), Offset(1f, 1f), 3f)), modifier = Modifier.padding(top = 4.dp))
    }
}

/** An open folder: its name (editable) and its apps; a tap opens an app, a long press takes it out of the folder. */
@Composable
private fun FolderDialog(f: HomeItem.Folder, state: LauncherState, open: (LaunchApp) -> Unit, onClose: () -> Unit) {
    val ctx = LocalContext.current
    var name by remember(f.id) { mutableStateOf(f.name) }
    val none = remember { MutableInteractionSource() }
    fun save(newName: String = name, pkgs: List<String> = f.apps.map { it.pkg }) {
        val folders = LauncherPrefs.folders
        if (pkgs.size < 2) {
            // a folder of one app is just the app again
            val order = LauncherPrefs.homeOrder.toMutableList(); val at = order.indexOf("f:${f.id}")
            if (at >= 0) { order.removeAt(at); pkgs.forEach { order.add(at, "app:$it") } }
            commit(ctx, order, folders - f.id)
        } else commit(ctx, LauncherPrefs.homeOrder, folders + (f.id to "$newName|${pkgs.joinToString(",")}"))
    }
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .55f)).clickable(interactionSource = none, indication = null) { save(); onClose() }, contentAlignment = Alignment.Center) {
        Column(Modifier.padding(24.dp).widthIn(max = 380.dp).fillMaxWidth().background(T.bg).border(1.dp, T.line).clickable(interactionSource = none, indication = null) {}.padding(16.dp)) {
            PField(name, { name = it }, tr("Folder name", "Klasör adı"), Modifier.fillMaxWidth())
            Spacer(Modifier.size(10.dp))
            f.apps.chunked(4).forEach { row ->
                Row(Modifier.fillMaxWidth()) {
                    row.forEach { app ->
                        Column(Modifier.weight(1f).padding(vertical = 6.dp).pointerInput(app.pkg) {
                            detectTapGestures(
                                onTap = { save(); onClose(); open(app) },
                                onLongPress = { save(pkgs = f.apps.map { it.pkg } - app.pkg); onClose() },
                            )
                        }, horizontalAlignment = Alignment.CenterHorizontally) {
                            LaunchAppPicture(app, 44.dp, T.panel, T.text)
                            Text(app.label, color = T.text, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                    repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}


/** How a home page moves while the finger swipes: the offset of this page from the current one drives a turn, a zoom, a pile or a flip. */
private fun Modifier.pageEffect(state: androidx.compose.foundation.pager.PagerState, page: Int, effect: String): Modifier = if (effect == "simple") this else graphicsLayer {
    val off = (state.currentPage - page) + state.currentPageOffsetFraction     // 0 on the page in front, -1 / 1 on the neighbours
    val a = kotlin.math.abs(off).coerceIn(0f, 1f)
    when (effect) {
        "cube" -> { cameraDistance = 14f * density; rotationY = -off * 70f; transformOrigin = androidx.compose.ui.graphics.TransformOrigin(if (off > 0) 1f else 0f, .5f) }
        "zoom" -> { alpha = 1f - a; scaleX = 1f - a * .25f; scaleY = 1f - a * .25f }
        "stack" -> { if (off < 0) { translationX = size.width * off; alpha = 1f - a * .3f; scaleX = 1f - a * .1f; scaleY = 1f - a * .1f } else { translationX = size.width * off * .0f } }
        "flip" -> { cameraDistance = 16f * density; rotationY = -off * 180f; alpha = if (a > .5f) 0f else 1f }
    }
}
