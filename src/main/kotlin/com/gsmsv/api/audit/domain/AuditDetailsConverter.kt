package com.gsmsv.api.audit.domain

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter
import tools.jackson.core.type.TypeReference
import tools.jackson.databind.json.JsonMapper

/** 부가 정보를 JSON 문자열 한 칸에 저장한다. 값이 없으면 칸도 비운다 */
@Converter
class AuditDetailsConverter : AttributeConverter<Map<String, String>, String> {
    override fun convertToDatabaseColumn(attribute: Map<String, String>?): String? =
        attribute?.takeIf { it.isNotEmpty() }?.let(MAPPER::writeValueAsString)

    override fun convertToEntityAttribute(dbData: String?): Map<String, String> = dbData?.let { MAPPER.readValue(it, TYPE) } ?: emptyMap()

    private companion object {
        val MAPPER: JsonMapper = JsonMapper.builder().build()
        val TYPE = object : TypeReference<Map<String, String>>() {}
    }
}
