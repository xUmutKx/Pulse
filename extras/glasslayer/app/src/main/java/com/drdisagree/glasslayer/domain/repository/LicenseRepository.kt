package com.drdisagree.glasslayer.domain.repository

import com.drdisagree.glasslayer.domain.model.License

interface LicenseRepository {
    suspend fun licenses(): List<License>
}
