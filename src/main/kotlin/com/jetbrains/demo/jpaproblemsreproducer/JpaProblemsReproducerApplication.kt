package com.jetbrains.demo.jpaproblemsreproducer

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class JpaProblemsReproducerApplication

fun main(args: Array<String>) {
    runApplication<JpaProblemsReproducerApplication>(*args)
}