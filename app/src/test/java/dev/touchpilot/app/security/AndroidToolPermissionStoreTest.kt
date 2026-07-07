package dev.touchpilot.app.security

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AndroidToolPermissionStoreTest {
    @Test
    fun toolsAllowedByDefault() {
        val store = memoryStore()

        assertTrue(store.isAllowed("tap"))
        assertTrue(AndroidToolPermissionStore.isToolAllowed("tap"))
    }

    @Test
    fun revokeBlocksToolImmediately() {
        val store = memoryStore()

        store.revoke("tap")

        assertFalse(store.isAllowed("tap"))
        assertFalse(AndroidToolPermissionStore.isToolAllowed("tap"))
    }

    @Test
    fun grantRestoresRevokedTool() {
        val store = memoryStore()
        store.revoke("tap")

        store.grant("tap")

        assertTrue(store.isAllowed("tap"))
    }

    @Test
    fun displayLabelUsesHumanReadableDescription() {
        val label = AndroidToolPermissionStore.displayLabel("tap")

        assertTrue(label.contains("Tap", ignoreCase = true))
        assertFalse(label.contains("_"))
    }

    private fun memoryStore(): AndroidToolPermissionStore {
        var json = ""
        return AndroidToolPermissionStore(
            readJson = { json },
            writeJson = { json = it },
        )
    }
}
