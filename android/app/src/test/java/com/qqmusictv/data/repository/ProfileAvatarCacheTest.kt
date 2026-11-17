package com.qqmusictv.data.repository

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class ProfileAvatarCacheTest {
    private val dir get() = java.io.File(RuntimeEnvironment.getApplication().filesDir, "profile-avatar")

    @Test fun persistentSuccessSurvivesRecreationWithoutUsingAnEvictableCache() {
        val context = RuntimeEnvironment.getApplication()
        val image = byteArrayOf(1, 2, 3)
        ProfileAvatarCache(context).save("account-a", image)
        assertArrayEquals(image, ProfileAvatarCache(context).read("account-a")!!.readBytes())
        assertNull(ProfileAvatarCache(context).read("account-b"))
        assertFalse(dir.listFiles()!!.any { "account-a" in it.name })
    }

    @Test fun signOutClearsAllImagesAndPendingWrites() {
        val context = RuntimeEnvironment.getApplication()
        val cache = ProfileAvatarCache(context)
        cache.save("account-a", byteArrayOf(1))
        cache.clear()
        assertNull(cache.read("account-a"))
        assertTrue(dir.listFiles()!!.isEmpty())
    }
}
