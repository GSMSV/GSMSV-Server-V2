package com.gsmsv.api.notification

import com.gsmsv.api.TestcontainersConfiguration
import com.gsmsv.api.global.authorization.Owner
import com.gsmsv.api.global.grn.IdGenerator
import com.gsmsv.api.notification.domain.NotificationCategory
import com.gsmsv.api.notification.domain.NotificationGroup
import com.gsmsv.api.notification.domain.NotificationSetting
import com.gsmsv.api.notification.domain.NotificationSettingId
import com.gsmsv.api.notification.repository.NotificationRepository
import com.gsmsv.api.notification.repository.NotificationSettingRepository
import com.gsmsv.api.notification.service.NotificationMessage
import com.gsmsv.api.notification.service.NotificationResource
import com.gsmsv.api.notification.service.NotificationSender
import com.gsmsv.api.notification.service.NotificationService
import com.gsmsv.api.user.domain.User
import com.gsmsv.api.user.repository.UserRepository
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.data.domain.PageRequest
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertEquals

@Import(TestcontainersConfiguration::class)
@SpringBootTest(properties = ["gsmsv.security.jwt.secret=dGVzdC1vbmx5LWtleS10ZXN0LW9ubHkta2V5LXRlc3Q="])
@Tag("integration")
@Transactional
class NotificationPersistenceTest
    @Autowired
    constructor(
        private val sender: NotificationSender,
        private val notificationRepository: NotificationRepository,
        private val settingRepository: NotificationSettingRepository,
        private val userRepository: UserRepository,
    ) {
        @Test
        fun `알림을 저장하고 자원이 지워져도 GRN과 이름이 남는다`() {
            val userId = saveUser()

            sender.send(Owner.User(userId), message())
            notificationRepository.flush()

            val saved = notificationRepository.findByUserIdOrderByCreatedAtAsc(userId, PageRequest.of(0, 10))
            assertEquals(1, saved.size)
            assertEquals("주문 서비스 DB", saved.single().resourceName)
        }

        @Test
        fun `사용자당 50건을 넘으면 오래된 것부터 지운다`() {
            val userId = saveUser()

            repeat(NotificationService.MAX_PER_USER + 3) { sender.send(Owner.User(userId), message()) }
            notificationRepository.flush()

            assertEquals(NotificationService.MAX_PER_USER.toLong(), notificationRepository.countByUserId(userId))
        }

        @Test
        fun `그룹별 설정을 저장하고 사용자 id로 다시 읽는다`() {
            val userId = saveUser()
            settingRepository.saveAndFlush(NotificationSetting(NotificationSettingId(userId, NotificationGroup.USAGE), false, true))

            val found = settingRepository.findAllByIdUserId(userId)

            assertEquals(1, found.size)
            assertEquals(false, found.single().email)
            assertEquals(true, found.single().discord)
        }

        private fun saveUser(): String = userRepository.saveAndFlush(newUser()).id

        private fun newUser(): User {
            val id = IdGenerator.userId()
            return User(id = id, dataGsmId = "dg-$id", email = "$id@gsm.hs.kr", name = "알림테스트")
        }

        private fun message() =
            NotificationMessage(
                NotificationCategory.DB_STORAGE_80,
                "DB 용량이 80%에 도달했습니다",
                "주문 서비스 DB가 한도의 80%를 썼습니다.",
                NotificationResource("grn:team-4p8w2k9m1xza:db-k3m9x2p7q4ta", "주문 서비스 DB"),
            )
    }
