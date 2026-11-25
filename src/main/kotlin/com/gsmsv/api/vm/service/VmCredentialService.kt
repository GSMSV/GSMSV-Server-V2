package com.gsmsv.api.vm.service

import com.gsmsv.api.audit.domain.AuditEventType
import com.gsmsv.api.audit.service.AuditRecorder
import com.gsmsv.api.global.authorization.Actor
import com.gsmsv.api.global.grn.GrnParser
import com.gsmsv.api.vm.crypto.VmPasswordCipher
import com.gsmsv.api.vm.domain.Vm
import com.gsmsv.api.vm.dto.VmCredentialResponse
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * VM 초기 비밀번호 조회(4.7). 로더가 돌려준 [Vm]을 받으므로 권한 판단은 끝난 상태다.
 *
 * 볼 때마다 감사 로그를 남긴다. 감사 로그에는 대상 GRN만 넣고 비밀번호 값은 넣지 않는다.
 * 암호화 키를 설정하지 않으면 빈이 없어, 기동이 아니라 호출할 때 실패한다.
 */
@Service
class VmCredentialService(
    private val cipherProvider: ObjectProvider<VmPasswordCipher>,
    private val auditRecorder: AuditRecorder,
) {
    /** 감사 기록이 실패하면 비밀번호를 내주지 않는다. 그래서 기록을 응답보다 먼저 한다 */
    @Transactional
    fun credentials(
        actor: Actor,
        vm: Vm,
    ): VmCredentialResponse {
        val password = cipher().decrypt(vm.initialPasswordEncrypted)
        auditRecorder.record(actor, AuditEventType.VM_CREDENTIALS_READ, GrnParser.format(vm.owner.id, vm.id))
        return VmCredentialResponse(
            user = vm.initialUsername,
            initialPassword = password,
            notice = NOTICE,
        )
    }

    private fun cipher(): VmPasswordCipher =
        checkNotNull(cipherProvider.ifAvailable) { "VM 비밀번호 암호화 키(gsmsv.vm.encryption-key)가 설정되지 않았습니다." }

    private companion object {
        const val NOTICE = "보안을 위해 비밀번호를 바꾸거나 SSH 키를 등록하세요."
    }
}
