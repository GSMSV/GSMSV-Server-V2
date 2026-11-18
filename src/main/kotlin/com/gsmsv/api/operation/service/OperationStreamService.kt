package com.gsmsv.api.operation.service

import com.gsmsv.api.operation.config.OperationProperties
import com.gsmsv.api.operation.domain.Operation
import com.gsmsv.api.operation.dto.OperationResponse
import com.gsmsv.api.operation.repository.OperationRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.http.MediaType
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

private val log = KotlinLogging.logger {}

/**
 * operation 진행 스트림(SSE). 이벤트 형식은 Contracts-V2 `openapi/operation.yaml`을 따른다.
 *
 * - 연결하자마자 지금 상태를 보내고, 그 뒤 바뀔 때마다 `Operation` 전체를 보낸다. 끝난 상태를 보내면 닫는다
 * - 상태를 바꾸는 폴러가 다른 인스턴스에 있을 수 있어서, 이벤트를 넘겨받지 않고 연결된 operation을 DB에서 짧은 주기로 다시 읽는다
 * - [subscribe]에는 작업 로더를 통과한 operation만 넘긴다. 권한은 연결할 때 한 번 판단한다
 */
@Service
class OperationStreamService(
    private val operationRepository: OperationRepository,
    private val properties: OperationProperties,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val subscriptions = ConcurrentHashMap<SseEmitter, Subscription>()

    /** 지금 열려 있는 연결 수 */
    val activeCount: Int
        get() = subscriptions.size

    fun subscribe(operation: Operation): SseEmitter {
        val emitter = SseEmitter(properties.streamTimeout.toMillis())
        if (!send(emitter, operation)) return emitter
        if (operation.status.finished) {
            emitter.complete()
            return emitter
        }
        subscriptions[emitter] = Subscription(operation.id, operation.updatedAt, clock.instant())
        emitter.onCompletion { subscriptions.remove(emitter) }
        emitter.onTimeout { emitter.complete() }
        emitter.onError { subscriptions.remove(emitter) }
        return emitter
    }

    /** 연결된 operation이 바뀌었으면 보내고, 오래 조용했으면 연결 유지용 주석 줄을 보낸다 */
    @Scheduled(fixedDelayString = "\${gsmsv.operation.stream-check-interval:PT1S}")
    @Transactional(readOnly = true)
    fun push() {
        if (subscriptions.isEmpty()) return
        val now = clock.instant()
        val operations = operationRepository.findAllById(subscriptions.values.map { it.operationId }.toSet()).associateBy { it.id }
        for ((emitter, subscription) in subscriptions) {
            val operation = operations[subscription.operationId]
            when {
                // 정리돼 사라졌으면 더 보낼 것이 없다
                operation == null -> {
                    close(emitter)
                }

                operation.updatedAt != subscription.lastUpdatedAt -> {
                    if (send(emitter, operation)) {
                        subscription.lastUpdatedAt = operation.updatedAt
                        subscription.lastSentAt = now
                        if (operation.status.finished) close(emitter)
                    }
                }

                !subscription.lastSentAt.plus(properties.streamHeartbeat).isAfter(now) -> {
                    if (ping(emitter)) subscription.lastSentAt = now
                }
            }
        }
    }

    private fun send(
        emitter: SseEmitter,
        operation: Operation,
    ): Boolean =
        trySend(emitter) {
            emitter.send(
                SseEmitter
                    .event()
                    .name(EVENT_NAME)
                    .data(OperationResponse.from(operation), MediaType.APPLICATION_JSON),
            )
        }

    private fun ping(emitter: SseEmitter): Boolean = trySend(emitter) { emitter.send(SseEmitter.event().comment("ping")) }

    private fun trySend(
        emitter: SseEmitter,
        block: () -> Unit,
    ): Boolean =
        try {
            block()
            true
        } catch (e: IOException) {
            // 화면이 연결을 끊은 경우다. 다시 연결하면 지금 상태부터 받는다
            log.debug { "SSE 전송 실패로 연결을 닫는다: ${e.javaClass.simpleName}" }
            subscriptions.remove(emitter)
            emitter.completeWithError(e)
            false
        } catch (e: IllegalStateException) {
            // 이미 닫힌 연결
            subscriptions.remove(emitter)
            false
        }

    private fun close(emitter: SseEmitter) {
        subscriptions.remove(emitter)
        emitter.complete()
    }

    private class Subscription(
        val operationId: String,
        var lastUpdatedAt: Instant,
        var lastSentAt: Instant,
    )

    private companion object {
        /** SSE `event:` 줄의 값. 계약 `openapi/operation.yaml`의 이벤트 이름과 같아야 화면이 받는다 */
        const val EVENT_NAME = "operation"
    }
}
