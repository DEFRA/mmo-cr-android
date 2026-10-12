@file:Suppress("detekt.MaxLineLength")

package uk.gov.defra.mmocatchrecord.feature.catchrecord.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import uk.gov.defra.mmocatchrecord.common.design.RecordStatusTag
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.CatchRecordDraft
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.CatchRecordDraftFactory
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.DmyDate
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.DraftStatus
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.GearUse
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.LandingStorageEntry
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.MeasurementValue
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.NotLandedSpeciesEntry
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.PortSelection
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.PortSelectionMode
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.SpeciesWeightEntry

@RunWith(RobolectricTestRunner::class)
class RoomCatchRecordDraftRepositoryTests {
    private lateinit var database: CatchRecordDatabase
    private lateinit var dao: CatchRecordDraftDao
    private lateinit var repository: RoomCatchRecordDraftRepository
    private var idCounter = 0
    private var clockMillis = 1_000L

    @Before
    fun setUp() {
        database =
            Room
                .inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    CatchRecordDatabase::class.java,
                ).allowMainThreadQueries()
                .build()
        dao = database.catchRecordDraftDao()
        repository =
            RoomCatchRecordDraftRepository(dao = dao, clock = { clockMillis })
    }

    /**
     * Test-only convenience mirroring the pre-BR-XX single-argument API: builds a candidate via
     * [CatchRecordDraftFactory] so every pre-existing call site below keeps compiling unchanged.
     */
    private suspend fun RoomCatchRecordDraftRepository.startDraft(vesselId: String): Result<CatchRecordDraft> {
        val id = "id-${idCounter++}"
        val reference = CatchRecordReferenceGenerator.generate(clockMillis)
        return startDraft(CatchRecordDraftFactory.newDraft(vesselId = vesselId, id = id, reference = reference))
    }

    @Test
    fun `starting a draft for a vessel with no active draft creates a new one`() =
        runTest {
            val result = repository.startDraft("vessel-achilles")
            assertTrue(result.isSuccess)
            val draft = result.getOrThrow()
            assertEquals("vessel-achilles", draft.vesselId)
            assertEquals(DraftStatus.Draft, draft.status)
        }

    @Test
    fun `starting a draft twice for the same vessel returns the existing active draft, not a duplicate`() =
        runTest {
            val first = repository.startDraft("vessel-achilles").getOrThrow()
            val second = repository.startDraft("vessel-achilles").getOrThrow()
            assertEquals(first.id, second.id)
        }

    @Test
    fun `most recently modified active draft is returned across vessels`() =
        runTest {
            val first = repository.startDraft("vessel-achilles").getOrThrow()
            clockMillis = 2_000L
            val second = repository.startDraft("vessel-hercules").getOrThrow()
            clockMillis = 3_000L
            repository.saveDraft(first.copy(isTripToday = true)).getOrThrow()
            val latest = repository.getAnyActiveDraft().getOrThrow()
            assertEquals(first.id, latest?.id)
            assertTrue(second.id != latest?.id)
        }

    @Test
    fun `a submitted draft does not block starting a new active draft for the same vessel`() =
        runTest {
            val first = repository.startDraft("vessel-achilles").getOrThrow()
            repository.saveDraft(first.copy(status = DraftStatus.Submitted)).getOrThrow()
            val second = repository.startDraft("vessel-achilles").getOrThrow()
            assertTrue(second.id != first.id)
        }

    @Test
    fun `getActiveDraft returns null when the vessel has no active draft`() =
        runTest {
            val result = repository.getActiveDraft("vessel-with-no-draft")
            assertTrue(result.isSuccess)
            assertNull(result.getOrThrow())
        }

    @Test
    fun `saving a draft persists the full aggregate and round-trips correctly`() =
        runTest {
            val started = repository.startDraft("vessel-hercules").getOrThrow()
            val fullDraft =
                started.copy(
                    isTripToday = true,
                    departureDate = DmyDate(1, 6, 2026),
                    returnDate = DmyDate(3, 6, 2026),
                    departurePort = PortSelection("port-hastings", PortSelectionMode.Favourite),
                    returnPort = PortSelection("port-dover", PortSelectionMode.FirstTime),
                    gearUses =
                        listOf(
                            GearUse(
                                id = "gear-1",
                                gearTypeId = "gear-trawl",
                                statisticalSubRectangleCode = "rect-hastings-1",
                                measurements =
                                    mapOf(
                                        "mesh_size" to MeasurementValue.Numeric(80.0, "mm"),
                                        "notes" to MeasurementValue.Text("Standard set"),
                                    ),
                                speciesWeights =
                                    listOf(
                                        SpeciesWeightEntry(
                                            id = "sw-1",
                                            speciesId = "species-cod",
                                            weightAboveMinimumSizeKg = 12.0,
                                            weightBelowMinimumSizeKg = 0.0,
                                            weightLegallyDiscardedKg = 0.0,
                                            confirmedCaught = true,
                                        ),
                                    ),
                            ),
                        ),
                    landingStorageEntries =
                        listOf(
                            LandingStorageEntry(id = "storage-1", fields = mapOf("box_count" to "4")),
                        ),
                )
            val saved = repository.saveDraft(fullDraft).getOrThrow()
            val reloaded = repository.getActiveDraft("vessel-hercules").getOrThrow()
            assertNotNull(reloaded)
            assertEquals(saved.id, reloaded!!.id)
            assertEquals(true, reloaded.isTripToday)
            assertEquals(DmyDate(1, 6, 2026), reloaded.departureDate)
            assertEquals(DmyDate(3, 6, 2026), reloaded.returnDate)
            assertEquals(PortSelection("port-hastings", PortSelectionMode.Favourite), reloaded.departurePort)
            assertEquals(PortSelection("port-dover", PortSelectionMode.FirstTime), reloaded.returnPort)
            assertEquals(1, reloaded.gearUses.size)
            val gearUse = reloaded.gearUses.first()
            assertEquals("gear-trawl", gearUse.gearTypeId)
            assertEquals(MeasurementValue.Numeric(80.0, "mm"), gearUse.measurements["mesh_size"])
            assertEquals(MeasurementValue.Text("Standard set"), gearUse.measurements["notes"])
            assertEquals(1, gearUse.speciesWeights.size)
            assertEquals(12.0, gearUse.speciesWeights.first().weightAboveMinimumSizeKg!!, 0.0)
            assertTrue(gearUse.speciesWeights.first().confirmedCaught)
            assertEquals(1, reloaded.landingStorageEntries.size)
            assertEquals("4", reloaded.landingStorageEntries.first().fields["box_count"])
        }

    @Test
    fun `gear uses round-trip their shots count and confirmed-used-on-trip flag`() =
        runTest {
            val started = repository.startDraft("vessel-hercules").getOrThrow()
            val fullDraft =
                started.copy(
                    gearUses =
                        listOf(
                            GearUse(
                                id = "gear-1",
                                gearTypeId = "gear-seine-nets",
                                statisticalSubRectangleCode = null,
                                measurements = mapOf("mesh_size_mm" to MeasurementValue.Numeric(100.0, "mm")),
                                numberOfShots = 4,
                                confirmedUsedOnTrip = true,
                            ),
                        ),
                )
            repository.saveDraft(fullDraft).getOrThrow()
            val reloaded = repository.getActiveDraft("vessel-hercules").getOrThrow()
            val gearUse = reloaded!!.gearUses.single()
            assertEquals(4, gearUse.numberOfShots)
            assertTrue(gearUse.confirmedUsedOnTrip)
        }

    @Test
    fun `gear uses default to unconfirmed with no shots when not set`() =
        runTest {
            val started = repository.startDraft("vessel-hercules").getOrThrow()
            val fullDraft =
                started.copy(
                    gearUses =
                        listOf(
                            GearUse(id = "gear-1", gearTypeId = "gear-seine-nets", statisticalSubRectangleCode = null),
                        ),
                )
            repository.saveDraft(fullDraft).getOrThrow()
            val reloaded = repository.getActiveDraft("vessel-hercules").getOrThrow()
            val gearUse = reloaded!!.gearUses.single()
            assertNull(gearUse.numberOfShots)
            assertTrue(!gearUse.confirmedUsedOnTrip)
        }

    /**
     * Phase 4: [GearUse.statisticalSubRectangleCode] (renamed from `statRectangleId`, DB v2->v3) must
     * round-trip a real-format code string (not a reference-data foreign-key id) — including a code
     * entered via the "Other" free-text search that is not present in the local reference-data stub, since
     * the full geographic grid is not locally enumerable.
     */
    @Test
    fun `statistical sub-rectangle code round-trips including codes absent from the reference-data stub`() =
        runTest {
            val started = repository.startDraft("vessel-hercules").getOrThrow()
            val fullDraft =
                started.copy(
                    gearUses =
                        listOf(
                            GearUse(
                                id = "gear-1",
                                gearTypeId = "gear-seine-nets",
                                statisticalSubRectangleCode = "38E95",
                                confirmedUsedOnTrip = true,
                            ),
                            GearUse(
                                id = "gear-2",
                                gearTypeId = "gear-drifting-longlines",
                                // A validly-formatted but not locally-stubbed code, as entered via the "Other"
                                // free-text autocomplete search screen.
                                statisticalSubRectangleCode = "99Z99",
                                confirmedUsedOnTrip = true,
                            ),
                            GearUse(
                                id = "gear-3",
                                gearTypeId = "gear-handlines",
                                statisticalSubRectangleCode = null,
                                confirmedUsedOnTrip = true,
                            ),
                        ),
                )
            repository.saveDraft(fullDraft).getOrThrow()
            val reloaded = repository.getActiveDraft("vessel-hercules").getOrThrow()
            assertEquals(3, reloaded!!.gearUses.size)
            assertEquals("38E95", reloaded.gearUses.first { it.id == "gear-1" }.statisticalSubRectangleCode)
            assertEquals("99Z99", reloaded.gearUses.first { it.id == "gear-2" }.statisticalSubRectangleCode)
            assertNull(reloaded.gearUses.first { it.id == "gear-3" }.statisticalSubRectangleCode)
        }

    @Test
    fun `a full multi-gear list round-trips with mixed checked, unchecked and shots values`() =
        runTest {
            val started = repository.startDraft("vessel-hercules").getOrThrow()
            val fullDraft =
                started.copy(
                    gearUses =
                        listOf(
                            GearUse(
                                id = "gear-1",
                                gearTypeId = "gear-seine-nets",
                                statisticalSubRectangleCode = null,
                                measurements = mapOf("mesh_size_mm" to MeasurementValue.Numeric(100.0, "mm")),
                                numberOfShots = 3,
                                confirmedUsedOnTrip = true,
                            ),
                            GearUse(
                                id = "gear-2",
                                gearTypeId = "gear-bottom-otter-trawls-tb",
                                statisticalSubRectangleCode = null,
                                measurements =
                                    mapOf(
                                        "number_of_trawl_nets" to MeasurementValue.Numeric(2.0, ""),
                                        "mesh_size_mm" to MeasurementValue.Numeric(80.0, "mm"),
                                    ),
                                confirmedUsedOnTrip = false,
                            ),
                        ),
                )
            repository.saveDraft(fullDraft).getOrThrow()
            val reloaded = repository.getActiveDraft("vessel-hercules").getOrThrow()
            assertEquals(2, reloaded!!.gearUses.size)
            val gearUseIds = reloaded.gearUses.map { it.id }
            assertTrue(gearUseIds.containsAll(listOf("gear-1", "gear-2")))
            val confirmedGear = reloaded.gearUses.first { it.id == "gear-1" }
            assertTrue(confirmedGear.confirmedUsedOnTrip)
            assertEquals(3, confirmedGear.numberOfShots)
            val unconfirmedGear = reloaded.gearUses.first { it.id == "gear-2" }
            assertTrue(!unconfirmedGear.confirmedUsedOnTrip)
            assertNull(unconfirmedGear.numberOfShots)
            assertEquals(
                MeasurementValue.Numeric(2.0, ""),
                unconfirmedGear.measurements["number_of_trawl_nets"],
            )
        }

    @Test
    fun `newly confirmed gear types round-trip their whole-number measurement schemas`() =
        runTest {
            val started = repository.startDraft("vessel-hercules").getOrThrow()
            val fullDraft =
                started.copy(
                    gearUses =
                        listOf(
                            GearUse(
                                id = "gear-1",
                                gearTypeId = "gear-pots",
                                statisticalSubRectangleCode = null,
                                measurements =
                                    mapOf(
                                        "total_pots_or_traps_hauled" to MeasurementValue.Numeric(20.0, ""),
                                        "total_pots_or_traps_left_in_water" to MeasurementValue.Numeric(5.0, ""),
                                    ),
                                numberOfShots = 4,
                                confirmedUsedOnTrip = true,
                            ),
                            GearUse(
                                id = "gear-2",
                                gearTypeId = "gear-gillnets-circling",
                                statisticalSubRectangleCode = null,
                                measurements =
                                    mapOf(
                                        "mesh_size_mm" to MeasurementValue.Numeric(60.0, "mm"),
                                        "total_length_of_nets_hauled_m" to MeasurementValue.Numeric(500.0, "m"),
                                        "total_length_of_nets_left_in_water_m" to MeasurementValue.Numeric(50.0, "m"),
                                    ),
                                confirmedUsedOnTrip = false,
                            ),
                        ),
                )
            repository.saveDraft(fullDraft).getOrThrow()
            val reloaded = repository.getActiveDraft("vessel-hercules").getOrThrow()
            assertEquals(2, reloaded!!.gearUses.size)
            val pots = reloaded.gearUses.first { it.id == "gear-1" }
            assertEquals("gear-pots", pots.gearTypeId)
            assertTrue(pots.confirmedUsedOnTrip)
            assertEquals(4, pots.numberOfShots)
            assertEquals(MeasurementValue.Numeric(20.0, ""), pots.measurements["total_pots_or_traps_hauled"])
            assertEquals(MeasurementValue.Numeric(5.0, ""), pots.measurements["total_pots_or_traps_left_in_water"])
            val gillnets = reloaded.gearUses.first { it.id == "gear-2" }
            assertEquals("gear-gillnets-circling", gillnets.gearTypeId)
            assertTrue(!gillnets.confirmedUsedOnTrip)
            assertNull(gillnets.numberOfShots)
            assertEquals(MeasurementValue.Numeric(60.0, "mm"), gillnets.measurements["mesh_size_mm"])
            assertEquals(
                MeasurementValue.Numeric(500.0, "m"),
                gillnets.measurements["total_length_of_nets_hauled_m"],
            )
            assertEquals(
                MeasurementValue.Numeric(50.0, "m"),
                gillnets.measurements["total_length_of_nets_left_in_water_m"],
            )
        }

    /**
     * Phase 5: the redesigned [SpeciesWeightEntry] (nullable weight fields + [SpeciesWeightEntry.confirmedCaught])
     * and the draft-level [NotLandedSpeciesEntry] list (DB v3->v4) must round-trip correctly, including an
     * added-but-not-yet-confirmed species entry (all weights null, `confirmedCaught = false`).
     */
    @Test
    fun `species weight entries and not-landed species entries round-trip, including unconfirmed entries`() =
        runTest {
            val started = repository.startDraft("vessel-hercules").getOrThrow()
            val fullDraft =
                started.copy(
                    gearUses =
                        listOf(
                            GearUse(
                                id = "gear-1",
                                gearTypeId = "gear-seine-nets",
                                statisticalSubRectangleCode = "38E95",
                                confirmedUsedOnTrip = true,
                                speciesWeights =
                                    listOf(
                                        SpeciesWeightEntry(
                                            id = "sw-1",
                                            speciesId = "species-cod",
                                            weightAboveMinimumSizeKg = 12.5,
                                            weightBelowMinimumSizeKg = 1.5,
                                            weightLegallyDiscardedKg = 0.5,
                                            confirmedCaught = true,
                                        ),
                                        SpeciesWeightEntry(
                                            id = "sw-2",
                                            speciesId = "species-plaice",
                                            confirmedCaught = false,
                                        ),
                                    ),
                            ),
                        ),
                    notLandedStraightAway = true,
                    notLandedSpeciesEntries =
                        listOf(
                            NotLandedSpeciesEntry(speciesId = "species-cod", weightAboveMinimumSizeKeptOnboardKg = 3.0),
                        ),
                )
            repository.saveDraft(fullDraft).getOrThrow()
            val reloaded = repository.getActiveDraft("vessel-hercules").getOrThrow()
            assertNotNull(reloaded)
            assertEquals(true, reloaded!!.notLandedStraightAway)
            assertEquals(1, reloaded.notLandedSpeciesEntries.size)
            assertEquals("species-cod", reloaded.notLandedSpeciesEntries.first().speciesId)
            assertEquals(3.0, reloaded.notLandedSpeciesEntries.first().weightAboveMinimumSizeKeptOnboardKg!!, 0.0)

            val gearUse = reloaded.gearUses.single()
            assertEquals(2, gearUse.speciesWeights.size)
            val cod = gearUse.speciesWeights.first { it.speciesId == "species-cod" }
            assertTrue(cod.confirmedCaught)
            assertEquals(12.5, cod.weightAboveMinimumSizeKg!!, 0.0)
            assertEquals(1.5, cod.weightBelowMinimumSizeKg!!, 0.0)
            assertEquals(0.5, cod.weightLegallyDiscardedKg!!, 0.0)
            val plaice = gearUse.speciesWeights.first { it.speciesId == "species-plaice" }
            assertTrue(!plaice.confirmedCaught)
            assertNull(plaice.weightAboveMinimumSizeKg)
            assertNull(plaice.weightBelowMinimumSizeKg)
            assertNull(plaice.weightLegallyDiscardedKg)
        }

    @Test
    fun `notLandedStraightAway defaults to null and notLandedSpeciesEntries defaults to empty`() =
        runTest {
            val started = repository.startDraft("vessel-hercules").getOrThrow()
            repository.saveDraft(started).getOrThrow()
            val reloaded = repository.getActiveDraft("vessel-hercules").getOrThrow()
            assertNull(reloaded!!.notLandedStraightAway)
            assertTrue(reloaded.notLandedSpeciesEntries.isEmpty())
        }

    @Test
    fun `re-saving a draft replaces its previous children rather than accumulating them`() =
        runTest {
            val started = repository.startDraft("vessel-hercules").getOrThrow()
            val withOneGear =
                started.copy(
                    gearUses =
                        listOf(
                            GearUse(id = "gear-1", gearTypeId = "gear-trawl", statisticalSubRectangleCode = null),
                        ),
                )
            repository.saveDraft(withOneGear).getOrThrow()
            val withNoGear = withOneGear.copy(gearUses = emptyList())
            repository.saveDraft(withNoGear).getOrThrow()
            val reloaded = repository.getActiveDraft("vessel-hercules").getOrThrow()
            assertTrue(reloaded!!.gearUses.isEmpty())
        }

    @Test
    fun `deleting a draft removes it and its children via cascade`() =
        runTest {
            val started = repository.startDraft("vessel-achilles").getOrThrow()
            val withGear =
                started.copy(
                    gearUses =
                        listOf(
                            GearUse(id = "gear-1", gearTypeId = "gear-trawl", statisticalSubRectangleCode = null),
                        ),
                )
            repository.saveDraft(withGear).getOrThrow()
            val deleteResult = repository.deleteDraft(withGear.id)
            assertTrue(deleteResult.isSuccess)
            assertNull(dao.findDraftEntity(withGear.id))
            assertNull(repository.getActiveDraft("vessel-achilles").getOrThrow())
        }

    @Test
    fun `marking a draft ready to submit transitions its status`() =
        runTest {
            val started = repository.startDraft("vessel-achilles").getOrThrow()
            val result = repository.markReadyToSubmit(started.id)
            assertTrue(result.isSuccess)
            assertEquals(DraftStatus.ReadyToSubmit, result.getOrThrow().status)
        }

    @Test
    fun `marking an unknown draft ready to submit fails`() =
        runTest {
            val result = repository.markReadyToSubmit("unknown-draft-id")
            assertTrue(result.isFailure)
        }

    @Test
    fun `starting a draft generates and persists a catch record reference`() =
        runTest {
            clockMillis = 1_700_000_000_000L
            val started = repository.startDraft("vessel-achilles").getOrThrow()
            assertNotNull(started.catchRecordReference)
            assertEquals(CatchRecordReferenceGenerator.generate(clockMillis), started.catchRecordReference)

            // Round-trips unchanged through a save/reload.
            val reloaded = repository.getActiveDraft("vessel-achilles").getOrThrow()
            assertEquals(started.catchRecordReference, reloaded?.catchRecordReference)
        }

    @Test
    fun `late-submission acknowledgement and pending-sync status round-trip correctly`() =
        runTest {
            val started = repository.startDraft("vessel-achilles").getOrThrow()
            val updated =
                started.copy(
                    status = DraftStatus.PendingSync,
                    lateSubmissionWarningAcknowledged = true,
                )
            repository.saveDraft(updated).getOrThrow()

            // PendingSync is a terminal, non-"active" status, so it must be reloaded by id rather than via
            // getActiveDraft (which only ever returns Draft/ReadyToSubmit — see [DraftStatus.isActive]).
            val reloaded = repository.getDraftById(updated.id).getOrThrow()
            assertEquals(DraftStatus.PendingSync, reloaded?.status)
            assertTrue(reloaded?.lateSubmissionWarningAcknowledged == true)
        }

    @Test
    fun `a pending-sync draft does not block starting a new active draft for the same vessel`() =
        runTest {
            val first = repository.startDraft("vessel-achilles").getOrThrow()
            repository.saveDraft(first.copy(status = DraftStatus.PendingSync)).getOrThrow()
            val second = repository.startDraft("vessel-achilles").getOrThrow()
            assertTrue(second.id != first.id)
        }

    @Test
    fun `createdAt is stamped once by startDraft and is never overwritten by later saves`() =
        runTest {
            clockMillis = 5_000L
            val started = repository.startDraft("vessel-achilles").getOrThrow()
            assertEquals(5_000L, started.createdAtEpochMillis)

            clockMillis = 9_000L
            val saved = repository.saveDraft(started.copy(isTripToday = true)).getOrThrow()
            assertEquals(5_000L, saved.createdAtEpochMillis)
        }

    @Test
    fun `submittedAt is stamped the first time a draft becomes pending sync and is not overwritten`() =
        runTest {
            val started = repository.startDraft("vessel-achilles").getOrThrow()
            assertNull(started.submittedAtEpochMillis)

            clockMillis = 7_000L
            val pending = repository.saveDraft(started.copy(status = DraftStatus.PendingSync)).getOrThrow()
            assertEquals(7_000L, pending.submittedAtEpochMillis)

            clockMillis = 8_000L
            val resaved = repository.saveDraft(pending.copy(lateSubmissionWarningAcknowledged = true)).getOrThrow()
            assertEquals(7_000L, resaved.submittedAtEpochMillis)
        }

    @Test
    fun `syncedAt is stamped only once a draft reaches submitted, not at pending sync`() =
        runTest {
            val started = repository.startDraft("vessel-achilles").getOrThrow()
            clockMillis = 7_000L
            val pending = repository.saveDraft(started.copy(status = DraftStatus.PendingSync)).getOrThrow()
            assertNull(pending.syncedAtEpochMillis)

            clockMillis = 9_000L
            val submitted = repository.saveDraft(pending.copy(status = DraftStatus.Submitted)).getOrThrow()
            assertEquals(9_000L, submitted.syncedAtEpochMillis)
            assertEquals(7_000L, submitted.submittedAtEpochMillis)
        }

    @Test
    fun `an online submission stamps submittedAt and syncedAt at the same instant`() =
        runTest {
            val started = repository.startDraft("vessel-achilles").getOrThrow()
            clockMillis = 11_000L
            val submitted = repository.saveDraft(started.copy(status = DraftStatus.Submitted)).getOrThrow()
            assertEquals(11_000L, submitted.submittedAtEpochMillis)
            assertEquals(11_000L, submitted.syncedAtEpochMillis)
        }

    @Test
    fun `observeRecordSummaries excludes discarded drafts`() =
        runTest {
            val kept = repository.startDraft("vessel-achilles").getOrThrow()
            val discarded = repository.startDraft("vessel-hercules").getOrThrow()
            repository.saveDraft(discarded.copy(status = DraftStatus.Discarded)).getOrThrow()

            repository.observeRecordSummaries().test {
                val summaries = awaitItem()
                assertEquals(listOf(kept.id), summaries.map { it.id })
                assertEquals(RecordStatusTag.Draft, summaries.single().status)
            }
        }

    @Test
    fun `observeRecordSummaries sorts by modifiedAt desc then id desc as a tiebreak`() =
        runTest {
            clockMillis = 1_000L
            val oldest = repository.startDraft("vessel-achilles").getOrThrow()
            clockMillis = 2_000L
            val newest = repository.startDraft("vessel-hercules").getOrThrow()
            clockMillis = 2_000L
            val tiebreak = repository.startDraft("vessel-icarus").getOrThrow()

            repository.observeRecordSummaries().test {
                val ids = awaitItem().map { it.id }
                val tiedAtTopTwo = listOf(newest.id, tiebreak.id).sortedDescending()
                assertEquals(tiedAtTopTwo + oldest.id, ids)
            }
        }

    @Test
    fun `observeRecordSummaries emits again when a draft is modified`() =
        runTest {
            val started = repository.startDraft("vessel-achilles").getOrThrow()

            repository.observeRecordSummaries().test {
                assertEquals(RecordStatusTag.Draft, awaitItem().single().status)

                clockMillis = 4_000L
                repository.markReadyToSubmit(started.id).getOrThrow()
                assertEquals(RecordStatusTag.ReadyToSubmit, awaitItem().single().status)
            }
        }

    @Test
    fun `observeRecordSummaries maps a draft with no return date to a null tripEndDate`() =
        runTest {
            repository.startDraft("vessel-achilles").getOrThrow()

            repository.observeRecordSummaries().test {
                assertNull(awaitItem().single().tripEndDate)
            }
        }
}
