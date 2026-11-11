package com.gsmsv.api.team.service

import com.gsmsv.api.global.authorization.Action
import com.gsmsv.api.global.authorization.Actor
import com.gsmsv.api.global.authorization.TeamLoader
import com.gsmsv.api.global.exception.BusinessException
import com.gsmsv.api.global.exception.ErrorCode
import com.gsmsv.api.global.grn.IdGenerator
import com.gsmsv.api.global.grn.IdParser
import com.gsmsv.api.global.response.Cursor
import com.gsmsv.api.global.response.CursorPage
import com.gsmsv.api.global.response.CursorPageRequest
import com.gsmsv.api.team.config.SchoolEmailProperties
import com.gsmsv.api.team.domain.Team
import com.gsmsv.api.team.domain.TeamInvitation
import com.gsmsv.api.team.domain.TeamStatus
import com.gsmsv.api.team.dto.InvitationResponse
import com.gsmsv.api.team.repository.TeamInvitationRepository
import com.gsmsv.api.user.service.UserService
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.HexFormat

@Service
class TeamInvitationService(
    private val invitationRepository: TeamInvitationRepository,
    private val teamLoader: TeamLoader,
    private val teamService: TeamService,
    private val userService: UserService,
    private val mailSender: InvitationMailSender,
    private val schoolEmailProperties: SchoolEmailProperties,
) {
    @Transactional
    fun invite(
        actor: Actor,
        teamId: String,
        email: String,
    ) {
        val team = teamLoader.load(actor, teamId, Action.INVITE_MEMBER, teamService)
        if (team.status == TeamStatus.REJECTED) throw BusinessException(ErrorCode.TEAM_NOT_APPROVED)
        val normalizedEmail = email.trim().lowercase()
        validateEmail(normalizedEmail)
        if (userService.findByEmail(normalizedEmail)?.let { teamService.findRole(team.id, it.id) != null } == true) {
            throw BusinessException(ErrorCode.ALREADY_MEMBER)
        }
        val now = Instant.now()
        val token = newToken()
        val hash = hashToken(token)
        val existing = invitationRepository.findByTeamIdAndEmailAndAcceptedAtIsNullAndCanceledAtIsNull(team.id, normalizedEmail)
        try {
            if (existing == null) {
                invitationRepository.saveAndFlush(
                    TeamInvitation(IdGenerator.invitationId(), team.id, normalizedEmail, hash, now, now.plus(EXPIRY_DAYS, ChronoUnit.DAYS)),
                )
            } else {
                existing.reissue(hash, now, now.plus(EXPIRY_DAYS, ChronoUnit.DAYS))
            }
        } catch (_: DataIntegrityViolationException) {
            throw BusinessException(ErrorCode.CONFLICT)
        }
        sendAfterCommit(normalizedEmail, token)
    }

    @Transactional(readOnly = true)
    fun list(
        actor: Actor,
        teamId: String,
        page: CursorPageRequest,
    ): CursorPage<InvitationResponse> {
        val team = teamLoader.load(actor, teamId, Action.INVITE_MEMBER, teamService)
        val cursor = page.decodedCursor()
        val rows =
            invitationRepository.findActivePage(
                team.id,
                Instant.now(),
                cursor?.createdAt,
                cursor?.id,
                PageRequest.of(
                    0,
                    page.limit + 1,
                ),
            )
        return CursorPage.of(rows, page.limit, { Cursor(it.sentAt, it.id) }) {
            InvitationResponse(it.id, it.email, it.sentAt, it.expiresAt)
        }
    }

    @Transactional
    fun cancel(
        actor: Actor,
        teamId: String,
        invitationId: String,
    ) {
        val team = teamLoader.load(actor, teamId, Action.INVITE_MEMBER, teamService)
        IdParser.requireInvitationId(invitationId)
        val invitation = invitationRepository.findByIdAndTeamId(invitationId, team.id)
        if (invitation == null || invitation.acceptedAt != null || invitation.canceledAt != null ||
            !invitation.expiresAt.isAfter(Instant.now())
        ) {
            throw BusinessException(ErrorCode.RESOURCE_NOT_FOUND)
        }
        invitation.cancel(Instant.now())
    }

    private fun validateEmail(email: String) {
        val domain = schoolEmailProperties.emailDomain.trim().lowercase()
        if (domain.isBlank() || email.substringAfterLast('@').lowercase() != domain) {
            throw BusinessException(ErrorCode.VALIDATION_FAILED)
        }
    }

    private fun sendAfterCommit(
        email: String,
        token: String,
    ) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            mailSender.send(email, token)
            return
        }
        TransactionSynchronizationManager.registerSynchronization(
            object : TransactionSynchronization {
                override fun afterCommit() {
                    mailSender.send(email, token)
                }
            },
        )
    }

    private fun newToken(): String =
        ByteArray(TOKEN_BYTES).also(random::nextBytes).let {
            java.util.Base64
                .getUrlEncoder()
                .withoutPadding()
                .encodeToString(it)
        }

    private fun hashToken(token: String): String =
        MessageDigest.getInstance("SHA-256").digest(token.toByteArray()).let {
            HexFormat.of().formatHex(it)
        }

    companion object {
        private const val EXPIRY_DAYS = 7L
        private const val TOKEN_BYTES = 32
        private val random = SecureRandom()
    }
}
