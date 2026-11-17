package com.drdisagree.glasslayer.data.repository

import android.content.Context
import com.drdisagree.glasslayer.R
import com.drdisagree.glasslayer.domain.model.License
import com.drdisagree.glasslayer.domain.repository.LicenseRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LicenseRepositoryImpl(private val context: Context) : LicenseRepository {

    override suspend fun licenses(): List<License> = withContext(Dispatchers.IO) {
        LICENSES.mapNotNull { (name, file) ->
            try {
                License(context.getString(name), context.assets.open("$LICENSE_DIR/$file").bufferedReader().use { it.readText() })
            } catch (_: Exception) {
                null
            }
        }
    }
}

private const val LICENSE_DIR = "licenses"
private val LICENSES = listOf(
    R.string.license_liquid_glass to "android_liquid_glass_apache.txt",
    R.string.license_roboto_flex to "roboto_flex_ofl.txt",
    R.string.license_android_robot to "android_robot_cc_by.txt"
)
