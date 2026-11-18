package com.gsmsv.api.vm.service

/** 고를 수 있는 운영체제 하나와 그 운영체제로 VM을 만들 때의 최소 사양. RAM·SSD는 GiB다 */
data class VmImage(
    val id: String,
    val name: String,
    val minVcpu: Int,
    val minRamGib: Int,
    val minSsdGib: Int,
)
