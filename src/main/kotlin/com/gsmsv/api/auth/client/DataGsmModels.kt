package com.gsmsv.api.auth.client

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * 토큰 교환 응답에서 쓰는 값. DataGSM 토큰은 로그인할 때 신원 확인에만 쓰고 저장하지 않는다.
 * [toString]에 토큰이 찍히지 않게 한다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class DataGsmToken(
    @param:JsonProperty("access_token") val accessToken: String,
) {
    override fun toString(): String = "DataGsmToken(accessToken=***)"
}

/**
 * `/userinfo` 응답. 쓰는 필드만 받는다 (나머지는 무시).
 *
 * [role]은 계정 역할(`ROOT`·`ADMIN`·`USER`), [status]는 계정 상태(`PENDING`·`ACTIVE`), [objectType]은 `STUDENT`·`TEACHER`다.
 * 값은 DataGSM이 늘릴 수 있어 enum으로 받지 않고 문자열로 둔다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class DataGsmUserInfo(
    val id: Long,
    val email: String?,
    val role: String?,
    val status: String?,
    val objectType: String?,
    val student: DataGsmStudent? = null,
    val teacher: DataGsmTeacher? = null,
) {
    /**
     * 재학 여부. DataGSM에는 재학 확인 API가 따로 없고 `student.role`로 판단한다 (졸업·자퇴는 재학이 아니다).
     * 학생 정보가 없으면(`datagsm:student_read` 범위가 없거나 교사) 재학으로 보지 않는다.
     */
    fun isEnrolled(): Boolean = objectType == STUDENT && student?.role?.let { it in ENROLLED_ROLES } == true

    companion object {
        private const val STUDENT = "STUDENT"
        private val ENROLLED_ROLES = setOf("GENERAL_STUDENT", "STUDENT_COUNCIL", "DORMITORY_MANAGER")
    }
}

/** PKCE 인가 요청. [codeVerifier]는 로그인 검증값과 함께 저장하고 콜백 후 [DataGsmClient.exchangeCode]에 전달한다. */
data class DataGsmAuthorizationRequest(
    val authorizationUri: String,
    val codeVerifier: String,
) {
    override fun toString(): String = "DataGsmAuthorizationRequest(authorizationUri=***, codeVerifier=***)"
}

/** 학생 정보. [role]은 `GENERAL_STUDENT`·`STUDENT_COUNCIL`·`DORMITORY_MANAGER`·`GRADUATE`·`WITHDRAWN` 중 하나다 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class DataGsmStudent(
    val id: Long,
    val name: String?,
    val email: String?,
    val grade: Int?,
    val classNum: Int?,
    val number: Int?,
    val studentNumber: Int?,
    val role: String?,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class DataGsmTeacher(
    val id: Long,
    val name: String?,
    val email: String?,
)
