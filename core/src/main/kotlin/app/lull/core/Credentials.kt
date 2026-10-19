package app.lull.core

import java.security.MessageDigest

/**
 * The music.youtube.com session cookies captured at sign-in.
 * Requests are authorised the way the web client does it: a SAPISIDHASH header
 * built from the SAPISID cookie, the current time and the origin.
 */
class Credentials(val cookie: String) {

    val sapisid: String? = cookie.split(';')
        .map { it.trim() }
        .mapNotNull { part ->
            val eq = part.indexOf('=')
            if (eq <= 0) null else part.substring(0, eq) to part.substring(eq + 1)
        }
        .let { pairs ->
            pairs.firstOrNull { it.first == "SAPISID" }?.second
                ?: pairs.firstOrNull { it.first == "__Secure-3PAPISID" }?.second
        }

    fun authorization(origin: String, nowSec: Long = System.currentTimeMillis() / 1000): String? {
        val key = sapisid ?: return null
        return "SAPISIDHASH ${nowSec}_${sha1("$nowSec $key $origin")}"
    }

    private fun sha1(s: String): String =
        MessageDigest.getInstance("SHA-1").digest(s.toByteArray())
            .joinToString("") { "%02x".format(it) }
}
