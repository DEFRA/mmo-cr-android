package uk.gov.defra.mmocatchrecord.common.navigation

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

// Navigation Compose encodes typed routes with kotlinx.serialization, so each must round-trip to its singleton.
class AppRoutesTests {
    @Test
    fun `root routes round trip through kotlinx serialization to the same singleton`() {
        assertSame(SplashRoute, Json.decodeFromString<SplashRoute>(Json.encodeToString(SplashRoute)))
        assertSame(SignInRoute, Json.decodeFromString<SignInRoute>(Json.encodeToString(SignInRoute)))
        assertSame(AppLockRoute, Json.decodeFromString<AppLockRoute>(Json.encodeToString(AppLockRoute)))
        assertSame(HomeRoute, Json.decodeFromString<HomeRoute>(Json.encodeToString(HomeRoute)))
        assertSame(
            CatchRecordGraphRoute,
            Json.decodeFromString<CatchRecordGraphRoute>(Json.encodeToString(CatchRecordGraphRoute)),
        )
    }

    @Test
    fun `root routes have distinct readable names for navigation diagnostics`() {
        val names =
            listOf(
                SplashRoute,
                SignInRoute,
                AppLockRoute,
                HomeRoute,
                CatchRecordGraphRoute,
            ).map { it.toString() }

        assertEquals(listOf("SplashRoute", "SignInRoute", "AppLockRoute", "HomeRoute", "CatchRecordGraphRoute"), names)
    }
}
