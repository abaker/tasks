package com.todoroo.astrid.service

import com.google.common.collect.Lists
import com.todoroo.andlib.utility.DateUtilities
import com.todoroo.astrid.dao.TaskDao
import com.todoroo.astrid.data.Task
import com.todoroo.astrid.gcal.GCalHelper
import org.tasks.LocalBroadcastManager
import org.tasks.data.*
import org.tasks.preferences.Preferences
import java.util.*
import javax.inject.Inject

class TaskDuplicator @Inject constructor(
        private val gcalHelper: GCalHelper,
        private val taskDao: TaskDao,
        private val localBroadcastManager: LocalBroadcastManager,
        private val tagDao: TagDao,
        private val tagDataDao: TagDataDao,
        private val googleTaskDao: GoogleTaskDao,
        private val caldavDao: CaldavDao,
        private val locationDao: LocationDao,
        private val alarmDao: AlarmDao,
        private val preferences: Preferences) {

    suspend fun duplicate(taskIds: List<Long>): List<Task> {
        val result: MutableList<Task> = ArrayList()
        for (task in taskDao.fetch(taskIds)) {
            result.add(clone(task))
        }
        localBroadcastManager.broadcastRefresh()
        return result
    }

    private suspend fun clone(clone: Task, parentId: Long = 0L): Task {
        val originalId = clone.id
        with(clone) {
            creationDate = DateUtilities.now()
            modificationDate = DateUtilities.now()
            completionDate = 0L
            calendarURI = ""
            parent = parentId
            uuid = Task.NO_UUID
            suppressSync()
            suppressRefresh()
        }
        val newId = taskDao.createNew(clone)
        val tags = tagDataDao.getTagDataForTask(originalId)
        if (tags.isNotEmpty()) {
            tagDao.insert(Lists.transform(tags) { td: TagData? -> Tag(clone, td!!) })
        }
        val googleTask = googleTaskDao.getByTaskId(originalId)
        val addToTop = preferences.addTasksToTop()
        if (googleTask != null) {
            googleTaskDao.insertAndShift(GoogleTask(clone.id, googleTask.listId!!), addToTop)
        }
        val caldavTask = caldavDao.getTask(originalId)
        if (caldavTask != null) {
            caldavDao.insert(clone, CaldavTask(clone.id, caldavTask.calendar), addToTop)
        }
        for (g in locationDao.getGeofencesForTask(originalId)) {
            locationDao.insert(
                    Geofence(clone.id, g.place, g.isArrival, g.isDeparture, g.radius))
        }
        val alarms = alarmDao.getAlarms(originalId)
        if (alarms.isNotEmpty()) {
            alarmDao.insert(Lists.transform(alarms) { a: Alarm? -> Alarm(clone.id, a!!.time) })
        }
        gcalHelper.createTaskEventIfEnabled(clone)
        taskDao.save(clone, null) // TODO: delete me
        getDirectChildren(originalId)
                .filter { it.parent == originalId }
                .forEach { subtask -> clone(subtask, newId) }
        return clone
    }

    private suspend fun getDirectChildren(taskId: Long): List<Task> =
        taskDao.fetch(taskDao.getChildren(taskId)).filter { it.parent == taskId }
}