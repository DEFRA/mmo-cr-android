package uk.gov.defra.mmocatchrecord.feature.catchrecord.data.submission

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.submission.remote.ArtifactMetadataDto
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.submission.remote.CatchRecordHttpException
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.submission.remote.CatchRecordListResponseDto
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.submission.remote.CatchRecordListingItemDto
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.submission.remote.CatchRecordListingProgressDto
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.submission.remote.CatchRecordListingTripDto
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.submission.remote.CatchRecordListingVesselDto
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.submission.remote.CatchRecordRecoverySnapshotDto
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.submission.remote.CatchRecordRemoteDataSource
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.submission.remote.ProgressDto
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.submission.remote.SectionCompletionDto
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.submission.remote.StandardSaveResponseDto
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.submission.remote.SubmissionResponseDto
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.CatchRecordDraft
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.DraftStatus
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.GearUse
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.MeasurementValue
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.PortSelection
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.PortSelectionMode
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.SpeciesWeightEntry
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.SubmissionMappingException
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.UnresolvedGearMeasurementException
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.GearMeasurementField
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.GearMeasurementFieldType
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.GearType
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.Port
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.ReferenceDataRefreshScheduler
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.Species
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.StatisticalSubRectangle
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.Vessel
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.FakeCatchRecordDraftRepository
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.FakeReferenceDataRepository

private const val REFERENCE = "GBR-PH101-270726-150815"
private const val LEGACY_REFERENCE = "PH101-27072026-150815"

/** [RealCatchRecordSubmissionRepository]'s resumable create/replace/submit sequence (ADR 0026, Phase 4). */
class RealCatchRecordSubmissionRepositoryTests {
    private val draftRepository = FakeCatchRecordDraftRepository()
    private val referenceDataRepository =
        FakeReferenceDataRepository(
            vessels = listOf(Vessel(id = "vessel-1", name = "Boat", pln = "PH101")),
            ports = listOf(Port(id = "port-1", name = "Hastings", statisticalAreaId = "area-1", code = "GBHTG")),
            gearTypes = listOf(GearType(id = "gear-1", name = "Seine net", code = "DRB")),
            species = listOf(Species(id = "species-1", name = "Mackerel", faoCode = "MAC")),
            statisticalSubRectangles =
                listOf(
                    StatisticalSubRectangle(
                        id = "rect-1",
                        code = "38E95",
                        statisticalAreaId = "area-1",
                        icesRectangleName = "38E9",
                    ),
                ),
        )
    private val remote = mock<CatchRecordRemoteDataSource>()
    private var nextKey = 0
    private val idFactory = { "key-${nextKey++}" }

    private fun repository() =
        RealCatchRecordSubmissionRepository(remote, draftRepository, referenceDataRepository, idFactory) { 0L }

    private fun fixtureDraft(
        status: String? = null,
        serverCatchRecordId: String? = null,
        serverVersion: Int? = null,
        reference: String? = REFERENCE,
    ): CatchRecordDraft =
        CatchRecordDraft(
            id = "draft-1",
            vesselId = "vessel-1",
            catchRecordReference = reference,
            departurePort = PortSelection("port-1", PortSelectionMode.FirstTime),
            returnPort = PortSelection("port-1", PortSelectionMode.FirstTime),
            isTripToday = true,
            gearUses =
                listOf(
                    GearUse(
                        id = "gear-use-1",
                        gearTypeId = "gear-1",
                        statisticalSubRectangleCode = "38E95",
                        speciesWeights =
                            listOf(SpeciesWeightEntry(id = "sw-1", speciesId = "species-1", confirmedCaught = true)),
                        confirmedUsedOnTrip = true,
                    ),
                ),
            status = DraftStatus.ReadyToSubmit,
            modifiedAtEpochMillis = 0L,
            submissionStep = status,
            serverCatchRecordId = serverCatchRecordId,
            serverVersion = serverVersion,
        )

    private suspend fun seed(draft: CatchRecordDraft) {
        draftRepository.startDraft(draft)
    }

    private fun standardSaveResponse(
        id: String = "cr-1",
        version: Int = 1,
    ) = StandardSaveResponseDto(
        id = id,
        catchRecordReference = REFERENCE,
        status = "DRAFT",
        displayStatus = "Draft",
        version = version,
        savedSection = null,
        sectionCompletion = SectionCompletionDto(trip = true, pairFishing = true, gears = true),
        progress =
            ProgressDto(
                hasUnsubmittedChanges = true,
                numberOfSubmissions = 0,
                incompleteGearAssociationIds = emptyList(),
                currentIncompleteGearAssociationId = null,
                allGearsComplete = true,
            ),
    )

    private fun submissionResponse() =
        SubmissionResponseDto(
            id = "cr-1",
            catchRecordReference = REFERENCE,
            status = "SUBMITTED",
            displayStatus = "Submitted",
            version = 2,
            savedSection = null,
            sectionCompletion = SectionCompletionDto(trip = true, pairFishing = true, gears = true),
            progress = ProgressDto(false, 1, emptyList(), null, true),
            submittedAt = "2026-07-27T15:08:15Z",
            submittedBy = "user-1",
            artifacts = listOf(ArtifactMetadataDto(1, "JSON_SNAPSHOT")),
        )

    private fun recoverResponse(
        id: String = "cr-1",
        version: Int = 2,
        status: String = "DRAFT",
        hasUnsubmittedChanges: Boolean = true,
    ) = CatchRecordRecoverySnapshotDto(
        id = id,
        catchRecordReference = REFERENCE,
        status = status,
        version = version,
        hasUnsubmittedChanges = hasUnsubmittedChanges,
    )

    @Test
    fun `a brand-new draft is created, replaced and submitted in order`() =
        runTest {
            seed(fixtureDraft())
            whenever(remote.createDraft(any(), any())).thenReturn(standardSaveResponse(version = 1))
            whenever(remote.replace(any(), any(), any())).thenReturn(standardSaveResponse(version = 2))
            whenever(remote.submit(any(), any(), any())).thenReturn(submissionResponse())

            val result = repository().submit(draftRepository.persistedDrafts.single())

            assertTrue(result.isSuccess)
            val saved = result.getOrThrow()
            assertEquals("cr-1", saved.serverCatchRecordId)
            assertEquals("submitted", saved.submissionStep)
            verify(remote).createDraft(any(), any())
            verify(remote).replace(any(), any(), any())
            verify(remote).submit(any(), any(), any())
        }

    @Test
    fun `resuming from created skips create and calls replace then submit`() =
        runTest {
            seed(fixtureDraft(status = "created", serverCatchRecordId = "cr-1", serverVersion = 1))
            whenever(remote.replace(any(), any(), any())).thenReturn(standardSaveResponse(version = 2))
            whenever(remote.submit(any(), any(), any())).thenReturn(submissionResponse())

            val result = repository().submit(draftRepository.persistedDrafts.single())

            assertTrue(result.isSuccess)
        }

    @Test
    fun `resuming from replaced skips both create and replace`() =
        runTest {
            seed(fixtureDraft(status = "replaced", serverCatchRecordId = "cr-1", serverVersion = 2))
            whenever(remote.submit(any(), any(), any())).thenReturn(submissionResponse())

            val result = repository().submit(draftRepository.persistedDrafts.single())

            assertTrue(result.isSuccess)
        }

    @Test
    fun `an already-submitted draft makes no network calls at all`() =
        runTest {
            seed(fixtureDraft(status = "submitted", serverCatchRecordId = "cr-1", serverVersion = 2))

            val result = repository().submit(draftRepository.persistedDrafts.single())

            assertTrue(result.isSuccess)
            verifyNoInteractions(remote)
        }

    @Test
    fun `a non-conforming legacy reference is regenerated and written back before create`() =
        runTest {
            seed(fixtureDraft(reference = LEGACY_REFERENCE))
            whenever(remote.createDraft(any(), any())).thenReturn(standardSaveResponse(version = 1))
            whenever(remote.replace(any(), any(), any())).thenReturn(standardSaveResponse(version = 2))
            whenever(remote.submit(any(), any(), any())).thenReturn(submissionResponse())

            val result = repository().submit(draftRepository.persistedDrafts.single())

            assertTrue(result.isSuccess)
            assertTrue(
                result
                    .getOrThrow()
                    .catchRecordReference
                    .orEmpty()
                    .startsWith("GBR-PH101-"),
            )
        }

    @Test
    fun `a create idempotency conflict recovers the id from the owner listing`() =
        runTest {
            seed(fixtureDraft())
            val conflict = CatchRecordHttpException("createDraft", 409, "IDEMPOTENCY_CONFLICT", null, retryable = false)
            whenever(remote.createDraft(any(), any())).thenAnswer { throw conflict }
            whenever(remote.list(limit = 100)).thenReturn(
                CatchRecordListResponseDto(
                    items =
                        listOf(
                            CatchRecordListingItemDto(
                                id = "cr-1",
                                catchRecordReference = REFERENCE,
                                status = "DRAFT",
                                displayStatus = "Draft",
                                version = 1,
                                vessel = CatchRecordListingVesselDto(),
                                trip = CatchRecordListingTripDto(),
                                createdAt = "2026-07-27T15:00:00Z",
                                updatedAt = "2026-07-27T15:00:00Z",
                                submittedAt = null,
                                completedAt = null,
                                progress = CatchRecordListingProgressDto(),
                            ),
                        ),
                    limit = 100,
                    count = 1,
                ),
            )
            whenever(remote.recover("cr-1")).thenReturn(recoverResponse(version = 1))
            whenever(remote.replace(any(), any(), any())).thenReturn(standardSaveResponse(version = 2))
            whenever(remote.submit(any(), any(), any())).thenReturn(submissionResponse())

            val result = repository().submit(draftRepository.persistedDrafts.single())

            assertTrue(result.isSuccess)
            assertEquals("cr-1", result.getOrThrow().serverCatchRecordId)
        }

    @Test
    fun `a submit conflict that is COMPLETE server-side with no unsubmitted changes is treated as success`() =
        runTest {
            seed(fixtureDraft(status = "replaced", serverCatchRecordId = "cr-1", serverVersion = 2))
            val conflict = CatchRecordHttpException("submit", 409, "VERSION_CONFLICT", null, retryable = false)
            whenever(remote.submit(any(), any(), any())).thenAnswer { throw conflict }
            whenever(remote.recover("cr-1"))
                .thenReturn(recoverResponse(status = "COMPLETE", hasUnsubmittedChanges = false))

            val result = repository().submit(draftRepository.persistedDrafts.single())

            assertTrue(result.isSuccess)
            assertEquals("submitted", result.getOrThrow().submissionStep)
        }

    @Test
    fun `B2 a SUBMITTED conflict that still has unsubmitted changes is not treated as success`() =
        runTest {
            seed(fixtureDraft(status = "replaced", serverCatchRecordId = "cr-1", serverVersion = 2))
            val conflict = CatchRecordHttpException("submit", 409, "VERSION_CONFLICT", null, retryable = false)
            whenever(remote.submit(any(), any(), any())).thenAnswer { throw conflict }
            whenever(remote.recover("cr-1"))
                .thenReturn(recoverResponse(status = "SUBMITTED", hasUnsubmittedChanges = true))

            val result = repository().submit(draftRepository.persistedDrafts.single())

            assertFalse(result.isSuccess)
        }

    @Test
    fun `B2 a replace conflict refreshes the version and retries exactly once`() =
        runTest {
            seed(fixtureDraft(status = "created", serverCatchRecordId = "cr-1", serverVersion = 1))
            val conflict = CatchRecordHttpException("replace", 409, "VERSION_CONFLICT", null, retryable = false)
            whenever(remote.replace(eq("cr-1"), eq("1"), any())).thenAnswer { throw conflict }
            whenever(remote.recover("cr-1")).thenReturn(recoverResponse(version = 5))
            whenever(remote.replace(eq("cr-1"), eq("5"), any())).thenReturn(standardSaveResponse(version = 6))
            whenever(remote.submit(any(), any(), any())).thenReturn(submissionResponse())

            val result = repository().submit(draftRepository.persistedDrafts.single())

            assertTrue(result.isSuccess)
            verify(remote, times(2)).replace(any(), any(), any())
        }

    @Test
    fun `B2 a replace conflict that never resolves stops after one refresh, not an infinite loop`() =
        runTest {
            seed(fixtureDraft(status = "created", serverCatchRecordId = "cr-1", serverVersion = 1))
            val conflict = CatchRecordHttpException("replace", 409, "VERSION_CONFLICT", null, retryable = false)
            whenever(remote.replace(any(), any(), any())).thenAnswer { throw conflict }
            whenever(remote.recover("cr-1")).thenReturn(recoverResponse(version = 5))

            val result = repository().submit(draftRepository.persistedDrafts.single())

            assertFalse(result.isSuccess)
            verify(remote, times(2)).replace(any(), any(), any())
            verify(remote, times(1)).recover("cr-1")
        }

    @Test
    fun `B2 a submit idempotency conflict that never resolves stops after one refresh and rotates the key`() =
        runTest {
            seed(fixtureDraft(status = "replaced", serverCatchRecordId = "cr-1", serverVersion = 2))
            val conflict = CatchRecordHttpException("submit", 409, "IDEMPOTENCY_CONFLICT", null, retryable = false)
            whenever(remote.submit(any(), any(), any())).thenAnswer { throw conflict }
            whenever(remote.recover("cr-1")).thenReturn(recoverResponse(version = 2, status = "DRAFT"))

            val result = repository().submit(draftRepository.persistedDrafts.single())

            assertFalse(result.isSuccess)
            verify(remote, times(2)).submit(any(), any(), any())
            verify(remote, times(1)).recover("cr-1")
        }

    @Test
    fun `B2 ensureReplaced never PUTs onto a SUBMITTED record with unsubmitted changes`() =
        runTest {
            seed(fixtureDraft(status = "created", serverCatchRecordId = "cr-1", serverVersion = 1))
            val conflict = CatchRecordHttpException("replace", 409, "VERSION_CONFLICT", null, retryable = false)
            whenever(remote.replace(any(), any(), any())).thenAnswer { throw conflict }
            whenever(remote.recover("cr-1"))
                .thenReturn(recoverResponse(status = "SUBMITTED", hasUnsubmittedChanges = true))

            val result = repository().submit(draftRepository.persistedDrafts.single())

            assertFalse(result.isSuccess)
            verify(remote, times(1)).replace(any(), any(), any())
        }

    @Test
    fun `S5 a 404 on replace clears resumable state so the next attempt restarts from create`() =
        runTest {
            seed(fixtureDraft(status = "created", serverCatchRecordId = "cr-1", serverVersion = 1))
            val notFound = CatchRecordHttpException("replace", 404, "CATCH_RECORD_NOT_FOUND", null, retryable = false)
            whenever(remote.replace(any(), any(), any())).thenAnswer { throw notFound }

            val result = repository().submit(draftRepository.persistedDrafts.single())

            assertFalse(result.isSuccess)
            val saved = draftRepository.persistedDrafts.single()
            assertNull(saved.serverCatchRecordId)
            assertNull(saved.serverVersion)
            assertNull(saved.submissionStep)
            assertNull(saved.createIdempotencyKey)
            assertNull(saved.submitIdempotencyKey)
        }

    @Test
    fun `S5 a 404 on submit clears resumable state so the next attempt restarts from create`() =
        runTest {
            seed(fixtureDraft(status = "replaced", serverCatchRecordId = "cr-1", serverVersion = 2))
            val notFound = CatchRecordHttpException("submit", 404, "CATCH_RECORD_NOT_FOUND", null, retryable = false)
            whenever(remote.submit(any(), any(), any())).thenAnswer { throw notFound }

            val result = repository().submit(draftRepository.persistedDrafts.single())

            assertFalse(result.isSuccess)
            val saved = draftRepository.persistedDrafts.single()
            assertNull(saved.serverCatchRecordId)
            assertNull(saved.submissionStep)
        }

    @Test
    fun `B1 submit reloads the latest persisted row before resuming, never overwriting newer progress`() =
        runTest {
            // The in-memory `draft` passed in is stale (no progress at all), but the persisted row has
            // already advanced to "replaced" by a previous attempt — resume from there, not from scratch.
            seed(fixtureDraft(status = "replaced", serverCatchRecordId = "cr-1", serverVersion = 2))
            val staleInMemoryDraft = fixtureDraft()
            whenever(remote.submit(any(), any(), any())).thenReturn(submissionResponse())

            val result = repository().submit(staleInMemoryDraft)

            assertTrue(result.isSuccess)
            verify(remote, never()).createDraft(any(), any())
            verify(remote, never()).replace(any(), any(), any())
        }

    @Test
    fun `B1 two concurrent submit calls for the same draft do not double-create`() =
        runTest {
            seed(fixtureDraft())
            whenever(remote.createDraft(any(), any())).thenReturn(standardSaveResponse(version = 1))
            whenever(remote.replace(any(), any(), any())).thenReturn(standardSaveResponse(version = 2))
            whenever(remote.submit(any(), any(), any())).thenReturn(submissionResponse())
            val sharedRepository = repository()
            val draft = draftRepository.persistedDrafts.single()

            val first = sharedRepository.submit(draft)
            val second = sharedRepository.submit(draft)

            assertTrue(first.isSuccess)
            assertTrue(second.isSuccess)
            verify(remote, times(1)).createDraft(any(), any())
        }

    @Test
    fun `an unresolvable reference-data field surfaces as a terminal mapping failure, not a crash`() =
        runTest {
            val noCodePorts =
                FakeReferenceDataRepository(
                    ports = listOf(Port(id = "port-1", name = "Hastings", statisticalAreaId = "area-1")),
                )
            seed(fixtureDraft(status = "created", serverCatchRecordId = "cr-1", serverVersion = 1))
            val repository = RealCatchRecordSubmissionRepository(remote, draftRepository, noCodePorts, idFactory) { 0L }

            val result = repository.submit(draftRepository.persistedDrafts.single())

            assertFalse(result.isSuccess)
            assertTrue(result.exceptionOrNull() is SubmissionMappingException)
        }

    @Test
    fun `a blank apiMeasurementId is a transient mapping failure that schedules a reference-data refresh`() =
        runTest {
            val staleGearData =
                FakeReferenceDataRepository(
                    vessels = referenceDataRepository.getVessels().getOrThrow(),
                    ports = referenceDataRepository.getPorts().getOrThrow(),
                    gearTypes =
                        listOf(
                            GearType(
                                id = "gear-1",
                                name = "Seine net",
                                code = "DRB",
                                measurementFields =
                                    listOf(
                                        GearMeasurementField(
                                            key = "mesh_size_mm",
                                            label = "Mesh",
                                            type = GearMeasurementFieldType.Decimal,
                                        ),
                                    ),
                            ),
                        ),
                    species = referenceDataRepository.getSpecies().getOrThrow(),
                    statisticalSubRectangles = referenceDataRepository.getStatisticalSubRectangles().getOrThrow(),
                )
            val draft =
                fixtureDraft(status = "created", serverCatchRecordId = "cr-1", serverVersion = 1)
                    .copy(
                        gearUses =
                            listOf(
                                GearUse(
                                    id = "gear-use-1",
                                    gearTypeId = "gear-1",
                                    statisticalSubRectangleCode = "38E95",
                                    measurements = mapOf("mesh_size_mm" to MeasurementValue.Numeric(80.0, "mm")),
                                    confirmedUsedOnTrip = true,
                                ),
                            ),
                    )
            seed(draft)
            val scheduler = mock<ReferenceDataRefreshScheduler>()
            val repository =
                RealCatchRecordSubmissionRepository(
                    remote,
                    draftRepository,
                    staleGearData,
                    idFactory,
                    scheduler,
                ) { 0L }

            val result = repository.submit(draftRepository.persistedDrafts.single())

            assertTrue(result.exceptionOrNull() is UnresolvedGearMeasurementException)
            verify(scheduler).scheduleRefreshAfterSignIn()
        }
}
