package com.localllm.app.ui.screens.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.localllm.app.data.db.ProjectEntity
import com.localllm.app.data.db.TaskEntity
import com.localllm.app.data.repository.TaskRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TaskViewModel @Inject constructor(
    private val taskRepository: TaskRepository
) : ViewModel() {

    val todaysTasks: Flow<List<TaskEntity>> = taskRepository.getTodaysTasks()
    val activeProjects: Flow<List<ProjectEntity>> = taskRepository.getActiveProjects()

    private val _selectedProject = MutableStateFlow<ProjectEntity?>(null)
    val selectedProject: StateFlow<ProjectEntity?> = _selectedProject.asStateFlow()

    fun tasksForProject(projectId: String): Flow<List<TaskEntity>> =
        taskRepository.getTasksForProject(projectId)

    fun openProject(project: ProjectEntity) {
        _selectedProject.value = project
    }

    fun closeProject() {
        _selectedProject.value = null
    }

    fun toggleDone(task: TaskEntity) {
        viewModelScope.launch { taskRepository.toggleTaskDone(task) }
    }

    fun deleteTask(task: TaskEntity) {
        viewModelScope.launch { taskRepository.deleteTask(task) }
    }

    fun createProject(name: String) {
        viewModelScope.launch { taskRepository.createProject(name) }
    }

    fun deleteProject(project: ProjectEntity) {
        viewModelScope.launch { taskRepository.deleteProject(project); _selectedProject.value = null }
    }
}
