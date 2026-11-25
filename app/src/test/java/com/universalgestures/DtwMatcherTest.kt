package com.universalgestures

import com.universalgestures.core.Vec3
import com.universalgestures.recognition.DtwMatcher
import org.junit.Assert.assertTrue
import org.junit.Test

class DtwMatcherTest {

    @Test
    fun identicalSequencesHaveZeroDistance() {
        val seq = listOf(Vec3(1f, 0f, 0f), Vec3(2f, 0f, 0f), Vec3(3f, 0f, 0f))
        assertTrue(DtwMatcher.distance(seq, seq) < 0.01f)
    }

    @Test
    fun differentSequencesHaveLargerDistance() {
        val a = listOf(Vec3(1f, 0f, 0f), Vec3(2f, 0f, 0f), Vec3(3f, 0f, 0f))
        val b = listOf(Vec3(-5f, 0f, 0f), Vec3(-6f, 0f, 0f), Vec3(-7f, 0f, 0f))
        assertTrue(DtwMatcher.distance(a, b) > DtwMatcher.distance(a, a))
    }

    @Test
    fun emptySequencesReturnMaxDistance() {
        assertTrue(DtwMatcher.distance(emptyList(), listOf(Vec3())) == 1f)
        assertTrue(DtwMatcher.distance(listOf(Vec3()), emptyList()) == 1f)
    }

    @Test
    fun timeWarpedSequencesMatchClosely() {
        // Same shape, stretched in time.
        val a = listOf(Vec3(0f, 0f, 0f), Vec3(1f, 0f, 0f), Vec3(2f, 0f, 0f), Vec3(1f, 0f, 0f))
        val b = listOf(Vec3(0f, 0f, 0f), Vec3(0.5f, 0f, 0f), Vec3(1f, 0f, 0f), Vec3(1.5f, 0f, 0f), Vec3(2f, 0f, 0f), Vec3(1.5f, 0f, 0f), Vec3(1f, 0f, 0f))
        assertTrue("warped distance ${DtwMatcher.distance(a, b)}", DtwMatcher.distance(a, b) < 0.2f)
    }
}
