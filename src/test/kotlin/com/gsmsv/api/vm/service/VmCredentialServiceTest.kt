package com.gsmsv.api.vm.service

import com.gsmsv.api.audit.domain.AuditEventType
import com.gsmsv.api.audit.service.AuditRecorder
import com.gsmsv.api.global.authorization.Owner
import com.gsmsv.api.global.authorization.UserActor
import com.gsmsv.api.vm.crypto.VmPasswordCipher
import com.gsmsv.api.vm.domain.Vm
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.ObjectProvider
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class VmCredentialServiceTest {
    // 테스트 전용 키다 (32바이트를 Base64로 인코딩한 값)
    private val cipher = VmPasswordCipher("dGVzdC1vbmx5LWtleS10ZXN0LW9ubHkta2V5LXRlc3Q=")
    private val auditRecorder = mockk<AuditRecorder>(relaxed = true)
    private val service = VmCredentialService(provide(cipher), auditRecorder)
    private val actor = UserActor(ME)

    @Test
    fun `저장된 비밀번호를 풀어 초기 계정과 안내 문구를 함께 준다`() {
        val response = service.credentials(actor, vm(Owner.User(ME)))

        assertEquals("ubuntu", response.user)
        assertEquals("example-password-not-real", response.initialPassword)
        assertEquals("보안을 위해 비밀번호를 바꾸거나 SSH 키를 등록하세요.", response.notice)
        verify(exactly = 1) { auditRecorder.record(actor, AuditEventType.VM_CREDENTIALS_READ, "grn:$ME:$VM_ID", emptyMap()) }
    }

    @Test
    fun `팀 VM은 팀 GRN으로 감사 로그를 남긴다`() {
        service.credentials(actor, vm(Owner.Team(TEAM_ID)))

        verify(exactly = 1) { auditRecorder.record(actor, AuditEventType.VM_CREDENTIALS_READ, "grn:$TEAM_ID:$VM_ID", emptyMap()) }
    }

    @Test
    fun `볼 때마다 감사 로그를 남긴다`() {
        val vm = vm(Owner.User(ME))

        service.credentials(actor, vm)
        service.credentials(actor, vm)

        verify(exactly = 2) { auditRecorder.record(actor, AuditEventType.VM_CREDENTIALS_READ, any(), any()) }
    }

    @Test
    fun `감사 로그 기록이 실패하면 비밀번호를 내주지 않는다`() {
        every { auditRecorder.record(any(), any(), any(), any()) } throws IllegalStateException("기록 실패")

        assertFailsWith<IllegalStateException> { service.credentials(actor, vm(Owner.User(ME))) }
    }

    @Test
    fun `암호화 키가 없으면 감사 로그 없이 실패한다`() {
        val withoutKey = VmCredentialService(mockk { every { ifAvailable } returns null }, auditRecorder)

        assertFailsWith<IllegalStateException> { withoutKey.credentials(actor, vm(Owner.User(ME))) }
        verify(exactly = 0) { auditRecorder.record(any(), any(), any(), any()) }
    }

    @Test
    fun `응답의 toString에 비밀번호가 나오지 않는다`() {
        val response = service.credentials(actor, vm(Owner.User(ME)))

        assertFalse(response.toString().contains("example-password-not-real"))
    }

    private fun vm(owner: Owner) =
        Vm.create(
            id = VM_ID,
            name = "테스트 VM",
            purpose = null,
            owner = owner,
            image = "ubuntu-24.04",
            vcpu = 2,
            ramGb = 4,
            ssdGb = 20,
            initialUsername = "ubuntu",
            initialPasswordEncrypted = cipher.encrypt("example-password-not-real"),
            createdBy = ME,
            expiresAt = Instant.parse("2027-01-01T00:00:00Z"),
        )

    private inline fun <reified T : Any> provide(value: T): ObjectProvider<T> =
        mockk<ObjectProvider<T>> {
            every { ifAvailable } returns
                value
        }

    companion object {
        private const val ME = "user-a1b2c3d4e5f6"
        private const val TEAM_ID = "team-4p8w2k9m1xza"
        private const val VM_ID = "vm-k3m9x2p7q4ta"
    }
}
