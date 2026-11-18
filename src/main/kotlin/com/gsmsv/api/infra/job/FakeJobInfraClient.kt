package com.gsmsv.api.infra.job

import com.gsmsv.api.global.exception.BusinessException
import com.gsmsv.api.infra.config.InfraProfiles
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

/**
 * Go 연동 전까지 쓰는 모의 구현 (`dev`·`local`). [put]으로 넣은 작업 상태를 그대로 돌려준다.
 * [failWith]를 지정하면 다음 호출부터 그 오류로 실패한다.
 */
@Component
@Profile(InfraProfiles.MOCK)
class FakeJobInfraClient : JobInfraClient {
    private val jobs = ConcurrentHashMap<String, InfraJob>()
    private val failure = AtomicReference<BusinessException?>(null)

    fun put(job: InfraJob) {
        jobs[job.jobId] = job
    }

    fun remove(jobId: String) {
        jobs.remove(jobId)
    }

    fun failWith(exception: BusinessException?) = failure.set(exception)

    fun reset() {
        jobs.clear()
        failure.set(null)
    }

    override fun getJob(jobId: String): InfraJob? {
        failure.get()?.let { throw it }
        return jobs[jobId]
    }
}
