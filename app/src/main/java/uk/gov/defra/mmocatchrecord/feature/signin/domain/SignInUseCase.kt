package uk.gov.defra.mmocatchrecord.feature.signin.domain

import uk.gov.defra.mmocatchrecord.core.architecture.ValidationHelper
import javax.inject.Inject

/**
 * Repository abstraction over authenticating a user against MMO identity.
 *
 * **Real implementation contract (later stage):** must call the DEFRA/MMO identity endpoint over TLS
 * only, must never log the supplied credentials, and must map every transport/HTTP failure to
 * [SignInFailure] rather than leaking raw exception/HTTP details to the UI layer.
 */
interface SignInRepository {
    suspend fun signIn(
        username: String,
        password: String,
    ): Result<SignInSession>
}

/**
 * Use-case wrapping [SignInRepository.signIn] with pure input validation via [ValidationHelper], so the
 * ViewModel never has to duplicate validation rules.
 */
class SignInUseCase
    @Inject
    constructor(
        private val repository: SignInRepository,
    ) {
        suspend operator fun invoke(
            username: String,
            password: String,
        ): Result<SignInSession> = repository.signIn(username, password)
    }
