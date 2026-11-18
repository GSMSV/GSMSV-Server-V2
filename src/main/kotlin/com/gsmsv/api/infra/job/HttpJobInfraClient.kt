package com.gsmsv.api.infra.job

import com.gsmsv.api.infra.GoApiCaller
import com.gsmsv.api.infra.config.InfraProfiles
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

/** 실제 Go 연동. `dev`·`local`이 아닌 프로필에서 쓴다 */
@Component
@Profile(InfraProfiles.REAL)
class HttpJobInfraClient(
    goRestClient: RestClient,
) : JobInfraClient {
    private val caller = GoApiCaller(goRestClient)

    override fun getJob(jobId: String): InfraJob? =
        caller.getOrNull(
            operation = "job.get",
            uri = "/jobs/{jobId}",
            uriVariables = mapOf("jobId" to jobId),
            responseType = InfraJob::class.java,
        )
}
