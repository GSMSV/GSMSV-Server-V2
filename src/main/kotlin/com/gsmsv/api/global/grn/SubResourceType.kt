package com.gsmsv.api.global.grn

/**
 * 하위 자원 종류. [prefix]는 하위 자원 id의 접두사이고, [parents]는 이 하위 자원이 붙을 수 있는 부모 종류다.
 *
 * 값은 Contracts-V2 `constants/resource-id.md` 3장 표를 그대로 옮겼다. 접두사는 한 번 정하면 바꾸지 않는다.
 */
enum class SubResourceType(
    val prefix: String,
    val parents: Set<ResourceType>,
) {
    SNAPSHOT("snap", setOf(ResourceType.VM)),
    PORT_FORWARD("port", setOf(ResourceType.VM)),
    DOMAIN("domain", setOf(ResourceType.VM, ResourceType.FUNCTION, ResourceType.APP)),
    FIREWALL_RULE("fwrule", setOf(ResourceType.VM)),
    TRIGGER("trigger", setOf(ResourceType.FUNCTION)),
    VERSION("version", setOf(ResourceType.FUNCTION)),
    BACKUP("backup", setOf(ResourceType.DATABASE)),
    SERVICE("service", setOf(ResourceType.APP)),
    ENVIRONMENT("env", setOf(ResourceType.APP)),
    DEPLOYMENT("deployment", setOf(ResourceType.APP)),
    ;

    companion object {
        fun fromPrefix(prefix: String): SubResourceType? = entries.find { it.prefix == prefix }
    }
}
