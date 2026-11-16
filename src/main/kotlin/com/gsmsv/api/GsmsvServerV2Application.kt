package com.gsmsv.api

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

@SpringBootApplication
@ConfigurationPropertiesScan
class GsmsvServerV2Application

fun main(args: Array<String>) {
    runApplication<GsmsvServerV2Application>(*args)
}
