package app.aoki.example.debug

import android.content.ContentValues
import android.content.Context
import app.aoki.example.module.NativeBridge
import android.os.Build
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import com.topjohnwu.superuser.Shell

enum class SectionStatus(val label: String) {
    OK("ok"),
    MISSING("missing"),
    EMPTY("empty"),
    FAILED("failed"),
    DEGRADED("degraded"),
    NOT_APPLICABLE("not-applicable"),
    NOT_CAPTURED("not-captured"),
}

fun interface SectionCapture {
    fun capture(context: Context): String
}

data class ExportSection(
    val id: String,
    val fileName: String,
    val capture: SectionCapture,
)

data class SectionResult(
    val section: ExportSection,
    val status: SectionStatus,
    val bytes: Long,
    val sha256: String,
)

data class ExportResult(
    val zipFile: File,
    val downloadCopyStatus: String,
    val sections: List<SectionResult>,
)

@Serializable
data class AppInfo(
    val packageName: String,
    val versionName: String?,
    val versionCode: Long,
    val manufacturer: String,
    val model: String,
    val androidRelease: String,
    val sdkInt: Int,
    val fingerprint: String,
)

@Serializable
data class IndexEntry(
    val id: String,
    val file: String,
    val status: String,
    val bytes: Long,
    val sha256: String,
)

@Serializable
data class NativeHooks(
    val loadStatus: String,
    val installStatus: String,
    val openatCount: Long,
    val closeCount: Long,
)

@Serializable
data class RootInfo(
    val granted: String,
    val command: String,
    val success: Boolean,
    val exitCode: Int,
    val out: String,
    val err: String,
)

@Serializable
data class IndexFile(
    val packageName: String,
    val sections: List<IndexEntry>,
)

object DebugExporter {

    private val json = Json { prettyPrint = true }

    private val sections: List<ExportSection> = listOf(
        ExportSection("app-info", "app-info.json") { context ->
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            json.encodeToString(
                AppInfo(
                    packageName = context.packageName,
                    versionName = info.versionName,
                    versionCode = info.longVersionCode,
                    manufacturer = Build.MANUFACTURER,
                    model = Build.MODEL,
                    androidRelease = Build.VERSION.RELEASE,
                    sdkInt = Build.VERSION.SDK_INT,
                    fingerprint = Build.FINGERPRINT,
                ),
            )
        },
        ExportSection("journal", "journal.txt") { context ->
            Journal.readAll(context)
        },
        ExportSection("native-hooks", "native-hooks.json") { _ ->
            val counts = NativeBridge.hookCounts()
            json.encodeToString(
                NativeHooks(
                    loadStatus = NativeBridge.loadStatus,
                    installStatus = NativeBridge.hooksInstallStatus,
                    openatCount = counts.getOrElse(0) { 0L },
                    closeCount = counts.getOrElse(1) { 0L },
                ),
            )
        },
        ExportSection("root", "root.json") { _ ->
            val r = Shell.cmd("id").exec()
            json.encodeToString(
                RootInfo(
                    granted = Shell.isAppGrantedRoot()?.toString() ?: "unknown",
                    command = "id",
                    success = r.isSuccess,
                    exitCode = r.code,
                    out = r.out.joinToString("\n"),
                    err = r.err.joinToString("\n"),
                ),
            )
        },
    )

    fun export(context: Context): ExportResult {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val staging = File(context.cacheDir, "export-" + stamp)
        staging.mkdirs()

        val results = mutableListOf<SectionResult>()
        for (section in sections) {
            val outcome = runCatching { section.capture.capture(context) }
            val target = File(staging, section.fileName)
            val result = outcome.fold(
                onSuccess = { text ->
                    if (text.isEmpty()) {
                        SectionResult(section, SectionStatus.EMPTY, 0L, sha256(ByteArray(0)))
                    } else {
                        val bytes = text.toByteArray(Charsets.UTF_8)
                        target.writeBytes(bytes)
                        SectionResult(section, SectionStatus.OK, bytes.size.toLong(), sha256(bytes))
                    }
                },
                onFailure = {
                    SectionResult(section, SectionStatus.FAILED, 0L, sha256(ByteArray(0)))
                },
            )
            results += result
        }

        val index = IndexFile(
            packageName = "debug-package-" + stamp,
            sections = results.map { r ->
                IndexEntry(
                    id = r.section.id,
                    file = r.section.fileName,
                    status = r.status.label,
                    bytes = r.bytes,
                    sha256 = r.sha256,
                )
            },
        )
        File(staging, "index.json").writeText(json.encodeToString(index))

        val exportsDir = File(context.filesDir, "exports")
        exportsDir.mkdirs()
        val zipFile = File(exportsDir, "debug-package-" + stamp + ".zip")
        ZipOutputStream(FileOutputStream(zipFile)).use { zip ->
            for (file in staging.listFiles().orEmpty().sortedBy { it.name }) {
                if (file.name == "index.json") continue
                zip.putNextEntry(ZipEntry(file.name))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
            val indexFile = File(staging, "index.json")
            zip.putNextEntry(ZipEntry("index.json"))
            indexFile.inputStream().use { it.copyTo(zip) }
            zip.closeEntry()
        }
        staging.deleteRecursively()

        val downloadStatus = copyToDownloads(context, zipFile)

        return ExportResult(zipFile, downloadStatus, results)
    }

    private fun copyToDownloads(context: Context, zipFile: File): String {
        return runCatching {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, zipFile.name)
                put(MediaStore.Downloads.MIME_TYPE, "application/zip")
                put(MediaStore.Downloads.RELATIVE_PATH, "Download")
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: return "insert returned null"
            context.contentResolver.openOutputStream(uri)?.use { out ->
                zipFile.inputStream().use { it.copyTo(out) }
            } ?: return "output stream null"
            "copied"
        }.getOrElse { "failed: " + (it.message ?: it.javaClass.simpleName) }
    }

    private fun sha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }
}
