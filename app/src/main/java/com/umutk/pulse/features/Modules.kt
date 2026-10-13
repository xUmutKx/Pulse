package com.umutk.pulse.features

import android.content.pm.ApplicationInfo
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.umutk.pulse.Look
import com.umutk.pulse.PIcon

/** Xposed and LSPosed modules declare one of these keys in their manifest meta-data. */
fun isXposedModule(ai: ApplicationInfo): Boolean =
    ai.metaData?.let { it.containsKey("xposedmodule") || it.containsKey("xposeddescription") } == true

/** Badge shown next to a module's name in the Apps list. */
@Composable
fun ModuleBadge() = PIcon("services", Look.accent, 14.dp)
