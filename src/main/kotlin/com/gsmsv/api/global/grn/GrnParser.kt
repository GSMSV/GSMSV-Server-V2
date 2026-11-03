package com.gsmsv.api.global.grn

import com.gsmsv.api.global.exception.BusinessException
import com.gsmsv.api.global.exception.ErrorCode

/**
 * 서비스 자원 GRN `grn:{소유자 id}:{자원 id}`의 파서.
 *
 * GRN 문자열은 이 파서로만 다룬다. `split`·`substring`으로 직접 자르지 않는다 (23번 규칙).
 */
object GrnParser {
    // 소유자 칸은 캡처하지 않는다. 파서 밖으로 꺼낼 길 자체를 없애려는 것이다
    private val SERVICE_RESOURCE_GRN =
        Regex("^grn:(?:user|team)-[0-9a-hjkmnp-tv-z]{12}:((vm|fn|db|deploy)-[0-9a-hjkmnp-tv-z]{12})$")

    /**
     * GRN 전체를 검사하고 자원 id를 돌려준다.
     *
     * @throws BusinessException 형식이 맞지 않으면 [ErrorCode.INVALID_GRN]
     */
    fun parse(grn: String): ResourceId {
        // matchEntire를 쓴다. find는 `$`가 끝 줄바꿈 앞에서도 맞아 "...\n"을 통과시킬 수 있다
        val match = SERVICE_RESOURCE_GRN.matchEntire(grn) ?: throw BusinessException(ErrorCode.INVALID_GRN)
        val (resourceId, prefix) = match.destructured
        val type = ResourceType.fromPrefix(prefix) ?: throw BusinessException(ErrorCode.INVALID_GRN)
        return ResourceId(type, resourceId)
    }

    /**
     * GRN을 검사하고, 자원 종류가 [expected]와 같을 때만 자원 id를 돌려준다.
     *
     * @throws BusinessException 형식 오류는 [ErrorCode.INVALID_GRN], 종류가 다르면 [ErrorCode.GRN_RESOURCE_TYPE_MISMATCH]
     */
    fun parse(
        grn: String,
        expected: ResourceType,
    ): ResourceId {
        val resourceId = parse(grn)
        if (resourceId.type != expected) {
            throw BusinessException(ErrorCode.GRN_RESOURCE_TYPE_MISMATCH)
        }
        return resourceId
    }

    /**
     * DB에 저장된 소유자 id와 자원 id로 GRN을 만든다. 로더가 요청 GRN과 통째로 비교할 때 쓴다.
     *
     * @throws IllegalStateException 만든 GRN이 형식에 맞지 않을 때. DB 값이 잘못된 것이므로 서버 오류다
     */
    fun format(
        ownerId: String,
        resourceId: String,
    ): String {
        val grn = "grn:$ownerId:$resourceId"
        check(SERVICE_RESOURCE_GRN.matches(grn)) { "DB 값으로 만든 GRN이 형식에 맞지 않습니다." }
        return grn
    }
}
