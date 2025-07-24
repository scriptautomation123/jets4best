package com.baml.mav.aieutil

class HelloKotlin {
    fun greet(name: String): String = "Hello, $name from Kotlin!"
}

// Top-level main function
fun main() {
    val hello = com.baml.mav.aieutil.HelloKotlin()
    println(hello.greet("World"))
} 