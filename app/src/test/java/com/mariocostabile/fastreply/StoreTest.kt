package com.mariocostabile.fastreply

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class StoreTest {
    @get:Rule val tmp = TemporaryFolder()

    private val c1 = Contact("1", "Mamma", "393331234567", listOf("Tutto ok", "Sono a casa"))
    private val c2 = Contact("2", "Capo \"Big\"", "441234567890", listOf("In riunione 😀"))

    @Test fun roundTrip() {
        val store = Store(File(tmp.root, "data.json"))
        val data = AppData("2", listOf(c1, c2))
        store.save(data)
        assertEquals(data, store.load())
    }

    @Test fun missingFile() = assertEquals(AppData(), Store(File(tmp.root, "nope.json")).load())

    @Test fun corruptFile() {
        val f = File(tmp.root, "data.json").apply { writeText("{not json") }
        assertEquals(AppData(), Store(f).load())
    }

    @Test fun currentFallback() {
        assertEquals(c1, AppData("x", listOf(c1)).current())
        assertEquals(c2, AppData("2", listOf(c1, c2)).current())
        assertNull(AppData().current())
    }
}
