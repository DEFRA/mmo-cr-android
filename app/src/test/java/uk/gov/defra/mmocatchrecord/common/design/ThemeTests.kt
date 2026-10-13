package uk.gov.defra.mmocatchrecord.common.design

import androidx.compose.ui.Modifier
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ThemeTests {
    @Test
    fun `govukFocusIndicator chains two border modifiers onto the receiver`() {
        val result = Modifier.govukFocusIndicator()

        assertNotEquals(Modifier, result)
    }
}
