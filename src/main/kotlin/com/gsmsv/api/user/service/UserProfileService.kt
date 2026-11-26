package com.gsmsv.api.user.service

import com.gsmsv.api.global.exception.BusinessException
import com.gsmsv.api.global.exception.ErrorCode
import com.gsmsv.api.global.security.AuthenticatedUser
import com.gsmsv.api.team.service.TeamService
import com.gsmsv.api.user.config.ProfileImageProperties
import com.gsmsv.api.user.domain.User
import com.gsmsv.api.user.dto.MeResponse
import com.gsmsv.api.user.dto.MyTeamResponse
import com.gsmsv.api.user.dto.ProfileImageResponse
import com.gsmsv.api.user.dto.toMeResponse
import com.gsmsv.api.user.repository.UserRepository
import jakarta.transaction.Transactional
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.web.multipart.MultipartFile

interface UserConnectionReader {
    fun findConnections(userId: String): Connections
}

data class Connections(
    val github: Boolean,
    val discord: Boolean,
)

private object EmptyUserConnectionReader : UserConnectionReader {
    override fun findConnections(userId: String) = Connections(github = false, discord = false)
}

@Service
class UserProfileService(
    private val userRepository: UserRepository,
    private val teamService: TeamService,
    private val storage: ProfileImageStorage,
    private val properties: ProfileImageProperties,
    private val connectionReader: UserConnectionReader = EmptyUserConnectionReader,
) {
    @Transactional
    fun getMe(userId: String): MeResponse {
        val user = requireUser(userId)
        val teams =
            teamService.findMyTeamSummaries(userId).map {
                MyTeamResponse(it.id, it.name, it.role.name.lowercase(), it.status.name.lowercase())
            }
        val connections = connectionReader.findConnections(userId)
        return user.toMeResponse(teams, connections.github, connections.discord)
    }

    @Transactional
    fun updateProfileImage(
        userId: String,
        file: MultipartFile,
    ): ProfileImageResponse {
        val user = requireUser(userId)
        if (file.size > properties.maxFileSize.toBytes()) throw BusinessException(ErrorCode.PAYLOAD_TOO_LARGE)
        val bytes = file.bytes
        if (bytes.size > properties.maxFileSize.toBytes()) throw BusinessException(ErrorCode.PAYLOAD_TOO_LARGE)
        val fileName =
            try {
                storage.save(bytes)
            } catch (_: IllegalArgumentException) {
                throw BusinessException(ErrorCode.UNSUPPORTED_IMAGE)
            }
        val old = user.profileImageFileName
        try {
            user.changeProfileImage(fileName)
            userRepository.save(user)
            registerAfterCommit(
                onCommit = { old?.let(storage::delete) },
                onRollback = { storage.delete(fileName) },
            )
        } catch (exception: RuntimeException) {
            storage.delete(fileName)
            throw exception
        }
        return ProfileImageResponse("/api/v2/files/profile-images/$fileName")
    }

    @Transactional
    fun clearProfileImage(userId: String) {
        val user = requireUser(userId)
        val old = user.profileImageFileName ?: return
        user.clearProfileImage()
        userRepository.save(user)
        registerAfterCommit(onCommit = { storage.delete(old) })
    }

    @Transactional
    fun readProfileImage(
        requester: AuthenticatedUser,
        fileName: String,
    ): ByteArray {
        val owner =
            userRepository.findByProfileImageFileNameAndDeletedAtIsNull(fileName)
                ?: throw BusinessException(ErrorCode.RESOURCE_NOT_FOUND)
        if (requester.role.name != "ADMIN" && requester.userId != owner.id &&
            !teamService.sharesTeam(requester.userId, owner.id)
        ) {
            throw BusinessException(ErrorCode.RESOURCE_NOT_FOUND)
        }
        return storage.read(fileName) ?: throw BusinessException(ErrorCode.RESOURCE_NOT_FOUND)
    }

    private fun requireUser(userId: String): User =
        userRepository.findById(userId).orElseThrow { BusinessException(ErrorCode.RESOURCE_NOT_FOUND) }

    private fun registerAfterCommit(
        onCommit: () -> Unit,
        onRollback: (() -> Unit)? = null,
    ) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            onCommit()
            return
        }
        TransactionSynchronizationManager.registerSynchronization(
            object : TransactionSynchronization {
                override fun afterCommit() = onCommit()

                override fun afterCompletion(status: Int) {
                    if (status != TransactionSynchronization.STATUS_COMMITTED) onRollback?.invoke()
                }
            },
        )
    }
}
