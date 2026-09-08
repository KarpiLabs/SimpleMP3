package io.karpilabs.simplemp3.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class LibrarySessionCallbackTest {
    @Test
    fun customCommands_areDefinedForShuffleAndRepeatActions() {
        assert(LibrarySessionCallback.CUSTOM_SHUFFLE.customAction == LibrarySessionCallback.ACTION_TOGGLE_SHUFFLE)
        assert(LibrarySessionCallback.CUSTOM_REPEAT.customAction == LibrarySessionCallback.ACTION_CYCLE_REPEAT)
    }

    @Test
    fun customCommands_areExposedAsSessionCommands() {
        val shuffleCommand = LibrarySessionCallback.CUSTOM_SHUFFLE
        val repeatCommand = LibrarySessionCallback.CUSTOM_REPEAT

        assert(shuffleCommand.customAction.isNotBlank())
        assert(repeatCommand.customAction.isNotBlank())
    }

    @Test
    fun streamsIsADistinctTopLevelBrowseId() {
        assertEquals("streams", MediaIds.STREAMS)
        assertEquals("favorite_streams", MediaIds.FAVORITE_STREAMS)
        assertFalse(MediaIds.STREAMS == MediaIds.SONGS)
        assertFalse(MediaIds.STREAMS == MediaIds.LIKED)
        assertFalse(MediaIds.FAVORITE_STREAMS == MediaIds.STREAMS)
        assertFalse(MediaIds.FAVORITE_STREAMS == MediaIds.LIKED)
    }
}
