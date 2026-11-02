package com.mariocostabile.fastreply

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhoneTest {
    @Test fun plusPrefix() = assertEquals("393331234567", normalizePhone("+39 333 123 4567"))
    @Test fun doubleZero() = assertEquals("393331234567", normalizePhone("0039-333-1234567"))
    @Test fun noPrefix() = assertEquals("393331234567", normalizePhone("(333) 123.4567"))
    @Test fun foreign() = assertEquals("441234567890", normalizePhone("+44 1234 567890"))
    @Test fun empty() = assertNull(normalizePhone("abc"))
}
