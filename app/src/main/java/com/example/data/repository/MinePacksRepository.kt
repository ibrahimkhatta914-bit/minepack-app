package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.ResourcePack
import com.example.data.model.UserProfile
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class MinePacksRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        Firebase.auth
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    fun observePublicPacks(): Flow<List<ResourcePack>> = flow {
        val uid = requireUserId()
        val path = PACKS_COLLECTION
        emitAll(
            db.collection(PACKS_COLLECTION)
                .whereEqualTo("visibility", "public")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        ResourcePack::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    handleFlowError(error, OperationType.LIST, path, uid)
                    throw error
                }
        )
    }

    fun observePublicUsers(): Flow<List<UserProfile>> = flow {
        val uid = requireUserId()
        val path = USERS_COLLECTION
        emitAll(
            db.collection(USERS_COLLECTION)
                .whereEqualTo("isPublicProfile", true)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        UserProfile::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    handleFlowError(error, OperationType.LIST, path, uid)
                    throw error
                }
        )
    }

    fun observeUserProfile(userId: String): Flow<UserProfile?> = flow {
        val uid = requireUserId()
        val path = "$USERS_COLLECTION/$userId"
        emitAll(
            db.collection(USERS_COLLECTION).document(userId)
                .snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(
                            UserProfile::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    } else {
                        null
                    }
                }
                .catch { error ->
                    handleFlowError(error, OperationType.GET, path, uid)
                    throw error
                }
        )
    }

    suspend fun getUserProfile(userId: String): Result<UserProfile?> = try {
        requireUserId()
        val docRef = db.collection(USERS_COLLECTION).document(userId)
        val snapshot = docRef.get().await()
        val profile = if (snapshot.exists()) {
            snapshot.toObject(
                UserProfile::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            )
        } else {
            null
        }
        Result.success(profile)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, OperationType.GET, "$USERS_COLLECTION/$userId", auth)
        Result.failure(e)
    }

    suspend fun upsertUserProfile(username: String, avatarUrl: String?): Result<Unit> = try {
        val uid = requireUserId()
        val trimmedName = username.trim()
        require(trimmedName.length in 2..30) { "Username must be between 2 and 30 characters" }

        val docRef = db.collection(USERS_COLLECTION).document(uid)
        val existingSnapshot = docRef.get().await()

        val payload = buildMap<String, Any> {
            put("username", trimmedName)
            if (!avatarUrl.isNullOrBlank()) {
                put("avatarUrl", avatarUrl.take(500))
            }
            put("updatedAt", FieldValue.serverTimestamp())
            if (!existingSnapshot.exists()) {
                put("userId", uid)
                put("isPublicProfile", true)
                put("packsCount", 0L)
                put("createdAt", FieldValue.serverTimestamp())
            }
        }

        if (existingSnapshot.exists()) {
            docRef.update(payload).await()
        } else {
            docRef.set(payload).await()
        }
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        val uid = auth.currentUser?.uid ?: "unknown"
        handleFirestoreError(e, OperationType.WRITE, "$USERS_COLLECTION/$uid", auth)
        Result.failure(e)
    }

    suspend fun createPack(
        title: String,
        description: String?,
        imageUrl: String,
        downloadUrl: String,
        authorName: String,
        authorAvatarUrl: String?
    ): Result<String> = try {
        val uid = requireUserId()
        val cleanTitle = title.trim()
        val cleanImage = imageUrl.trim()
        val cleanDownload = downloadUrl.trim()
        val cleanAuthor = authorName.trim().ifBlank { "لاعب ماين كرافت" }.take(30)

        require(cleanTitle.length in 2..80) { "Pack title must be between 2 and 80 characters" }
        require(cleanImage.length >= 5) { "Pack image is required" }
        require(cleanDownload.startsWith("http://") || cleanDownload.startsWith("https://")) {
            "Download URL must start with http:// or https://"
        }

        val packRef = db.collection(PACKS_COLLECTION).document()
        val payload = buildMap<String, Any> {
            put("title", cleanTitle)
            if (!description.isNullOrBlank()) {
                put("description", description.trim().take(300))
            }
            put("imageUrl", cleanImage)
            put("downloadUrl", cleanDownload.take(1000))
            put("authorId", uid)
            put("authorName", cleanAuthor)
            if (!authorAvatarUrl.isNullOrBlank()) {
                put("authorAvatarUrl", authorAvatarUrl.take(500))
            }
            put("downloadsCount", 0L)
            put("likesCount", 0L)
            put("visibility", "public")
            put("createdAt", FieldValue.serverTimestamp())
            put("updatedAt", FieldValue.serverTimestamp())
        }

        packRef.set(payload).await()

        // Best-effort sync of user's packsCount
        val userRef = db.collection(USERS_COLLECTION).document(uid)
        val userSnap = userRef.get().await()
        if (userSnap.exists()) {
            userRef.update(
                mapOf(
                    "packsCount" to FieldValue.increment(1L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        }

        Result.success(packRef.id)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, OperationType.CREATE, PACKS_COLLECTION, auth)
        Result.failure(e)
    }

    suspend fun incrementDownloadCount(packId: String): Result<Unit> = try {
        requireUserId()
        val packRef = db.collection(PACKS_COLLECTION).document(packId)
        packRef.update(
            mapOf(
                "downloadsCount" to FieldValue.increment(1L),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, OperationType.UPDATE, "$PACKS_COLLECTION/$packId", auth)
        Result.failure(e)
    }

    suspend fun isPackLikedByCurrentUser(packId: String): Boolean = try {
        val uid = requireUserId()
        val likeDoc = db.collection(PACKS_COLLECTION)
            .document(packId)
            .collection(LIKES_SUBCOLLECTION)
            .document(uid)
            .get()
            .await()
        likeDoc.exists()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        false
    }

    suspend fun toggleLike(packId: String, currentlyLiked: Boolean): Result<Boolean> = try {
        val uid = requireUserId()
        val packRef = db.collection(PACKS_COLLECTION).document(packId)
        val likeRef = packRef.collection(LIKES_SUBCOLLECTION).document(uid)

        val batch = db.batch()
        if (currentlyLiked) {
            batch.delete(likeRef)
            batch.update(
                packRef,
                mapOf(
                    "likesCount" to FieldValue.increment(-1L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
        } else {
            batch.set(
                likeRef,
                mapOf(
                    "userId" to uid,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            batch.update(
                packRef,
                mapOf(
                    "likesCount" to FieldValue.increment(1L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
        }
        batch.commit().await()
        Result.success(!currentlyLiked)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, OperationType.UPDATE, "$PACKS_COLLECTION/$packId", auth)
        Result.failure(e)
    }

    suspend fun deletePack(packId: String): Result<Unit> = try {
        requireUserId()
        val packRef = db.collection(PACKS_COLLECTION).document(packId)
        packRef.delete().await()
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, OperationType.DELETE, "$PACKS_COLLECTION/$packId", auth)
        Result.failure(e)
    }

    suspend fun deleteUser(userId: String): Result<Unit> = try {
        requireUserId()
        val userRef = db.collection(USERS_COLLECTION).document(userId)
        userRef.delete().await()
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, OperationType.DELETE, "$USERS_COLLECTION/$userId", auth)
        Result.failure(e)
    }

    private fun handleFlowError(
        error: Throwable,
        operationType: OperationType,
        path: String,
        expectedUid: String
    ) {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null && auth.currentUser?.uid == expectedUid) {
            handleFirestoreError(exceptionToLog, operationType, path, auth)
        }
    }

    companion object {
        const val USERS_COLLECTION = "users"
        const val PACKS_COLLECTION = "packs"
        const val LIKES_SUBCOLLECTION = "likes"
    }
}
