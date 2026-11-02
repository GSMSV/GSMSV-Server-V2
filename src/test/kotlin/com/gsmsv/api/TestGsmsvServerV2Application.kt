package com.gsmsv.api

import org.springframework.boot.fromApplication
import org.springframework.boot.with


fun main(args: Array<String>) {
    fromApplication<GsmsvServerV2Application>().with(TestcontainersConfiguration::class).run(*args)
}
