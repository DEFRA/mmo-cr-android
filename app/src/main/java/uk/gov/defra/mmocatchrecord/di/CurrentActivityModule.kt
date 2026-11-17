package uk.gov.defra.mmocatchrecord.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import uk.gov.defra.mmocatchrecord.core.android.CurrentActivityProvider
import uk.gov.defra.mmocatchrecord.core.android.CurrentActivityProviderImpl
import javax.inject.Singleton

/** Binds [CurrentActivityProvider] to the single [CurrentActivityProviderImpl] registered in `MmoApplication`. */
@Module
@InstallIn(SingletonComponent::class)
fun interface CurrentActivityModule {
    @Binds
    @Singleton
    fun bindCurrentActivityProvider(impl: CurrentActivityProviderImpl): CurrentActivityProvider
}
