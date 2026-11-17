package com.drdisagree.glasslayer.data.mapper

import com.drdisagree.glasslayer.domain.model.HookReport
import com.drdisagree.glasslayer.domain.model.ModReport
import org.json.JSONArray
import org.json.JSONObject

object HookReportJsonMapper {

    fun toJson(report: HookReport): String = JSONObject()
        .put("timestamp", report.timestamp)
        .put("framework", report.framework)
        .put("systemUiVersion", report.systemUiVersion)
        .put("buildId", report.buildId)
        .put("moduleUpdatedAt", report.moduleUpdatedAt)
        .put("moduleLoadedAt", report.moduleLoadedAt)
        .put("moduleApkPath", report.moduleApkPath)
        .put("uptimeMs", report.uptimeMs)
        .put("captureAvailable", report.captureAvailable)
        .put("mods", JSONArray().apply {
            report.mods.forEach { mod ->
                put(JSONObject().put("name", mod.name).put("hooks", mod.hooks).put("broken", mod.broken))
            }
        })
        .put("broken", JSONArray(report.broken))
        .put("errors", JSONArray(report.errors))
        .put("logs", JSONArray(report.logs))
        .put("safeMode", report.safeMode)
        .toString()

    fun fromJson(json: String): HookReport? = try {
        val root = JSONObject(json)
        val mods = root.optJSONArray("mods") ?: JSONArray()
        HookReport(
            timestamp = root.getLong("timestamp"),
            framework = root.optString("framework"),
            systemUiVersion = root.optString("systemUiVersion"),
            buildId = root.optString("buildId"),
            moduleUpdatedAt = root.optLong("moduleUpdatedAt"),
            moduleLoadedAt = root.optLong("moduleLoadedAt"),
            moduleApkPath = root.optString("moduleApkPath"),
            uptimeMs = root.optLong("uptimeMs"),
            captureAvailable = root.optBoolean("captureAvailable"),
            mods = List(mods.length()) { index ->
                val mod = mods.getJSONObject(index)
                ModReport(mod.getString("name"), mod.optInt("hooks"), mod.optBoolean("broken"))
            },
            broken = root.optStrings("broken"),
            errors = root.optStrings("errors"),
            logs = root.optStrings("logs"),
            safeMode = root.optBoolean("safeMode")
        )
    } catch (_: Exception) {
        null
    }

    private fun JSONObject.optStrings(name: String): List<String> {
        val array = optJSONArray(name) ?: return emptyList()
        return List(array.length()) { array.getString(it) }
    }
}
