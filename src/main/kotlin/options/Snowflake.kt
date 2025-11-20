package dev.schlaubi.role_assigner.options

import com.github.ajalt.clikt.parameters.options.RawOption
import com.github.ajalt.clikt.parameters.options.convert
import dev.kord.common.entity.Snowflake

fun RawOption.snowflake() = convert { value ->
    try {
        Snowflake(value)
    } catch (e: Exception) {
        fail(e.message ?: "Cannot convert $value to snowflake")
    }
}
