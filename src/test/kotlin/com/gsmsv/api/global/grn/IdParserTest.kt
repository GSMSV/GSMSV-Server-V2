package com.gsmsv.api.global.grn

import com.gsmsv.api.global.exception.BusinessException
import com.gsmsv.api.global.exception.ErrorCode
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.assertEquals

// 맞는/틀린 목록은 Contracts-V2 `constants/resource-id.md` 3·6장의 예시를 옮겨 왔다
class IdParserTest {
    @Test
    fun `맞는 사용자·팀 id는 그대로 돌려준다`() {
        assertEquals("user-8hq2m5v7c3kx", IdParser.requireUserId("user-8hq2m5v7c3kx"))
        assertEquals("team-4p8w2k9m1xza", IdParser.requireTeamId("team-4p8w2k9m1xza"))
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "",
            "user-8hq2m5v7c3k",
            "user-8hq2m5v7c3kxa",
            "user-8hq2m5v7c3kl",
            "USER-8hq2m5v7c3kx",
            "user_8hq2m5v7c3kx",
            "8hq2m5v7c3kx",
            "team-4p8w2k9m1xza",
            " user-8hq2m5v7c3kx",
            "user-8hq2m5v7c3kx\n",
        ],
    )
    fun `틀린 사용자 id는 INVALID_RESOURCE_ID로 거절한다`(id: String) {
        assertEquals(ErrorCode.INVALID_RESOURCE_ID, assertThrows<BusinessException> { IdParser.requireUserId(id) }.errorCode)
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "team-4p8w2k9m1xz", "team-4p8w2k9m1xzo", "user-8hq2m5v7c3kx", "grn:team-4p8w2k9m1xza"])
    fun `틀린 팀 id는 INVALID_RESOURCE_ID로 거절한다`(id: String) {
        assertEquals(ErrorCode.INVALID_RESOURCE_ID, assertThrows<BusinessException> { IdParser.requireTeamId(id) }.errorCode)
    }

    @Test
    fun `맞는 하위 자원 id는 접두사 10종 모두 종류와 함께 돌려준다`() {
        val examples =
            mapOf(
                SubResourceType.SNAPSHOT to "snap-7qz3m8k2",
                SubResourceType.PORT_FORWARD to "port-5q8w3n7z",
                SubResourceType.DOMAIN to "domain-4k8m2q9t",
                SubResourceType.FIREWALL_RULE to "fwrule-7m3k9p2x",
                SubResourceType.TRIGGER to "trigger-5n3w7r2x",
                SubResourceType.VERSION to "version-9c4m6k2p",
                SubResourceType.BACKUP to "backup-3x7q9m4k",
                SubResourceType.SERVICE to "service-6t2p8w4z",
                SubResourceType.ENVIRONMENT to "env-2k9m5x7q",
                SubResourceType.DEPLOYMENT to "deployment-8q4n6c3v",
            )

        assertEquals(SubResourceType.entries.toSet(), examples.keys)
        for ((type, id) in examples) {
            assertEquals(SubResourceId(type, id), IdParser.parseSubResourceId(id))
        }
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "",
            "snap-7qz3m8k",
            "snap-7qz3m8k2a",
            "snap-7qz3m8ki",
            "SNAP-7qz3m8k2",
            "snap_7qz3m8k2",
            "7qz3m8k2",
            "vm-7qz3m8k2",
            "unknown-7qz3m8k2",
            "snap-7qz3m8k2/port-5q8w3n7z",
        ],
    )
    fun `틀린 하위 자원 id는 INVALID_RESOURCE_ID로 거절한다`(id: String) {
        assertEquals(ErrorCode.INVALID_RESOURCE_ID, assertThrows<BusinessException> { IdParser.parseSubResourceId(id) }.errorCode)
    }

    @Test
    fun `하위 자원 id 종류가 기대와 다르면 GRN_RESOURCE_TYPE_MISMATCH로 거절한다`() {
        val exception = assertThrows<BusinessException> { IdParser.parseSubResourceId("port-5q8w3n7z", SubResourceType.SNAPSHOT) }

        assertEquals(ErrorCode.GRN_RESOURCE_TYPE_MISMATCH, exception.errorCode)
    }

    @Test
    fun `접두사는 서비스 자원·하위 자원·사용자·팀을 통틀어 서로 다르다`() {
        val prefixes =
            ResourceType.entries.map { it.prefix } + SubResourceType.entries.map { it.prefix } + listOf("user", "team")

        assertEquals(prefixes.size, prefixes.toSet().size)
    }
}
