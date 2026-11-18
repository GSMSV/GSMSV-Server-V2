package com.gsmsv.api.infra.job

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Infra 작업 상태 조회 (Contracts-V2 `openapi/internal.yaml`의 `GET /jobs/{job_id}`).
 *
 * Infra는 끝난 작업을 1시간 이상 남기고, 다시 뜨면 기록을 잃는다. 기록이 없으면 null이다.
 */
interface JobInfraClient {
    /**
     * @throws com.gsmsv.api.global.exception.BusinessException Infra가 응답하지 않거나 응답을 읽을 수 없으면
     */
    fun getJob(jobId: String): InfraJob?
}

/**
 * Infra `Job`. [status]·[stage]는 Infra가 보낸 문자열 그대로다. 해석은 operation 도메인이 한다.
 * 시각 칸처럼 Server가 쓰지 않는 칸과 Infra가 나중에 더하는 칸은 무시한다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class InfraJob(
    @param:JsonProperty("job_id") val jobId: String,
    @param:JsonProperty("type") val type: String,
    @param:JsonProperty("resource_id") val resourceId: String,
    @param:JsonProperty("status") val status: String,
    @param:JsonProperty("stage") val stage: String,
    @param:JsonProperty("queue_position") val queuePosition: Int,
    @param:JsonProperty("error") val error: InfraJobError? = null,
)

/** Infra `JobError`. [details]에는 구현 값이 들어 있어 사용자에게 내보내지 않는다 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class InfraJobError(
    @param:JsonProperty("code") val code: String,
    @param:JsonProperty("retryable") val retryable: Boolean,
    @param:JsonProperty("details") val details: Map<String, Any?>? = null,
)
