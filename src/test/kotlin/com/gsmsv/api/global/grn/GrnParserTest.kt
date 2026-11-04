package com.gsmsv.api.global.grn

import com.gsmsv.api.global.exception.BusinessException
import com.gsmsv.api.global.exception.ErrorCode
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import kotlin.test.assertEquals

// 맞는/틀린 목록은 Contracts-V2 `constants/resource-id.md` 6장과 Contracts #5의 예시를 옮겨 왔다.
// 목록 파일 형식이 정해지면 그 파일을 읽도록 바꾼다
class GrnParserTest {
    @ParameterizedTest
    @MethodSource("validGrns")
    fun `맞는 GRN은 자원 id를 돌려준다`(
        grn: String,
        expected: ResourceId,
    ) {
        assertEquals(expected, GrnParser.parse(grn))
    }

    @ParameterizedTest
    @MethodSource("invalidGrns")
    fun `틀린 GRN은 INVALID_GRN으로 거절한다`(grn: String) {
        val exception = assertThrows<BusinessException> { GrnParser.parse(grn) }

        assertEquals(ErrorCode.INVALID_GRN, exception.errorCode)
    }

    @Test
    fun `기대한 종류와 같으면 자원 id를 돌려준다`() {
        val resourceId = GrnParser.parse("grn:team-4p8w2k9m1xza:vm-k3m9x2p7q4ta", ResourceType.VM)

        assertEquals(ResourceId(ResourceType.VM, "vm-k3m9x2p7q4ta"), resourceId)
    }

    @Test
    fun `형식은 맞지만 기대한 종류와 다르면 GRN_RESOURCE_TYPE_MISMATCH로 거절한다`() {
        val exception =
            assertThrows<BusinessException> {
                GrnParser.parse("grn:team-4p8w2k9m1xza:fn-6d2r8t5y3n7e", ResourceType.VM)
            }

        assertEquals(ErrorCode.GRN_RESOURCE_TYPE_MISMATCH, exception.errorCode)
    }

    @Test
    fun `종류 검사보다 형식 검사가 먼저다`() {
        val exception = assertThrows<BusinessException> { GrnParser.parse("grn:team-4p8w2k9m1xza:fn-6d2r8t5y3n7", ResourceType.VM) }

        assertEquals(ErrorCode.INVALID_GRN, exception.errorCode)
    }

    @ParameterizedTest
    @MethodSource("validSubResources")
    fun `맞는 하위 자원 표기는 부모 GRN·부모 id·하위 id로 나눈다`(
        notation: String,
        expected: SubResourceRef,
    ) {
        assertEquals(expected, GrnParser.parseSubResource(notation))
    }

    @ParameterizedTest
    @MethodSource("invalidSubResources")
    fun `틀린 하위 자원 표기는 INVALID_GRN으로 거절한다`(notation: String) {
        val exception = assertThrows<BusinessException> { GrnParser.parseSubResource(notation) }

        assertEquals(ErrorCode.INVALID_GRN, exception.errorCode)
    }

    @Test
    fun `DB 값으로 만든 GRN은 요청 GRN과 같다`() {
        val grn = "grn:team-4p8w2k9m1xza:vm-k3m9x2p7q4ta"

        assertEquals(grn, GrnParser.format("team-4p8w2k9m1xza", "vm-k3m9x2p7q4ta"))
    }

    @Test
    fun `DB 값이 형식에 맞지 않으면 GRN을 만들지 않는다`() {
        assertThrows<IllegalStateException> { GrnParser.format("team-4p8w2k9m1xza", "vm-k3m9x2p7") }
        assertThrows<IllegalStateException> { GrnParser.format("vm-k3m9x2p7q4ta", "vm-k3m9x2p7q4ta") }
    }

    companion object {
        private const val OWNER = "team-4p8w2k9m1xza"
        private const val VM_GRN = "grn:$OWNER:vm-k3m9x2p7q4ta"
        private val VM = ResourceId(ResourceType.VM, "vm-k3m9x2p7q4ta")

        @JvmStatic
        fun validSubResources() =
            listOf(
                arrayOf("$VM_GRN/snap-7qz3m8k2", SubResourceRef(VM_GRN, VM, SubResourceId(SubResourceType.SNAPSHOT, "snap-7qz3m8k2"))),
                arrayOf(
                    "$VM_GRN/fwrule-7m3k9p2x",
                    SubResourceRef(VM_GRN, VM, SubResourceId(SubResourceType.FIREWALL_RULE, "fwrule-7m3k9p2x")),
                ),
                arrayOf(
                    "grn:$OWNER:fn-6d2r8t5y3n7e/trigger-5n3w7r2x",
                    SubResourceRef(
                        "grn:$OWNER:fn-6d2r8t5y3n7e",
                        ResourceId(ResourceType.FUNCTION, "fn-6d2r8t5y3n7e"),
                        SubResourceId(SubResourceType.TRIGGER, "trigger-5n3w7r2x"),
                    ),
                ),
                arrayOf(
                    "grn:$OWNER:app-k3m9x2p7q4ta/domain-4k8m2q9t",
                    SubResourceRef(
                        "grn:$OWNER:app-k3m9x2p7q4ta",
                        ResourceId(ResourceType.APP, "app-k3m9x2p7q4ta"),
                        SubResourceId(SubResourceType.DOMAIN, "domain-4k8m2q9t"),
                    ),
                ),
                arrayOf(
                    "grn:$OWNER:db-k3m9x2p7q4ta/backup-3x7q9m4k",
                    SubResourceRef(
                        "grn:$OWNER:db-k3m9x2p7q4ta",
                        ResourceId(ResourceType.DATABASE, "db-k3m9x2p7q4ta"),
                        SubResourceId(SubResourceType.BACKUP, "backup-3x7q9m4k"),
                    ),
                ),
            )

        @JvmStatic
        fun invalidSubResources() =
            listOf(
                // 하위 id가 틀림
                "$VM_GRN/snap-7qz3m8k",
                "$VM_GRN/snap-7qz3m8kl",
                "$VM_GRN/snap-k3m9x2p7q4ta",
                "$VM_GRN/SNAP-7qz3m8k2",
                "$VM_GRN/unknown-7qz3m8k2",
                "$VM_GRN/",
                // 하위 자원은 한 단계만 둔다
                "$VM_GRN/snap-7qz3m8k2/port-5q8w3n7z",
                // 부모 종류에 붙을 수 없는 하위 자원
                "grn:$OWNER:fn-6d2r8t5y3n7e/snap-7qz3m8k2",
                "grn:$OWNER:db-k3m9x2p7q4ta/domain-4k8m2q9t",
                "$VM_GRN/backup-3x7q9m4k",
                // 부모 GRN이 틀림
                "grn:$OWNER:vm-k3m9x2p7/snap-7qz3m8k2",
                "grn:vm-k3m9x2p7q4ta:vm-k3m9x2p7q4ta/snap-7qz3m8k2",
                "snap-7qz3m8k2",
                VM_GRN,
                // 앞뒤 공백·줄바꿈
                " $VM_GRN/snap-7qz3m8k2",
                "$VM_GRN/snap-7qz3m8k2 ",
                "$VM_GRN/snap-7qz3m8k2\n",
            )

        @JvmStatic
        fun validGrns() =
            listOf(
                arrayOf("grn:$OWNER:vm-k3m9x2p7q4ta", ResourceId(ResourceType.VM, "vm-k3m9x2p7q4ta")),
                arrayOf("grn:$OWNER:fn-6d2r8t5y3n7e", ResourceId(ResourceType.FUNCTION, "fn-6d2r8t5y3n7e")),
                arrayOf("grn:$OWNER:db-k3m9x2p7q4ta", ResourceId(ResourceType.DATABASE, "db-k3m9x2p7q4ta")),
                arrayOf("grn:$OWNER:app-k3m9x2p7q4ta", ResourceId(ResourceType.APP, "app-k3m9x2p7q4ta")),
                arrayOf("grn:user-8hq2m5v7c3kx:vm-k3m9x2p7q4ta", ResourceId(ResourceType.VM, "vm-k3m9x2p7q4ta")),
            )

        @JvmStatic
        fun invalidGrns() =
            listOf(
                // 자원 id 자체가 틀림 (resource-id.md 6장)
                "grn:$OWNER:VM-k3m9x2p7q4ta",
                "grn:$OWNER:vm-k3m9x2p7q4tl",
                "grn:$OWNER:vm-k3m9x2p7",
                "grn:$OWNER:k3m9x2p7q4ta",
                "grn:$OWNER:vm_k3m9x2p7q4ta",
                // 소유자 칸이 user-·team-가 아님
                "grn:vm-k3m9x2p7q4ta:vm-k3m9x2p7q4ta",
                "grn:team-4p8w2k9m1xz:vm-k3m9x2p7q4ta",
                "grn:team-4p8w2k9m1xzo:vm-k3m9x2p7q4ta",
                // 자원 칸에 하위 id(8자)
                "grn:$OWNER:snap-7qz3m8k2",
                // 하위 자원 표기는 서비스 자원 파서가 받지 않는다
                "grn:$OWNER:vm-k3m9x2p7q4ta/snap-7qz3m8k2",
                "grn:$OWNER:vm-k3m9x2p7q4ta/snap-7qz3m8k2/port-5q8w3n7z",
                // 정해지지 않은 접두사 (원터치 배포는 `deploy`가 아니라 `app`이다)
                "grn:$OWNER:deploy-k3m9x2p7q4ta",
                "grn:$OWNER:team-4p8w2k9m1xza",
                // 앞뒤 공백·줄바꿈·대문자
                " grn:$OWNER:vm-k3m9x2p7q4ta",
                "grn:$OWNER:vm-k3m9x2p7q4ta ",
                "grn:$OWNER:vm-k3m9x2p7q4ta\n",
                "GRN:$OWNER:vm-k3m9x2p7q4ta",
                // 구조가 틀림
                "",
                "vm-k3m9x2p7q4ta",
                "grn:$OWNER",
                "grn::vm-k3m9x2p7q4ta",
                "grn:$OWNER:vm-k3m9x2p7q4ta:vm-k3m9x2p7q4ta",
            )
    }
}
