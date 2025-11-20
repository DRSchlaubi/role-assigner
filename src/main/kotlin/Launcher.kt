package dev.schlaubi.role_assigner

import com.github.ajalt.clikt.command.main

suspend fun main(args: Array<String>) = MainCommand().main(args)
