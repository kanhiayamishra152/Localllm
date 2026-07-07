package com.localllm.app.data.repository

import com.localllm.app.data.db.AppDatabase
import com.localllm.app.data.db.ProjectEntity
import com.localllm.app.data.db.TaskEntity
import kotlinx.coroutines.flow.Flow
import java.util.Calendar
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TaskRepository @Inject constructor(db: AppDatabase) {

    private val taskDao = db.taskDao()

    fun getActiveTasks(): Flow<List<TaskEntity>> = taskDao.getActiveTasks()

    fun getTodaysTasks(): Flow<List<TaskEntity>> {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val start = cal.timeInMillis
        val end = start + 24 * 60 * 60 * 1000L - 1
        return taskDao.getTasksForToday(start, end)
    }

    fun getActiveProjects(): Flow<List<ProjectEntity>> = taskDao.getActiveProjects()

    fun getTasksForProject(projectId: String): Flow<List<TaskEntity>> =
        taskDao.getTasksForProject(projectId)

    suspend fun addTask(
        title: String,
        description: String? = null,
        projectId: String? = null,
        dueDate: Long? = null,
        isRoutine: Boolean = false,
        recurrence: String? = null,
        priority: Int = 0,
        source: String = "manual"
    ): String {
        val id = UUID.randomUUID().toString()
        taskDao.insertTask(
            TaskEntity(
                id = id,
                title = title,
                description = description,
                projectId = projectId,
                dueDate = dueDate,
                isRoutine = isRoutine,
                recurrence = recurrence,
                priority = priority,
                source = source
            )
        )
        return id
    }

    suspend fun toggleTaskDone(task: TaskEntity) {
        taskDao.updateTask(
            task.copy(
                isDone = !task.isDone,
                completedAt = if (!task.isDone) System.currentTimeMillis() else null
            )
        )
    }

    suspend fun updateDueDate(task: TaskEntity, dueDate: Long?) {
        taskDao.updateTask(task.copy(dueDate = dueDate))
    }

    suspend fun deleteTask(task: TaskEntity) = taskDao.deleteTask(task)

    suspend fun createProject(name: String, description: String? = null, icon: String? = null): String {
        val id = UUID.randomUUID().toString()
        taskDao.insertProject(ProjectEntity(id = id, name = name, description = description, icon = icon))
        return id
    }

    suspend fun deleteProject(project: ProjectEntity) = taskDao.deleteProject(project)
}
