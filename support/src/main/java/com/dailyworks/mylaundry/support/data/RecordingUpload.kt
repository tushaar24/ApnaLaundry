package com.dailyworks.mylaundry.support.data

import android.content.Context
import android.net.Uri
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.dailyworks.mylaundry.support.SupportApp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.concurrent.TimeUnit

/** What the agent sees about a call's recording upload on this phone. */
enum class UploadState { IDLE, WORKING, DONE, NOT_FOUND, FAILED }

object RecordingUploads {
    private fun name(callId: String) = "recording-$callId"

    /** Find the dialer's recording for this call (or use [fileUri]) and upload it. */
    fun enqueue(
        context: Context,
        callId: String,
        phone: String,
        startedAtMs: Long,
        endedAtMs: Long,
        fileUri: Uri? = null,
    ) {
        val req = OneTimeWorkRequestBuilder<UploadRecordingWorker>()
            .setInputData(
                workDataOf(
                    K_CALL to callId, K_PHONE to phone, K_START to startedAtMs,
                    K_END to endedAtMs, K_URI to fileUri?.toString(),
                ),
            )
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            // Dialers save the file a few seconds after hang-up; some only after
            // the recorder app is reopened. Keep looking for ~40 minutes.
            .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.SECONDS)
            .setInitialDelay(if (fileUri == null) 5 else 0, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(name(callId), ExistingWorkPolicy.REPLACE, req)
    }

    fun state(context: Context, callId: String): Flow<UploadState> =
        WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(name(callId)).map { infos ->
            val info = infos.firstOrNull() ?: return@map UploadState.IDLE
            when (info.state) {
                WorkInfo.State.ENQUEUED, WorkInfo.State.RUNNING, WorkInfo.State.BLOCKED -> UploadState.WORKING
                WorkInfo.State.SUCCEEDED -> UploadState.DONE
                WorkInfo.State.CANCELLED -> UploadState.IDLE
                WorkInfo.State.FAILED ->
                    if (info.outputData.getString(K_REASON) == REASON_NOT_FOUND) UploadState.NOT_FOUND else UploadState.FAILED
            }
        }

    internal const val K_CALL = "callId"
    internal const val K_PHONE = "phone"
    internal const val K_START = "startedAt"
    internal const val K_END = "endedAt"
    internal const val K_URI = "fileUri"
    internal const val K_REASON = "reason"
    internal const val REASON_NOT_FOUND = "not_found"
}

class UploadRecordingWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    private val maxSearchAttempts = 12

    override suspend fun doWork(): Result {
        val graph = (applicationContext as SupportApp).graph
        val callId = inputData.getString(RecordingUploads.K_CALL) ?: return Result.failure()
        val phone = inputData.getString(RecordingUploads.K_PHONE).orEmpty()
        val startedAt = inputData.getLong(RecordingUploads.K_START, 0)
        val endedAt = inputData.getLong(RecordingUploads.K_END, System.currentTimeMillis())
        val explicit = inputData.getString(RecordingUploads.K_URI)?.let(Uri::parse)

        val file = if (explicit != null) {
            graph.finder.describe(explicit)
        } else {
            val tree = graph.prefs.current().recordingsTree
            pickRecording(graph.finder.candidates(tree, startedAt - 15_000), phone, startedAt, endedAt)
        }
        if (file == null) {
            return if (explicit == null && runAttemptCount < maxSearchAttempts) {
                Result.retry()
            } else {
                Result.failure(workDataOf(RecordingUploads.K_REASON to RecordingUploads.REASON_NOT_FOUND))
            }
        }

        return try {
            val ext = extOf(file.name).ifBlank { "m4a" }
            val mime = file.mime?.takeIf { it.startsWith("audio/") } ?: mimeFor(ext)
            val target = graph.api.recordingUploadTarget(callId, ext)
            graph.api.putRecording(target.uploadUrl, mime, file.size) {
                applicationContext.contentResolver.openInputStream(Uri.parse(file.uri))
                    ?: throw ApiException("Can't open the recording file")
            }
            graph.api.completeRecording(callId, target.path, file.size, mime)
            Result.success()
        } catch (e: ApiException) {
            // 4xx other than conflict won't fix itself; network / 5xx might.
            if (e.status in 400..499 && e.status != 409) {
                Result.failure(workDataOf(RecordingUploads.K_REASON to (e.message ?: "upload failed")))
            } else if (runAttemptCount < maxSearchAttempts + 6) {
                Result.retry()
            } else {
                Result.failure(workDataOf(RecordingUploads.K_REASON to (e.message ?: "upload failed")))
            }
        }
    }
}
