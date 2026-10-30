package com.umutk.pulse.extras.keyboard

import androidx.compose.material.icons.Icons
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.filled.ArrowBack as FArrowBack
import androidx.compose.material.icons.filled.ArrowDownward as FArrowDownward
import androidx.compose.material.icons.filled.ArrowForward as FArrowForward
import androidx.compose.material.icons.filled.ArrowUpward as FArrowUpward
import androidx.compose.material.icons.filled.Backspace as FBackspace
import androidx.compose.material.icons.filled.Check as FCheck
import androidx.compose.material.icons.filled.Close as FClose
import androidx.compose.material.icons.filled.ContentCopy as FContentCopy
import androidx.compose.material.icons.filled.ContentCut as FContentCut
import androidx.compose.material.icons.filled.ContentPaste as FContentPaste
import androidx.compose.material.icons.filled.EmojiEmotions as FEmojiEmotions
import androidx.compose.material.icons.filled.ExpandLess as FExpandLess
import androidx.compose.material.icons.filled.ExpandMore as FExpandMore
import androidx.compose.material.icons.filled.Keyboard as FKeyboard
import androidx.compose.material.icons.filled.KeyboardReturn as FKeyboardReturn
import androidx.compose.material.icons.filled.Language as FLanguage
import androidx.compose.material.icons.filled.MoreHoriz as FMoreHoriz
import androidx.compose.material.icons.filled.Search as FSearch
import androidx.compose.material.icons.filled.SelectAll as FSelectAll
import androidx.compose.material.icons.filled.Send as FSend
import androidx.compose.material.icons.filled.SkipNext as FSkipNext
import androidx.compose.material.icons.filled.SkipPrevious as FSkipPrevious
import androidx.compose.material.icons.filled.Undo as FUndo
import androidx.compose.material.icons.filled.Redo as FRedo
import androidx.compose.material.icons.filled.Gesture as FGesture
import androidx.compose.material.icons.filled.Dashboard as FDashboard
import androidx.compose.material.icons.filled.AspectRatio as FAspectRatio
import androidx.compose.material.icons.filled.OpenWith as FOpenWith
import androidx.compose.material.icons.filled.Palette as FPalette
import androidx.compose.material.icons.filled.Pin as FPin
import androidx.compose.material.icons.filled.Settings as FSettings
import androidx.compose.material.icons.filled.Spellcheck as FSpellcheck
import androidx.compose.material.icons.filled.TouchApp as FTouchApp
import androidx.compose.material.icons.filled.VisibilityOff as FVisibilityOff
import androidx.compose.material.icons.filled.KeyboardCapslock as FKeyboardCapslock
import androidx.compose.material.icons.outlined.ArrowBack as OArrowBack
import androidx.compose.material.icons.outlined.ArrowDownward as OArrowDownward
import androidx.compose.material.icons.outlined.ArrowForward as OArrowForward
import androidx.compose.material.icons.outlined.ArrowUpward as OArrowUpward
import androidx.compose.material.icons.outlined.Backspace as OBackspace
import androidx.compose.material.icons.outlined.Check as OCheck
import androidx.compose.material.icons.outlined.Close as OClose
import androidx.compose.material.icons.outlined.ContentCopy as OContentCopy
import androidx.compose.material.icons.outlined.ContentCut as OContentCut
import androidx.compose.material.icons.outlined.ContentPaste as OContentPaste
import androidx.compose.material.icons.outlined.EmojiEmotions as OEmojiEmotions
import androidx.compose.material.icons.outlined.ExpandLess as OExpandLess
import androidx.compose.material.icons.outlined.ExpandMore as OExpandMore
import androidx.compose.material.icons.outlined.Keyboard as OKeyboard
import androidx.compose.material.icons.outlined.KeyboardReturn as OKeyboardReturn
import androidx.compose.material.icons.outlined.Language as OLanguage
import androidx.compose.material.icons.outlined.MoreHoriz as OMoreHoriz
import androidx.compose.material.icons.outlined.Search as OSearch
import androidx.compose.material.icons.outlined.SelectAll as OSelectAll
import androidx.compose.material.icons.outlined.Send as OSend
import androidx.compose.material.icons.outlined.SkipNext as OSkipNext
import androidx.compose.material.icons.outlined.SkipPrevious as OSkipPrevious
import androidx.compose.material.icons.outlined.Undo as OUndo
import androidx.compose.material.icons.outlined.Redo as ORedo
import androidx.compose.material.icons.outlined.Gesture as OGesture
import androidx.compose.material.icons.outlined.Dashboard as ODashboard
import androidx.compose.material.icons.outlined.AspectRatio as OAspectRatio
import androidx.compose.material.icons.outlined.OpenWith as OOpenWith
import androidx.compose.material.icons.outlined.Palette as OPalette
import androidx.compose.material.icons.outlined.Pin as OPin
import androidx.compose.material.icons.outlined.Settings as OSettings
import androidx.compose.material.icons.outlined.Spellcheck as OSpellcheck
import androidx.compose.material.icons.outlined.TouchApp as OTouchApp
import androidx.compose.material.icons.outlined.VisibilityOff as OVisibilityOff
import androidx.compose.material.icons.outlined.KeyboardCapslock as OKeyboardCapslock
import androidx.compose.material.icons.rounded.ArrowBack as RArrowBack
import androidx.compose.material.icons.rounded.ArrowDownward as RArrowDownward
import androidx.compose.material.icons.rounded.ArrowForward as RArrowForward
import androidx.compose.material.icons.rounded.ArrowUpward as RArrowUpward
import androidx.compose.material.icons.rounded.Backspace as RBackspace
import androidx.compose.material.icons.rounded.Check as RCheck
import androidx.compose.material.icons.rounded.Close as RClose
import androidx.compose.material.icons.rounded.ContentCopy as RContentCopy
import androidx.compose.material.icons.rounded.ContentCut as RContentCut
import androidx.compose.material.icons.rounded.ContentPaste as RContentPaste
import androidx.compose.material.icons.rounded.EmojiEmotions as REmojiEmotions
import androidx.compose.material.icons.rounded.ExpandLess as RExpandLess
import androidx.compose.material.icons.rounded.ExpandMore as RExpandMore
import androidx.compose.material.icons.rounded.Keyboard as RKeyboard
import androidx.compose.material.icons.rounded.KeyboardReturn as RKeyboardReturn
import androidx.compose.material.icons.rounded.Language as RLanguage
import androidx.compose.material.icons.rounded.MoreHoriz as RMoreHoriz
import androidx.compose.material.icons.rounded.Search as RSearch
import androidx.compose.material.icons.rounded.SelectAll as RSelectAll
import androidx.compose.material.icons.rounded.Send as RSend
import androidx.compose.material.icons.rounded.SkipNext as RSkipNext
import androidx.compose.material.icons.rounded.SkipPrevious as RSkipPrevious
import androidx.compose.material.icons.rounded.Undo as RUndo
import androidx.compose.material.icons.rounded.Redo as RRedo
import androidx.compose.material.icons.rounded.Gesture as RGesture
import androidx.compose.material.icons.rounded.Dashboard as RDashboard
import androidx.compose.material.icons.rounded.AspectRatio as RAspectRatio
import androidx.compose.material.icons.rounded.OpenWith as ROpenWith
import androidx.compose.material.icons.rounded.Palette as RPalette
import androidx.compose.material.icons.rounded.Pin as RPin
import androidx.compose.material.icons.rounded.PushPin as RPushPin
import androidx.compose.material.icons.rounded.Bolt as RBolt
import androidx.compose.material.icons.rounded.Settings as RSettings
import androidx.compose.material.icons.rounded.Spellcheck as RSpellcheck
import androidx.compose.material.icons.rounded.TouchApp as RTouchApp
import androidx.compose.material.icons.rounded.VisibilityOff as RVisibilityOff
import androidx.compose.material.icons.rounded.KeyboardCapslock as RKeyboardCapslock
import androidx.compose.material.icons.sharp.ArrowBack as SArrowBack
import androidx.compose.material.icons.sharp.ArrowDownward as SArrowDownward
import androidx.compose.material.icons.sharp.ArrowForward as SArrowForward
import androidx.compose.material.icons.sharp.ArrowUpward as SArrowUpward
import androidx.compose.material.icons.sharp.Backspace as SBackspace
import androidx.compose.material.icons.sharp.Check as SCheck
import androidx.compose.material.icons.sharp.Close as SClose
import androidx.compose.material.icons.sharp.ContentCopy as SContentCopy
import androidx.compose.material.icons.sharp.ContentCut as SContentCut
import androidx.compose.material.icons.sharp.ContentPaste as SContentPaste
import androidx.compose.material.icons.sharp.EmojiEmotions as SEmojiEmotions
import androidx.compose.material.icons.sharp.ExpandLess as SExpandLess
import androidx.compose.material.icons.sharp.ExpandMore as SExpandMore
import androidx.compose.material.icons.sharp.Keyboard as SKeyboard
import androidx.compose.material.icons.sharp.KeyboardReturn as SKeyboardReturn
import androidx.compose.material.icons.sharp.Language as SLanguage
import androidx.compose.material.icons.sharp.MoreHoriz as SMoreHoriz
import androidx.compose.material.icons.sharp.Search as SSearch
import androidx.compose.material.icons.sharp.SelectAll as SSelectAll
import androidx.compose.material.icons.sharp.Send as SSend
import androidx.compose.material.icons.sharp.SkipNext as SSkipNext
import androidx.compose.material.icons.sharp.SkipPrevious as SSkipPrevious
import androidx.compose.material.icons.sharp.Undo as SUndo
import androidx.compose.material.icons.sharp.Redo as SRedo
import androidx.compose.material.icons.sharp.Gesture as SGesture
import androidx.compose.material.icons.sharp.Dashboard as SDashboard
import androidx.compose.material.icons.sharp.AspectRatio as SAspectRatio
import androidx.compose.material.icons.sharp.OpenWith as SOpenWith
import androidx.compose.material.icons.sharp.Palette as SPalette
import androidx.compose.material.icons.sharp.Pin as SPin
import androidx.compose.material.icons.sharp.Settings as SSettings
import androidx.compose.material.icons.sharp.Spellcheck as SSpellcheck
import androidx.compose.material.icons.sharp.TouchApp as STouchApp
import androidx.compose.material.icons.sharp.VisibilityOff as SVisibilityOff
import androidx.compose.material.icons.sharp.KeyboardCapslock as SKeyboardCapslock

/** The keyboard's icons in the icon family of its theme: filled (Windows 7, XP), sharp (Windows 10, 95), rounded (Material) or outlined (AMOLED, Windows 11, custom). */
fun kbIcon(th: KbTheme, name: String, filled: Boolean = false): ImageVector = when (if (filled) "filled" else th.icons) {
    "filled" -> when (name) {
        "ArrowBack" -> Icons.Filled.FArrowBack
        "ArrowDownward" -> Icons.Filled.FArrowDownward
        "ArrowForward" -> Icons.Filled.FArrowForward
        "ArrowUpward" -> Icons.Filled.FArrowUpward
        "Backspace" -> Icons.Filled.FBackspace
        "Check" -> Icons.Filled.FCheck
        "Close" -> Icons.Filled.FClose
        "ContentCopy" -> Icons.Filled.FContentCopy
        "ContentCut" -> Icons.Filled.FContentCut
        "ContentPaste" -> Icons.Filled.FContentPaste
        "EmojiEmotions" -> Icons.Filled.FEmojiEmotions
        "ExpandLess" -> Icons.Filled.FExpandLess
        "ExpandMore" -> Icons.Filled.FExpandMore
        "Keyboard" -> Icons.Filled.FKeyboard
        "KeyboardReturn" -> Icons.Filled.FKeyboardReturn
        "Language" -> Icons.Filled.FLanguage
        "MoreHoriz" -> Icons.Filled.FMoreHoriz
        "Search" -> Icons.Filled.FSearch
        "SelectAll" -> Icons.Filled.FSelectAll
        "Send" -> Icons.Filled.FSend
        "SkipNext" -> Icons.Filled.FSkipNext
        "SkipPrevious" -> Icons.Filled.FSkipPrevious
        "Undo" -> Icons.Filled.FUndo
        "Redo" -> Icons.Filled.FRedo
        "Gesture" -> Icons.Filled.FGesture
        "Dashboard" -> Icons.Filled.FDashboard
        "AspectRatio" -> Icons.Filled.FAspectRatio
        "OpenWith" -> Icons.Filled.FOpenWith
        "Palette" -> Icons.Filled.FPalette
        "Pin" -> Icons.Filled.FPin
        "Settings" -> Icons.Filled.FSettings
        "Spellcheck" -> Icons.Filled.FSpellcheck
        "TouchApp" -> Icons.Filled.FTouchApp
        "VisibilityOff" -> Icons.Filled.FVisibilityOff
        "KeyboardCapslock" -> Icons.Filled.FKeyboardCapslock
        else -> Icons.Outlined.OArrowBack
    }
    "rounded" -> when (name) {
        "ArrowBack" -> Icons.Rounded.RArrowBack
        "ArrowDownward" -> Icons.Rounded.RArrowDownward
        "ArrowForward" -> Icons.Rounded.RArrowForward
        "ArrowUpward" -> Icons.Rounded.RArrowUpward
        "Backspace" -> Icons.Rounded.RBackspace
        "Check" -> Icons.Rounded.RCheck
        "Close" -> Icons.Rounded.RClose
        "ContentCopy" -> Icons.Rounded.RContentCopy
        "ContentCut" -> Icons.Rounded.RContentCut
        "ContentPaste" -> Icons.Rounded.RContentPaste
        "EmojiEmotions" -> Icons.Rounded.REmojiEmotions
        "ExpandLess" -> Icons.Rounded.RExpandLess
        "ExpandMore" -> Icons.Rounded.RExpandMore
        "Keyboard" -> Icons.Rounded.RKeyboard
        "KeyboardReturn" -> Icons.Rounded.RKeyboardReturn
        "Language" -> Icons.Rounded.RLanguage
        "MoreHoriz" -> Icons.Rounded.RMoreHoriz
        "Search" -> Icons.Rounded.RSearch
        "SelectAll" -> Icons.Rounded.RSelectAll
        "Send" -> Icons.Rounded.RSend
        "SkipNext" -> Icons.Rounded.RSkipNext
        "SkipPrevious" -> Icons.Rounded.RSkipPrevious
        "Undo" -> Icons.Rounded.RUndo
        "Redo" -> Icons.Rounded.RRedo
        "Gesture" -> Icons.Rounded.RGesture
        "Dashboard" -> Icons.Rounded.RDashboard
        "AspectRatio" -> Icons.Rounded.RAspectRatio
        "OpenWith" -> Icons.Rounded.ROpenWith
        "Palette" -> Icons.Rounded.RPalette
        "Pin" -> Icons.Rounded.RPin
        "PushPin" -> Icons.Rounded.RPushPin
        "Bolt" -> Icons.Rounded.RBolt
        "Settings" -> Icons.Rounded.RSettings
        "Spellcheck" -> Icons.Rounded.RSpellcheck
        "TouchApp" -> Icons.Rounded.RTouchApp
        "VisibilityOff" -> Icons.Rounded.RVisibilityOff
        "KeyboardCapslock" -> Icons.Rounded.RKeyboardCapslock
        else -> Icons.Outlined.OArrowBack
    }
    "sharp" -> when (name) {
        "ArrowBack" -> Icons.Sharp.SArrowBack
        "ArrowDownward" -> Icons.Sharp.SArrowDownward
        "ArrowForward" -> Icons.Sharp.SArrowForward
        "ArrowUpward" -> Icons.Sharp.SArrowUpward
        "Backspace" -> Icons.Sharp.SBackspace
        "Check" -> Icons.Sharp.SCheck
        "Close" -> Icons.Sharp.SClose
        "ContentCopy" -> Icons.Sharp.SContentCopy
        "ContentCut" -> Icons.Sharp.SContentCut
        "ContentPaste" -> Icons.Sharp.SContentPaste
        "EmojiEmotions" -> Icons.Sharp.SEmojiEmotions
        "ExpandLess" -> Icons.Sharp.SExpandLess
        "ExpandMore" -> Icons.Sharp.SExpandMore
        "Keyboard" -> Icons.Sharp.SKeyboard
        "KeyboardReturn" -> Icons.Sharp.SKeyboardReturn
        "Language" -> Icons.Sharp.SLanguage
        "MoreHoriz" -> Icons.Sharp.SMoreHoriz
        "Search" -> Icons.Sharp.SSearch
        "SelectAll" -> Icons.Sharp.SSelectAll
        "Send" -> Icons.Sharp.SSend
        "SkipNext" -> Icons.Sharp.SSkipNext
        "SkipPrevious" -> Icons.Sharp.SSkipPrevious
        "Undo" -> Icons.Sharp.SUndo
        "Redo" -> Icons.Sharp.SRedo
        "Gesture" -> Icons.Sharp.SGesture
        "Dashboard" -> Icons.Sharp.SDashboard
        "AspectRatio" -> Icons.Sharp.SAspectRatio
        "OpenWith" -> Icons.Sharp.SOpenWith
        "Palette" -> Icons.Sharp.SPalette
        "Pin" -> Icons.Sharp.SPin
        "Settings" -> Icons.Sharp.SSettings
        "Spellcheck" -> Icons.Sharp.SSpellcheck
        "TouchApp" -> Icons.Sharp.STouchApp
        "VisibilityOff" -> Icons.Sharp.SVisibilityOff
        "KeyboardCapslock" -> Icons.Sharp.SKeyboardCapslock
        else -> Icons.Outlined.OArrowBack
    }
    else -> when (name) {
        "ArrowBack" -> Icons.Outlined.OArrowBack
        "ArrowDownward" -> Icons.Outlined.OArrowDownward
        "ArrowForward" -> Icons.Outlined.OArrowForward
        "ArrowUpward" -> Icons.Outlined.OArrowUpward
        "Backspace" -> Icons.Outlined.OBackspace
        "Check" -> Icons.Outlined.OCheck
        "Close" -> Icons.Outlined.OClose
        "ContentCopy" -> Icons.Outlined.OContentCopy
        "ContentCut" -> Icons.Outlined.OContentCut
        "ContentPaste" -> Icons.Outlined.OContentPaste
        "EmojiEmotions" -> Icons.Outlined.OEmojiEmotions
        "ExpandLess" -> Icons.Outlined.OExpandLess
        "ExpandMore" -> Icons.Outlined.OExpandMore
        "Keyboard" -> Icons.Outlined.OKeyboard
        "KeyboardReturn" -> Icons.Outlined.OKeyboardReturn
        "Language" -> Icons.Outlined.OLanguage
        "MoreHoriz" -> Icons.Outlined.OMoreHoriz
        "Search" -> Icons.Outlined.OSearch
        "SelectAll" -> Icons.Outlined.OSelectAll
        "Send" -> Icons.Outlined.OSend
        "SkipNext" -> Icons.Outlined.OSkipNext
        "SkipPrevious" -> Icons.Outlined.OSkipPrevious
        "Undo" -> Icons.Outlined.OUndo
        "Redo" -> Icons.Outlined.ORedo
        "Gesture" -> Icons.Outlined.OGesture
        "Dashboard" -> Icons.Outlined.ODashboard
        "AspectRatio" -> Icons.Outlined.OAspectRatio
        "OpenWith" -> Icons.Outlined.OOpenWith
        "Palette" -> Icons.Outlined.OPalette
        "Pin" -> Icons.Outlined.OPin
        "Settings" -> Icons.Outlined.OSettings
        "Spellcheck" -> Icons.Outlined.OSpellcheck
        "TouchApp" -> Icons.Outlined.OTouchApp
        "VisibilityOff" -> Icons.Outlined.OVisibilityOff
        "KeyboardCapslock" -> Icons.Outlined.OKeyboardCapslock
        else -> Icons.Outlined.OArrowBack
    }
}
