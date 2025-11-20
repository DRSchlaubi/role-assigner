package dev.schlaubi.role_assigner

import dev.kord.common.entity.Permission
import dev.kord.common.entity.Permissions
import dev.kord.common.entity.Snowflake
import dev.kord.core.Kord
import dev.kord.core.behavior.edit
import dev.kord.core.behavior.requestMembers
import dev.kord.core.entity.Guild
import dev.kord.core.entity.Role
import dev.kord.core.entity.User
import dev.kord.core.event.gateway.ReadyEvent
import dev.kord.core.on
import dev.kord.gateway.Intent
import dev.kord.gateway.PrivilegedIntent
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.flow.toList
import kotlin.math.ceil
import kotlin.system.exitProcess

private val requiredPermissions = Permissions(Permission.ManageRoles)

private val LOG = KotlinLogging.logger { }

class AssignJob(
    private val command: MainCommand,
    private val tokens: List<String>, private val users: List<Snowflake>
) {
    private val usersPerToken = ceil( users.size / tokens.size.toDouble()).toInt()

    suspend fun start(users: List<Snowflake> = this.users) = coroutineScope {
        val workerDescriptors =
            tokens.zip(users.chunked(usersPerToken))
        val workers = workerDescriptors.map { (token, users) -> Worker(token, users, command, this) }

        var fail = false
        workers.map { worker ->
            async {
                try {
                    worker.ensureReady()
                } catch (e: Exception) {
                    command.echo("Worker ($worker) failed:", err = true)
                    e.printStackTrace()
                    fail = true
                }
            }
        }.awaitAll()
        try {
            if (fail) return@coroutineScope

            workers.map { worker ->
                val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
                    command.echo("Worker ($worker) failed:", err = true)
                    throwable.printStackTrace()
                }

                async(exceptionHandler) {
                    worker.ensureRoles()
                }
            }.awaitAll()

            command.echo("All workers finished")
        } finally {
            workers.forEach {
                it.close()
            }
            exitProcess(if (fail) 1 else 0)
        }
    }
}

private class Worker(
    private val token: String,
    private val users: List<Snowflake>,
    private val command: MainCommand,
    private val coroutineScope: CoroutineScope
) {
    val id = token.sha256()
    lateinit var kord: Kord
    lateinit var selfInfo: User
    lateinit var guild: Guild
    lateinit var targetRoles: List<Role>

    suspend fun close() {
        if (::kord.isInitialized) {
            kord.logout()
            kord.resources.httpClient.close()
        }
    }

    @OptIn(PrivilegedIntent::class)
    suspend fun ensureReady() {
        kord = Kord(token)
        val readyCheck = CompletableDeferred<Unit>()
        kord.on<ReadyEvent> { readyCheck.complete(Unit)  }
        coroutineScope.launch {
            kord.login {
                intents {
                    +Intent.GuildMembers
                }
            }
        }

        LOG.info { "Waiting for worker: $id to become ready" }
        readyCheck.await()
        LOG.info { "Worker $id is ready, requesting initial info" }

        selfInfo = kord.getSelf()
        guild = kord.getGuild(command.guild)
        targetRoles = command.roles.mapNotNull { guild.getRoleOrNull(it) }

        LOG.info { "Requesting guild members for worker: $id" }

        guild.requestMembers {
            query = ""
            limit = 0
        }.toList()

        val member = selfInfo.asMember(guild.id)
        val highestRole = member.roles.toList().maxBy { it.rawPosition }
        if (requiredPermissions !in member.getPermissions()) {
            error("Bot does not have required permissions: ${requiredPermissions.values}")
        }

        targetRoles.forEach { role ->
            if (role.rawPosition >= highestRole.rawPosition) {
                error("Bot cannot assign this role: ${role.name}")
            }
        }

        LOG.info { "Worker: $id is ready" }
    }

    suspend fun ensureRoles() {
        LOG.info { "Got job to assign roles to ${users.size} users" }
        users.forEach { user ->
            val member = guild.getMemberOrNull(user)
            if (member == null) {
                LOG.warn { "User $user is not on guild, skipping" }
                return@forEach
            }

            if (member.roleIds.containsAll(command.roles)) {
                LOG.info { "User ${member.tag} already has all roles, skipping" }
            } else {
                LOG.info { "Adding roles to ${member.tag}" }
                try {
                    member.edit {
                        roles = (command.roles + member.roleIds).toMutableSet()
                    }
                } catch (e: Exception) {
                    LOG.error(e) { "Failed to add roles to ${member.tag}" }
                }
            }
        }
    }

    override fun toString(): String = if (::selfInfo.isInitialized) selfInfo.tag else id
}
