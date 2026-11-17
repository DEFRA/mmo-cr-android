package uk.gov.defra.mmocatchrecord.releasetools

import java.io.PrintStream
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.system.exitProcess

private const val USAGE =
    "usage: release-tools inspect|package|verify --bundle|--base <aab> ... (see build-logic/release-tools)"

fun main(args: Array<String>) {
    exitProcess(runCli(args))
}

/** Runs one subcommand; returns the exit code and prints a single-line error on failure. */
fun runCli(
    args: Array<String>,
    out: PrintStream = System.out,
    err: PrintStream = System.err,
): Int =
    try {
        val command = args.firstOrNull() ?: throw ReleaseToolsException(USAGE)
        val options = Options.parse(args.drop(1))
        when (command) {
            "inspect" -> {
                val json = Inspector.inspect(options.path("bundle")).toJson()
                options.path("out").toFile().writeText(json)
            }
            "package" -> {
                Packager.packageBundle(
                    base = options.path("base"),
                    out = options.path("out"),
                    packageName = options.value("package-name"),
                    versionCode = options.int("version-code"),
                    config = options.path("config"),
                )
                out.println("packaged ${options.value("package-name")} (${options.int("version-code")})")
            }
            "verify" -> {
                Verifier.verify(
                    base = options.path("base"),
                    packaged = options.path("package"),
                    packageName = options.value("package-name"),
                    versionCode = options.int("version-code"),
                    config = options.path("config"),
                )
                out.println("verified ${options.value("package-name")} (${options.int("version-code")})")
            }
            else -> throw ReleaseToolsException("unknown command '$command'. $USAGE")
        }
        0
    } catch (e: ReleaseToolsException) {
        err.println("error: ${e.message}")
        1
    }

private class Options(private val values: Map<String, String>) {
    fun value(name: String): String = values[name] ?: throw ReleaseToolsException("missing --$name")

    fun path(name: String): Path = Paths.get(value(name))

    fun int(name: String): Int =
        value(name).toIntOrNull() ?: throw ReleaseToolsException("--$name must be a whole number")

    companion object {
        fun parse(args: List<String>): Options {
            if (args.size % 2 != 0) throw ReleaseToolsException("each option needs a value")
            val values =
                args.chunked(2).associate { (flag, value) ->
                    if (!flag.startsWith("--")) throw ReleaseToolsException("unexpected argument '$flag'")
                    flag.removePrefix("--") to value
                }
            return Options(values)
        }
    }
}
