package com.github.cyberpartizan.notnullfork

import com.example.lib.LibraryRequest
import com.example.lib.generated.LibraryRequestMirror
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/** Finding the Mirror generated for a Request Body. */
class MirrorsTest {

    @Test
    fun `a Mirror is found for a Request Body loaded by a class loader that can't see the app's classes`() {
        // As with Spring Boot DevTools, where libraries are loaded by a parent of the app's class loader.
        val libraryLoader = object : ClassLoader(getPlatformClassLoader()) {
            override fun findClass(name: String): Class<*> {
                if (name != LibraryRequest::class.java.name) throw ClassNotFoundException(name)
                val bytes = LibraryRequest::class.java.getResourceAsStream("LibraryRequest.class")!!.use { it.readBytes() }
                return defineClass(name, bytes, 0, bytes.size)
            }
        }
        val requestBodyClass = libraryLoader.loadClass(LibraryRequest::class.java.name)
        val thread = Thread.currentThread()
        val appLoader = MirrorsTest::class.java.classLoader

        val previous = thread.contextClassLoader
        thread.contextClassLoader = appLoader
        try {
            assertThat(Mirrors.mirrorClassFor(requestBodyClass)).isEqualTo(LibraryRequestMirror::class.java)
        } finally {
            thread.contextClassLoader = previous
        }
    }
}
