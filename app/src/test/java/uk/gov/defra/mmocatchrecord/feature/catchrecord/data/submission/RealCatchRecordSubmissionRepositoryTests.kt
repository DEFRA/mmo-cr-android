package uk.gov.defra.mmocatchrecord.feature.catchrecord.data.submission

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
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
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.submission.remote.CatchRecordRemoteDataSource
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.submission.remote.PairFishingDto
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.submission.remote.PortRefDto
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.submission.remote.ProgressDto
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.submission.remote.RetrieveCatchRecordResponseDto
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.submission.remote.SectionCompletionDto
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.submission.remote.StandardSaveResponseDto
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.submission.remote.SubmissionResponseDto
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.submission.remote.TripSnapshotDto
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.submission.remote.VesselSnapshotDto
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.CatchRecordDraft
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.DraftStatus
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.GearUse
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.PortSelection
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.PortSelectionMode
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.SpeciesWeightEntry
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.SubmissionMappingException
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.GearType
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.Port
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.Species
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.StatisticalSubRectangle
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.Vessel
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.FakeCatchRecordDraftRepository
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.FakeReferenceDataRepository

private const val REFERENCE = "GBR-PH101-270726-150815"
private const val LEGACY_REFERENCE = "PH101-27072026-150815"

/** [RealCatchRecordSubmissionRepository]'s resumable create/replace/submit sequence (ADR 0022, Phase 4). */
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

    private fun retrieveResponse(
        id: String = "cr-1",
        version: Int = 2,
        status: String = "DRAFT",
    ) = RetrieveCatchRecordResponseDto(
        schemaVersion = 1,
        id = id,
        catchRecordReference = REFERENCE,
        ownerUserId = "user-1",
        status = status,
        version = version,
        numberOfSubmissions = 0,
        hasUnsubmittedChanges = true,
        vessel =
            VesselSnapshotDto(
                id = "vessel-1",
                rssSnapshot = "PH101",
                nameSnapshot = "Boat",
                externalMarkSnapshot = "PH101",
            ),
        trip =
            TripSnapshotDto(
                startedAndFinishedToday = true,
                dateStarted = "2026-07-27",
                dateEnded = "2026-07-27",
                departurePort = PortRefDto("port-1", "GBHTG", "Hastings"),
                returnPort = PortRefDto("port-1", "GBHTG", "Hastings"),
            ),
        pairFishing = PairFishingDto(enabled = false, pairVessel = null, pairSkipperName = null),
        gears = emptyList(),
        speciesNotLanded = emptyList(),
        artifacts = emptyList(),
        createdAt = "2026-07-27T15:00:00Z",
        createdBy = "user-1",
        updatedAt = "2026-07-27T15:00:00Z",
        updatedBy = "user-1",
        submittedAt = null,
        submittedBy = null,
        completedAt = null,
        completedBy = null,
        displayStatus = "Draft",
        sectionCompletion = SectionCompletionDto(trip = true, pairFishing = true, gears = true),
        completedSections = emptyList(),
        incompleteSections = emptyList(),
        progress = ProgressDto(true, 0, emptyList(), null, true),
        submissionEligible = true,
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
            val saved = draftRepository.persistedDrafts.single()
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
            val saved = draftRepository.persistedDrafts.single()
            assertTrue(saved.catchRecordReference.orEmpty().startsWith("GBR-PH101-"))
        }

    @Test
    fun `a create idempotency conflict recovers the id from the owner listing`() =
        runTest {
            seed(fixtureDraft())
            val conflict = CatchRecordHttpException("createDraft", 409, "IDEMPOTENCY_CONFLICT", null, retryable = false)
            whenever(remote.createDraft(any(), any())).thenAnswer { throw conflict }
            whenever(remote.list()).thenReturn(
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
                    limit = 20,
                    count = 1,
                ),
            )
            whenever(remote.retrieve("cr-1")).thenReturn(retrieveResponse(version = 1))
            whenever(remote.replace(any(), any(), any())).thenReturn(standardSaveResponse(version = 2))
            whenever(remote.submit(any(), any(), any())).thenReturn(submissionResponse())

            val result = repository().submit(draftRepository.persistedDrafts.single())

            assertTrue(result.isSuccess)
            assertEquals("cr-1", draftRepository.persistedDrafts.single().serverCatchRecordId)
        }

    @Test
    fun `a submit conflict that is already submitted server-side is treated as success`() =
        runTest {
            seed(fixtureDraft(status = "replaced", serverCatchRecordId = "cr-1", serverVersion = 2))
            val conflict = CatchRecordHttpException("submit", 409, "VERSION_CONFLICT", null, retryable = false)
            whenever(remote.submit(any(), any(), any())).thenAnswer { throw conflict }
            whenever(remote.retrieve("cr-1")).thenReturn(retrieveResponse(status = "SUBMITTED"))

            val result = repository().submit(draftRepository.persistedDrafts.single())

            assertTrue(result.isSuccess)
            assertEquals("submitted", draftRepository.persistedDrafts.single().submissionStep)
        }

    @Test
    fun `a replace conflict refreshes the version and retries once`() =
        runTest {
            seed(fixtureDraft(status = "created", serverCatchRecordId = "cr-1", serverVersion = 1))
            val conflict = CatchRecordHttpException("replace", 409, "VERSION_CONFLICT", null, retryable = false)
            whenever(remote.replace(eq("cr-1"), eq("1"), any())).thenAnswer { throw conflict }
            whenever(remote.retrieve("cr-1")).thenReturn(retrieveResponse(version = 5))
            whenever(remote.replace(eq("cr-1"), eq("5"), any())).thenReturn(standardSaveResponse(version = 6))
            whenever(remote.submit(any(), any(), any())).thenReturn(submissionResponse())

            val result = repository().submit(draftRepository.persistedDrafts.single())

            assertTrue(result.isSuccess)
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
}
