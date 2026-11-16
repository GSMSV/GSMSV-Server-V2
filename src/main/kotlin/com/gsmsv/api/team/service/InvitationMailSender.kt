package com.gsmsv.api.team.service

import org.springframework.stereotype.Component

interface InvitationMailSender {
    fun send(
        email: String,
        token: String,
    )
}

@Component
class FakeInvitationMailSender : InvitationMailSender {
    override fun send(
        email: String,
        token: String,
    ) = Unit
}
