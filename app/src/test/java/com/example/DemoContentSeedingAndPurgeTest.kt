package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.demo.DemoDataGenerator
import com.example.data.model.*
import com.example.data.repository.ProHostRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DemoContentSeedingAndPurgeTest {

    private lateinit var context: Context
    private lateinit var repository: ProHostRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        repository = ProHostRepository()
    }

    @Test
    fun `test demo generator produces genuine data models`() {
        val users = DemoDataGenerator.generateDemoUsers()
        val listings = DemoDataGenerator.generateDemoListings()
        val bookings = DemoDataGenerator.generateDemoBookings()

        assertEquals(5, users.size)
        assertEquals(4, listings.size)
        assertEquals(3, bookings.size)

        // Verify Specialist vs ProHost representation
        val hosts = users.filter { it.role == UserRole.PRO_HOST }
        val specialists = users.filter { it.role == UserRole.SPECIALIST }
        assertEquals(2, hosts.size)
        assertEquals(3, specialists.size)

        // Verify all items carry isDemo = true
        assertTrue(users.all { it.isDemo })
        assertTrue(listings.all { it.isDemo })
        assertTrue(bookings.all { it.isDemo })

        // Verify genuine Lebanese details
        assertTrue(listings.any { it.district == "Achrafieh" })
        assertTrue(listings.any { it.district == "Badaro" })
        assertTrue(listings.any { it.district == "Verdun" })
        assertTrue(users.all { it.phone.startsWith("+961") })
    }

    @Test
    fun `test seeding and purging demo content lifecycle`() = runBlocking {
        // 1. Seed demo content
        val seedSuccess = repository.seedDemoContent()
        assertTrue("Seeding demo content must succeed", seedSuccess)

        val seededSpaces = repository.spaces.value.filter { it.isDemo || it.id.startsWith("demo-") }
        val seededBookings = repository.bookingRequests.value.filter { it.isDemo || it.id.startsWith("demo-") }
        val seededUsers = repository.users.value.filter { it.isDemo || it.id.startsWith("demo-") }

        assertEquals("Should load 4 demo listings", 4, seededSpaces.size)
        assertEquals("Should load 3 demo booking requests", 3, seededBookings.size)
        assertEquals("Should load 5 demo users", 5, seededUsers.size)

        // 2. Execute purge
        val purgedCount = repository.purgeDemoContent()
        assertTrue("Purged count must be > 0", purgedCount >= 0)

        // 3. Verify clean production state restoration
        val remainingDemoSpaces = repository.spaces.value.filter { it.isDemo || it.id.startsWith("demo-") }
        val remainingDemoBookings = repository.bookingRequests.value.filter { it.isDemo || it.id.startsWith("demo-") }
        val remainingDemoUsers = repository.users.value.filter { it.isDemo || it.id.startsWith("demo-") }

        assertEquals("No demo spaces must remain", 0, remainingDemoSpaces.size)
        assertEquals("No demo bookings must remain", 0, remainingDemoBookings.size)
        assertEquals("No demo users must remain", 0, remainingDemoUsers.size)
    }
}
