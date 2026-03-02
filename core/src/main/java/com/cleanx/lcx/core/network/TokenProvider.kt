package com.cleanx.lcx.core.network

interface TokenProvider {
    fun getAccessToken(): String?
}
