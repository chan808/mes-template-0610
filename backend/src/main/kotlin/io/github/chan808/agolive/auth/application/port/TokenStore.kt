package io.github.chan808.agolive.auth.application.port

import io.github.chan808.agolive.auth.domain.RefreshTokenSession

interface TokenStore {
    fun save(sid: String, session: RefreshTokenSession, ttlSeconds: Long)
    fun find(sid: String): RefreshTokenSession?
    fun deleteSession(userId: Long, sid: String)
    fun tryLock(sid: String): Boolean
    fun releaseLock(sid: String)
    fun addSession(userId: Long, sid: String)
    fun deleteAllSessionsForUser(userId: Long)
    fun findAccessTokenVersion(userId: Long): Long?
    fun cacheAccessTokenVersion(userId: Long, tokenVersion: Long)
    fun deleteAccessTokenVersion(userId: Long)
}
