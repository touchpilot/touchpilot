package dev.touchpilot.app.agent

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ConversationalGateTest {
    private val greetingReply = "Hello, I am TouchPilot, how can I help you?"
    private val helpReply =
        "I can help you control Android apps, open settings, tap visible text, scroll, " +
            "go back or home, and use approved skills. What would you like to do?"

    @Test
    fun respondsToHello() {
        assertEquals(greetingReply, ConversationalGate.respond("Hello")?.message)
    }

    @Test
    fun respondsToLowercaseHi() {
        assertEquals(greetingReply, ConversationalGate.respond("hi")?.message)
    }

    @Test
    fun respondsToGreetingWithPunctuation() {
        assertEquals(greetingReply, ConversationalGate.respond("Hello!")?.message)
        assertEquals(greetingReply, ConversationalGate.respond("Hey.")?.message)
    }

    @Test
    fun respondsToMultiWordGreeting() {
        assertEquals(greetingReply, ConversationalGate.respond("Good morning")?.message)
    }

    @Test
    fun trimsLeadingAndTrailingWhitespace() {
        assertEquals(greetingReply, ConversationalGate.respond("  Hello  ")?.message)
    }

    @Test
    fun doesNotMatchActionPhrase() {
        assertNull(ConversationalGate.respond("open Settings"))
        assertNull(ConversationalGate.respond("scroll down"))
    }

    @Test
    fun doesNotMatchGreetingFollowedByTask() {
        assertNull(ConversationalGate.respond("Hello, open Settings"))
        assertNull(ConversationalGate.respond("Hi can you tap OK"))
    }

    @Test
    fun doesNotMatchBlankInput() {
        assertNull(ConversationalGate.respond(""))
        assertNull(ConversationalGate.respond("   "))
    }

    @Test
    fun respondsToHelp() {
        assertEquals(helpReply, ConversationalGate.respond("help")?.message)
    }

    @Test
    fun respondsToHelpCaseInsensitiveAndPunctuated() {
        assertEquals(helpReply, ConversationalGate.respond("Help")?.message)
        assertEquals(helpReply, ConversationalGate.respond("HELP!")?.message)
        assertEquals(helpReply, ConversationalGate.respond("help.")?.message)
    }

    @Test
    fun respondsToWhatCanYouDo() {
        assertEquals(helpReply, ConversationalGate.respond("what can you do")?.message)
        assertEquals(helpReply, ConversationalGate.respond("What can you do?")?.message)
    }

    @Test
    fun respondsToHowCanYouHelp() {
        assertEquals(helpReply, ConversationalGate.respond("how can you help")?.message)
        assertEquals(helpReply, ConversationalGate.respond("How can you help?")?.message)
    }

    @Test
    fun doesNotMatchHelpFollowedByTask() {
        assertNull(ConversationalGate.respond("help me open Settings"))
        assertNull(ConversationalGate.respond("help me tap OK"))
    }

    @Test
    fun doesNotMatchHelpEmbeddedInTask() {
        assertNull(ConversationalGate.respond("open the help page"))
        assertNull(ConversationalGate.respond("scroll to help section"))
    }
}
