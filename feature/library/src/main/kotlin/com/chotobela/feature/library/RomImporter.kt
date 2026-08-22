package com.chotobela.feature.library

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.chotobela.core.common.DispatcherProvider
import com.chotobela.core.database.dao.LibraryDao
import com.chotobela.core.database.entity.GameEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

sealed interface ImportResult {
    data class Success(val gameTitle: String) : ImportResult
    data class Rejected(val reason: String) : ImportResult
}

/**
 * Imports user-provided ROMs via SAF into the private library storage.
 * Platform detection by extension; CHIP-8 supported at launch, more cores
 * slot in behind the same engine ABI later.
 */
@Singleton
class RomImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val libraryDao: LibraryDao,
    private val dispatchers: DispatcherProvider
) {

    suspend fun import(uri: Uri): ImportResult = withContext(dispatchers.io) {
        val resolver = context.contentResolver

        val displayName = queryDisplayName(uri) ?: return@withContext Rejected("Unreadable file")
        val safeName = displayName.replace(Regex("[^A-Za-z0-9._ -]"), "_")
        val ext = safeName.substringAfterLast('.', "").lowercase()

        val platformInfo = when (ext) {
            "ch8", "chip8" -> Triple("CHIP-8", "chip8", "roms/chip8")
            else -> return@withContext Rejected(
                "Unsupported format .$ext — CHIP-8 (.ch8) supported at launch"
            )
        }

        val bytes = runCatching { resolver.openInputStream(uri)?.use { it.readBytes() } }
            .getOrNull()
            ?: return@withContext Rejected("Could not read file")

        val romsDir = File(context.filesDir, platformInfo.third).apply { mkdirs() }
        val target = File(romsDir, "${uniqueId(safeName)}_$safeName")
        val tmp = File(romsDir, target.name + ".tmp")
        tmp.writeBytes(bytes)
        if (!tmp.renameTo(target)) {
            // fallback copy across filesystems
            tmp.copyTo(target, overwrite = true)
            tmp.delete()
        }

        val title = safeName.removeSuffix(".$ext").trim().ifEmpty { "Imported ROM" }
        libraryDao.upsert(
            GameEntity(
                id = uniqueId(displayName),
                title = title,
                platform = platformInfo.first,
                core = platformInfo.second,
                romPath = target.absolutePath,
                sizeBytes = target.length(),
                addedAt = System.currentTimeMillis()
            )
        )
        ImportResult.Success(title)
    }

    private fun uniqueId(name: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(name.toByteArray())
        return "local-" + digest.take(6).joinToString("") { "%02x".format(it) }
    }

    private fun queryDisplayName(uri: Uri): String? =
        runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && idx >= 0) cursor.getString(idx) else null
            } ?: uri.lastPathSegment
        }.getOrNull()
}
