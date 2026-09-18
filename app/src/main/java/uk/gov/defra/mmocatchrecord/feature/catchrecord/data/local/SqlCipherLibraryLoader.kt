package uk.gov.defra.mmocatchrecord.feature.catchrecord.data.local

/**
 * Loads the SQLCipher native library exactly once per process.
 *
 * `net.zetetic:sqlcipher-android` (see ADR 0006) does not auto-load its own native library via a
 * manifest-declared initializer or a static class initializer — unlike some native-library AARs, callers
 * must explicitly call `System.loadLibrary("sqlcipher")` themselves before opening any
 * [net.zetetic.database.sqlcipher.SQLiteDatabase]/Room database that uses
 * [net.zetetic.database.sqlcipher.SupportOpenHelperFactory]. Omitting this call surfaces as a confusing
 * `UnsatisfiedLinkError: No implementation found for ... nativeOpen` at first database access rather than
 * a clear "library not found" error, since the native method is simply never registered.
 *
 * Call [ensureLoaded] once before any SQLCipher-backed database is opened — both in production (see
 * `di/DatabaseModule.kt`) and from instrumented tests that open a SQLCipher database directly.
 */
object SqlCipherLibraryLoader {
    @Volatile
    private var loaded = false

    @Synchronized
    fun ensureLoaded() {
        if (!loaded) {
            System.loadLibrary("sqlcipher")
            loaded = true
        }
    }
}
