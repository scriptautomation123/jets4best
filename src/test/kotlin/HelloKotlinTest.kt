package com.baml.mav.aieutil

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class HelloKotlinTest {
    @Test
    fun testGreet() {
        val hello = HelloKotlin()
        assertEquals("Hello, world from Kotlin!", hello.greet("world"))
    }
} 