package com.umutk.pulse.extras.palette.iconpack

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.FileProvider
import com.umutk.pulse.extras.palette.core.icon.ThemeSpec
import com.umutk.pulse.extras.palette.AppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Two export routes:
 *
 *  A) PACK_ZIP  — a complete, buildable Android Studio / Gradle icon-pack project
 *     containing every generated PNG plus appfilter.xml, drawable.xml and a
 *     GitHub Actions workflow. Push it, and CI hands back an installable APK that
 *     every icon-pack launcher on the market accepts. No SDK needed on device.
 *
 *  B) IMAGES_ZIP — flat PNGs named by component, for launchers with per-icon
 *     manual assignment (Nova, Total, Niagara, Smart).
 */
object IconPackExporter {

    data class Result(val file: File, val uri: Uri, val count: Int)

    suspend fun exportImages(
        ctx: Context, icons: Map<AppInfo, Bitmap>, spec: ThemeSpec
    ): Result = withContext(Dispatchers.IO) {
        val out = exportsDir(ctx).resolve(fileName(spec, "icons"))
        ZipOutputStream(FileOutputStream(out).buffered()).use { zip ->
            icons.forEach { (app, bmp) ->
                zip.putNextEntry(ZipEntry("${app.safeName}.png"))
                zip.write(png(bmp)); zip.closeEntry()
            }
            zip.putNextEntry(ZipEntry("theme.json"))
            zip.write(com.umutk.pulse.extras.palette.PaletteStore.specJson(spec).toString(2).toByteArray()); zip.closeEntry()
        }
        Result(out, uriOf(ctx, out), icons.size)
    }

    suspend fun exportPackProject(
        ctx: Context, icons: Map<AppInfo, Bitmap>, spec: ThemeSpec,
        packName: String = spec.name, packId: String = defaultId(spec)
    ): Result = withContext(Dispatchers.IO) {
        val out = exportsDir(ctx).resolve(fileName(spec, "pack"))
        ZipOutputStream(FileOutputStream(out).buffered()).use { zip ->
            fun put(path: String, bytes: ByteArray) {
                zip.putNextEntry(ZipEntry(path)); zip.write(bytes); zip.closeEntry()
            }
            fun putText(path: String, text: String) = put(path, text.toByteArray())

            icons.forEach { (app, bmp) ->
                put("app/src/main/res/drawable-nodpi/${app.safeName}.png", png(bmp))
            }
            putText("app/src/main/res/xml/appfilter.xml", appfilter(icons.keys))
            putText("app/src/main/res/xml/drawable.xml", drawableXml(icons.keys))
            putText("app/src/main/res/xml/icon_pack.xml", drawableXml(icons.keys))
            putText("app/src/main/res/values/strings.xml", strings(packName))
            putText("app/src/main/AndroidManifest.xml", manifest())
            putText("app/build.gradle", appGradle(packId))
            putText("build.gradle", rootGradle())
            putText("settings.gradle", "rootProject.name = 'IconPack'\ninclude ':app'\n")
            putText("gradle.properties", "android.useAndroidX=true\norg.gradle.jvmargs=-Xmx2g\n")
            putText(".github/workflows/build.yml", ciWorkflow())
            putText("README.md", readme(packName, icons.size))
        }
        Result(out, uriOf(ctx, out), icons.size)
    }

    // ---- generated files --------------------------------------------------

    private fun appfilter(apps: Collection<AppInfo>): String = buildString {
        appendLine("<?xml version=\"1.0\" encoding=\"utf-8\"?>")
        appendLine("<resources>")
        appendLine("    <scale factor=\"1.0\" />")
        apps.forEach {
            appendLine("    <item component=\"ComponentInfo{${it.packageName}/${it.activityName}}\" drawable=\"${it.safeName}\" />")
        }
        appendLine("</resources>")
    }

    private fun drawableXml(apps: Collection<AppInfo>): String = buildString {
        appendLine("<?xml version=\"1.0\" encoding=\"utf-8\"?>")
        appendLine("<resources>")
        appendLine("    <category title=\"Palette\" />")
        apps.forEach { appendLine("    <item drawable=\"${it.safeName}\" />") }
        appendLine("</resources>")
    }

    private fun strings(name: String) =
        "<resources>\n    <string name=\"app_name\">" + name.replace("&", "and") + "</string>\n</resources>\n"

    private fun manifest() = """
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application android:label="@string/app_name" android:allowBackup="true">
        <activity android:name=".MainActivity" android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
            <intent-filter>
                <action android:name="com.novalauncher.THEME" />
                <action android:name="org.adw.launcher.THEMES" />
                <action android:name="com.anddoes.launcher.THEME" />
                <action android:name="ch.deletescape.lawnchair.ICONPACK" />
                <action android:name="com.gau.go.launcherex.theme" />
                <action android:name="com.teslacoilsw.launcher.THEME" />
                <category android:name="android.intent.category.DEFAULT" />
            </intent-filter>
        </activity>
    </application>
</manifest>
""".trimIndent()

    private fun appGradle(appId: String) = """
apply plugin: 'com.android.application'

android {
    namespace '$appId'
    compileSdk 35
    defaultConfig {
        applicationId '$appId'
        minSdk 21
        targetSdk 35
        versionCode 1
        versionName '1.0'
    }
    buildTypes {
        release { minifyEnabled false }
    }
}
""".trimIndent()

    private fun rootGradle() = """
buildscript {
    repositories { google(); mavenCentral() }
    dependencies { classpath 'com.android.tools.build:gradle:8.7.3' }
}
allprojects { repositories { google(); mavenCentral() } }
""".trimIndent()

    private fun ciWorkflow() = """
name: Build icon pack
on: [push, workflow_dispatch]
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '17'
      - uses: gradle/actions/setup-gradle@v4
        with:
          gradle-version: '8.9'
      - run: gradle :app:assembleDebug --no-daemon
      - uses: actions/upload-artifact@v4
        with:
          name: icon-pack
          path: app/build/outputs/apk/debug/*.apk
""".trimIndent()

    private fun readme(name: String, count: Int) = """
# $name

Generated by Palette. Contains $count themed icons.

1. Push this folder to a GitHub repository.
2. The included workflow builds an installable APK under Actions -> Artifacts.
3. Install the APK, then pick "$name" as the icon pack in your launcher.

Everything was rendered on-device; no assets came from a server.
""".trimIndent()

    // ---- helpers ----------------------------------------------------------

    private fun png(b: Bitmap): ByteArray = ByteArrayOutputStream().also {
        b.compress(Bitmap.CompressFormat.PNG, 100, it)
    }.toByteArray()

    private fun exportsDir(ctx: Context) =
        File(ctx.cacheDir, "exports").apply { mkdirs() }

    private fun uriOf(ctx: Context, f: File): Uri =
        FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)

    private fun fileName(spec: ThemeSpec, kind: String) =
        "palette_" + spec.name.lowercase().replace(Regex("[^a-z0-9]"), "_") + "_" + kind + ".zip"

    private fun defaultId(spec: ThemeSpec) =
        "com.palette.pack." + spec.name.lowercase().replace(Regex("[^a-z0-9]"), "")
            .ifBlank { "theme" }
}
