package com.gsmsv.api.vm.config

import org.junit.jupiter.api.Test
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.context.properties.bind.validation.BindValidationException
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class VmImagePropertiesTest {
    @EnableConfigurationProperties(VmImageProperties::class)
    class Config

    // application.yml을 읽어 실제 기동과 같은 값으로 바인딩한다
    private val runner =
        ApplicationContextRunner()
            .withInitializer(ConfigDataApplicationContextInitializer())
            .withUserConfiguration(Config::class.java)

    @Test
    fun `기본값은 정책 문서의 운영체제 목록과 최소 사양이다`() {
        runner.run { context ->
            val images = context.getBean(VmImageProperties::class.java).images

            assertEquals(listOf("ubuntu-24.04", "ubuntu-22.04", "debian-13"), images.map { it.id })
            assertEquals(listOf("Ubuntu 24.04 LTS", "Ubuntu 22.04 LTS", "Debian 13"), images.map { it.name })
            assertTrue(images.all { it.minRamGib == 1 && it.minSsdGib == 4 && it.templateDiskGib == null })
        }
    }

    @Test
    fun `id 형식이 틀리면 기동에 실패한다`() {
        runner
            .withPropertyValues(*image(id = "Ubuntu_24"))
            .run { context -> assertTrue(causesOf(context.startupFailure).any { it is BindValidationException }) }
    }

    @Test
    fun `최소 사양이 0이면 기동에 실패한다`() {
        runner
            .withPropertyValues(*image(minRamGib = 0))
            .run { context -> assertTrue(causesOf(context.startupFailure).any { it is BindValidationException }) }
    }

    @Test
    fun `id가 겹치면 기동에 실패한다`() {
        runner
            .withPropertyValues(*image(), *image(index = 1))
            .run { context -> assertTrue(causesOf(context.startupFailure).any { it is BindValidationException }) }
    }

    @Test
    fun `목록이 없으면 기동에 실패한다`() {
        ApplicationContextRunner()
            .withUserConfiguration(Config::class.java)
            .run { context -> assertNotNull(context.startupFailure) }
    }

    // 목록은 한 설정 출처에서 통째로 묶이므로 원소의 칸을 모두 넣는다
    private fun image(
        index: Int = 0,
        id: String = "ubuntu-24.04",
        minRamGib: Int = 1,
    ) = arrayOf(
        "gsmsv.vm.images[$index].id=$id",
        "gsmsv.vm.images[$index].name=Ubuntu 24.04 LTS",
        "gsmsv.vm.images[$index].min-ram-gib=$minRamGib",
        "gsmsv.vm.images[$index].min-ssd-gib=4",
    )

    private fun causesOf(failure: Throwable?) = generateSequence(failure) { it.cause }
}
