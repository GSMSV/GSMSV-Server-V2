package com.gsmsv.api.infra.node

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.gsmsv.api.infra.GoApiCaller
import com.gsmsv.api.infra.config.InfraProfiles
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

/** 실제 Go 연동. `dev`·`local`이 아닌 프로필에서 쓴다 */
@Component
@Profile(InfraProfiles.REAL)
class HttpNodeInfraClient(
    goRestClient: RestClient,
) : NodeInfraClient {
    private val caller = GoApiCaller(goRestClient)

    override fun listNodes(refresh: Boolean): List<InfraNodeStatus> =
        caller
            .get(
                operation = "node.list",
                uri = "/nodes?refresh={refresh}",
                uriVariables = mapOf("refresh" to refresh),
                responseType = NodeList::class.java,
            ).items

    @JsonIgnoreProperties(ignoreUnknown = true)
    private data class NodeList(
        @param:JsonProperty("items") val items: List<InfraNodeStatus>,
    )
}
