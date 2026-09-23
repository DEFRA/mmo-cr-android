package uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.map

/**
 * Supplies the offline map-rendering geometry (land, statistical sub-rectangles, ports) used by the
 * interactive statistical-sub-area map+list screen — see ADR 0013.
 *
 * Deliberately a **dedicated interface**, separate from
 * [uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.ReferenceDataRepository]: geometry
 * for ~2,857 sea-overlapping sub-rectangles is a much larger, map-feature-specific dataset than the
 * lightweight nearby-rectangle lookup rows the wizard state already holds, and keeping it behind its own
 * seam avoids bloating `CatchRecordFlowViewState` with polygon data no other screen needs.
 *
 * Backed in Stage 1 by a bundled derived binary asset (see
 * `uk.gov.defra.mmocatchrecord.feature.catchrecord.data.map.AssetMapGeometryRepository`) rather than a real
 * API — following the same drop-in-replaceable stub shape as ADR 0008.
 */
interface MapGeometryRepository {
    /** The full/global geometry dataset — see [MapGeometryDataset]. */
    suspend fun getMapGeometry(): Result<MapGeometryDataset>
}
