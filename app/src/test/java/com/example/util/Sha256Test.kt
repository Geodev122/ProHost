package com.example.util

import org.junit.Assert.assertArrayEquals
import org.junit.Test
import java.security.MessageDigest
import kotlin.random.Random

/** The shared SHA-256 must equal the JDK's for every length around the 55/56/64-byte padding edges and beyond. */
class Sha256Test {
    @Test fun matchesMessageDigest() {
        val rnd = Random(42)
        for (n in 0..300) {
            val input = ByteArray(n) { rnd.nextInt().toByte() }
            assertArrayEquals("length $n", MessageDigest.getInstance("SHA-256").digest(input), Sha256.digest(input))
        }
        val text = "GPA.1234-5678-9012-34567".encodeToByteArray()
        assertArrayEquals(MessageDigest.getInstance("SHA-256").digest(text), Sha256.digest(text))
    }
}
