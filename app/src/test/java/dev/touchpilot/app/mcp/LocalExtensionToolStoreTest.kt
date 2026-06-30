package dev.touchpilot.app.mcp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LocalExtensionToolStoreTest {
    @Test
    fun storesAndReloadsTools() {
        var backing = ""
        val store = LocalExtensionToolStore(
            readJson = { backing },
            writeJson = { backing = it },
        )

        store.add(LocalExtensionTool("weather", "Show weather", "http://localhost:8080"))

        assertTrue(backing.contains("weather"))
        assertEquals(1, store.all().size)
        assertEquals("weather", store.all().first().name)
    }

    @Test
    fun removesToolsByName() {
        var backing = ""
        val store = LocalExtensionToolStore(
            readJson = { backing },
            writeJson = { backing = it },
        )

        store.add(LocalExtensionTool("weather", "Show weather", "http://localhost:8080"))
        assertTrue(store.remove("weather", "http://localhost:8080"))
        assertEquals(0, store.all().size)
    }

    @Test
    fun keepsSameNamedToolsOnDifferentEndpoints() {
        var backing = ""
        val store = LocalExtensionToolStore(
            readJson = { backing },
            writeJson = { backing = it },
        )

        store.add(LocalExtensionTool("weather", "Local", "http://localhost:8080"))
        store.add(LocalExtensionTool("weather", "Remote", "http://localhost:9090"))

        assertEquals(2, store.all().size)
        assertTrue(store.remove("weather", "http://localhost:8080"))
        assertEquals(listOf("http://localhost:9090"), store.all().map { it.endpoint })
    }
}
