package com.umutk.pulse

import android.Manifest
import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private sealed interface CtScreen {
    class Detail(val id: Long) : CtScreen
    class Edit(val id: Long, val seed: CtDetail?) : CtScreen
    class Group(val id: Long) : CtScreen
    class Custom(val id: Long) : CtScreen
    object Settings : CtScreen
    object Dupes : CtScreen
}

/** Pending request from an incoming intent; CtRoot / CtMain / CtKeypad consume it. */
internal var ctInUri by mutableStateOf<Uri?>(null)
internal var ctInDial by mutableStateOf<String?>(null)
internal var ctInInsert by mutableStateOf<CtDetail?>(null)
internal var ctCover by mutableStateOf(false) // a pushed screen is on top of the list
internal var ctPick by mutableStateOf(false)

internal fun ctHandle(i: Intent?) {
    ctPick = false
    i ?: return
    when (i.action) {
        Intent.ACTION_VIEW, Intent.ACTION_EDIT -> ctInUri = i.data
        Intent.ACTION_DIAL -> ctInDial = i.data?.schemeSpecificPart?.let { Uri.decode(it) } ?: ""
        Intent.ACTION_PICK -> ctPick = true
        Intent.ACTION_INSERT, Intent.ACTION_INSERT_OR_EDIT -> {
                        val nm = i.getStringExtra(android.provider.ContactsContract.Intents.Insert.NAME).orEmpty().trim()
            val ph = i.getStringExtra(android.provider.ContactsContract.Intents.Insert.PHONE).orEmpty(); val em = i.getStringExtra(android.provider.ContactsContract.Intents.Insert.EMAIL).orEmpty()
            ctInInsert = CtDetail(0, emptyList(), nm, nm.substringBeforeLast(' ', nm), if (' ' in nm) nm.substringAfterLast(' ') else "", i.getStringExtra(android.provider.ContactsContract.Intents.Insert.COMPANY).orEmpty(), false, null,
                if (ph.isBlank()) emptyList() else listOf(CtField(2, ph)), if (em.isBlank()) emptyList() else listOf(CtField(1, em)), emptyList(), "", i.getStringExtra(android.provider.ContactsContract.Intents.Insert.NOTES).orEmpty(), emptySet())
        }
    }
}

/**
 * Contact id behind a URI sent by another app (dialer "info", share sheets): a contact, a lookup link (contacts/lookup/key/id)
 * or one data row (data/id, which holds the contact id). 0 when none of them resolves.
 */
private fun ctIdOf(ctx: Context, u: Uri): Long = runCatching {
    val cr = ctx.contentResolver
    val seg = u.pathSegments
    when {
        "lookup" in seg -> android.provider.ContactsContract.Contacts.lookupContact(cr, u)?.let { android.content.ContentUris.parseId(it) } ?: 0L
        seg.firstOrNull() == "data" && u.authority == android.provider.ContactsContract.AUTHORITY ->
            cr.query(u, arrayOf(android.provider.ContactsContract.Data.CONTACT_ID), null, null, null)?.use { if (it.moveToFirst()) it.getLong(0) else 0L } ?: 0L
        else -> cr.query(u, arrayOf("_id"), null, null, null)?.use { if (it.moveToFirst()) it.getLong(0) else 0L } ?: 0L
    }
}.getOrDefault(0L)

private val ctRadius get() = if (T.material) 14.dp else T.radius
private val ctAcc get() = Look.accent.copy(alpha = 1f)

private val ctL get() = CtPrefs.look
private val LocalCtFg = compositionLocalOf<Color?> { null }

private fun ctKey(v: androidx.compose.ui.graphics.vector.ImageVector): String = when (v) {
    Icons.Outlined.Person, Icons.Outlined.PersonOff, Icons.Outlined.Contacts -> "person"
    Icons.Outlined.StarOutline -> "star"; Icons.Outlined.Star -> "starOn"
    Icons.Outlined.Groups, Icons.Outlined.Group -> "users"; Icons.Outlined.GroupAdd, Icons.Outlined.Add -> "add"
    Icons.Outlined.Search -> "search"; Icons.Outlined.MoreVert -> "more"; Icons.Outlined.Close -> "close"; Icons.Outlined.Check -> "check"
    Icons.Outlined.Edit -> "edit"; Icons.Outlined.Share -> "share"; Icons.Outlined.Delete -> "delete"
    Icons.Outlined.Call -> "phone"; Icons.Outlined.Sms -> "chat"; Icons.Outlined.Email -> "mail"; Icons.Outlined.Place -> "globe"
    Icons.Outlined.Cake -> "calendar"; Icons.Outlined.EditNote -> "notes"; Icons.Outlined.PhotoCamera -> "image"
    Icons.Outlined.MergeType -> "dupes"; Icons.Outlined.Settings -> "settings"; Icons.Outlined.FileDownload -> "download"
    Icons.Outlined.FileUpload -> "upload"; Icons.Outlined.Palette -> "palette"; Icons.Outlined.DarkMode -> "light"
    Icons.Outlined.SortByAlpha -> "sortName"; Icons.AutoMirrored.Outlined.ArrowBack -> "back"
    Icons.Outlined.Info -> "info"; Icons.Outlined.Dialpad -> "gridView"; Icons.Outlined.History -> "history"; Icons.Outlined.Animation -> "flash"
    Icons.Outlined.Language -> "globe"; Icons.Outlined.Backspace -> "back"; Icons.Outlined.CallMade -> "up"; Icons.Outlined.CallReceived -> "down"; Icons.Outlined.CallMissed -> "error"
    Icons.Outlined.Tune -> "settings"; Icons.Outlined.Wallpaper, Icons.Outlined.Videocam -> "image"; Icons.Outlined.Badge -> "person"
    else -> "info"
}

/** Long-press selection of list rows (export selected). */
private val ctSel = mutableStateListOf<Long>()
private fun ctToggle(id: Long) { if (!ctSel.remove(id)) ctSel.add(id) }

/** Material look: the Material icon as before; every other look: the look's own icon pack through PIcon. */
@Composable
private fun CtGlyph(v: androidx.compose.ui.graphics.vector.ImageVector, cd: String?, modifier: Modifier = Modifier, tint: Color = Color.Unspecified) {
    val c = if (tint != Color.Unspecified) tint else LocalCtFg.current ?: androidx.compose.material3.LocalContentColor.current
    if (T.material) androidx.compose.material3.Icon(v, cd, modifier, c) else PIcon(ctKey(v), c, 24.dp, modifier)
}

@Composable
private fun CtIcon(v: androidx.compose.ui.graphics.vector.ImageVector, tint: Color = Color.Unspecified, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val c = if (tint != Color.Unspecified) tint else LocalCtFg.current ?: T.text
    if (onClick == null) CtGlyph(v, null, tint = c)
    else IconButton(onClick, modifier) { CtGlyph(v, null, tint = c) }
}

/** Recycle-bin button: the look's own bin, tilted open around its bottom-left hinge by [angle]. */
@Composable
private fun CtBinButton(angle: Float, onClick: () -> Unit) {
    IconButton(onClick, Modifier.size(40.dp)) {
        BinIcon(LocalCtFg.current ?: T.text, 24.dp, Modifier.graphicsLayer { rotationZ = angle; transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 1f) })
    }
}

/** Era chrome of the title strip: Win95 navy, XP/Messenger blue, Win7 Aero glass, Win10 flat accent. Null: plain. */
private fun ctBar(): Brush? = when (ctL) {
    "win95" -> Brush.horizontalGradient(listOf(Color(0xFF000080), Color(0xFF1084D0)))
    "xp" -> Brush.verticalGradient(listOf(Color(0xFF8DBBF0), Color(0xFF4C86D8), Color(0xFF2C62BC)))
    "win7" -> Brush.verticalGradient(listOf(Color(0xFFF2F8FE), Color(0xFFC4DAF1), Color(0xFFB0CBE8)))
    "win10" -> SolidColor(ctAcc)
    else -> null
}
private fun ctBarFg(): Color = when (ctL) { "win95", "xp" -> Color.White; "win10" -> onAccent(ctAcc); else -> T.text }

private class CtSearch(val can: Boolean, val open: Boolean, val q: String, val onQ: (String) -> Unit, val onOpen: (Boolean) -> Unit, val onGo: () -> Unit)

private fun ctFieldShape(): androidx.compose.ui.graphics.Shape = when (ctL) {
    "win95", "win10" -> androidx.compose.ui.graphics.RectangleShape
    "xp", "win7" -> RoundedCornerShape(2.dp)
    "win11" -> RoundedCornerShape(8.dp)
    else -> RoundedCornerShape(if (T.material) 24.dp else ctRadius)
}

@Composable
private fun CtTopBar(title: String, onBack: (() -> Unit)?, search: CtSearch? = null, actions: @Composable RowScope.() -> Unit = {}) {
    val bar = ctBar()
    val fg = if (bar != null) ctBarFg() else null
    CompositionLocalProvider(LocalCtFg provides fg) {
        Column(Modifier.fillMaxWidth().then(if (bar != null) Modifier.background(bar) else Modifier)) {
            Row(Modifier.fillMaxWidth().statusBarsPadding().height(if (T.material) 56.dp else 44.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                val open = search?.open == true
                val frac by androidx.compose.animation.core.animateFloatAsState(if (open) 1f else 0f, androidx.compose.animation.core.tween(200), label = "ctSearch")
                if (search != null && search.can) CtIcon(if (open) Icons.AutoMirrored.Outlined.ArrowBack else Icons.Outlined.Search) { search.onOpen(!open) }
                else if (onBack != null) CtIcon(Icons.AutoMirrored.Outlined.ArrowBack, onClick = onBack) else Spacer(Modifier.width(12.dp))
                if (frac < .99f) Text(title, color = fg ?: T.text, fontSize = if (T.material) 20.sp else 17.sp, fontWeight = if (ctL == "win95") FontWeight.Bold else FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight((1f - frac).coerceAtLeast(.0001f)).graphicsLayer { alpha = 1f - frac })
                if (search != null && frac > .01f) {
                    val fr = remember { androidx.compose.ui.focus.FocusRequester() }
                    LaunchedEffect(open) { if (open) runCatching { fr.requestFocus() } }
                    val onBar = bar != null
                    Row(Modifier.weight(frac.coerceAtLeast(.0001f)).padding(end = 8.dp).height(40.dp).clip(ctFieldShape()).background(if (onBar) Color.White else T.panel)
                        .then(if (T.material && !onBar) Modifier else Modifier.border(1.dp, T.line, ctFieldShape())).padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.foundation.text.BasicTextField(search.q, search.onQ, Modifier.weight(1f).focusRequester(fr), singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(color = if (onBar) Color.Black else T.text, fontSize = 16.sp), cursorBrush = SolidColor(ctAcc),
                            keyboardOptions = KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
                            keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { search.onGo() }),
                            decorationBox = { inner -> Box { if (search.q.isEmpty()) Text(ctr("Search", "Ara"), color = if (onBar) Color.Gray else T.sub, fontSize = 16.sp); inner() } })
                        if (search.q.isNotEmpty()) IconButton({ search.onQ("") }, Modifier.size(40.dp)) { CtGlyph(Icons.Outlined.Close, null, tint = if (onBar) Color.Gray else T.sub) }
                    }
                }
                if (frac < .01f) actions()
            }
            if (ctL == "win7" || ctL == "xp") Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF7F9DB9)))
            if (ctL == "win11") Box(Modifier.fillMaxWidth().height(1.dp).background(T.line))
        }
    }
}

private fun ctAvShape(): androidx.compose.ui.graphics.Shape = when (ctL) {
    "win95", "win10" -> RoundedCornerShape(0.dp); "xp" -> RoundedCornerShape(2.dp); "win7" -> RoundedCornerShape(4.dp); else -> CircleShape
}

@Composable
private fun CtAvatar(photo: String?, name: String, size: Dp, bitmap: Bitmap? = null) {
    val ctx = LocalContext.current
    val bmp by produceState(bitmap?.asImageBitmap(), photo, bitmap) {
        value = when {
            bitmap != null -> bitmap.asImageBitmap()
            photo == null -> null
            else -> withContext(Dispatchers.IO) {
                runCatching { ctx.contentResolver.openInputStream(Uri.parse(photo))?.use { BitmapFactory.decodeStream(it) }?.asImageBitmap() }.getOrNull()
            }
        }
    }
    val hue = (name.hashCode().and(0x7fffffff) % 360).toFloat()
    val sh = ctAvShape()
    val frame = if (ctL == "win7" || ctL == "xp") Modifier.border(1.dp, Color(0xFF7F9DB9), sh) else if (ctL == "win95") Modifier.border(1.dp, T.line) else Modifier
    Box(Modifier.size(size).clip(sh).background(Color.hsv(hue, .45f, if (T.dark) .55f else .8f)).then(frame), contentAlignment = Alignment.Center) {
        val b = bmp
        if (b != null) Image(b, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Text(name.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "?", color = Color.White, fontSize = (size.value * .42f).sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun CtInput(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier, kb: KeyboardType = KeyboardType.Text, single: Boolean = true, trailing: @Composable (() -> Unit)? = null) {
    // the label sits on the top border, left-aligned, and cuts the line behind it; the text starts a little lower inside the box
    var focused by remember { mutableStateOf(false) }
    var labelW by remember { mutableStateOf(0) }
    val border = if (focused) ctAcc else T.line
    val labelX = maxOf(12.dp, ctRadius + 4.dp)
    Box(modifier.padding(top = 8.dp)) {
        Box(Modifier.matchParentSize().drawBehind {
            val sw = (if (focused) 2.dp else 1.dp).toPx()
            val gapStart = labelX.toPx()
            clipRect(gapStart, -sw, gapStart + labelW, sw * 2, androidx.compose.ui.graphics.ClipOp.Difference) {
                drawRoundRect(border, topLeft = Offset(sw / 2, sw / 2), size = Size(size.width - sw, size.height - sw), cornerRadius = CornerRadius(ctRadius.toPx()), style = Stroke(sw))
            }
        })
        Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = if (trailing != null) 4.dp else 14.dp, top = 14.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.foundation.text.BasicTextField(
                value, onChange, Modifier.weight(1f).onFocusChanged { focused = it.isFocused },
                textStyle = androidx.compose.ui.text.TextStyle(color = T.text, fontSize = 16.sp),
                singleLine = single, cursorBrush = androidx.compose.ui.graphics.SolidColor(ctAcc),
                keyboardOptions = KeyboardOptions(keyboardType = kb),
            )
            trailing?.invoke()
        }
        Text(label, color = if (focused) ctAcc else T.sub, fontSize = 12.sp, maxLines = 1,
            modifier = Modifier.align(Alignment.TopStart).offset(labelX, (-8).dp).padding(horizontal = 4.dp).onSizeChanged { labelW = it.width })
    }
}

private fun Context.toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

// ------------------------------------------------------------------------------------------------------------ root

@Composable
fun CtRoot() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val perms = arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS)
    fun has() = perms.all { ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED }
    var ok by remember { mutableStateOf(has()) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { ok = has() }
    LaunchedEffect(Unit) { if (!ok) ask.launch(perms) }
    var list by remember { mutableStateOf<List<CtContact>>(emptyList()) }
    var groups by remember { mutableStateOf<List<CtGroup>>(emptyList()) }
    var members by remember { mutableStateOf<Map<Long, Set<Long>>>(emptyMap()) }
    var tick by remember { mutableStateOf(0) }
    LaunchedEffect(ok, tick) {
        if (ok) {
            list = withContext(Dispatchers.IO) { ctLoadAll(ctx) }
            val g = withContext(Dispatchers.IO) { ctLoadGroups(ctx) }
            groups = g.first; members = g.second
        }
    }
    val stack = remember { mutableStateListOf<CtScreen>() }
    fun push(s: CtScreen) { stack.add(s) }
    fun pop() { if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex) }
    BackHandler(stack.isNotEmpty()) { pop() }
    SideEffect { ctCover = stack.isNotEmpty() }
    LaunchedEffect(ctInUri, ok) {
        val u = ctInUri
        if (u != null && ok) {
            ctInUri = null
            val id = withContext(Dispatchers.IO) { ctIdOf(ctx, u) }
            if (id > 0) push(CtScreen.Detail(id))
        }
    }
    LaunchedEffect(ctInInsert) { ctInInsert?.let { ctInInsert = null; push(CtScreen.Edit(0, it)) } }

    var expIds by remember { mutableStateOf<Set<Long>?>(null) }
    val exportL = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/x-vcard")) { uri ->
        if (uri != null) scope.launch {
            val n = withContext(Dispatchers.IO) {
                val all = ctLoadDetails(ctx, expIds)
                runCatching { ctx.contentResolver.openOutputStream(uri)?.use { it.write(ctVcf(all).toByteArray()) }; all.size }.getOrDefault(-1)
            }
            ctx.toast(if (n >= 0) "$n" else ctr("Failed", "Başarısız"))
        }
    }
    val importL = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val n = withContext(Dispatchers.IO) {
                runCatching {
                    val text = ctx.contentResolver.openInputStream(uri)?.use { String(it.readBytes(), Charsets.UTF_8) } ?: ""
                    ctParseVcf(text).count { ctSave(ctx, it, null, false) }
                }.getOrDefault(-1)
            }
            ctx.toast(if (n >= 0) "$n" else ctr("Failed", "Başarısız")); tick++
        }
    }

    // Text / Icon without a colour read LocalContentColor, which is black outside a Surface: AMOLED / dark would show black on black
    CompositionLocalProvider(LocalContentColor provides T.text) { Box(Modifier.fillMaxSize().background(T.bg)) {
        CtMain(ok, { ask.launch(perms) }, list, groups, members,
            onOpen = { id ->
                val a = ctx as? android.app.Activity
                if (ctPick && a != null) { a.setResult(android.app.Activity.RESULT_OK, Intent().setData(android.content.ContentUris.withAppendedId(android.provider.ContactsContract.Contacts.CONTENT_URI, id)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)); a.finish() }
                else push(CtScreen.Detail(id))
            }, onAdd = { push(CtScreen.Edit(0, null)) }, onGroup = { push(CtScreen.Group(it)) },
            onSettings = { push(CtScreen.Settings) }, onDupes = { push(CtScreen.Dupes) },
            onImport = { importL.launch(arrayOf("text/x-vcard", "text/vcard", "text/plain", "*/*")) }, onExport = { expIds = null; exportL.launch("contacts.vcf") },
            onExportSel = { expIds = ctSel.toSet(); ctSel.clear(); exportL.launch("contacts.vcf") },
            onNewGroup = { t -> scope.launch { if (!withContext(Dispatchers.IO) { ctAddGroup(ctx, t) }) ctx.toast(ctr("Failed", "Başarısız")); tick++ } })
        val s = stack.lastOrNull()
        if (s != null) {
        val fa = remember(s) { androidx.compose.animation.core.Animatable(if (CtPrefs.fade) 0f else 1f) }
        LaunchedEffect(s) { fa.animateTo(1f, androidx.compose.animation.core.tween(180)) }
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = fa.value }.background(T.bg).pointerInput(Unit) { detectTapGestures { } }) {
            when (s) {
                is CtScreen.Detail -> CtDetailScreen(s.id, groups, onBack = { pop() }, onEdit = { push(CtScreen.Edit(it, null)) }, onCustom = { push(CtScreen.Custom(it)) }, onChanged = { tick++ })
                is CtScreen.Custom -> CtCustomScreen(s.id, onBack = { pop() }, onChanged = { tick++ })
                is CtScreen.Edit -> CtEditScreen(s.id, s.seed, onBack = { pop() }, onSaved = { tick++; pop() })
                is CtScreen.Group -> CtGroupScreen(groups.firstOrNull { it.id == s.id }, list.filter { it.id in (members[s.id] ?: emptySet()) },
                    onBack = { pop() }, onOpen = { push(CtScreen.Detail(it)) }, onDelete = { scope.launch { withContext(Dispatchers.IO) { ctDeleteGroup(ctx, s.id) }; tick++; pop() } })
                CtScreen.Settings -> CtSettings(onBack = { pop() },
                    onImport = { importL.launch(arrayOf("text/x-vcard", "text/vcard", "text/plain", "*/*")) }, onExport = { expIds = null; exportL.launch("contacts.vcf") })
                CtScreen.Dupes -> CtDupes(list, onBack = { pop() }, onMerged = { tick++ })
            }
        }
        }
    } }
}

// ------------------------------------------------------------------------------------------------------------ main

@Composable
private fun CtTabs(tab: Int, onTab: (Int) -> Unit) {
    val classic = ctL == "win95" || ctL == "xp" || ctL == "win7"
    Column(Modifier.fillMaxWidth().background(if (classic) T.bg else T.panel).navigationBarsPadding()) {
        Box(Modifier.fillMaxWidth().height(if (ctL == "win95") 2.dp else 1.dp).background(T.line))
        Row(Modifier.fillMaxWidth().height(48.dp)) {
            val keys = mapOf(3 to "gridView", 4 to "history", 0 to "person", 1 to "star", 2 to "users")
            listOf(3, 4, 0, 1, 2).forEach { i -> val k = keys[i]!!
                val sel = tab == i
                Box(Modifier.weight(1f).fillMaxHeight().clickable { onTab(i) }
                    .then(if (sel && ctL == "win95") Modifier.border(1.dp, T.text) else if (sel && ctL == "win11") Modifier.padding(6.dp).clip(RoundedCornerShape(8.dp)).background(ctAcc.copy(alpha = .16f)) else if (sel) Modifier.background(ctAcc.copy(alpha = .14f)) else Modifier),
                    contentAlignment = Alignment.Center) {
                    PIcon(if (sel && k == "star") "starOn" else k, if (sel) ctAcc else T.sub, if (ctL == "win95") 18.dp else 24.dp)
                    if (sel && (ctL == "win10" || ctL == "xp" || ctL == "win7")) Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().height(3.dp).background(ctAcc))
                }
            }
        }
    }
}

@Composable
private fun CtMain(
    ok: Boolean, onAsk: () -> Unit, list: List<CtContact>, groups: List<CtGroup>, members: Map<Long, Set<Long>>,
    onOpen: (Long) -> Unit, onAdd: () -> Unit, onGroup: (Long) -> Unit, onSettings: () -> Unit, onDupes: () -> Unit,
    onImport: () -> Unit, onExport: () -> Unit, onExportSel: () -> Unit, onNewGroup: (String) -> Unit,
) {
    BackHandler(ctSel.isNotEmpty()) { ctSel.clear() }
    var tab by remember { mutableStateOf(0) }
    var searching by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf(false) }
    var q by remember { mutableStateOf("") }
    LaunchedEffect(ctInDial) { if (ctInDial != null) { tab = 3; searching = false; results = false } }
    BackHandler(searching && !results && !ctCover) { searching = false; q = "" }
    BackHandler(results && !ctCover) { results = false }
    val canSearch = tab == 0 || tab == 1 || tab == 4
    LaunchedEffect(canSearch) { if (!canSearch) { searching = false; q = "" } }
    var menu by remember { mutableStateOf(false) }
    var newGroup by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
    Scaffold(containerColor = T.bg, contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (!T.material) CtTabs(tab) { tab = it }
            else NavigationBar(containerColor = T.panel) {
                listOf(3 to Icons.Outlined.Dialpad, 4 to Icons.Outlined.History, 0 to Icons.Outlined.Person, 1 to Icons.Outlined.StarOutline, 2 to Icons.Outlined.Groups).forEach { (i, ic) ->
                    NavigationBarItem(tab == i, { tab = i }, { CtGlyph(ic, null) }, colors = NavigationBarItemDefaults.colors(selectedIconColor = onAccent(ctAcc), indicatorColor = ctAcc, unselectedIconColor = T.sub))
                }
            }
        },
        floatingActionButton = {
            if (tab < 3) FloatingActionButton({ if (tab == 2) newGroup = true else onAdd() }, containerColor = fabFill(), contentColor = fabMark(), shape = RoundedCornerShape(if (T.material) 18.dp else T.radius)) {
                CtGlyph(if (tab == 2) Icons.Outlined.GroupAdd else Icons.Outlined.Add, null)
            }
        }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (ctSel.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().statusBarsPadding().height(56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    CtIcon(Icons.Outlined.Close) { ctSel.clear() }
                    Text("${ctSel.size}", color = T.text, fontSize = 18.sp, modifier = Modifier.weight(1f))
                    CtIcon(Icons.Outlined.FileUpload) { onExportSel() }
                }
            } else CtTopBar(listOf(ctr("Contacts", "Kişiler"), ctr("Favourites", "Favoriler"), ctr("Groups", "Gruplar"), ctr("Keypad", "Tuş takımı"), ctr("Recents", "Son aramalar"))[tab], null, search = CtSearch(canSearch, searching, q, { q = it }, { searching = it; if (!it) q = "" }, { if (q.isNotBlank()) results = true })) {
                Box {
                    CtIcon(Icons.Outlined.MoreVert) { menu = true }
                    DropdownMenu(menu, { menu = false }, containerColor = T.panel) {
                        DropdownMenuItem({ Text(ctr("Import .vcf", "İçe aktar .vcf"), color = T.text) }, { menu = false; onImport() }, leadingIcon = { CtGlyph(Icons.Outlined.FileDownload, null, tint = T.sub) })
                        DropdownMenuItem({ Text(ctr("Export .vcf", "Dışa aktar .vcf"), color = T.text) }, { menu = false; onExport() }, leadingIcon = { CtGlyph(Icons.Outlined.FileUpload, null, tint = T.sub) })
                        DropdownMenuItem({ Text(ctr("Duplicates", "Yinelenenler"), color = T.text) }, { menu = false; onDupes() }, leadingIcon = { CtGlyph(Icons.Outlined.MergeType, null, tint = T.sub) })
                        DropdownMenuItem({ Text(ctr("Settings", "Ayarlar"), color = T.text) }, { menu = false; onSettings() }, leadingIcon = { CtGlyph(Icons.Outlined.Settings, null, tint = T.sub) })
                    }
                }
            }
            if (!ok) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                FilledTonalIconButton(onAsk, Modifier.size(72.dp)) { CtGlyph(Icons.Outlined.Contacts, null, Modifier.size(32.dp)) }
            } else when (tab) {
                0 -> {
                    CtList(list.filter { ctMatch(it, q) }, onOpen, true)
                }
                1 -> CtList(list.filter { it.starred && ctMatch(it, q) }, onOpen, false)
                3 -> CtKeypad(list, onOpen)
                4 -> CtRecents(list, onOpen, q)
                else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 88.dp)) {
                    items(groups, key = { it.id }) { g ->
                        Row(Modifier.fillMaxWidth().clickable { onGroup(g.id) }.padding(16.dp, 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(44.dp).clip(CircleShape).background(ctAcc.copy(alpha = .2f)), contentAlignment = Alignment.Center) { CtGlyph(Icons.Outlined.Group, null, tint = ctAcc) }
                            Spacer(Modifier.width(16.dp))
                            Text(g.title, color = T.text, fontSize = 16.sp, modifier = Modifier.weight(1f))
                            Text("${members[g.id]?.size ?: 0}", color = T.sub, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }
    if (results) CtResults(q, tab, list, onOpen, { results = false })
    }
    if (newGroup) {
        var t by remember { mutableStateOf("") }
        AlertDialog({ newGroup = false }, containerColor = T.panel, shape = RoundedCornerShape(ctRadius),
            text = { CtInput(t, { t = it }, ctr("Group", "Grup")) },
            confirmButton = { IconButton({ if (t.isNotBlank()) onNewGroup(t.trim()); newGroup = false }) { CtGlyph(Icons.Outlined.Check, null, tint = ctAcc) } },
            dismissButton = { IconButton({ newGroup = false }) { CtGlyph(Icons.Outlined.Close, null, tint = T.sub) } })
    }
}

/** Name parts, numbers (digits only) and e-mail. */
private fun ctMatch(c: CtContact, q: String): Boolean {
    val t = q.trim()
    if (t.isEmpty()) return true
    val d = t.filter(Char::isDigit)
    return c.display.contains(t, true) || c.label().contains(t, true) || c.given.contains(t, true) || c.family.contains(t, true) || c.emails.contains(t, true) ||
        (d.isNotEmpty() && d.length >= t.count { it.isLetterOrDigit() } && c.nums.filter(Char::isDigit).contains(d))
}

private fun ctHl(text: String, q: String): androidx.compose.ui.text.AnnotatedString = androidx.compose.ui.text.buildAnnotatedString {
    val t = q.trim(); val i = if (t.isEmpty()) -1 else text.indexOf(t, ignoreCase = true)
    if (i < 0) { append(text); return@buildAnnotatedString }
    append(text.substring(0, i))
    withStyle(androidx.compose.ui.text.SpanStyle(background = ctAcc.copy(alpha = .35f), fontWeight = FontWeight.SemiBold)) { append(text.substring(i, i + t.length)) }
    append(text.substring(i + t.length))
}

/** Full results page: every match with the match highlighted, call / message buttons. Recents tab searches the call log. */
@Composable
private fun CtResults(q: String, tab: Int, all: List<CtContact>, onOpen: (Long) -> Unit, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val call = rememberCtCaller()
    var calls by remember { mutableStateOf<List<CtCall>>(emptyList()) }
    LaunchedEffect(tab) { if (tab == 4) calls = withContext(Dispatchers.IO) { runCatching { ctLoadCalls(ctx) }.getOrDefault(emptyList()) } }
    class Hit(val id: Long?, val title: String, val sub: String, val num: String, val photo: String?)
    val hits = remember(q, tab, all, calls) {
        if (tab == 4) {
            val d = q.filter(Char::isDigit)
            calls.filter { (it.name ?: "").contains(q.trim(), true) || (d.isNotEmpty() && it.number.filter(Char::isDigit).contains(d)) }.map { c ->
                val k = all.firstOrNull { x -> x.nums.split('\n').any { p -> p.filter(Char::isDigit).takeLast(8).let { it.length >= 4 && it == c.number.filter(Char::isDigit).takeLast(8) } } }
                Hit(k?.id, k?.label() ?: c.name ?: c.number, c.number, c.number, k?.photo)
            }
        } else all.filter { (tab != 1 || it.starred) && ctMatch(it, q) }.map { c ->
            val d = q.filter(Char::isDigit)
            val sub = c.emails.lines().firstOrNull { it.contains(q.trim(), true) } ?: c.nums.lines().firstOrNull { d.isNotEmpty() && it.filter(Char::isDigit).contains(d) } ?: c.nums.substringBefore('\n')
            Hit(c.id, c.label(), sub, c.nums.substringBefore('\n').trim(), c.photo)
        }
    }
    Column(Modifier.fillMaxSize().background(T.bg).pointerInput(Unit) { detectTapGestures { } }) {
        CtTopBar(q, onBack)
        if (hits.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CtGlyph(Icons.Outlined.PersonOff, null, Modifier.size(48.dp), tint = T.sub) }
        else LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            items(hits.size) { i ->
                val h = hits[i]
                Row(Modifier.fillMaxWidth().clickable { h.id?.let(onOpen) }.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    CtAvatar(h.photo, h.title, 40.dp)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(ctHl(h.title, q), color = T.text, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (h.sub.isNotBlank()) Text(ctHl(h.sub, q), color = T.sub, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (h.num.isNotBlank()) {
                        CtIcon(Icons.Outlined.Sms, T.sub) { runCatching { ctx.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(h.num))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
                        CtIcon(Icons.Outlined.Call, ctAcc) { call(h.num) }
                    }
                }
            }
        }
    }
}

private sealed interface CtRow { class H(val l: String) : CtRow; class C(val c: CtContact) : CtRow }

private val ctMsnBlue = Color(0xFF1E3C78)

/** Messenger-style buddy list (XP look): blue header with own tile + status, collapsible Favorites / Online / Offline. Status is only a decoration. */
@Composable
private fun CtMsnList(contacts: List<CtContact>, onOpen: (Long) -> Unit) {
    val sorted = remember(contacts, CtPrefs.sortBy, CtPrefs.firstFirst) { ctSorted(contacts) }
    fun st(c: CtContact) = (c.id % 4).toInt()
    val sections = listOf(
        ctr("Favorites", "Favoriler") to sorted.filter { it.starred },
        ctr("Online", "Çevrimiçi") to sorted.filter { !it.starred && st(it) < 3 },
        ctr("Offline", "Çevrimdışı") to sorted.filter { !it.starred && st(it) == 3 })
    val closed = remember { mutableStateListOf<String>() }
    val dot = listOf(Color(0xFF3FB950), Color(0xFFE5484D), Color(0xFFF0A30A), Color(0xFF9AA0A6))
    val word = listOf("", ctr(" (Busy)", " (Meşgul)"), ctr(" (Away)", " (Uzakta)"), "")
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 88.dp)) {
        item {
            Row(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color(0xFFEAF3FD), Color(0xFFC9DDF5)))).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp).clip(RoundedCornerShape(3.dp)).border(2.dp, Color(0xFF7F9DB9), RoundedCornerShape(3.dp)).background(Brush.verticalGradient(listOf(Color(0xFF8DBBF0), Color(0xFF2C62BC)))), contentAlignment = Alignment.Center) {
                    PIcon("users", Color.White, 28.dp)
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(ctr("Contacts", "Kişiler"), color = ctMsnBlue, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(9.dp).clip(CircleShape).background(dot[0]))
                        Text(" " + ctr("Available", "Müsait") + "  ${contacts.size}", color = ctMsnBlue.copy(alpha = .75f), fontSize = 12.sp)
                    }
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF7F9DB9)))
        }
        sections.forEach { (name, list) ->
            if (list.isEmpty()) return@forEach
            val open = name !in closed
            item(key = "h$name") {
                Row(Modifier.fillMaxWidth().clickable { if (open) closed.add(name) else closed.remove(name) }.padding(start = 10.dp, top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (open) "\u25BE " else "\u25B8 ", color = ctMsnBlue, fontSize = 13.sp)
                    Text("$name (${list.size})", color = ctMsnBlue, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
            if (open) items(list, key = { "$name${it.id}" }) { c ->
                val s = if (c.starred) c.id.toInt().and(1) * 2 else st(c)
                Row(Modifier.fillMaxWidth().clickable { onOpen(c.id) }.padding(start = 22.dp, end = 12.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(38.dp)) {
                        CtAvatar(c.photo, c.display, 34.dp)
                        Box(Modifier.align(Alignment.BottomEnd).size(12.dp).clip(CircleShape).background(Color.White).padding(2.dp).clip(CircleShape).background(dot[s]))
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(c.label(), color = if (s == 3) T.sub else T.text, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, false))
                    Text(word[s], color = T.sub, fontSize = 12.sp, maxLines = 1)
                    Spacer(Modifier.weight(1f))
                    if (c.starred) CtGlyph(Icons.Outlined.Star, null, Modifier.size(14.dp), tint = dot[2])
                }
            }
        }
    }
}

@Composable
private fun CtList(contacts: List<CtContact>, onOpen: (Long) -> Unit, letters: Boolean) {
    val ctx = LocalContext.current
    val call = rememberCtCaller()
    val rows = remember(contacts, CtPrefs.sortBy, CtPrefs.firstFirst) {
        val out = ArrayList<CtRow>(); var last = ""
        for (c in ctSorted(contacts)) { val l = ctLetter(c); if (letters && l != last) { out.add(CtRow.H(l)); last = l }; out.add(CtRow.C(c)) }
        out
    }
    if (rows.isEmpty()) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CtGlyph(Icons.Outlined.PersonOff, null, Modifier.size(48.dp), tint = T.sub) }; return }
    if (ctL == "xp" && letters) { CtMsnList(contacts, onOpen); return }
    val st = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val heads = rows.mapIndexedNotNull { i, r -> if (r is CtRow.H) r.l to i else null }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), st, contentPadding = PaddingValues(bottom = 88.dp)) {
            if (ctL == "win95" && letters) item {
                Row(Modifier.fillMaxWidth().background(T.bg).border(1.dp, T.line).padding(horizontal = 8.dp, vertical = 3.dp)) {
                    Text(ctr("Name", "Ad"), color = T.text, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text("${contacts.size}", color = T.sub, fontSize = 12.sp)
                }
            }
            items(rows.size) { i ->
                val l = ctL
                when (val r = rows[i]) {
                    is CtRow.H -> when (l) {
                        "win10" -> Box(Modifier.padding(start = 16.dp, top = 10.dp, bottom = 4.dp).size(32.dp).background(ctAcc), contentAlignment = Alignment.Center) { Text(r.l, color = onAccent(ctAcc), fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
                        "win7" -> Text(r.l, color = Color(0xFF1E395B), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color(0xFFF2F8FE), Color(0xFFD5E4F5)))).padding(start = 16.dp, top = 3.dp, bottom = 3.dp))
                        "win95" -> Text(r.l, color = T.text, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth().background(T.bg).padding(start = 8.dp, top = 1.dp, bottom = 1.dp))
                        else -> Text(r.l, color = ctAcc, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 24.dp, top = 12.dp, bottom = 4.dp))
                    }
                    is CtRow.C -> {
                        val small = l == "win95"
                        val deco = if (l == "win11") Modifier.padding(horizontal = 8.dp, vertical = 1.dp).clip(RoundedCornerShape(8.dp)).background(T.panel) else Modifier
                        Row(Modifier.fillMaxWidth().then(deco).then(if (r.c.id in ctSel) Modifier.background(ctAcc.copy(alpha = .18f)) else Modifier)
                            .pointerInput(r.c.id) { detectTapGestures(onLongPress = { ctToggle(r.c.id) }, onTap = { if (ctSel.isNotEmpty()) ctToggle(r.c.id) else onOpen(r.c.id) }) }
                            .pointerInput(r.c.id) {
                                // swipe right = call, left = message
                                var dx = 0f
                                detectHorizontalDragGestures(onDragStart = { dx = 0f }, onDragEnd = {
                                    val n = r.c.nums.substringBefore('\n').trim()
                                    if (n.isNotEmpty() && dx > 160f) call(n)
                                    else if (n.isNotEmpty() && dx < -160f) runCatching { ctx.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(n))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                                }) { _, d -> dx += d }
                            }.padding(start = if (small) 8.dp else 16.dp, end = if (letters) 28.dp else 16.dp, top = if (small) 2.dp else 8.dp, bottom = if (small) 2.dp else 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            CtAvatar(r.c.photo, r.c.display, if (small) 20.dp else 44.dp)
                            Spacer(Modifier.width(if (small) 8.dp else 16.dp))
                            Text(r.c.label(), color = T.text, fontSize = if (small) 13.sp else 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            if (r.c.starred && letters) CtGlyph(Icons.Outlined.Star, null, Modifier.size(16.dp), tint = ctAcc)
                        }
                    }
                }
            }
        }
        if (letters && heads.size > 1) {
            var h by remember { mutableStateOf(1) }
            fun pick(y: Float) { val k = ((y / h) * heads.size).toInt().coerceIn(0, heads.size - 1); scope.launch { st.scrollToItem(heads[k].second) } }
            Column(Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(vertical = 8.dp).width(26.dp).onSizeChanged { h = it.height.coerceAtLeast(1) }
                .pointerInput(heads) { detectTapGestures { pick(it.y) } }
                .pointerInput(heads) { detectVerticalDragGestures(onDragStart = { pick(it.y) }) { c, _ -> pick(c.position.y) } },
                verticalArrangement = Arrangement.SpaceEvenly, horizontalAlignment = Alignment.CenterHorizontally) {
                heads.forEach { Text(it.first, color = T.sub, fontSize = 10.sp) }
            }
        }
    }
}

// ------------------------------------------------------------------------------------------------------------ detail

@Composable
private fun CtDetailScreen(id: Long, groups: List<CtGroup>, onBack: () -> Unit, onEdit: (Long) -> Unit, onCustom: (Long) -> Unit, onChanged: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var rev by remember { mutableStateOf(0) }
    var d by remember { mutableStateOf<CtDetail?>(null) }
    LaunchedEffect(id, rev) { d = withContext(Dispatchers.IO) { ctLoadDetails(ctx, setOf(id)).firstOrNull() } }
    var pickGroup by remember { mutableStateOf(false) }
    val binAngle = remember { androidx.compose.animation.core.Animatable(0f) }
    var deleting by remember { mutableStateOf(false) }
    fun go(i: Intent) { runCatching { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
    val c = d
    if (c == null) { CtTopBar("", onBack); return }
    val name = CtContact(c.id, c.display, c.given, c.family, c.starred, c.photo, "", c.prefix, c.middle, c.suffix).label()
    val bg = remember(c.lookup, rev) { ctBgAll(ctx)[c.lookup] }
    val st = remember(c.lookup, rev) { ctStyleGet(ctx, c.lookup) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding()) {
        CtTopBar("", onBack) {
            // 40 dp targets: six icons plus the back button fit a 360 dp phone with room to spare, the bin stays on the right
            val bs = Modifier.size(40.dp)
            CtIcon(if (c.starred) Icons.Outlined.Star else Icons.Outlined.StarOutline, if (c.starred) ctAcc else T.text, bs) { scope.launch { withContext(Dispatchers.IO) { ctSetStar(ctx, id, !c.starred) }; rev++; onChanged() } }
            CtIcon(Icons.Outlined.Info, modifier = bs) { go(Intent(Intent.ACTION_VIEW, android.content.ContentUris.withAppendedId(android.provider.ContactsContract.Contacts.CONTENT_URI, id))) }
            CtIcon(Icons.Outlined.Tune, modifier = bs) { onCustom(id) }
            CtIcon(Icons.Outlined.Edit, modifier = bs) { onEdit(id) }
            CtIcon(Icons.Outlined.Share, modifier = bs) {
                go(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/x-vcard").putExtra(Intent.EXTRA_TEXT, ctVcf(listOf(c))), null))
            }
            CtBinButton(binAngle.value) {
                if (!deleting) {
                    deleting = true
                    scope.launch {
                        binAngle.animateTo(-25f, androidx.compose.animation.core.tween(250))
                        withContext(Dispatchers.IO) { ctDelete(ctx, id) }
                        onChanged(); onBack()
                    }
                }
            }
        }
        val l = ctL
        val fill = when (l) {
            "xp" -> Modifier.background(Brush.verticalGradient(listOf(Color(0xFFD6E6FA), Color.White)))
            "win7" -> Modifier.background(Brush.verticalGradient(listOf(Color(0xFFCFE3F7), Color(0xFFEEF5FC))))
            "win10" -> Modifier.background(ctAcc)
            "win95" -> Modifier.border(2.dp, T.line)
            "win11" -> Modifier.padding(12.dp, 4.dp).clip(RoundedCornerShape(12.dp)).background(T.panel)
            else -> Modifier
        }
        val hf = if (bg != null) Color.White else if (l == "win10") onAccent(ctAcc) else if (l == "xp" || l == "win7") ctMsnBlue else T.text
        // no call background: the contact photo, blurred and darkened, is the hero
        val heroPhoto by produceState<Bitmap?>(null, c.photo, bg) { value = if (bg == null && c.photo != null) withContext(Dispatchers.IO) { runCatching { ctPhotoBitmap(ctx, Uri.parse(c.photo)) }.getOrNull() } else null }
        val hero = bg != null || heroPhoto != null
        Box(Modifier.fillMaxWidth()) {
        if (bg != null) { CtBgView(bg, Modifier.matchParentSize()); Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = .35f))) }
        else heroPhoto?.let { Box(Modifier.matchParentSize().clipToBounds()) { Image(it.asImageBitmap(), null, Modifier.fillMaxSize().blur(24.dp), contentScale = ContentScale.Crop) }; Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = .5f))) }
        Column(Modifier.fillMaxWidth().then(if (hero) Modifier else fill).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            CtAvatar(c.photo, name, if (l == "win95") 72.dp else 112.dp)
            Spacer(Modifier.height(12.dp))
            Text(name, color = if (st.color != 0) Color(st.color) else if (hero) Color.White else hf, fontSize = if (st.size > 0) st.size.sp else if (l == "win95") 18.sp else 24.sp,
                fontWeight = if (st.bold) FontWeight.Bold else FontWeight.SemiBold, fontStyle = if (st.italic) androidx.compose.ui.text.font.FontStyle.Italic else null, fontFamily = ctFont(st.fam))
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val p = c.phones.firstOrNull(); val m = c.emails.firstOrNull()
                if (p != null) {
                    CtAction(Icons.Outlined.Call) { go(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(p.value)))) }
                    CtAction(Icons.Outlined.Sms) { go(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(p.value)))) }
                }
                if (m != null) CtAction(Icons.Outlined.Email) { go(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + m.value))) }
                if (groups.isNotEmpty() && c.raws.isNotEmpty()) CtAction(Icons.Outlined.Group) { pickGroup = true }
            }
        }
        }
        CtBgCard(bg, c.lookup, id, onCustom, go = ::go)
        if (c.nick.isNotBlank()) CtInfo(Icons.Outlined.Badge, c.nick, null, null)
        c.phones.forEach { p ->
            CtInfo(Icons.Outlined.Call, p.value, ctTypeLabel(0, p.type), { go(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(p.value)))) }) {
                CtIcon(Icons.Outlined.Sms, ctAcc) { go(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(p.value)))) }
            }
        }
        c.emails.forEach { e -> CtInfo(Icons.Outlined.Email, e.value, ctTypeLabel(1, e.type), { go(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + e.value))) }) }
        c.addrs.forEach { a -> CtInfo(Icons.Outlined.Place, a.value, ctTypeLabel(2, a.type), { go(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(a.value)))) }) }
        if (c.birthday.isNotBlank()) CtInfo(Icons.Outlined.Cake, c.birthday, null, null)
        if (c.note.isNotBlank()) CtInfo(Icons.Outlined.EditNote, c.note, null, null)
        val gs = groups.filter { it.id in c.groups }
        if (gs.isNotEmpty()) CtInfo(Icons.Outlined.Group, gs.joinToString(", ") { it.title }, null, null)
    }
    if (pickGroup) AlertDialog({ pickGroup = false }, containerColor = T.panel, shape = RoundedCornerShape(ctRadius),
        text = {
            Column {
                groups.forEach { g ->
                    val on = g.id in c.groups
                    Row(Modifier.fillMaxWidth().clickable {
                        scope.launch { withContext(Dispatchers.IO) { ctSetGroup(ctx, c.raws[0], g.id, !on) }; rev++; onChanged() }
                    }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        PCheck(on, { scope.launch { withContext(Dispatchers.IO) { ctSetGroup(ctx, c.raws[0], g.id, it) }; rev++; onChanged() } })
                        Text(g.title, color = T.text)
                    }
                }
            }
        }, confirmButton = { IconButton({ pickGroup = false }) { CtGlyph(Icons.Outlined.Check, null, tint = ctAcc) } })
}

/** Preview of the call background with share / save / open-in-Samsung. Samsung and Google keep theirs in private storage: no import, no direct send. */
@Composable
private fun CtBgCard(bg: CtBg?, lookup: String, id: Long, onCustom: (Long) -> Unit, go: (Intent) -> Unit) {
    val ctx = LocalContext.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CtBgThumb(bg, 56.dp, Modifier.clickable { onCustom(id) })
            Spacer(Modifier.width(12.dp))
            Text(ctr("Call background", "Arama arka planı"), color = T.text, fontSize = 15.sp, modifier = Modifier.weight(1f))
            if (bg != null) {
                CtIcon(Icons.Outlined.Share, ctAcc) {
                    val u = ctBgUri(ctx, bg); val mime = if (bg.video) "video/*" else "image/*"
                    go(Intent.createChooser(Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, u).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION).also { it.clipData = android.content.ClipData.newRawUri("", u) }, null))
                }
                CtIcon(Icons.Outlined.SaveAlt, ctAcc) { ctx.toast(if (ctBgToGallery(ctx, bg)) ctr("Saved to gallery", "Galeriye kaydedildi") else ctr("Failed", "Başarısız")) }
            }
            CtIcon(Icons.Outlined.OpenInNew, T.sub) {
                val uri = android.content.ContentUris.withAppendedId(android.provider.ContactsContract.Contacts.CONTENT_URI, id)
                val ok = listOf("com.samsung.android.app.contacts", "com.samsung.android.dialer", "com.samsung.contacts").any { pkg ->
                    runCatching { ctx.startActivity(Intent(Intent.ACTION_EDIT).setDataAndType(uri, android.provider.ContactsContract.Contacts.CONTENT_ITEM_TYPE).setPackage(pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
                }
                if (!ok) ctx.toast(ctr("Samsung Contacts not found", "Samsung Kişiler yok"))
            }
        }
    }
}

@Composable
private fun CtAction(icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    val w10 = ctL == "win10"
    Box(Modifier.size(56.dp).clip(RoundedCornerShape(ctRadius + 4.dp)).background(if (w10) Color.White.copy(alpha = .25f) else ctAcc.copy(alpha = .16f)).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        CtGlyph(icon, null, tint = if (w10) onAccent(ctAcc) else ctAcc)
    }
}

@Composable
private fun CtInfo(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, sub: String?, onClick: (() -> Unit)?, trailing: @Composable (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        CtGlyph(icon, null, tint = T.sub)
        Spacer(Modifier.width(24.dp))
        Column(Modifier.weight(1f)) {
            Text(text, color = T.text, fontSize = 16.sp)
            if (sub != null) Text(sub, color = T.sub, fontSize = 13.sp)
        }
        trailing?.invoke()
    }
}

// ------------------------------------------------------------------------------------------------------------ edit

@Composable
private fun CtMulti(kind: Int, icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, items: SnapshotStateList<CtField>) {
    val kb = when (kind) { 0 -> KeyboardType.Phone; 1 -> KeyboardType.Email; else -> KeyboardType.Text }
    items.forEachIndexed { i, f ->
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            CtGlyph(icon, null, tint = if (i == 0) T.sub else Color.Transparent)
            Spacer(Modifier.width(12.dp))
            CtInput(f.value, { items[i] = f.copy(value = it) }, label, Modifier.weight(1f), kb, single = kind != 2)
            Spacer(Modifier.width(6.dp))
            Box(Modifier.clip(RoundedCornerShape(ctRadius)).border(1.dp, T.line, RoundedCornerShape(ctRadius)).clickable {
                val ts = ctTypes(kind); items[i] = f.copy(type = ts[(ts.indexOf(f.type) + 1) % ts.size])
            }.padding(8.dp, 6.dp)) { Text(ctTypeLabel(kind, f.type), color = T.text, fontSize = 12.sp) }
            IconButton({ items.removeAt(i) }) { CtGlyph(Icons.Outlined.Close, null, tint = T.sub) }
        }
    }
    TextButton({ items.add(CtField(ctTypes(kind)[0], "")) }) { CtGlyph(Icons.Outlined.Add, null, tint = ctAcc); Spacer(Modifier.width(4.dp)); Text(label, color = ctAcc) }
}

@Composable
private fun CtEditScreen(id: Long, seed: CtDetail?, onBack: () -> Unit, onSaved: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var base by remember { mutableStateOf<CtDetail?>(seed) }
    var ready by remember { mutableStateOf(id == 0L) }
    LaunchedEffect(id) { if (id != 0L) { base = withContext(Dispatchers.IO) { ctLoadDetails(ctx, setOf(id)).firstOrNull() }; ready = true } }
    if (!ready) { CtTopBar("", onBack); return }
    val b = base
    var given by remember { mutableStateOf(b?.given ?: "") }
    var family by remember { mutableStateOf(b?.family ?: "") }
    var prefix by remember { mutableStateOf(b?.prefix ?: "") }
    var middle by remember { mutableStateOf(b?.middle ?: "") }
    var suffix by remember { mutableStateOf(b?.suffix ?: "") }
    var nick by remember { mutableStateOf(b?.nick ?: "") }
    var org by remember { mutableStateOf(b?.org ?: "") }
    var bday by remember { mutableStateOf(b?.birthday ?: "") }
    var note by remember { mutableStateOf(b?.note ?: "") }
    val phones = remember { mutableStateListOf<CtField>().apply { addAll(b?.phones ?: listOf(CtField(2, ""))) } }
    val emails = remember { mutableStateListOf<CtField>().apply { addAll(b?.emails ?: emptyList()) } }
    val addrs = remember { mutableStateListOf<CtField>().apply { addAll(b?.addrs ?: emptyList()) } }
    var newPhoto by remember { mutableStateOf<Bitmap?>(null) }
    var clearPhoto by remember { mutableStateOf(false) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { u ->
        if (u != null) scope.launch { newPhoto = withContext(Dispatchers.IO) { ctPhotoBitmap(ctx, u) }; clearPhoto = false }
    }
    val can = given.isNotBlank() || family.isNotBlank() || prefix.isNotBlank() || middle.isNotBlank() || suffix.isNotBlank() || phones.any { it.value.isNotBlank() } || emails.any { it.value.isNotBlank() }
    Column(Modifier.fillMaxSize().imePadding()) {
        CtTopBar(if (id == 0L) ctr("New", "Yeni") else ctr("Edit", "Düzenle"), onBack) {
            IconButton({
                val d = CtDetail(id, b?.raws ?: emptyList(), "", given.trim(), family.trim(), org.trim(), b?.starred ?: false, null,
                    phones.toList(), emails.toList(), addrs.toList(), bday.trim(), note.trim(), emptySet(), prefix.trim(), middle.trim(), suffix.trim(), nick.trim())
                scope.launch {
                    val ok = withContext(Dispatchers.IO) { ctSave(ctx, d, newPhoto?.let { ctJpeg(it) }, clearPhoto) }
                    if (ok) onSaved() else ctx.toast(ctr("Failed", "Başarısız"))
                }
            }, enabled = can) { CtGlyph(Icons.Outlined.Check, null, tint = if (can) ctAcc else T.sub) }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.clickable { pick.launch("image/*") }) {
                    CtAvatar(if (clearPhoto) null else b?.photo, (given + family).ifBlank { "+" }, 96.dp, newPhoto)
                    Box(Modifier.align(Alignment.BottomEnd).size(30.dp).clip(CircleShape).background(ctAcc), contentAlignment = Alignment.Center) { CtGlyph(Icons.Outlined.PhotoCamera, null, Modifier.size(16.dp), tint = onAccent(ctAcc)) }
                }
                if (newPhoto != null || (b?.photo != null && !clearPhoto)) IconButton({ newPhoto = null; clearPhoto = true }) { CtGlyph(Icons.Outlined.Delete, null, tint = T.sub) }
            }
            // an extra name field shows when its switch is on or the contact already has a value
            // name fields: one framed box each, label above, same as the phone field
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (CtPrefs.showPrefix || !b?.prefix.isNullOrBlank()) CtInput(prefix, { prefix = it }, ctr("Name prefix", "Ön ek"), Modifier.fillMaxWidth())
                CtInput(given, { given = it }, ctr("First name", "Ad"), Modifier.fillMaxWidth())
                if (CtPrefs.showMiddle || !b?.middle.isNullOrBlank()) CtInput(middle, { middle = it }, ctr("Middle name", "İkinci ad"), Modifier.fillMaxWidth())
                CtInput(family, { family = it }, ctr("Surname", "Soyad"), Modifier.fillMaxWidth())
                if (CtPrefs.showSuffix || !b?.suffix.isNullOrBlank()) CtInput(suffix, { suffix = it }, ctr("Name suffix", "Son ek"), Modifier.fillMaxWidth())
            }
            if (CtPrefs.showNick || !b?.nick.isNullOrBlank()) CtInput(nick, { nick = it }, ctr("Nickname", "Takma ad"), Modifier.fillMaxWidth())
            if (!b?.lookup.isNullOrBlank()) CtBgRow(b!!.lookup)
            CtMulti(0, Icons.Outlined.Call, ctr("Phone", "Telefon"), phones)
            CtMulti(1, Icons.Outlined.Email, ctr("Email", "E-posta"), emails)
            CtMulti(2, Icons.Outlined.Place, ctr("Address", "Adres"), addrs)
            CtInput(bday, { bday = it }, ctr("Birthday", "Doğum günü"), Modifier.fillMaxWidth(), trailing = {
                IconButton({
                    val p = bday.split('-').mapNotNull { it.toIntOrNull() }
                    val (y, m, dd) = if (p.size == 3) Triple(p[0], p[1] - 1, p[2]) else Triple(1990, 0, 1)
                    DatePickerDialog(ctx, { _, yy, mm, d2 -> bday = "%04d-%02d-%02d".format(yy, mm + 1, d2) }, y, m, dd).show()
                }) { CtGlyph(Icons.Outlined.Cake, null, tint = ctAcc) }
            })
            CtInput(note, { note = it }, ctr("Notes", "Not"), Modifier.fillMaxWidth(), single = false)
            Spacer(Modifier.height(24.dp))
        }
    }
}

// ------------------------------------------------------------------------------------------------------------ call background

/** Still image or looping muted video, scaled to fill (centre crop). */
@Composable
private fun CtBgView(bg: CtBg, modifier: Modifier) {
    val ctx = LocalContext.current
    if (!bg.video) {
        val img by produceState<Bitmap?>(null, bg.path) { value = withContext(Dispatchers.IO) { ctBgThumb(ctx, bg) } }
        Box(modifier.background(Color.Black)) { img?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) } }
        return
    }
    val player = remember(bg.path) { arrayOfNulls<android.media.MediaPlayer>(1) }
    DisposableEffect(bg.path) { onDispose { runCatching { player[0]?.release() }; player[0] = null } }
    AndroidView({ c ->
        android.view.TextureView(c).apply {
            fun fit(mp: android.media.MediaPlayer, w: Int, h: Int) {
                if (mp.videoWidth <= 0 || mp.videoHeight <= 0 || w <= 0 || h <= 0) return
                val va = mp.videoWidth.toFloat() / mp.videoHeight; val ta = w.toFloat() / h
                setTransform(android.graphics.Matrix().apply { setScale(if (va > ta) va / ta else 1f, if (va > ta) 1f else ta / va, w / 2f, h / 2f) })
            }
            surfaceTextureListener = object : android.view.TextureView.SurfaceTextureListener {
                override fun onSurfaceTextureAvailable(st: android.graphics.SurfaceTexture, w: Int, h: Int) {
                    runCatching {
                        val mp = android.media.MediaPlayer()
                        player[0] = mp
                        mp.setDataSource(bg.path); mp.setSurface(android.view.Surface(st)); mp.isLooping = true; mp.setVolume(0f, 0f)
                        mp.setOnPreparedListener { fit(it, width, height); it.start() }
                        mp.prepareAsync()
                    }
                }
                override fun onSurfaceTextureSizeChanged(st: android.graphics.SurfaceTexture, w: Int, h: Int) { player[0]?.let { fit(it, w, h) } }
                override fun onSurfaceTextureDestroyed(st: android.graphics.SurfaceTexture): Boolean { runCatching { player[0]?.release() }; player[0] = null; return true }
                override fun onSurfaceTextureUpdated(st: android.graphics.SurfaceTexture) {}
            }
        }
    }, modifier.background(Color.Black))
}

@Composable
private fun CtBgThumb(bg: CtBg?, size: Dp, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val img by produceState<Bitmap?>(null, bg?.path) { value = bg?.let { withContext(Dispatchers.IO) { ctBgThumb(ctx, it) } } }
    Box(modifier.size(size).clip(RoundedCornerShape(ctRadius)).background(T.panel).border(1.dp, T.line, RoundedCornerShape(ctRadius)), contentAlignment = Alignment.Center) {
        val b = img
        if (b != null) Image(b.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) else CtGlyph(Icons.Outlined.Wallpaper, null, tint = T.sub)
        if (bg?.video == true) CtGlyph(Icons.Outlined.Videocam, null, Modifier.align(Alignment.BottomEnd).padding(2.dp).size(18.dp), tint = Color.White)
    }
}

/** Call background of one contact: preview, pick a photo or video, remove. Saved at once. */
@Composable
private fun CtBgRow(lookup: String, onChange: () -> Unit = {}) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var rev by remember { mutableStateOf(0) }
    val bg = remember(lookup, rev) { ctBgAll(ctx)[lookup] }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { u ->
        if (u != null) scope.launch {
            if (!withContext(Dispatchers.IO) { ctBgSet(ctx, lookup, u) }) ctx.toast(ctr("Failed", "Başarısız"))
            rev++; onChange()
        }
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        CtGlyph(Icons.Outlined.Wallpaper, null, tint = T.sub)
        Spacer(Modifier.width(12.dp))
        CtBgThumb(bg, 64.dp, Modifier.clickable { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) })
        Spacer(Modifier.weight(1f))
        if (bg != null) IconButton({ ctBgClear(ctx, lookup); rev++; onChange() }) { CtGlyph(Icons.Outlined.Delete, null, tint = T.sub) }
    }
}

// ------------------------------------------------------------------------------------------------------------ customise

private fun ctFont(i: Int): androidx.compose.ui.text.font.FontFamily? = when (i) {
    1 -> androidx.compose.ui.text.font.FontFamily.Serif; 2 -> androidx.compose.ui.text.font.FontFamily.Monospace; 3 -> androidx.compose.ui.text.font.FontFamily.Cursive
    4 -> androidx.compose.ui.text.font.FontFamily(android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.NORMAL)); else -> null
}

/** Per-contact customise screen: live preview card (call background, photo, name in the chosen style) plus background, photo, font, size, bold / italic, colour. */
@Composable
private fun CtCustomScreen(id: Long, onBack: () -> Unit, onChanged: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var rev by remember { mutableStateOf(0) }
    var d by remember { mutableStateOf<CtDetail?>(null) }
    LaunchedEffect(id, rev) { d = withContext(Dispatchers.IO) { ctLoadDetails(ctx, setOf(id)).firstOrNull() } }
    val c = d
    if (c == null) { CtTopBar("", onBack); return }
    var st by remember(c.lookup) { mutableStateOf(ctStyleGet(ctx, c.lookup)) }
    var bgRev by remember { mutableStateOf(0) }
    val bg = remember(c.lookup, bgRev) { ctBgAll(ctx)[c.lookup] }
    fun upd(n: CtStyle) { st = n; ctStyleSet(ctx, c.lookup, n); onChanged() }
    fun photo(b: Bitmap?) = scope.launch {
        val ok = withContext(Dispatchers.IO) { ctSetPhoto(ctx, c.raws, b?.let { ctJpeg(ctSquare(it)) }) }
        if (!ok) ctx.toast(ctr("Failed", "Başarısız")); rev++; onChanged()
    }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { u -> if (u != null) scope.launch { photo(withContext(Dispatchers.IO) { ctPhotoBitmap(ctx, u) }) } }
    val shoot = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { b -> if (b != null) photo(b) }
    val name = CtContact(c.id, c.display, c.given, c.family, c.starred, c.photo, "", c.prefix, c.middle, c.suffix).label()
    Column(Modifier.fillMaxSize()) {
        CtTopBar(ctr("Call background", "Arama arka planı"), onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).navigationBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // preview drawn as the incoming call screen: background, photo, name in the chosen style, number, answer / decline
            val ink = if (bg != null) Color.White else T.text
            Box(Modifier.fillMaxWidth().height(400.dp).clip(RoundedCornerShape(ctRadius + 6.dp)).background(T.panel)) {
                if (bg != null) { CtBgView(bg, Modifier.matchParentSize()); Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = .35f))) }
                Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(Modifier.weight(1f))
                    CtAvatar(c.photo, name, 96.dp)
                    Spacer(Modifier.height(14.dp))
                    Text(name, color = if (st.color != 0) Color(st.color) else ink, fontSize = (if (st.size > 0) st.size else 24).sp, maxLines = 2, textAlign = TextAlign.Center,
                        fontWeight = if (st.bold) FontWeight.Bold else FontWeight.SemiBold, fontStyle = if (st.italic) androidx.compose.ui.text.font.FontStyle.Italic else null, fontFamily = ctFont(st.fam))
                    c.phones.firstOrNull()?.let { Text(it.value, color = ink.copy(alpha = .8f), fontSize = 15.sp, modifier = Modifier.padding(top = 4.dp)) }
                    Spacer(Modifier.weight(1f))
                    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(60.dp).clip(CircleShape).background(Color(0xFFE5484D)), contentAlignment = Alignment.Center) { CtGlyph(Icons.Outlined.CallEnd, null, tint = Color.White) }
                        Box(Modifier.size(60.dp).clip(CircleShape).background(Color(0xFF3FB950)), contentAlignment = Alignment.Center) { CtGlyph(Icons.Outlined.Call, null, tint = Color.White) }
                    }
                }
            }
            if (c.lookup.isNotBlank()) CtBgRow(c.lookup) { bgRev++ }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CtGlyph(Icons.Outlined.PhotoCamera, null, tint = T.sub)
                Spacer(Modifier.weight(1f))
                IconButton({ pick.launch("image/*") }) { CtGlyph(Icons.Outlined.Image, null, tint = ctAcc) }
                IconButton({ shoot.launch(null) }) { CtGlyph(Icons.Outlined.PhotoCamera, null, tint = ctAcc) }
                if (c.photo != null) IconButton({ photo(null) }) { CtGlyph(Icons.Outlined.Delete, null, tint = T.sub) }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                CtGlyph(Icons.Outlined.TextFields, null, tint = T.sub)
                listOf("Sans", "Serif", "Mono", "Script", "Narrow").forEachIndexed { i, n ->
                    Box(Modifier.clip(RoundedCornerShape(ctRadius)).background(if (st.fam == i) ctAcc else Color.Transparent).border(1.dp, if (st.fam == i) ctAcc else T.line, RoundedCornerShape(ctRadius))
                        .clickable { upd(CtStyle(i, st.size, st.bold, st.italic, st.color)) }.padding(14.dp, 8.dp)) {
                        Text(n, color = if (st.fam == i) onAccent(ctAcc) else T.text, fontSize = 14.sp, fontFamily = ctFont(i))
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                CtGlyph(Icons.Outlined.FormatSize, null, tint = T.sub)
                Slider((if (st.size > 0) st.size else 24).toFloat(), { upd(CtStyle(st.fam, it.toInt(), st.bold, st.italic, st.color)) }, Modifier.weight(1f).padding(horizontal = 12.dp), valueRange = 16f..40f,
                    colors = SliderDefaults.colors(thumbColor = ctAcc, activeTrackColor = ctAcc, inactiveTrackColor = T.line))
                IconButton({ upd(CtStyle(st.fam, st.size, !st.bold, st.italic, st.color)) }) { CtGlyph(Icons.Outlined.FormatBold, null, tint = if (st.bold) ctAcc else T.sub) }
                IconButton({ upd(CtStyle(st.fam, st.size, st.bold, !st.italic, st.color)) }) { CtGlyph(Icons.Outlined.FormatItalic, null, tint = if (st.italic) ctAcc else T.sub) }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                CtGlyph(Icons.Outlined.Palette, null, tint = T.sub)
                listOf(0L, 0xFFFFFFFF, 0xFF000000, 0xFFE5484D, 0xFFF0A30A, 0xFF3FB950, 0xFF2D7FF9, 0xFF8E4EC6, 0xFFFF6BA6).forEach { col ->
                    val v = col.toInt()
                    Box(Modifier.size(34.dp).clip(CircleShape).background(if (v == 0) T.panel else Color(v)).border(if (st.color == v) 3.dp else 1.dp, if (st.color == v) ctAcc else T.line, CircleShape)
                        .clickable { upd(CtStyle(st.fam, st.size, st.bold, st.italic, v)) }, contentAlignment = Alignment.Center) { if (v == 0) CtGlyph(Icons.Outlined.Close, null, Modifier.size(16.dp), tint = T.sub) }
                }
            }
        }
    }
}

// ------------------------------------------------------------------------------------------------------------ group, duplicates

@Composable
private fun CtGroupScreen(g: CtGroup?, members: List<CtContact>, onBack: () -> Unit, onOpen: (Long) -> Unit, onDelete: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        CtTopBar(g?.title ?: "", onBack) { CtIcon(Icons.Outlined.Delete, onClick = onDelete) }
        CtList(members, onOpen, false)
    }
}

@Composable
private fun CtDupes(all: List<CtContact>, onBack: () -> Unit, onMerged: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val groups = remember(all) { ctDuplicates(all) }
    Column(Modifier.fillMaxSize()) {
        CtTopBar(ctr("Duplicates", "Yinelenenler"), onBack)
        if (groups.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CtGlyph(Icons.Outlined.Check, null, Modifier.size(48.dp), tint = T.sub) }
        else LazyColumn(Modifier.fillMaxSize().navigationBarsPadding(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(groups) { g ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(ctRadius)).background(T.panel).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { g.forEach { Text(it.label(), color = T.text, maxLines = 1, overflow = TextOverflow.Ellipsis) } }
                    IconButton({ scope.launch { val r = withContext(Dispatchers.IO) { ctMerge(ctx, g.map { it.id }) }; if (!r) ctx.toast(ctr("Failed", "Başarısız")); onMerged() } }) { CtGlyph(Icons.Outlined.MergeType, null, tint = ctAcc) }
                }
            }
        }
    }
}

// ------------------------------------------------------------------------------------------------------------ settings

@Composable
private fun CtSettings(onBack: () -> Unit, onImport: () -> Unit, onExport: () -> Unit) {
    val ctx = LocalContext.current
    fun save() = CtPrefs.save(ctx)
    val matYou = CtPrefs.look == "material" && Build.VERSION.SDK_INT >= 31
    var dlg by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize()) {
        CtTopBar(ctr("Settings", "Ayarlar"), onBack)
        val setSc = rememberScrollState()
        Column(Modifier.weight(1f).verticalScroll(setSc).navigationBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val sec: @Composable (String, androidx.compose.ui.graphics.vector.ImageVector, Boolean, @Composable ColumnScope.() -> Unit) -> Unit = { t, v, o, c -> SetSection(t, { CtGlyph(v, null) }, o, c) }
            val ig: (androidx.compose.ui.graphics.vector.ImageVector) -> (@Composable () -> Unit) = { v -> { CtGlyph(v, null, tint = T.sub) } }
            val lookNames = mapOf("material" to "Material", "oneui" to "Samsung", "coloros" to "ColorOS", "win11" to "Windows 11", "win10" to "Windows 10", "win7" to "Windows 7", "xp" to "Windows XP", "win95" to "Windows 95")
            val nameOpts = listOf(true to "Ada Lovelace", false to "Lovelace Ada")
            val sortOpts = listOf("first" to ctr("First name", "Ad"), "last" to ctr("Surname", "Soyad"))
            sec(ctr("Look", "Görünüm"), Icons.Outlined.Palette, true) {
                SetRow(ig(Icons.Outlined.Palette), ctr("Look", "Görünüm"), lookNames[CtPrefs.look], { dlg = "look" })
            }
            sec(ctr("Colours", "Renkler"), Icons.Outlined.Palette, true) {
                if (matYou) CtToggle(Icons.Outlined.Palette, "Material You", CtPrefs.dynamic) { CtPrefs.dynamic = it; save() }
                if (!(matYou && CtPrefs.dynamic) && !CtPrefs.amoled && CtPrefs.look in listOf("material", "win11", "win10"))
                    SetRow(ig(Icons.Outlined.Palette), ctr("Accent", "Vurgu"), null, { dlg = "accent" }) { accentsFor(T.material).getOrNull(CtPrefs.accent)?.let { ColorDot(it) } }
                if (CtPrefs.look == "material") CtToggle(Icons.Outlined.DarkMode, "AMOLED", CtPrefs.amoled) { CtPrefs.amoled = it; save() }
            }
            sec(ctr("Language", "Dil"), Icons.Outlined.Language, false) {
                SetRow(ig(Icons.Outlined.Language), ctr("Language", "Dil"), if (CtPrefs.lang == "tr") "Türkçe" else "English", { dlg = "lang" })
            }
            sec(ctr("Contacts", "Kişiler"), Icons.Outlined.Person, false) {
                SetRow(ig(Icons.Outlined.Person), ctr("Name order", "Ad sırası"), nameOpts.first { it.first == CtPrefs.firstFirst }.second, { dlg = "order" })
                SetRow(ig(Icons.Outlined.SortByAlpha), ctr("Sort by", "Sırala"), sortOpts.first { it.first == CtPrefs.sortBy }.second, { dlg = "sort" })
                CtToggle(Icons.Outlined.Animation, ctr("Fade", "Solma"), CtPrefs.fade) { CtPrefs.fade = it; save() }
            }
            sec(ctr("Names", "Adlar"), Icons.Outlined.Badge, false) {
            CtGlyph(Icons.Outlined.Edit, null, tint = T.sub)
            CtToggleL(ctr("Prefix", "Ön ek"), CtPrefs.showPrefix) { CtPrefs.showPrefix = it; save() }
            CtToggleL(ctr("Middle name", "İkinci ad"), CtPrefs.showMiddle) { CtPrefs.showMiddle = it; save() }
            CtToggleL(ctr("Suffix", "Son ek"), CtPrefs.showSuffix) { CtPrefs.showSuffix = it; save() }
            CtToggleL(ctr("Nickname", "Takma ad"), CtPrefs.showNick) { CtPrefs.showNick = it; save() }
            CtGlyph(Icons.Outlined.Person, null, tint = T.sub)
            CtToggleL(ctr("Prefix", "Ön ek"), CtPrefs.dispPrefix) { CtPrefs.dispPrefix = it; save() }
            CtToggleL(ctr("Middle name", "İkinci ad"), CtPrefs.dispMiddle) { CtPrefs.dispMiddle = it; save() }
            CtToggleL(ctr("Suffix", "Son ek"), CtPrefs.dispSuffix) { CtPrefs.dispSuffix = it; save() }
            }
            sec(ctr("Call backgrounds", "Arama arka planları"), Icons.Outlined.Wallpaper, false) {
            var bgRev by remember { mutableStateOf(0) }
            var editBg by remember { mutableStateOf<String?>(null) }
            val ents by produceState(emptyList<Triple<String, String, CtBg>>(), bgRev) { value = withContext(Dispatchers.IO) { ctBgContacts(ctx) } }
            val bgSc = rememberScrollState()
            Row(Modifier.horizontalScroll(bgSc), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (ents.isEmpty()) CtGlyph(Icons.Outlined.Wallpaper, null, tint = T.sub)
                ents.forEach { (k, n, b) ->
                    Column(Modifier.width(72.dp).clickable { editBg = k }, horizontalAlignment = Alignment.CenterHorizontally) {
                        CtBgThumb(b, 72.dp)
                        Text(n, color = T.text, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            // the real incoming-call screen belongs to the system dialer
            Text(ctr("Shown in Pulse Contacts only. The phone app draws the real call screen.", "Yalnızca Pulse Kişiler'de görünür. Gerçek arama ekranını telefon uygulaması çizer."), color = T.sub, fontSize = 12.sp)
            editBg?.let { k -> AlertDialog({ editBg = null; bgRev++ }, containerColor = T.panel, shape = RoundedCornerShape(ctRadius), text = { CtBgRow(k) { bgRev++ } },
                confirmButton = { IconButton({ editBg = null; bgRev++ }) { CtGlyph(Icons.Outlined.Check, null, tint = ctAcc) } }) }
            }
            sec(ctr("Data", "Veri"), Icons.Outlined.FileDownload, false) {
            listOf(Triple(Icons.Outlined.FileDownload, ctr("Import .vcf", "İçe aktar .vcf"), onImport), Triple(Icons.Outlined.FileUpload, ctr("Export .vcf", "Dışa aktar .vcf"), onExport)).forEach { (ic, t, f) ->
                SetRow({ CtGlyph(ic, null, tint = T.sub) }, t, null, { f() })
            }
            }
        }
    }
    when (dlg) {
        "look" -> SetLookDialog(ctr("Look", "Görünüm"), CtPrefs.looks, CtPrefs.look, { lookNames0[it] ?: it }, { CtPrefs.look = it; save() }) { dlg = "" }
        "accent" -> SetColorDialog(ctr("Accent", "Vurgu"), accentsFor(T.material), CtPrefs.accent, { CtPrefs.accent = it; save() }) { dlg = "" }
        "lang" -> SetPickDialog(ctr("Language", "Dil"), listOf("en" to "English", "tr" to "Türkçe"), CtPrefs.lang, { CtPrefs.lang = it; save() }, { dlg = "" })
        "order" -> SetPickDialog(ctr("Name order", "Ad sırası"), listOf(true to "Ada Lovelace", false to "Lovelace Ada"), CtPrefs.firstFirst, { CtPrefs.firstFirst = it; save() }, { dlg = "" })
        "sort" -> SetPickDialog(ctr("Sort by", "Sırala"), listOf("first" to ctr("First name", "Ad"), "last" to ctr("Surname", "Soyad")), CtPrefs.sortBy, { CtPrefs.sortBy = it; save() }, { dlg = "" })
    }
}

private val lookNames0 = mapOf("material" to "Material", "oneui" to "Samsung", "coloros" to "ColorOS", "win11" to "Windows 11", "win10" to "Windows 10", "win7" to "Windows 7", "xp" to "Windows XP", "win95" to "Windows 95")

@Composable
private fun CtToggleL(label: String, on: Boolean, onChange: (Boolean) -> Unit) =
    SetSwitchRow({ CtGlyph(Icons.Outlined.Edit, null, tint = T.sub) }, label, on, onChange)

@Composable
private fun CtToggle(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, on: Boolean, onChange: (Boolean) -> Unit) =
    SetSwitchRow({ CtGlyph(icon, null, tint = T.sub) }, label, on, onChange)

// ------------------------------------------------------------------------------------------------------------ keypad, recents

/** ACTION_CALL when CALL_PHONE is granted (asked once on first use), the dialer otherwise. */
@Composable
private fun rememberCtCaller(): (String) -> Unit {
    val ctx = LocalContext.current
    var pending by remember { mutableStateOf("") }
    fun dial(n: String) {
        val granted = ContextCompat.checkSelfPermission(ctx, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
        runCatching { ctx.startActivity(Intent(if (granted) Intent.ACTION_CALL else Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(n))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { dial(pending) }
    return { n ->
        pending = n
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) dial(n) else ask.launch(Manifest.permission.CALL_PHONE)
    }
}

private const val CT_T9 = "22233344455566677778889999"
/** Letters to phone digits (a-z plus Turkish ç ğ ı ö ş ü); everything else is dropped. */
private fun ctT9(s: String): String = buildString {
    for (ch in s.lowercase()) when {
        ch in 'a'..'z' -> append(CT_T9[ch - 'a'])
        ch == 'ç' -> append('2'); ch == 'ğ' || ch == 'ı' -> append('4'); ch == 'ö' -> append('6'); ch == 'ş' -> append('7'); ch == 'ü' -> append('8')
        ch.isDigit() -> append(ch)
    }
}

@Composable
private fun CtKeypad(contacts: List<CtContact>, onOpen: (Long) -> Unit) {
    val ctx = LocalContext.current
    val call = rememberCtCaller()
    var n by remember { mutableStateOf("") }
    LaunchedEffect(ctInDial) { ctInDial?.let { n = it; ctInDial = null } }
    val sug = remember(n, contacts) {
        if (n.isEmpty()) emptyList() else contacts.filter { c -> ctT9(c.display).contains(n) || c.nums.split('\n').any { p -> p.filter(Char::isDigit).contains(n) } }.take(4)
    }
    Column(Modifier.fillMaxSize().navigationBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(Modifier.weight(1f).fillMaxWidth()) {
            sug.forEach { c ->
                Row(Modifier.fillMaxWidth().clickable { onOpen(c.id) }.padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    CtAvatar(c.photo, c.display, 36.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(c.label(), color = T.text, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(c.nums.substringBefore('\n'), color = T.sub, fontSize = 12.sp, maxLines = 1)
                    }
                    CtIcon(Icons.Outlined.Call, ctAcc) { c.nums.substringBefore('\n').trim().takeIf { it.isNotEmpty() }?.let(call) }
                }
            }
        }
        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                if (n.isNotEmpty()) CtIcon(Icons.Outlined.Add) { runCatching { ctx.startActivity(Intent(Intent.ACTION_INSERT, android.provider.ContactsContract.Contacts.CONTENT_URI).putExtra(android.provider.ContactsContract.Intents.Insert.PHONE, n).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
            }
            Text(n, color = T.text, fontSize = 28.sp, maxLines = 1, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.weight(1f))
            Box(Modifier.size(48.dp).pointerInput(Unit) { detectTapGestures(onTap = { n = n.dropLast(1) }, onLongPress = { n = "" }) }, contentAlignment = Alignment.Center) {
                if (n.isNotEmpty()) CtGlyph(Icons.Outlined.Backspace, null, tint = T.sub)
            }
        }
        val subs = mapOf('2' to "ABC", '3' to "DEF", '4' to "GHI", '5' to "JKL", '6' to "MNO", '7' to "PQRS", '8' to "TUV", '9' to "WXYZ", '0' to "+")
        listOf("123", "456", "789", "*0#").forEach { row ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                row.forEach { k ->
                    Column(Modifier.weight(1f).height(60.dp).pointerInput(k) {
                        detectTapGestures(onTap = { n += k }, onLongPress = { if (k == '0') n += "+" else n += k })
                    }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Text("$k", color = T.text, fontSize = 26.sp)
                        Text(subs[k] ?: " ", color = T.sub, fontSize = 10.sp)
                    }
                }
            }
        }
        Box(Modifier.padding(vertical = 12.dp).size(64.dp).clip(CircleShape).background(Color(0xFF2E9E4F)).clickable { if (n.isNotEmpty()) call(n) }, contentAlignment = Alignment.Center) {
            CtGlyph(Icons.Outlined.Call, null, tint = Color.White)
        }
    }
}

@Composable
private fun CtRecents(contacts: List<CtContact>, onOpen: (Long) -> Unit, q: String = "") {
    val ctx = LocalContext.current
    val call = rememberCtCaller()
    var ok by remember { mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok = it }
    LaunchedEffect(Unit) { if (!ok) ask.launch(Manifest.permission.READ_CALL_LOG) }
    var calls by remember { mutableStateOf<List<CtCall>>(emptyList()) }
    LaunchedEffect(ok) { if (ok) calls = withContext(Dispatchers.IO) { ctLoadCalls(ctx) } }
    if (!ok) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { FilledTonalIconButton({ ask.launch(Manifest.permission.READ_CALL_LOG) }, Modifier.size(72.dp)) { CtGlyph(Icons.Outlined.History, null, Modifier.size(32.dp)) } }; return }
    if (calls.isEmpty()) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CtGlyph(Icons.Outlined.History, null, Modifier.size(48.dp), tint = T.sub) }; return }
    // number -> contact by the last 8 digits
    val byNum = remember(contacts) {
        HashMap<String, CtContact>().also { m -> contacts.forEach { c -> c.nums.split('\n').map { it.filter(Char::isDigit) }.filter { it.length >= 4 }.forEach { m.putIfAbsent(it.takeLast(8), c) } } }
    }
    val shown = if (q.isBlank()) calls else { val d = q.filter(Char::isDigit); calls.filter { (it.name ?: "").contains(q.trim(), true) || (d.isNotEmpty() && it.number.filter(Char::isDigit).contains(d)) ||
        byNum[it.number.filter(Char::isDigit).takeLast(8)]?.let { k -> ctMatch(k, q) } == true } }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        items(shown.size) { i ->
            val c = shown[i]
            val known = byNum[c.number.filter(Char::isDigit).takeLast(8)]
            val title = known?.label() ?: c.name ?: c.number.ifBlank { "?" }
            val missed = c.type == 3 || c.type == 5 || c.type == 6
            var menu by remember { mutableStateOf(false) }
            Box {
                Row(Modifier.fillMaxWidth().pointerInput(c.number) { detectTapGestures(onTap = { if (c.number.isNotBlank()) call(c.number) }, onLongPress = { menu = true }) }.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    CtAvatar(known?.photo, title, 40.dp)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(title, color = if (missed) Color(0xFFE5484D) else T.text, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CtGlyph(when (c.type) { 2 -> Icons.Outlined.CallMade; 1 -> Icons.Outlined.CallReceived; else -> Icons.Outlined.CallMissed }, null, Modifier.size(14.dp), tint = if (missed) Color(0xFFE5484D) else T.sub)
                            Spacer(Modifier.width(4.dp))
                            Text(android.text.format.DateUtils.getRelativeTimeSpanString(c.date, System.currentTimeMillis(), android.text.format.DateUtils.MINUTE_IN_MILLIS).toString(), color = T.sub, fontSize = 12.sp, maxLines = 1)
                        }
                    }
                }
                DropdownMenu(menu, { menu = false }, containerColor = T.panel) {
                    DropdownMenuItem({ Text(ctr("Call", "Ara"), color = T.text) }, { menu = false; call(c.number) }, leadingIcon = { CtGlyph(Icons.Outlined.Call, null, tint = T.sub) })
                    DropdownMenuItem({ Text(ctr("Message", "Mesaj"), color = T.text) }, { menu = false; runCatching { ctx.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(c.number))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }, leadingIcon = { CtGlyph(Icons.Outlined.Sms, null, tint = T.sub) })
                    if (known != null) DropdownMenuItem({ Text(ctr("Details", "Ayrıntılar"), color = T.text) }, { menu = false; onOpen(known.id) }, leadingIcon = { CtGlyph(Icons.Outlined.Info, null, tint = T.sub) })
                    else DropdownMenuItem({ Text(ctr("Add contact", "Kişi ekle"), color = T.text) }, { menu = false; runCatching { ctx.startActivity(Intent(Intent.ACTION_INSERT, android.provider.ContactsContract.Contacts.CONTENT_URI).putExtra(android.provider.ContactsContract.Intents.Insert.PHONE, c.number).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }, leadingIcon = { CtGlyph(Icons.Outlined.Add, null, tint = T.sub) })
                }
            }
        }
    }
}
