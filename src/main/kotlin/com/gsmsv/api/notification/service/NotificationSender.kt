package com.gsmsv.api.notification.service

import com.gsmsv.api.global.authorization.Owner
import com.gsmsv.api.notification.domain.NotificationCategory

/** 알림에 붙는 관련 자원. 자원이 지워져도 남도록 GRN과 이름을 값으로 받는다 */
data class NotificationResource(
    val grn: String,
    val name: String,
)

data class NotificationMessage(
    val category: NotificationCategory,
    val title: String,
    val body: String,
    val resource: NotificationResource? = null,
)

/**
 * 다른 도메인이 알림을 보낼 때 쓰는 공개 인터페이스.
 *
 * 받는 사람이 팀이면 팀원 전원에게 보낸다. 호출한 쪽의 트랜잭션 안에서 알림을 저장하고,
 * 이메일·Discord 같은 외부 채널 발송은 그 트랜잭션이 커밋된 뒤에 한다.
 * 채널 발송이 실패해도 호출한 쪽은 실패하지 않는다.
 *
 * **(미정)** 발송 인터페이스의 모양은 계약에 없다. 쓰는 도메인이 생기면 함께 합의해 고친다.
 */
interface NotificationSender {
    /** [recipient]는 사용자 또는 팀이다 */
    fun send(
        recipient: Owner,
        message: NotificationMessage,
    )
}
