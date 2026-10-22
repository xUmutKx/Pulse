package com.umutk.pulse

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** Whether icons are drawn as Fluent glyphs: null follows the look (Windows 11), true / false is set by a window that has its own look (the Office 365 window). */
val LocalFluentIcons = compositionLocalOf<Boolean?> { null }

/**
 * Drop-in for the Material Icon: in the Windows 11 look (and the Office 365 window) an icon that has a Fluent twin is drawn as the Fluent
 * glyph (filled, from the Fluent System Icons), every other look keeps the Material one. Files import this instead of material3.Icon.
 */
@Composable
fun Icon(imageVector: ImageVector, contentDescription: String?, modifier: Modifier = Modifier, tint: Color = LocalContentColor.current) {
    val on = LocalFluentIcons.current ?: (T.id in setOf("win11", "mica2", "acrylic2") && IconStyle.of(IconCat.SCREENS) != "line")
    val key = imageVector.name.substringAfterLast('.')
    // the Windows 11 icon pack (the coloured Fluency pictures, assets/icons/win11) first, so these icons match the rest of the look
    if (on) MAT_PACK[key]?.let { k ->
        val bmp = com.umutk.pulse.features.ThemeIcons.bitmap(androidx.compose.ui.platform.LocalContext.current, "win11", k)
        if (bmp != null) { androidx.compose.foundation.Image(bmp, contentDescription, modifier.size(24.dp)); return }
    }
    val raw = if (on) FluentMat.map[key] else null
    if (raw == null) { androidx.compose.material3.Icon(imageVector, contentDescription, modifier, tint); return }
    val paths = remember(key) { raw.map { PathParser().parsePathString(it).toPath().apply { fillType = PathFillType.EvenOdd } } }
    val sem = if (contentDescription != null) Modifier.semantics { this.contentDescription = contentDescription } else Modifier
    Canvas(modifier.then(sem).size(24.dp)) { scale(size.width / 24f, pivot = Offset.Zero) { paths.forEach { drawPath(it, tint) } } }
}

@Composable
fun Icon(painter: Painter, contentDescription: String?, modifier: Modifier = Modifier, tint: Color = LocalContentColor.current) =
    androidx.compose.material3.Icon(painter, contentDescription, modifier, tint)

@Composable
fun Icon(bitmap: ImageBitmap, contentDescription: String?, modifier: Modifier = Modifier, tint: Color = LocalContentColor.current) =
    androidx.compose.material3.Icon(bitmap, contentDescription, modifier, tint)

/** Material icon name -> key of the Windows 11 icon pack (assets/icons/win11/<key>.png). */
private val MAT_PACK = mapOf(
    "Add" to "add",
    "Apps" to "apps",
    "Widgets" to "apps",
    "ArrowBack" to "back",
    "BatteryFull" to "batteryFull",
    "Notifications" to "bell",
    "Bolt" to "bolt",
    "MenuBook" to "book",
    "BookmarkBorder" to "bookmark",
    "Calculate" to "calc",
    "Functions" to "calc",
    "CalendarMonth" to "calendar",
    "CalendarToday" to "calendar",
    "Event" to "calendar",
    "Today" to "calendar",
    "Message" to "chat",
    "Sms" to "chat",
    "Check" to "check",
    "CheckCircle" to "check",
    "KeyboardArrowDown" to "down",
    "KeyboardArrowUp" to "up",
    "ArrowDownward" to "down",
    "ArrowUpward" to "up",
    "ArrowForward" to "forward",
    "CleaningServices" to "clean",
    "ContentPaste" to "paste",
    "Schedule" to "clock",
    "Close" to "close",
    "Cloud" to "cloud",
    "Code" to "code",
    "Paid" to "coin",
    "MonetizationOn" to "coin",
    "Explore" to "compass",
    "ContentCopy" to "copy",
    "FileCopy" to "copy",
    "Memory" to "cpu",
    "DeveloperBoard" to "cpu",
    "ContentCut" to "cut",
    "Delete" to "delete",
    "Casino" to "dice",
    "Storage" to "storage",
    "SdStorage" to "sdcard",
    "Description" to "document",
    "InsertDriveFile" to "document",
    "Download" to "download",
    "FileDownload" to "download",
    "SaveAlt" to "download",
    "Edit" to "edit",
    "EditNote" to "edit",
    "Warning" to "warning",
    "Visibility" to "eyeOn",
    "VisibilityOff" to "eyeOff",
    "FilterList" to "filter",
    "Flag" to "flag",
    "FlashlightOn" to "flash",
    "Folder" to "folder",
    "FolderOpen" to "openFolder",
    "CreateNewFolder" to "newfolder",
    "FolderZip" to "zip",
    "Archive" to "zip",
    "Fullscreen" to "fullscreen",
    "OpenInFull" to "fullscreen",
    "Public" to "globe",
    "Language" to "globe",
    "Web" to "globe",
    "GridView" to "gridView",
    "GridOn" to "gridView",
    "Favorite" to "heart",
    "FavoriteBorder" to "heart",
    "History" to "history",
    "Restore" to "restore",
    "Home" to "home",
    "Image" to "image",
    "PhotoLibrary" to "image",
    "Collections" to "image",
    "Wallpaper" to "image",
    "Info" to "info",
    "Key" to "key",
    "Password" to "key",
    "Link" to "link",
    "List" to "listView",
    "ViewList" to "listView",
    "Lock" to "lock",
    "LockOpen" to "unlock",
    "Email" to "mail",
    "Menu" to "menu",
    "MoreVert" to "more",
    "MoreHoriz" to "more",
    "MusicNote" to "music",
    "LibraryMusic" to "music",
    "QueueMusic" to "music",
    "Album" to "music",
    "NoteAdd" to "newfile",
    "Notes" to "notes",
    "OpenInNew" to "open",
    "OpenWith" to "openWith",
    "Palette" to "palette",
    "ColorLens" to "palette",
    "Brush" to "palette",
    "Pause" to "pause",
    "Person" to "person",
    "Contacts" to "person",
    "Badge" to "person",
    "People" to "users",
    "Group" to "users",
    "Groups" to "users",
    "PhoneAndroid" to "phone",
    "Call" to "phone",
    "PushPin" to "pin",
    "PlayArrow" to "play",
    "PowerSettingsNew" to "power",
    "Print" to "print",
    "Redo" to "redo",
    "Refresh" to "refresh",
    "RestartAlt" to "refresh",
    "DriveFileRenameOutline" to "rename",
    "RotateRight" to "rotate",
    "RotateLeft" to "rotate",
    "ScreenRotation" to "rotate",
    "Save" to "save",
    "SaveAs" to "save",
    "Straighten" to "scale",
    "Balance" to "scale",
    "Screenshot" to "screenshot",
    "Search" to "search",
    "SearchOff" to "search",
    "SelectAll" to "selectAll",
    "Sensors" to "sensors",
    "Settings" to "settings",
    "Tune" to "settings",
    "Share" to "share",
    "HealthAndSafety" to "shieldOk",
    "Sort" to "sort",
    "SortByAlpha" to "sortName",
    "SwapVert" to "sort",
    "Star" to "starOn",
    "StarBorder" to "star",
    "StarOutline" to "star",
    "Stop" to "stop",
    "Timer" to "timer",
    "TableChart" to "table",
    "TableRows" to "table",
    "Tag" to "tag",
    "Thermostat" to "thermal",
    "TouchApp" to "touch",
    "Undo" to "undo",
    "Upload" to "upload",
    "FileUpload" to "upload",
    "Vibration" to "vibrate",
    "Videocam" to "video",
    "Movie" to "video",
    "SlowMotionVideo" to "video",
    "Mic" to "voice",
    "VolumeUp" to "volume",
    "WbSunny" to "weather",
    "Wifi" to "wifi",
    "Build" to "wrench",
    "ZoomIn" to "zoomIn",
    "ShowChart" to "performance",
    "TrendingUp" to "performance",
    "DriveFileMove" to "move",
    "Bedtime" to "timer",
)
