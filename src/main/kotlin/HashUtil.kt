package dev.schlaubi.role_assigner

import java.security.MessageDigest

fun String.sha256(): String {
    val bytes = toByteArray()
    val md = MessageDigest.getInstance("SHA-256")
    val digest = md.digest(bytes)
    return digest.toHexString()
}
