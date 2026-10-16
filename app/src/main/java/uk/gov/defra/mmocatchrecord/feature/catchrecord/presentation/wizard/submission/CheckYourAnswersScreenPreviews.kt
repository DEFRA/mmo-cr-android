package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.submission

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import uk.gov.defra.mmocatchrecord.common.design.MmoTheme
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.CatchRecordDraft
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.DmyDate
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.GearUse
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.MeasurementValue
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.PortSelection
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.PortSelectionMode
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.SpeciesWeightEntry
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.GearMeasurementField
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.GearMeasurementFieldType
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.GearType
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.Port
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.Species
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.Vessel

// Moved out of CheckYourAnswersScreen.kt (excluded from Sonar coverage as `*Previews.kt`) — this preview
// data and the composables below exist solely for Android Studio rendering and are never invoked at runtime.

private val previewVessel = Vessel(id = "vessel-1", name = "The Providence")
private val previewDeparturePort = Port(id = "port-1", name = "Newlyn", statisticalAreaId = "area-1")
private val previewReturnPort = Port(id = "port-2", name = "Plymouth", statisticalAreaId = "area-1")
private val previewGearType =
    GearType(
        id = "gear-seine",
        name = "Seine nets",
        measurementFields =
            listOf(
                GearMeasurementField(
                    key = "mesh_size_mm",
                    label = "Mesh size",
                    type = GearMeasurementFieldType.Integer,
                    unit = "mm",
                ),
            ),
    )
private val previewSpecies = Species(id = "species-cod", name = "Cod", faoCode = "COD")

private val previewDraft =
    CatchRecordDraft(
        id = "draft-1",
        vesselId = previewVessel.id,
        isTripToday = false,
        departureDate = DmyDate(1, 6, 2024),
        returnDate = DmyDate(3, 6, 2024),
        departurePort = PortSelection(previewDeparturePort.id, PortSelectionMode.FirstTime),
        returnPort = PortSelection(previewReturnPort.id, PortSelectionMode.FirstTime),
        gearUses =
            listOf(
                GearUse(
                    id = "gear-use-1",
                    gearTypeId = previewGearType.id,
                    statisticalSubRectangleCode = "38E9",
                    confirmedUsedOnTrip = true,
                    numberOfShots = 4,
                    measurements = mapOf("mesh_size_mm" to MeasurementValue.Numeric(50.0, "mm")),
                    speciesWeights =
                        listOf(
                            SpeciesWeightEntry(
                                id = "sw-1",
                                speciesId = previewSpecies.id,
                                weightAboveMinimumSizeKg = 12.5,
                                confirmedCaught = true,
                            ),
                        ),
                ),
            ),
        modifiedAtEpochMillis = 0L,
    )

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF)
@Suppress("FunctionNaming")
@Composable
fun CheckYourAnswersScreenContentPreview() {
    MmoTheme {
        CheckYourAnswersScreenContent(
            draft = previewDraft,
            vessels = listOf(previewVessel),
            ports = listOf(previewDeparturePort, previewReturnPort),
            gearTypes = listOf(previewGearType),
            species = listOf(previewSpecies),
            onChangeRow = {},
            onSubmit = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF, name = "Offline")
@Suppress("FunctionNaming")
@Composable
fun CheckYourAnswersScreenContentOfflinePreview() {
    MmoTheme {
        CheckYourAnswersScreenContent(
            draft = previewDraft,
            vessels = listOf(previewVessel),
            ports = listOf(previewDeparturePort, previewReturnPort),
            gearTypes = listOf(previewGearType),
            species = listOf(previewSpecies),
            onChangeRow = {},
            onSubmit = {},
            isOffline = true,
        )
    }
}
