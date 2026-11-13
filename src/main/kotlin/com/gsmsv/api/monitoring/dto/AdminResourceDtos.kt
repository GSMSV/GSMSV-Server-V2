package com.gsmsv.api.monitoring.dto

import jakarta.validation.constraints.Pattern

/** 전체 자원 목록(11.14)의 거르기 조건. 페이지 조건(`cursor`, `limit`)은 따로 받는다 */
data class AdminResourceFilterRequest(
    @field:Pattern(regexp = "^(vm|fn|db|app)$", message = "vm, fn, db, app 중 하나여야 합니다.")
    val type: String? = null,
    val node: String? = null,
    /** `user-…` 또는 `team-…` */
    val owner: String? = null,
)

/**
 * 전체 자원 목록의 한 항목. 관리자 API라 노드 이름·`vmid`처럼 구현 값을 넣을 수 있다 (monitoring 명세의 예외).
 *
 * [node]·[vmid]·[powerState]는 VM만 준다.
 */
data class AdminResourceResponse(
    val grn: String,
    val type: String,
    val name: String,
    val node: String?,
    val vmid: Int?,
    val ownerEmail: String,
    val status: String,
    val powerState: String?,
)
