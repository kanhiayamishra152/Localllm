import * as Notifications from 'expo-notifications';
import * as TaskManager from 'expo-task-manager';
import * as BackgroundFetch from 'expo-background-fetch';
import * as FileSystem from 'expo-file-system/legacy';
import { getHardwareProfile } from './hardwareProfiler';

export type ModelExecutionTarget = 'cloud' | 'local';

export interface ScheduledAITask {
  id: string;
  title: string;
  body: string;
  modelExecutionTarget: ModelExecutionTarget;
  scheduledAt?: Date;
}

const BACKGROUND_TASK_NAME = 'neuraltask-background-ai-task';

Notifications.setNotificationHandler({
  handleNotification: async () => ({
    shouldShowAlert: true,
    shouldPlaySound: true,
    shouldSetBadge: true,
    shouldShowBanner: true,
    shouldShowList: true,
  }),
});

export async function registerBackgroundTask(): Promise<void> {
  try {
    await BackgroundFetch.registerTaskAsync(BACKGROUND_TASK_NAME, {
      minimumInterval: 15 * 60 * 1000,
      stopOnTerminate: false,
      startOnBoot: true,
    });
  } catch (error) {
    console.warn('[Notifications] Background fetch registration note:', error);
  }
}

export async function unregisterBackgroundTask(): Promise<void> {
  try {
    await BackgroundFetch.unregisterTaskAsync(BACKGROUND_TASK_NAME);
  } catch (error) {
    console.warn('[Notifications] Background fetch unregistration note:', error);
  }
}

TaskManager.defineTask(BACKGROUND_TASK_NAME, async () => {
  try {
    const pendingTasks = await getPendingAITasks();

    for (const task of pendingTasks) {
      if (task.modelExecutionTarget === 'local') {
        await handleLocalModelNotification(task);
      } else {
        await executeCloudBackgroundTask(task);
      }
    }

    return BackgroundFetch.BackgroundFetchResult.NewData;
  } catch (error) {
    console.error('[Notifications] Background task error:', error);
    return BackgroundFetch.BackgroundFetchResult.Failed;
  }
});

async function handleLocalModelNotification(task: ScheduledAITask): Promise<void> {
  await Notifications.scheduleNotificationAsync({
    content: {
      title: task.title,
      body: `${task.body} (Tap to run locally)`,
      data: {
        taskId: task.id,
        modelExecutionTarget: 'local',
      },
    },
    trigger: null,
  });
}

async function executeCloudBackgroundTask(task: ScheduledAITask): Promise<void> {
  try {
    const cloudResult = await mockCloudInference(task);

    await Notifications.scheduleNotificationAsync({
      content: {
        title: task.title,
        body: cloudResult,
        data: {
          taskId: task.id,
          modelExecutionTarget: 'cloud',
        },
      },
      trigger: null,
    });

    await markAITaskCompleted(task.id);
  } catch (error) {
    console.error('[Notifications] Cloud background task failed:', error);
  }
}

async function mockCloudInference(task: ScheduledAITask): Promise<string> {
  await new Promise((resolve) => setTimeout(resolve, 1000));

  const responses: Record<string, string> = {
    'email-summary': 'Daily inbox summary: 5 unread emails, 1 high-priority from your manager.',
    'task-reminder': 'Reminder: Your 2:00 PM team sync is in 30 minutes.',
    'web-search': 'Latest updates: The topic you asked about is trending with relevant developments today.',
  };

  return responses[task.id] || 'Background AI task completed via cloud inference.';
}

export async function scheduleAITask(
  task: Omit<ScheduledAITask, 'id'>,
  modelProvider: ModelExecutionTarget
): Promise<ScheduledAITask> {
  const profile = await getHardwareProfile();

  const executionTarget: ModelExecutionTarget =
    modelProvider === 'local' && !profile.supportsLocalInference ? 'cloud' : modelProvider;

  const scheduledTask: ScheduledAITask = {
    ...task,
    id: `ai-${Date.now()}-${Math.random().toString(36).slice(2, 9)}`,
    modelExecutionTarget: executionTarget,
    scheduledAt: new Date(),
  };

  if (executionTarget === 'cloud') {
    await scheduleCloudTaskNotification(scheduledTask);
  } else {
    await Notifications.scheduleNotificationAsync({
      content: {
        title: scheduledTask.title,
        body: `${scheduledTask.body} (Local LiteRT task — open app to run)`,
        data: {
          taskId: scheduledTask.id,
          modelExecutionTarget: 'local',
        },
      },
      trigger: scheduledTask.scheduledAt
        ? ({ type: 'date', date: scheduledTask.scheduledAt } as any)
        : null,
    });
  }

  await persistAITask(scheduledTask);
  return scheduledTask;
}

export async function handleNotificationResponse(
  response: Notifications.NotificationResponse
): Promise<{ completed: boolean; result?: string }> {
  const data = response.notification.request.content.data as
    | { taskId?: string; modelExecutionTarget?: ModelExecutionTarget }
    | undefined;

  if (!data?.taskId) {
    return { completed: false };
  }

  const task = await getAITaskById(data.taskId);
  if (!task) {
    return { completed: false };
  }

  if (task.modelExecutionTarget === 'local') {
    const result = await executeLocalForegroundTask(task);
    return { completed: true, result };
  }

  const cloudResult = await executeCloudForegroundTask(task);
  return { completed: true, result: cloudResult };
}

async function executeLocalForegroundTask(task: ScheduledAITask): Promise<string> {
  console.log(`[Notifications] Executing LOCAL LiteRT task in foreground: ${task.id}`);

  await new Promise((resolve) => setTimeout(resolve, 1500));

  return `Local LiteRT inference completed for: ${task.title}`;
}

async function executeCloudForegroundTask(task: ScheduledAITask): Promise<string> {
  console.log(`[Notifications] Executing CLOUD task in foreground: ${task.id}`);

  const result = await mockCloudInference(task);
  await markAITaskCompleted(task.id);
  return result;
}

async function scheduleCloudTaskNotification(task: ScheduledAITask): Promise<void> {
  if (task.scheduledAt && task.scheduledAt > new Date()) {
    await Notifications.scheduleNotificationAsync({
      content: {
        title: task.title,
        body: task.body,
        data: {
          taskId: task.id,
          modelExecutionTarget: 'cloud',
        },
      },
      trigger: { type: 'date', date: task.scheduledAt } as any,
    });
  }
}

async function getPendingAITasks(): Promise<ScheduledAITask[]> {
  try {
    const stored = await FileSystem.getInfoAsync(`${FileSystem.documentDirectory}neuraltask_ai_tasks.json`);
    if (!stored.exists) return [];

    const content = await FileSystem.readAsStringAsync(`${FileSystem.documentDirectory}neuraltask_ai_tasks.json`);
    const tasks: ScheduledAITask[] = JSON.parse(content);
    return tasks.filter((t) => !t.scheduledAt || t.scheduledAt > new Date(Date.now() - 1000));
  } catch {
    return [];
  }
}

async function persistAITask(task: ScheduledAITask): Promise<void> {
  try {
    const tasks = await getPendingAITasks();
    tasks.push(task);

    await FileSystem.writeAsStringAsync(
      `${FileSystem.documentDirectory}neuraltask_ai_tasks.json`,
      JSON.stringify(tasks, null, 2)
    );
  } catch (error) {
    console.error('[Notifications] Failed to persist AI task:', error);
  }
}

async function getAITaskById(id: string): Promise<ScheduledAITask | null> {
  const tasks = await getPendingAITasks();
  return tasks.find((t) => t.id === id) || null;
}

async function markAITaskCompleted(id: string): Promise<void> {
  try {
    const tasks = await getPendingAITasks();
    const filtered = tasks.filter((t) => t.id !== id);

    await FileSystem.writeAsStringAsync(
      `${FileSystem.documentDirectory}neuraltask_ai_tasks.json`,
      JSON.stringify(filtered, null, 2)
    );
  } catch (error) {
    console.error('[Notifications] Failed to mark task completed:', error);
  }
}
