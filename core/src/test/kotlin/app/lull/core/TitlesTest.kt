package app.lull.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TitlesTest {

    @Test
    fun stripsSharedSeriesName() {
        val titles = (1..12).map { "Shama'il of al-Muhammadiyyah (Part $it)" }
        assertEquals((1..12).map { "Part $it" }, Titles.distinct(titles))
    }

    @Test
    fun keepsWholeWordsAtTheBoundary() {
        assertEquals(listOf("Lesson 1", "Lesson 12"), Titles.distinct(listOf("Risala Class - Lesson 1", "Risala Class - Lesson 12")))
    }

    @Test
    fun leavesUnrelatedTitlesAlone() {
        val titles = listOf("Gymnopédie No. 1", "Gnossienne No. 1", "Je te veux")
        assertEquals(titles, Titles.distinct(titles))
    }

    @Test
    fun leavesTitlesAloneWhenOneWouldBeBlank() {
        val titles = listOf("Morning Adhkar", "Morning Adhkar (Extended)")
        assertEquals(titles, Titles.distinct(titles))
    }

    @Test
    fun recognisesCounts() {
        assertTrue(Titles.isCount("74 views"))
        assertTrue(Titles.isCount("1.2K plays"))
        assertTrue(Titles.isCount("3 views"))
        assertFalse(Titles.isCount("The Majlis"))
        assertFalse(Titles.isCount("Oct 5, 2026"))
    }
}
