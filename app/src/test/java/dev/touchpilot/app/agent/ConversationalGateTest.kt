package dev.touchpilot.app.agent

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ConversationalGateTest {
    private val greetingReply = "Hello, I am TouchPilot, how can I help you?"

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
}
