package com.drdisagree.glasslayer.ui.status

import android.content.Context
import android.os.Build
import com.drdisagree.glasslayer.BuildConfig
import com.drdisagree.glasslayer.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object StatusReportFormatter {

    fun format(context: Context, state: StatusUiState): String = buildString {
        section(context, R.string.report_section_diagnosis)
        diagnose(context, state).forEach { appendLine("- $it") }

        section(context, R.string.report_section_device)
        appendLine(context.getString(R.string.report_app, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE))
        appendLine(context.getString(R.string.report_installed, time(state.installedAt)))
        appendLine(context.getString(R.string.report_device, Build.MANUFACTURER, Build.MODEL))
        appendLine(context.getString(R.string.report_android, Build.VERSION.RELEASE, Build.VERSION.SDK_INT, Build.ID))
        appendLine(context.getString(R.string.report_rom, Build.DISPLAY, Build.VERSION.SECURITY_PATCH))
        appendLine(context.getString(R.string.report_fingerprint, Build.FINGERPRINT))
        val framework = state.framework
        val frameworkText = framework?.let {
            context.getString(R.string.report_framework_value, it.name, it.version, it.apiVersion)
        } ?: context.getString(R.string.not_detected)
        appendLine(context.getString(R.string.report_framework, frameworkText))
        appendLine(context.getString(R.string.report_scope, framework?.scope?.joinToString().orEmpty()))

        val report = state.report ?: return@buildString
        section(context, R.string.report_section_systemui)
        appendLine(context.getString(R.string.report_systemui, report.systemUiVersion))
        appendLine(context.getString(R.string.report_time, time(report.timestamp)))
        appendLine(context.getString(R.string.report_uptime, report.uptimeMs / MS_PER_SECOND))
        appendLine(context.getString(R.string.report_module_loaded, time(report.moduleLoadedAt), time(report.moduleUpdatedAt)))
        appendLine(context.getString(R.string.report_module_apk, report.moduleApkPath, state.installedApkPath))
        appendLine(context.getString(R.string.report_hooked_framework, report.framework))
        appendLine(context.getString(R.string.report_capture, report.captureAvailable.toString()))

        lines(context, R.string.report_section_mods, report.mods.map { mod ->
            mod.name + ": " + mod.hooks + (if (mod.broken) " (" + context.getString(R.string.mod_report_paused) + ")" else "")
        })
        lines(context, R.string.report_section_broken, report.broken)
        lines(context, R.string.report_section_errors, report.errors)
        lines(context, R.string.report_section_log, report.logs)
    }.trimEnd()

    private fun diagnose(context: Context, state: StatusUiState): List<String> {
        val issues = mutableListOf<String>()
        val report = state.report
        if (state.framework == null) {
            issues += context.getString(R.string.diag_no_framework)
        } else if (!state.isScoped) {
            issues += context.getString(R.string.diag_not_scoped)
        }
        if (report == null) {
            issues += context.getString(R.string.diag_no_report)
            return issues
        }
        if (state.isStale) issues += context.getString(R.string.diag_stale)
        if (!report.captureAvailable) issues += context.getString(R.string.diag_no_capture)
        report.mods.filter { it.hooks == 0 }.forEach { issues += context.getString(R.string.diag_mod_unsupported, it.name) }
        report.mods.filter { it.broken }.forEach { issues += context.getString(R.string.diag_mod_paused, it.name) }
        if (report.errors.isNotEmpty()) {
            issues += context.resources.getQuantityString(R.plurals.diag_errors, report.errors.size, report.errors.size)
        }
        if (issues.isEmpty()) issues += context.getString(R.string.diag_ok)
        return issues
    }

    private fun StringBuilder.section(context: Context, title: Int) {
        if (isNotEmpty()) appendLine()
        appendLine("## " + context.getString(title))
    }

    private fun StringBuilder.lines(context: Context, title: Int, lines: List<String>) {
        if (lines.isEmpty()) return
        section(context, title)
        lines.forEach { appendLine(it) }
    }

    private fun time(millis: Long): String =
        if (millis <= 0L) "-" else SimpleDateFormat(TIME_PATTERN, Locale.US).format(Date(millis))
}

private const val MS_PER_SECOND = 1000L
private const val TIME_PATTERN = "yyyy-MM-dd HH:mm:ss"
