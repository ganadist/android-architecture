/*
 * Copyright 2016, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.architecture.blueprints.todoapp.data.source.local

import android.content.ContentValues
import android.content.Context

import com.example.android.architecture.blueprints.todoapp.data.Task
import com.example.android.architecture.blueprints.todoapp.data.source.TasksDataSource
import com.example.android.architecture.blueprints.todoapp.data.source.local.TasksPersistenceContract.TaskEntry

import java.util.ArrayList


/**
 * Concrete implementation of a data source as a db.
 */
class TasksLocalDataSource// Prevent direct instantiation.
private constructor(context: Context) : TasksDataSource {

    private val mDbHelper: TasksDbHelper

    init {
        kotlin.checkNotNull(context)
        mDbHelper = TasksDbHelper(context)
    }

    /**
     * Note: [LoadTasksCallback.onDataNotAvailable] is fired if the database doesn't exist
     * or the table is empty.
     */
    override fun getTasks(callback: TasksDataSource.LoadTasksCallback) {
        val tasks = ArrayList<Task>()

        val projection = arrayOf(TaskEntry.COLUMN_NAME_ENTRY_ID, TaskEntry.COLUMN_NAME_TITLE, TaskEntry.COLUMN_NAME_DESCRIPTION, TaskEntry.COLUMN_NAME_COMPLETED)
        mDbHelper.readableDatabase.use {
            it.query(
                    TaskEntry.TABLE_NAME, projection, null, null, null, null, null)?.use {

                if (it.count > 0) {
                    while (it.moveToNext()) {
                        val itemId = it.getString(it.getColumnIndexOrThrow(TaskEntry.COLUMN_NAME_ENTRY_ID))
                        val title = it.getString(it.getColumnIndexOrThrow(TaskEntry.COLUMN_NAME_TITLE))
                        val description = it.getString(it.getColumnIndexOrThrow(TaskEntry.COLUMN_NAME_DESCRIPTION))
                        val completed = it.getInt(it.getColumnIndexOrThrow(TaskEntry.COLUMN_NAME_COMPLETED)) == 1
                        val task = Task(title, description, itemId, completed)
                        tasks.add(task)
                    }
                }
            }
        }

        if (tasks.isEmpty()) {
            // This will be called if the table is new or just empty.
            callback.onDataNotAvailable()
        } else {
            callback.onTasksLoaded(tasks)
        }

    }

    /**
     * Note: [GetTaskCallback.onDataNotAvailable] is fired if the [Task] isn't
     * found.
     */
    override fun getTask(taskId: String, callback: TasksDataSource.GetTaskCallback) {
        val projection = arrayOf(TaskEntry.COLUMN_NAME_ENTRY_ID, TaskEntry.COLUMN_NAME_TITLE, TaskEntry.COLUMN_NAME_DESCRIPTION, TaskEntry.COLUMN_NAME_COMPLETED)

        val selection = TaskEntry.COLUMN_NAME_ENTRY_ID + " LIKE ?"
        val selectionArgs = arrayOf(taskId)

        var task: Task? = null

        mDbHelper.readableDatabase.use {
            it.query(
                TaskEntry.TABLE_NAME, projection, selection, selectionArgs, null, null, null)?.use {
                if (it.count > 0) {
                    it.moveToFirst()
                    val itemId = it.getString(it.getColumnIndexOrThrow(TaskEntry.COLUMN_NAME_ENTRY_ID))
                    val title = it.getString(it.getColumnIndexOrThrow(TaskEntry.COLUMN_NAME_TITLE))
                    val description = it.getString(it.getColumnIndexOrThrow(TaskEntry.COLUMN_NAME_DESCRIPTION))
                    val completed = it.getInt(it.getColumnIndexOrThrow(TaskEntry.COLUMN_NAME_COMPLETED)) == 1
                    task = Task(title, description, itemId, completed)
                }
            }
        }

        if (task != null) {
            callback.onTaskLoaded(task)
        } else {
            callback.onDataNotAvailable()
        }
    }

    override fun saveTask(task: Task) {
        kotlin.checkNotNull(task)
        val values = ContentValues().apply {
            put(TaskEntry.COLUMN_NAME_ENTRY_ID, task.id)
            put(TaskEntry.COLUMN_NAME_TITLE, task.title)
            put(TaskEntry.COLUMN_NAME_DESCRIPTION, task.description)
            put(TaskEntry.COLUMN_NAME_COMPLETED, task.isCompleted)
        }
        mDbHelper.writableDatabase.use {
            it.insert(TaskEntry.TABLE_NAME, null, values)
        }
    }

    override fun completeTask(task: Task) {
        val values = ContentValues().apply {
            put(TaskEntry.COLUMN_NAME_COMPLETED, true)
        }
        val selection = TaskEntry.COLUMN_NAME_ENTRY_ID + " LIKE ?"
        val selectionArgs = arrayOf(task.id)
        mDbHelper.writableDatabase.use {
            it.update(TaskEntry.TABLE_NAME, values, selection, selectionArgs)
        }
    }

    override fun completeTask(taskId: String) {
        // Not required for the local data source because the {@link TasksRepository} handles
        // converting from a {@code taskId} to a {@link task} using its cached data.
    }

    override fun activateTask(task: Task) {
        val values = ContentValues().apply {
            put(TaskEntry.COLUMN_NAME_COMPLETED, false)
        }
        val selection = TaskEntry.COLUMN_NAME_ENTRY_ID + " LIKE ?"
        val selectionArgs = arrayOf(task.id)
        mDbHelper.writableDatabase.use {
            it.update(TaskEntry.TABLE_NAME, values, selection, selectionArgs)
        }
    }

    override fun activateTask(taskId: String) {
        // Not required for the local data source because the {@link TasksRepository} handles
        // converting from a {@code taskId} to a {@link task} using its cached data.
    }

    override fun clearCompletedTasks() {
        val selection = TaskEntry.COLUMN_NAME_COMPLETED + " LIKE ?"
        val selectionArgs = arrayOf("1")
        mDbHelper.writableDatabase.use {
            it.delete(TaskEntry.TABLE_NAME, selection, selectionArgs)
        }
    }

    override fun refreshTasks() {
        // Not required because the {@link TasksRepository} handles the logic of refreshing the
        // tasks from all the available data sources.
    }

    override fun deleteAllTasks() {
        mDbHelper.writableDatabase.use {
            it.delete(TaskEntry.TABLE_NAME, null, null)
        }
    }

    override fun deleteTask(taskId: String) {
        val selection = TaskEntry.COLUMN_NAME_ENTRY_ID + " LIKE ?"
        val selectionArgs = arrayOf(taskId)
        mDbHelper.writableDatabase.use {
            it.delete(TaskEntry.TABLE_NAME, selection, selectionArgs)
        }
    }

    companion object {
        private lateinit var INSTANCE: TasksLocalDataSource
        private var inited = false
        @JvmStatic
        fun getInstance(context: Context): TasksLocalDataSource {
            if (!inited) {
                INSTANCE = TasksLocalDataSource(context)
                inited = true
            }
            return INSTANCE
        }
    }
}
