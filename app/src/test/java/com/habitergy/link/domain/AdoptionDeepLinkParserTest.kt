package com.habitergy.link.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdoptionDeepLinkParserTest {
    private val token = "AbCdEfGhIjKlMnOpQrStUvWxYz0123456789_-abcde"

    @Test
    fun `accepts exact Manager adoption URI`() {
        assertEquals(
            AdoptionLaunchRequest.Session(token),
            AdoptionDeepLinkParser.parse("habitergy://link/adopt?token=$token"),
        )
    }

    @Test
    fun `rejects malformed token length and alphabet`() {
        assertTrue(
            AdoptionDeepLinkParser.parse(
                "habitergy://link/adopt?token=${token.dropLast(1)}",
            ) is AdoptionLaunchRequest.Invalid,
        )
        assertTrue(
            AdoptionDeepLinkParser.parse(
                "habitergy://link/adopt?token=${token.dropLast(1)}=",
            ) is AdoptionLaunchRequest.Invalid,
        )
    }

    @Test
    fun `rejects wrong route and extra query parameters`() {
        assertTrue(
            AdoptionDeepLinkParser.parse(
                "habitergy://other/adopt?token=$token",
            ) is AdoptionLaunchRequest.Invalid,
        )
        assertTrue(
            AdoptionDeepLinkParser.parse(
                "habitergy://link/adopt?token=$token&other=value",
            ) is AdoptionLaunchRequest.Invalid,
        )
    }

    @Test
    fun `rejects fragments ports and encoded tokens`() {
        assertTrue(
            AdoptionDeepLinkParser.parse(
                "habitergy://link:123/adopt?token=$token",
            ) is AdoptionLaunchRequest.Invalid,
        )
        assertTrue(
            AdoptionDeepLinkParser.parse(
                "habitergy://link/adopt?token=$token#fragment",
            ) is AdoptionLaunchRequest.Invalid,
        )
        assertTrue(
            AdoptionDeepLinkParser.parse(
                "habitergy://link/adopt?token=${token.dropLast(3)}%61%62%63",
            ) is AdoptionLaunchRequest.Invalid,
        )
    }
}
