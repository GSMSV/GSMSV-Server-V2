package com.gsmsv.api.operation.service

import org.springframework.stereotype.Component
import java.util.concurrent.Executors

/** 요청과 따로 뒤에서 이어지는 일을 실행한다. 테스트는 바로 실행하는 구현으로 바꾼다 */
fun interface OperationExecutor {
    fun execute(task: Runnable)
}

@Component
class VirtualThreadOperationExecutor : OperationExecutor {
    // 일은 공유 DB 서버를 기다리는 시간이 대부분이라 가상 스레드로 충분하다
    private val executor = Executors.newVirtualThreadPerTaskExecutor()

    override fun execute(task: Runnable) {
        executor.execute(task)
    }
}
