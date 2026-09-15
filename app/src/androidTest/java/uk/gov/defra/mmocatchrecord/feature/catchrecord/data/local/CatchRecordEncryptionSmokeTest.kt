package uk.gov.defra.mmocatchrecord.feature.catchrecord.data.local

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.test.runTest
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.CatchRecordDraft
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.DraftStatus
import java.io.File
import java.security.SecureRandom

/**
 * Instrumented encryption smoke test for ADR 0006: asserts the on-disk catch-record draft database file
 * is **not** plaintext-readable — a known, distinctive vessel id written through the SQLCipher-backed
 * Room database must not appear as a readable substring of the raw database file bytes. Requires a real
 * Android device/emulator (SQLCipher ships Android-only native `.so` libraries, so this cannot run under
 * a plain JVM/Robolectric unit test).
 */
@RunWith(AndroidJUnit4::class)
class CatchRecordEncryptionSmokeTest {
    private val databaseName = "encryption-smoke-test.db"
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    @After
    fun deleteDatabase() {
        context.deleteDatabase(databaseName)
    }

    @Before
    fun loadNativeLibrary() {
        SqlCipherLibraryLoader.ensureLoaded()
    }

    @Test
    fun encryptedDatabaseFileIsNotPlaintextReadable() =
        runTest {
            val distinctiveVesselId = "VESSEL-PLAINTEXT-CANARY-1234567890"
            val passphrase = ByteArray(32).also { SecureRandom().nextBytes(it) }

            val database =
                Room
                    .databaseBuilder(context, CatchRecordDatabase::class.java, databaseName)
                    .openHelperFactory(SupportOpenHelperFactory(passphrase))
                    .build()

            database.catchRecordDraftDao().upsertDraft(
                DraftMappers
                    .toEntities(
                        CatchRecordDraft(
                            id = "draft-1",
                            vesselId = distinctiveVesselId,
                            status = DraftStatus.Draft,
                            modifiedAtEpochMillis = 0L,
                        ),
                    ).draft,
            )
            database.close()

            val rawBytes = context.getDatabasePath(databaseName).readAllBytes()
            val rawFileText = String(rawBytes, Charsets.ISO_8859_1)

            assertFalse(
                "The raw encrypted database file must not contain the plaintext vessel id",
                rawFileText.contains(distinctiveVesselId),
            )
        }

    @Test
    fun openingTheDatabaseWithTheWrongPassphraseFails() =
        runTest {
            val correctPassphrase = ByteArray(32).also { SecureRandom().nextBytes(it) }
            val database =
                Room
                    .databaseBuilder(context, CatchRecordDatabase::class.java, databaseName)
                    .openHelperFactory(SupportOpenHelperFactory(correctPassphrase))
                    .build()
            database.openHelper.writableDatabase
            database.close()

            val wrongPassphrase = ByteArray(32).also { SecureRandom().nextBytes(it) }
            val reopened =
                Room
                    .databaseBuilder(context, CatchRecordDatabase::class.java, databaseName)
                    .openHelperFactory(SupportOpenHelperFactory(wrongPassphrase))
                    .build()

            var openedSuccessfully = true
            try {
                reopened.openHelper.writableDatabase
            } catch (expected: Exception) {
                openedSuccessfully = false
            } finally {
                reopened.close()
            }

            assertTrue(
                "Opening the encrypted database with the wrong passphrase must fail",
                !openedSuccessfully,
            )
        }
}

private fun File.readAllBytes(): ByteArray = this.inputStream().use { it.readBytes() }
