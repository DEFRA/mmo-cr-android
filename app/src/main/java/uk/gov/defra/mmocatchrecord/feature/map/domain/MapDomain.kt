package uk.gov.defra.mmocatchrecord.feature.map.domain

import javax.inject.Inject

/** Domain model for a single mapped catch location. */
data class CatchLocation(
    val id: String,
    val latitude: Double,
    val longitude: Double,
    val label: String,
)

/**
 * Repository abstraction over catch location data.
 *
 * **Real implementation contract (later stage):** must read from the same offline-first Room store as
 * `feature.catchrecord`; the map SDK/rendering choice (e.g. Google Maps Compose) is not yet decided and
 * must be confirmed with the developer before a real map view is built (see the
 * `android-project-scaffold` tech-stack confirmation gate for any new third-party SDK dependency).
 */
interface MapRepository {
    suspend fun getCatchLocations(): Result<List<CatchLocation>>
}

/** Use-case wrapping [MapRepository.getCatchLocations]. */
class GetCatchLocationsUseCase
    @Inject
    constructor(
        private val repository: MapRepository,
    ) {
    suspend operator fun invoke(): Result<List<CatchLocation>> = repository.getCatchLocations()
}
