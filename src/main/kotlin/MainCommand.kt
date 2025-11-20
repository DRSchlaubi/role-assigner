package dev.schlaubi.role_assigner

import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.parameters.options.*
import com.github.ajalt.clikt.parameters.types.path
import dev.kord.common.entity.Snowflake
import dev.schlaubi.role_assigner.options.snowflake
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlin.coroutines.CoroutineContext
import kotlin.io.path.Path
import kotlin.io.path.readText

class MainCommand : SuspendingCliktCommand("role_assigner"), CoroutineScope {
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.IO + SupervisorJob()
    val tokenFile by option("--token-file", "-t", help = "File containing the bot token", envvar = "TOKEN_FILE").path(
        mustExist = true,
        canBeFile = true,
        canBeDir = false,
        mustBeReadable = true
    ).default(Path("token.txt"))
    val userFile by option("--users-file", "-u", help = "File containing the user Ids", envvar = "USERS_FILE").path(
        mustExist = true,
        canBeFile = true,
        canBeDir = false,
        mustBeReadable = true
    ).default(Path("users.txt"))

    val guild by option("--guild", "-g", help="Guild to assign roles on")
        .snowflake().required()

    val roles by option("--role", "-r", help="Possible role to assign")
        .snowflake()
        .multiple(required = true)

    private fun readTokenList() = tokenFile.readText().lines().filter { it.isNotBlank() }

    private fun readUserIDList() = userFile.readText()
        .lineSequence().
        filter { it.isNotBlank() }
        .map { Snowflake(it) }
        .toList()

    override suspend fun run() {
        val job by lazy { AssignJob(this, readTokenList(), readUserIDList()) }

        job.start()
    }
}

