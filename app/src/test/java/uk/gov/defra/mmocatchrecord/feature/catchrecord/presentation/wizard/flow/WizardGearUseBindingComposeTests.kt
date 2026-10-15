package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.CatchRecordDraft
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.DraftStatus
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.GearUse

/** Composable-only coverage for [rememberWizardGearUse]'s pin/[LaunchedEffect] logic; [resolveWizardGearUse]
 *  and [gearRouteFor] are already exercised as plain functions in [WizardGearUseBindingTests]. */
@RunWith(RobolectricTestRunner::class)
class WizardGearUseBindingComposeTests {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val gearOne = GearUse("gear-use-1", "gear-seine-nets", statisticalSubRectangleCode = "38E95")
    private val gearTwoPending = GearUse("gear-use-2", "gear-seine-nets", statisticalSubRectangleCode = null)
    private val nextPending: (CatchRecordDraft) -> GearUse? =
        { it.gearUses.firstOrNull { gear -> gear.statisticalSubRectangleCode == null } }

    private fun draftWith(vararg gearUses: GearUse) =
        CatchRecordDraft(
            id = "draft-1",
            vesselId = "vessel-1",
            gearUses = gearUses.toList(),
            modifiedAtEpochMillis = 0L,
            status = DraftStatus.Draft,
        )

    @Test
    fun `the first resolved gear is pinned and stays shown after it is no longer pending`() {
        val draftState = mutableStateOf(draftWith(gearOne, gearTwoPending))
        var resolved: GearUse? = null
        composeTestRule.setContent {
            resolved =
                rememberWizardGearUse(
                    draft = draftState.value,
                    editGearUseId = null,
                    initialGearUseId = null,
                    nextPending = nextPending,
                )
        }
        composeTestRule.waitForIdle()
        assertEquals("gear-use-2", resolved?.id)

        // The pending gear is now complete, so a fresh resolution would move on — the pin must keep showing it.
        draftState.value = draftWith(gearOne, gearTwoPending.copy(statisticalSubRectangleCode = "38E95"))
        composeTestRule.waitForIdle()
        assertEquals("gear-use-2", resolved?.id)
        assertEquals("38E95", resolved?.statisticalSubRectangleCode)

        // Removing the pinned gear entirely changes resolvedId, re-triggering the effect with an
        // already-non-null boundGearUseId — it must not retarget the remaining gear.
        draftState.value = draftWith(gearOne)
        composeTestRule.waitForIdle()
        assertNull(resolved)
    }

    @Test
    fun `an explicit edit id is authoritative and is never pinned as the bound id`() {
        val draftState = mutableStateOf(draftWith(gearOne, gearTwoPending))
        val editIdState = mutableStateOf<String?>("gear-use-1")
        var resolved: GearUse? = null
        composeTestRule.setContent {
            resolved =
                rememberWizardGearUse(
                    draft = draftState.value,
                    editGearUseId = editIdState.value,
                    initialGearUseId = null,
                    nextPending = nextPending,
                )
        }
        composeTestRule.waitForIdle()
        assertEquals("gear-use-1", resolved?.id)

        // If the edit id had ever been pinned as boundGearUseId, clearing it would still resolve to
        // gear-use-1; instead it must fall through to whichever gear is genuinely still pending.
        editIdState.value = null
        composeTestRule.waitForIdle()
        assertEquals("gear-use-2", resolved?.id)
    }
}
