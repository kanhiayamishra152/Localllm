package com.localllm.app.ui.screens.tasks

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.localllm.app.data.db.TaskEntity
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardSection(
    modifier: Modifier = Modifier,
    viewModel: TaskViewModel = hiltViewModel()
) {
    var expanded by remember { mutableStateOf(true) }
    var showAddProject by remember { mutableStateOf(false) }
    val pagerState = rememberPagerState(pageCount = { 2 })
    val todaysTasks by viewModel.todaysTasks.collectAsStateWithLifecycle(emptyList())
    val projects by viewModel.activeProjects.collectAsStateWithLifecycle(emptyList())
    val selectedProject by viewModel.selectedProject.collectAsStateWithLifecycle()

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
            // Header
            Row(
                Modifier.fillMaxWidth().clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.Dashboard, null, Modifier.size(18.dp), MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(8.dp))
                Text("Dashboard", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                if (!expanded) {
                    Text(
                        "${todaysTasks.size} tasks today · ${projects.size} projects",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Icon(
                    if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    null, Modifier.size(20.dp), MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (expanded && pagerState.currentPage == 1) {
                    Spacer(Modifier.width(4.dp))
                    IconButton(onClick = { showAddProject = true }, Modifier.size(28.dp)) {
                        Icon(Icons.Outlined.Add, "Add project", Modifier.size(20.dp), MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            AnimatedVisibility(expanded) {
                Column(Modifier.fillMaxWidth().padding(top = 10.dp)) {
                    // Page indicator chips
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DashboardTab("Today's Tasks", pagerState.currentPage == 0) { pagerState.animateScrollToPage(0) }
                        DashboardTab("Active Projects", pagerState.currentPage == 1) { pagerState.animateScrollToPage(1) }
                    }
                    Spacer(Modifier.height(10.dp))

                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp)
                    ) { page ->
                        when (page) {
                            0 -> TodaysTasksPage(todaysTasks, onToggle = viewModel::toggleDone, onDelete = viewModel::deleteTask)
                            1 -> ProjectsPage(projects, onOpen = viewModel::openProject)
                        }
                    }
                }
            }
        }
    }

    // Project detail dialog
    if (selectedProject != null) {
        val projectTasks by produceState<List<TaskEntity>>(emptyList(), selectedProject!!.id) {
            viewModel.tasksForProject(selectedProject!!.id).collect { value = it }
        }
        ProjectDetailDialog(
            project = selectedProject!!,
            tasks = projectTasks,
            onToggle = viewModel::toggleDone,
            onDeleteProject = viewModel::deleteProject,
            onDismiss = viewModel::closeProject
        )
    }

    if (showAddProject) {
        AddProjectDialog(
            onDismiss = { showAddProject = false },
            onCreate = { name, icon -> viewModel.createProject(name, icon = icon); showAddProject = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddProjectDialog(onDismiss: () -> Unit, onCreate: (String, String?) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onCreate(name.trim(), null) }) {
                Text("Create")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text("New Project") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Project name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
    )
}

@Composable
private fun DashboardTab(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.height(32.dp)
    ) {
        Box(Modifier.padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
            )
        }
    }
}

@Composable
private fun TodaysTasksPage(
    tasks: List<TaskEntity>,
    onToggle: (TaskEntity) -> Unit,
    onDelete: (TaskEntity) -> Unit
) {
    if (tasks.isEmpty()) {
        EmptyHint("No tasks for today. Ask NeuralTask to \"remind me to…\"")
        return
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items(tasks, key = { it.id }) { task ->
            TaskRow(task, onToggle, onDelete)
        }
    }
}

@Composable
private fun TaskRow(task: TaskEntity, onToggle: (TaskEntity) -> Unit, onDelete: (TaskEntity) -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.fillMaxWidth().padding(10.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onToggle(task) }, Modifier.size(32.dp)) {
                Icon(
                    if (task.isDone) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
                    null, Modifier.size(20.dp),
                    if (task.isDone) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    task.title,
                    style = MaterialTheme.typography.bodyMedium,
                    textDecoration = if (task.isDone) TextDecoration.LineThrough else null,
                    color = if (task.isDone) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (task.isRoutine) {
                        Icon(Icons.Outlined.Repeat, null, Modifier.size(12.dp), MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(4.dp))
                    }
                    task.dueDate?.let {
                        Icon(Icons.Outlined.Schedule, null, Modifier.size(12.dp), MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(4.dp))
                        Text(formatDue(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (task.priority >= 2) {
                Icon(Icons.Outlined.PriorityHigh, "High priority", Modifier.size(16.dp), MaterialTheme.colorScheme.onSurface)
            }
            IconButton(onClick = { onDelete(task) }, Modifier.size(28.dp)) {
                Icon(Icons.Outlined.Delete, "Delete", Modifier.size(16.dp), MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ProjectsPage(projects: List<ProjectEntity>, onOpen: (ProjectEntity) -> Unit) {
    if (projects.isEmpty()) {
        EmptyHint("No active projects yet")
        return
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items(projects, key = { it.id }) { project ->
            Surface(
                onClick = { onOpen(project) },
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(36.dp).clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surface),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(project.icon ?: "📁", style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(project.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        project.description?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Icon(Icons.Filled.ChevronRight, null, Modifier.size(18.dp), MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProjectDetailDialog(
    project: ProjectEntity,
    tasks: List<TaskEntity>,
    onToggle: (TaskEntity) -> Unit,
    onDeleteProject: (ProjectEntity) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
        dismissButton = {
            TextButton(onClick = { onDeleteProject(project) }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
        },
        title = { Text("${project.icon ?: "📁"}  ${project.name}") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                if (tasks.isEmpty()) {
                    item { Text("No tasks in this project.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                items(tasks, key = { it.id }) { task ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { onToggle(task) }, Modifier.size(30.dp)) {
                            Icon(
                                if (task.isDone) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
                                null, Modifier.size(18.dp),
                                if (task.isDone) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            task.title,
                            style = MaterialTheme.typography.bodyMedium,
                            textDecoration = if (task.isDone) TextDecoration.LineThrough else null,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    )
}

@Composable
private fun EmptyHint(text: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = 20.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun formatDue(epoch: Long): String {
    val now = Calendar.getInstance()
    val due = Calendar.getInstance().apply { timeInMillis = epoch }
    val startOfToday = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }
    return when {
        due.before(startOfToday) -> "Overdue · " + SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(epoch))
        due.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR) &&
                due.get(Calendar.YEAR) == now.get(Calendar.YEAR) ->
            "Today · " + SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(epoch))
        else -> SimpleDateFormat("EEE, MMM d · h:mm a", Locale.getDefault()).format(Date(epoch))
    }
}
