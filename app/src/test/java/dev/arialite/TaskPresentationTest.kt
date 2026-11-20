package dev.arialite

import org.junit.Assert.*
import org.junit.Test

class TaskPresentationTest {
    private fun task(id: String, status: String = "active", size: Long = 100, name: String = id, created: Long = 0, seen: Long = 0) = Download(id, name, status, size, 0, 0, 0, "", created, seen)
    @Test fun everyStatusBelongsToExactlyOneGroup() {
        val expected = mapOf(
            "active" to TaskGroup.ACTIVE,
            "waiting" to TaskGroup.WAITING,
            "paused" to TaskGroup.WAITING,
            "complete" to TaskGroup.STOPPED,
            "error" to TaskGroup.STOPPED,
            "removed" to TaskGroup.STOPPED,
        )
        expected.forEach { (status, group) ->
            assertEquals(status, listOf(group), TaskGroup.entries.filter { it.includes(task(status, status)) })
        }
    }
    @Test fun creationSortUsesObservedTimeOnlyWhenUnknown() {
        val tasks = listOf(task("old", created = 100, seen = 900), task("new", created = 300), task("external", seen = 200))
        assertEquals(listOf("new", "external", "old"), sortedTasks(tasks, TaskSort.CREATED, true).map { it.gid })
    }
    @Test fun sizesAreNumericAndSupportBothDirections() {
        val tasks = listOf(task("a", size = 2), task("b", size = 100), task("c", size = 10))
        assertEquals(listOf("a", "c", "b"), sortedTasks(tasks, TaskSort.SIZE, false).map { it.gid })
        assertEquals(listOf("b", "c", "a"), sortedTasks(tasks, TaskSort.SIZE, true).map { it.gid })
    }
    @Test fun namesIgnoreCaseAndTiesRemainStable() {
        val tasks = listOf(task("z", name = "alpha"), task("b", name = "Beta"), task("a", name = "Alpha"))
        assertEquals(listOf("a", "z", "b"), sortedTasks(tasks, TaskSort.NAME, false).map { it.gid })
    }
    @Test fun historyRetainsOnlyRecentTwoMinutesAndPreservesMissingData() {
        val history = listOf(SpeedSample(0, 42, 8), SpeedSample(100_000, 50, 9))
        val result = appendSample(history, SpeedSample(180_000, null, null))
        assertEquals(2, result.size)
        assertNull(result.last().down)
        assertEquals(100_000, result.first().time)
    }
    @Test fun historyIsBoundedEvenWithFrequentRefreshes() {
        var history = emptyList<SpeedSample>()
        repeat(500) { history = appendSample(history, SpeedSample(it.toLong(), 0, 0)) }
        assertEquals(120, history.size)
    }
}
