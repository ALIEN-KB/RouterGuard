package app.routerguard.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Remembers every device the app has seen, in a private JSON file. */
class DeviceStore(context: Context) {
    private val file = File(context.filesDir, "devices.json")

    fun load(): List<Device> = runCatching {
        val arr = JSONArray(file.readText())
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Device(
                mac = o.getString("mac"),
                name = o.optString("name"),
                label = o.optString("label"),
                ip = o.optString("ip"),
                ssid = o.optString("ssid", "SSID-1"),
                wifi = o.optBoolean("wifi", true),
                firstSeen = o.optLong("firstSeen"),
                lastSeen = o.optLong("lastSeen"),
                online = false,
                blocked = o.optBoolean("blocked"),
            )
        }
    }.getOrDefault(emptyList())

    fun save(devices: List<Device>) {
        val arr = JSONArray()
        devices.forEach {
            arr.put(
                JSONObject()
                    .put("mac", it.mac).put("name", it.name).put("label", it.label)
                    .put("ip", it.ip).put("ssid", it.ssid).put("wifi", it.wifi)
                    .put("firstSeen", it.firstSeen).put("lastSeen", it.lastSeen)
                    .put("blocked", it.blocked)
            )
        }
        file.writeText(arr.toString())
    }
}

data class Credentials(val host: String, val user: String, val password: String)

/** Saves the router login; the password is encrypted with a key that never leaves the phone's Keystore. */
class CredentialStore(context: Context) {
    private val prefs = context.getSharedPreferences("router", Context.MODE_PRIVATE)

    fun load(): Credentials? {
        val host = prefs.getString("host", null) ?: return null
        val user = prefs.getString("user", null) ?: return null
        val password = decrypt(prefs.getString("password", null) ?: return null) ?: return null
        return Credentials(host, user, password)
    }

    fun save(c: Credentials, mode: String = "huawei") {
        prefs.edit().putString("host", c.host).putString("user", c.user)
            .putString("password", encrypt(c.password)).putString("mode", mode).apply()
    }

    /** "huawei" = full control through the HG8546M driver, "browser" = router page only. */
    fun mode(): String = prefs.getString("mode", "huawei") ?: "huawei"

    fun clear() = prefs.edit().clear().apply()

    fun lastHost(): String = prefs.getString("host", null) ?: "192.168.100.1"
    fun lastUser(): String = prefs.getString("user", null) ?: "telecomadmin"

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    private fun encrypt(plain: String): String {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key())
        return Base64.encodeToString(c.iv + c.doFinal(plain.toByteArray()), Base64.NO_WRAP)
    }

    private fun decrypt(stored: String): String? = runCatching {
        val all = Base64.decode(stored, Base64.NO_WRAP)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, all.copyOfRange(0, 12)))
        String(c.doFinal(all.copyOfRange(12, all.size)))
    }.getOrNull()

    private companion object {
        const val ALIAS = "router_password_key"
    }
}
