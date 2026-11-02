package com.mariocostabile.fastreply

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SenderTest {
    private val armed = PendingSend("1", "Tutto ok", 1000)

    @Test fun uriEncodesSpecials() = assertEquals(
        "https://wa.me/39333?text=a%26b%3F%20%231%0Aok%20%F0%9F%98%80",
        buildWaUri("39333", "a&b? #1\nok 😀"),
    )

    @Test fun sendsWhenMatching() = assertTrue(shouldSend(armed, "Tutto ok", 5999))
    @Test fun noSendAfterWindow() = assertFalse(shouldSend(armed, "Tutto ok", 6001))
    @Test fun noSendOnOtherDraft() = assertFalse(shouldSend(armed, "ciao", 2000))
    @Test fun noSendWhenNotArmed() = assertFalse(shouldSend(null, "Tutto ok", 2000))
}
