package tools.confido.application.routes

import java.io.File
import java.util.Timer
import kotlin.concurrent.schedule

/**
 * Slide decks are rendered and inspected by poppler's command-line tools
 * (`pdfinfo`, `pdftoppm`, `pdftohtml`, `pdftocairo` from the poppler-utils package) rather
 * than in-process: PDF rasterization in the JVM goes through AWT/Java2D, whose
 * JNI, reflection and resource lookups depend on the deck's contents and can
 * never be enumerated completely for the GraalVM native image (see git history
 * of native-config/ for how far that was taken). A subprocess boundary turns
 * that into "bytes in, files out", identical on the JVM and native builds.
 *
 * Tools are looked up on PATH, or in `CONFIDO_POPPLER_BIN_DIR` if set. Absence
 * is reported at startup and turns slide import into a 503, not a crash.
 */
object Poppler {
    private val binDir: File? = System.getenv("CONFIDO_POPPLER_BIN_DIR")?.takeIf { it.isNotBlank() }?.let { File(it) }
    private val tools = listOf("pdfinfo", "pdftoppm", "pdftohtml", "pdftocairo")

    /** Tools that could not be found; empty when slide import can work. */
    val missing: List<String> by lazy { tools.filter { resolve(it) == null } }
    val available: Boolean get() = missing.isEmpty()

    fun resolve(tool: String): File? {
        if (binDir != null) return File(binDir, tool).takeIf { it.canExecute() }
        return (System.getenv("PATH") ?: "").split(File.pathSeparator)
            .asSequence().filter { it.isNotEmpty() }
            .map { File(it, tool) }.firstOrNull { it.canExecute() }
    }

    class ToolFailed(val tool: String, message: String) : Exception("$tool: $message")

    /**
     * Run [tool] with [args] and return its stdout. Blocking (call on an IO
     * dispatcher). Kills the process after [timeoutSeconds]; a non-zero exit or
     * a timeout throws [ToolFailed] carrying the tail of stderr.
     */
    fun run(tool: String, vararg args: String, timeoutSeconds: Long): ByteArray {
        val exe = resolve(tool) ?: throw ToolFailed(tool, "not installed")
        val errFile = File.createTempFile("confido-$tool-", ".stderr")
        try {
            val process = ProcessBuilder(listOf(exe.path) + args)
                .redirectError(errFile)
                .redirectInput(ProcessBuilder.Redirect.from(File("/dev/null")))
                .start()
            var timedOut = false
            val killer = Timer(true)
            killer.schedule(timeoutSeconds * 1000) { timedOut = true; process.destroyForcibly() }
            val out = try { process.inputStream.use { it.readBytes() } } finally { killer.cancel() }
            val exit = process.waitFor()
            if (timedOut) throw ToolFailed(tool, "gave up after $timeoutSeconds s")
            if (exit != 0) {
                val err = errFile.readText().lines().filter { it.isNotBlank() && !it.startsWith("Syntax Warning") }
                throw ToolFailed(tool, "exit $exit" + (err.lastOrNull()?.let { ": $it" } ?: ""))
            }
            return out
        } finally {
            errFile.delete()
        }
    }
}
