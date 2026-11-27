package com.gsmsv.api.infra.vm

import com.gsmsv.api.infra.GoApiCaller
import com.gsmsv.api.infra.config.InfraProfiles
import com.gsmsv.api.infra.job.InfraJobAccepted
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

/** 실제 Go 연동. `dev`·`local`이 아닌 프로필에서 쓴다 */
@Component
@Profile(InfraProfiles.REAL)
class HttpVmInfraClient(
    goRestClient: RestClient,
) : VmInfraClient {
    private val caller = GoApiCaller(goRestClient)

    override fun createVm(
        request: InfraVmCreateRequest,
        idempotencyKey: String,
    ): InfraJobAccepted =
        caller.post(
            operation = "vm.create",
            uri = "/vms",
            uriVariables = emptyMap(),
            headers = mapOf(IDEMPOTENCY_KEY to idempotencyKey),
            body = request,
            responseType = InfraJobAccepted::class.java,
        )

    override fun deleteVm(
        vmId: String,
        idempotencyKey: String,
    ): InfraJobAccepted? =
        caller.deleteOrNull(
            operation = "vm.delete",
            uri = "/vms/{vmId}",
            uriVariables = mapOf("vmId" to vmId),
            headers = mapOf(IDEMPOTENCY_KEY to idempotencyKey),
            responseType = InfraJobAccepted::class.java,
        )

    override fun getVm(vmId: String): InfraVmState? =
        caller.getOrNull(
            operation = "vm.get",
            uri = "/vms/{vmId}",
            uriVariables = mapOf("vmId" to vmId),
            responseType = InfraVmState::class.java,
        )

    private companion object {
        const val IDEMPOTENCY_KEY = "Idempotency-Key"
    }
}
