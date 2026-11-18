package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MinePacksRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun upsertUserProfile_authenticatedUser_createsProfileAndObservesIt() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repository = MinePacksRepository(firestore, auth)

        val result = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.upsertUserProfile(username = "AlexCraft", avatarUrl = null)
        }
        assertTrue(result.isSuccess)

        val loaded = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.getUserProfile(uid).getOrThrow()
        }
        assertEquals("AlexCraft", loaded?.username)
        assertEquals(uid, loaded?.userId)
    }

    @Test
    fun createPack_andIncrementDownload_andToggleLike_succeedForAuthenticatedUsers() = runBlocking {
        signInTestUser(ALICE_EMAIL)
        val aliceRepo = MinePacksRepository(firestore, auth)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.upsertUserProfile("AliceMC", null).getOrThrow()
        }

        val packId = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.createPack(
                title = "Emerald PvP 16x",
                description = "Crisp textures for Bedwars",
                imageUrl = "https://example.com/emerald.png",
                downloadUrl = "https://example.com/emerald.zip",
                authorName = "AliceMC",
                authorAvatarUrl = null
            ).getOrThrow()
        }
        assertTrue(packId.isNotBlank())

        val packs = withTimeout(FLOW_TIMEOUT_MS) {
            aliceRepo.observePublicPacks().first { list -> list.any { it.id == packId } }
        }
        assertTrue(packs.any { it.id == packId && it.title == "Emerald PvP 16x" })

        signInTestUser(BOB_EMAIL)
        val bobRepo = MinePacksRepository(firestore, auth)

        val dlResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.incrementDownloadCount(packId)
        }
        assertTrue(dlResult.isSuccess)

        val likeResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.toggleLike(packId, currentlyLiked = false)
        }
        assertTrue(likeResult.isSuccess)
        assertTrue(likeResult.getOrThrow())

        // Non-owner cannot delete Alice's pack
        val deleteFail = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.deletePack(packId)
        }
        assertTrue(deleteFail.isFailure)
        val fsEx = deleteFail.exceptionOrNull()?.unwrapFirestoreException()
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsEx?.code)

        // Owner can delete their own pack
        signInTestUser(ALICE_EMAIL)
        val ownerDelete = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.deletePack(packId)
        }
        assertTrue(ownerDelete.isSuccess)
    }

    @Test
    fun observePublicPacks_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repository = MinePacksRepository(firestore, auth)

        var caught: Exception? = null
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repository.observePublicPacks().first()
            }
        } catch (e: Exception) {
            caught = e
        }
        assertTrue("Expected exception when unauthenticated", caught != null)
        // Either IllegalStateException from requireUserId() or PERMISSION_DENIED from Firestore
        val isExpected = caught is IllegalStateException ||
            caught?.unwrapFirestoreException()?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
        assertTrue("Unexpected exception: $caught", isExpected)
    }

    private companion object {
        const val ALICE_EMAIL = "alice_mp@test.com"
        const val BOB_EMAIL = "bob_mp@test.com"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 3000L
    }
}
