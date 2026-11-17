package uk.gov.defra.mmocatchrecord.feature.signin.domain

/** Clears the signed-in account so no [SignInRepository] can go on minting tokens after sign-out. */
fun interface SignOutRepository {
    /** Reports success so a caller never silently treats a failed clear as safe (review fix 2.2). */
    suspend fun signOut(): Boolean
}
