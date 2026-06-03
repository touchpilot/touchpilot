package dev.touchpilot.app.agent

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RecentAppsRoutingTest {
    private fun route(task: String): String {
        val provider = LocalRouterCommandProvider(task = task, skill = null)
        // First completion always observes the screen; the routed action is
        // produced on the second completion.
        val first = provider.complete("", "")
        assertEquals("""{"tool":"observe_screen","args":{}}""", first)
        return provider.complete("", "")
    }

    @Test
    fun routerRoutesRecentAppsPhraseToRecentAppsTool() {
        assertEquals("""{"tool":"recent_apps","args":{}}""", route("show recent apps"))
    }

    @Test
    fun routerRoutesAppSwitcherToRecentAppsTool() {
        assertEquals("""{"tool":"recent_apps","args":{}}""", route("open the app switcher"))
    }

    @Test
    fun routerRoutesOpenRecentsToRecentAppsNotOpenApp() {
        val routed = route("open recents")
        assertTrue(routed.contains("\"recent_apps\""), "expected recent_apps, got $routed")
    }
}
