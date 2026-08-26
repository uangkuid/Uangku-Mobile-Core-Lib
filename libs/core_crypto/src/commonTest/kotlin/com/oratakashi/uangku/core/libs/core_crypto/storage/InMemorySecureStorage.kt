package com.oratakashi.uangku.core.libs.core_crypto.storage

/**
 * Test fake for [SecureStorage]. Possible only because SecureStorage is an interface, not an
 * `expect class` — the exact reason the old expect-class design was replaced.
 */
class InMemorySecureStorage : SecureStorage {
    private val map = mutableMapOf<String, String>()
    override suspend fun put(key: String, value: String) { map[key] = value }
    override suspend fun get(key: String): String? = map[key]
    override suspend fun remove(key: String) { map.remove(key) }
    override suspend fun clear() { map.clear() }
    override suspend fun contains(key: String): Boolean = map.containsKey(key)
}
