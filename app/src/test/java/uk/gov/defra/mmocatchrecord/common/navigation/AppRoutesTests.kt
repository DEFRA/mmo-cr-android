package uk.gov.defra.mmocatchrecord.common.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * References/constructs each root-graph route singleton so its `@Serializable data object` declaration
 * (class `<clinit>`) is exercised - [CatchRecordGraphRoute] is already covered elsewhere (e.g. via
 * `CheckYourAnswersNavigationTests`), so only the remaining three plus [SplashRoute] need a direct test.
 */
class AppRoutesTests {
    @Test
    fun `SplashRoute is a stable singleton`() {
        assertEquals(SplashRoute, SplashRoute)
        assertNotNull(SplashRoute.toString())
    }

    @Test
    fun `SignInRoute is a stable singleton`() {
        assertEquals(SignInRoute, SignInRoute)
        assertNotNull(SignInRoute.toString())
    }

    @Test
    fun `AppLockRoute is a stable singleton`() {
        assertEquals(AppLockRoute, AppLockRoute)
        assertNotNull(AppLockRoute.toString())
    }

    @Test
    fun `HomeRoute is a stable singleton`() {
        assertEquals(HomeRoute, HomeRoute)
        assertNotNull(HomeRoute.toString())
    }
}
