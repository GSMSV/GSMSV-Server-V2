package com.gsmsv.api.global.response

import com.fasterxml.jackson.annotation.JsonProperty

data class ErrorResponse(
    val code: String,
    val message: String,
    @JsonProperty("request_id")
    val requestId: String,
)
