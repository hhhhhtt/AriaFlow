package dev.arialite

import java.util.Locale

data class SpeedSample(val time: Long, val down: Long?, val up: Long?)

fun appendSample(history: List<SpeedSample>, sample: SpeedSample): List<SpeedSample> =
    (history.filter { it.time >= sample.time - 120_000 } + sample).takeLast(120)

enum class TaskGroup(val label: String) {
    ACTIVE("下载中"),
    WAITING("等待"),
    STOPPED("已停止");

    fun includes(task: Download): Boolean =
        when (this) {
            ACTIVE -> task.status == "active"
            WAITING -> task.status == "waiting" || task.status == "paused"
            STOPPED -> task.status in setOf("complete", "error", "removed")
        }
}

enum class TaskSort(val label: String) {
    CREATED("创建时间"),
    NAME("文件名"),
    SIZE("文件大小"),
    PROGRESS("下载进度"),
}

fun sortedTasks(tasks: List<Download>, sort: TaskSort, descending: Boolean): List<Download> {
    val comparator: Comparator<Download> =
        when (sort) {
            TaskSort.CREATED -> compareBy { if (it.addedAt > 0) it.addedAt else it.observedAt }
            TaskSort.NAME -> compareBy { it.name.lowercase(Locale.ROOT) }
            TaskSort.SIZE -> compareBy { it.total }
            TaskSort.PROGRESS -> compareBy { it.progress }
        }
    return tasks.sortedWith(
        (if (descending) comparator.reversed() else comparator).thenBy { it.gid }
    )
}
