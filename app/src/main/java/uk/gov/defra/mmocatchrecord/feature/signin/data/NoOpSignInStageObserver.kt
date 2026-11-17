package uk.gov.defra.mmocatchrecord.feature.signin.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import uk.gov.defra.mmocatchrecord.feature.signin.domain.SignInStage
import uk.gov.defra.mmocatchrecord.feature.signin.domain.SignInStageObserver

/** [SignInStageObserver] for [FakeSignInRepository] — stays [SignInStage.Idle], never redirects. */
internal object NoOpSignInStageObserver : SignInStageObserver {
    override val stage: StateFlow<SignInStage> = MutableStateFlow(SignInStage.Idle)
}
