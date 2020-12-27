package org.tasks.jobs

import android.content.ContentResolver
import android.content.Context
import androidx.documentfile.provider.DocumentFile
import androidx.hilt.Assisted
import androidx.hilt.work.WorkerInject
import androidx.work.WorkerParameters
import com.todoroo.andlib.utility.DateUtilities
import com.todoroo.astrid.backup.BackupConstants
import org.tasks.R
import org.tasks.analytics.Firebase
import org.tasks.backup.TasksJsonExporter
import org.tasks.preferences.Preferences
import timber.log.Timber
import java.io.File
import java.io.FileFilter
import java.util.*

class BackupWork @WorkerInject constructor(
        @Assisted context: Context,
        @Assisted workerParams: WorkerParameters,
        firebase: Firebase,
        private val tasksJsonExporter: TasksJsonExporter,
        private val preferences: Preferences,
        private val workManager: WorkManager) : RepeatingWorker(context, workerParams, firebase) {
    
    override suspend fun run(): Result {
        preferences.setLong(R.string.p_last_backup, DateUtilities.now())
        startBackup(context)
        return Result.success()
    }

    override suspend fun scheduleNext() = workManager.scheduleBackup()

    private suspend fun startBackup(context: Context?) {
        if (!preferences.getBoolean(R.string.p_backups_enabled, true)) {
            Timber.d("Automatic backups disabled")
            return
        }
        try {
            deleteOldLocalBackups()
        } catch (e: Exception) {
            Timber.e(e)
        }
        try {
            tasksJsonExporter.exportTasks(
                    context, TasksJsonExporter.ExportType.EXPORT_TYPE_SERVICE, null)
        } catch (e: Exception) {
            Timber.e(e)
        }
    }

    private fun deleteOldLocalBackups() {
        val uri = preferences.backupDirectory
        when (uri?.scheme) {
            ContentResolver.SCHEME_CONTENT -> {
                val dir = DocumentFile.fromTreeUri(context, uri)
                for (file in getDeleteList(dir?.listFiles())) {
                    if (!file.delete()) {
                        Timber.e("Unable to delete: %s", file)
                    }
                }
            }
            ContentResolver.SCHEME_FILE -> {
                val astridDir = File(uri.path)
                val fileArray = astridDir.listFiles(FILE_FILTER)
                for (file in getDeleteList(fileArray, DAYS_TO_KEEP_BACKUP)) {
                    if (!file.delete()) {
                        Timber.e("Unable to delete: %s", file)
                    }
                }
            }
        }
    }

    companion object {
        const val DAYS_TO_KEEP_BACKUP = 7
        val BACKUP_FILE_NAME_REGEX = Regex("auto\\.[-\\d]+\\.json")
        private val FILENAME_FILTER = { f: String -> f.matches(BACKUP_FILE_NAME_REGEX) }
        val FILE_FILTER = FileFilter { f: File -> FILENAME_FILTER(f.name) }
        private val BY_LAST_MODIFIED = { f1: File, f2: File ->
            BackupConstants.getTimestamp(f2).compareTo(BackupConstants.getTimestamp(f1))
        }
        private val DOCUMENT_FILE_COMPARATOR = Comparator { d1: DocumentFile, d2: DocumentFile -> d2.lastModified().compareTo(d1.lastModified()) }

        fun getDeleteList(fileArray: Array<File>?, keepNewest: Int) =
                fileArray?.sortedWith(BY_LAST_MODIFIED)?.drop(keepNewest) ?: emptyList()

        private fun getDeleteList(fileArray: Array<DocumentFile>?) =
                fileArray
                        ?.filter { FILENAME_FILTER(it.name!!) }
                        ?.sortedWith(DOCUMENT_FILE_COMPARATOR)
                        ?.drop(DAYS_TO_KEEP_BACKUP)
                        ?: emptyList()
    }
}