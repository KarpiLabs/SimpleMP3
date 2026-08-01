package io.karpilabs.simplemp3.data.drive

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.FileContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileOutputStream
import java.io.InputStream
import java.util.Collections
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin Google Sign-In + Drive REST helper.
 * Uses [DriveScopes.DRIVE_FILE] (only files created by this app).
 */
@Singleton
class GoogleDriveGateway @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val scopes = listOf(DriveScopes.DRIVE_FILE)

    fun lastAccount(): GoogleSignInAccount? =
        GoogleSignIn.getLastSignedInAccount(context)

    fun hasDriveScope(account: GoogleSignInAccount?): Boolean {
        if (account == null) return false
        return GoogleSignIn.hasPermissions(account, Scope(DriveScopes.DRIVE_FILE))
    }

    fun signInIntent(): Intent {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(DriveScopes.DRIVE_FILE))
            .build()
        return GoogleSignIn.getClient(context, gso).signInIntent
    }

    suspend fun signOut() = withContext(Dispatchers.IO) {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(DriveScopes.DRIVE_FILE))
            .build()
        runCatching {
            GoogleSignIn.getClient(context, gso).signOut()
                .addOnCompleteListener { /* fire-and-forget */ }
        }
    }

    fun driveFor(account: GoogleSignInAccount): Drive {
        val credential = GoogleAccountCredential.usingOAuth2(
            context,
            Collections.singleton(DriveScopes.DRIVE_FILE)
        )
        credential.selectedAccount = account.account
        return Drive.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        )
            .setApplicationName("Simple MP3")
            .build()
    }

    suspend fun ensureBackupFolder(drive: Drive): String = withContext(Dispatchers.IO) {
        val existing = drive.files().list()
            .setQ(
                "mimeType='application/vnd.google-apps.folder' " +
                    "and name='$BACKUP_FOLDER_NAME' " +
                    "and trashed=false " +
                    "and 'root' in parents"
            )
            .setSpaces("drive")
            .setFields("files(id,name)")
            .setPageSize(5)
            .execute()
            .files
            ?.firstOrNull()
        if (existing != null) return@withContext existing.id

        val meta = File().apply {
            name = BACKUP_FOLDER_NAME
            mimeType = "application/vnd.google-apps.folder"
        }
        drive.files().create(meta).setFields("id").execute().id
    }

    suspend fun listBackups(drive: Drive, folderId: String): List<DriveBackupRemote> =
        withContext(Dispatchers.IO) {
            val result = drive.files().list()
                .setQ(
                    "'$folderId' in parents and trashed=false " +
                        "and (mimeType='application/zip' or name contains 'simplemp3-backup')"
                )
                .setSpaces("drive")
                .setFields("files(id,name,modifiedTime,size)")
                .setOrderBy("modifiedTime desc")
                .setPageSize(30)
                .execute()
            result.files.orEmpty().map { f ->
                DriveBackupRemote(
                    fileId = f.id,
                    name = f.name.orEmpty(),
                    modifiedTimeMs = f.modifiedTime?.value ?: 0L,
                    sizeBytes = f.getSize() ?: 0L
                )
            }
        }

    suspend fun uploadZip(
        drive: Drive,
        folderId: String,
        localZip: java.io.File,
        remoteName: String
    ): DriveBackupRemote = withContext(Dispatchers.IO) {
        val meta = File().apply {
            name = remoteName
            parents = listOf(folderId)
            mimeType = "application/zip"
        }
        val content = FileContent("application/zip", localZip)
        val created = drive.files().create(meta, content)
            .setFields("id,name,modifiedTime,size")
            .execute()
        DriveBackupRemote(
            fileId = created.id,
            name = created.name.orEmpty(),
            modifiedTimeMs = created.modifiedTime?.value ?: System.currentTimeMillis(),
            sizeBytes = created.getSize() ?: localZip.length()
        )
    }

    suspend fun downloadToFile(
        drive: Drive,
        fileId: String,
        dest: java.io.File
    ) = withContext(Dispatchers.IO) {
        dest.parentFile?.mkdirs()
        FileOutputStream(dest).use { out ->
            drive.files().get(fileId).executeMediaAndDownloadTo(out)
        }
    }

    suspend fun openMediaStream(drive: Drive, fileId: String): InputStream =
        withContext(Dispatchers.IO) {
            drive.files().get(fileId).executeMediaAsInputStream()
        }

    suspend fun deleteFile(drive: Drive, fileId: String) = withContext(Dispatchers.IO) {
        drive.files().delete(fileId).execute()
    }

    companion object {
        const val BACKUP_FOLDER_NAME = "Simple MP3 Backups"
    }
}
