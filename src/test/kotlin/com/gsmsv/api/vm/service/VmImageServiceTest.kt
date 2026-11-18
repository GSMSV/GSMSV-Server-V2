package com.gsmsv.api.vm.service

import com.gsmsv.api.vm.config.VmImageProperties
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class VmImageServiceTest {
    private val service =
        VmImageService(
            VmImageProperties(
                listOf(
                    VmImageProperties.Image("ubuntu-24.04", "Ubuntu 24.04 LTS", minRamGib = 1, minSsdGib = 4),
                    VmImageProperties.Image("debian-13", "Debian 13", minRamGib = 1, minSsdGib = 4, templateDiskGib = 10),
                ),
            ),
        )

    @Test
    fun `설정 순서대로 목록을 돌려주고 최소 vCPU는 1이다`() {
        val images = service.list()

        assertEquals(listOf("ubuntu-24.04", "debian-13"), images.map { it.id })
        assertEquals(listOf(1, 1), images.map { it.minVcpu })
    }

    @Test
    fun `템플릿 디스크 크기를 모르면 운영체제 최소 저장 공간을 쓴다`() {
        assertEquals(4, service.find("ubuntu-24.04")?.minSsdGib)
    }

    @Test
    fun `최소 SSD는 템플릿 디스크 크기보다 작지 않다`() {
        assertEquals(10, service.find("debian-13")?.minSsdGib)
    }

    @Test
    fun `목록에 없는 id면 null을 돌려준다`() {
        assertNull(service.find("windows-2022"))
    }
}
