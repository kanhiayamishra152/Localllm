import React, { createContext, useContext, useState, useCallback } from 'react';
import { agenticWebSearch, type SearchResponse, type SearchResult } from '@/services/webSearch';
import { scheduleAITask } from '@/services/notifications';

export interface TaskItem {
  id: string;
  title: string;
  status: 'pending' | 'in-progress' | 'completed';
  source?: string;
}

export interface ChatMessage {
  id: string;
  text: string;
  sender: 'user' | 'ai';
  timestamp: Date;
  searchResults?: SearchResult[];
}

interface AIEngineContextType {
  messages: ChatMessage[];
  tasks: TaskItem[];
  isLoading: boolean;
  isSearching: boolean;
  sendMessage: (text: string) => Promise<void>;
  forceWebSearch: (query: string) => Promise<SearchResponse>;
}

const AIEngineContext = createContext<AIEngineContextType | undefined>(undefined);

export function AIEngineProvider({ children }: { children: React.ReactNode }) {
  const [messages, setMessages] = useState<ChatMessage[]>([
    {
      id: '1',
      text: `Hello! I'm NeuralTask, your personal AI assistant. I can help you manage tasks, search the web, and integrate with your favorite tools. What would you like to do today?`,
      sender: 'ai',
      timestamp: new Date(),
    },
  ]);
  const [tasks, setTasks] = useState<TaskItem[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [isSearching, setIsSearching] = useState(false);

  const detectTaskFromMessage = useCallback((text: string): TaskItem | null => {
    const cleaned = text.replace(/^(@\w+\s+)?/, '').trim();
    const title = cleaned.length > 80 ? cleaned.slice(0, 77) + '...' : cleaned || 'Untitled Task';

    if (title.toLowerCase() === 'untitled task') {
      return null;
    }

    return {
      id: `task-${Date.now()}`,
      title,
      status: 'pending',
      source: 'chat',
    };
  }, []);

  const forceWebSearch = useCallback(async (query: string): Promise<SearchResponse> => {
    setIsSearching(true);
    try {
      const response = await agenticWebSearch(query);
      return response;
    } finally {
      setIsSearching(false);
    }
  }, []);

  const sendMessage = useCallback(async (text: string) => {
    const userMessage: ChatMessage = {
      id: `msg-${Date.now()}`,
      text,
      sender: 'user',
      timestamp: new Date(),
    };

    setMessages((prev) => [...prev, userMessage]);
    setIsLoading(true);

    try {
      if (needsWebSearch(text)) {
        const searchResponse = await agenticWebSearch(text);

        const aiMessage: ChatMessage = {
          id: `msg-${Date.now() + 1}`,
          text: searchResponse.summary,
          sender: 'ai',
          timestamp: new Date(),
          searchResults: searchResponse.results,
        };

        setMessages((prev) => [...prev, aiMessage]);
      } else if (isTaskCreationIntent(text)) {
        const task = detectTaskFromMessage(text);

        if (task) {
          setTasks((prev) => [...prev, task]);

          await scheduleAITask(
            {
              title: task.title,
              body: `New task created from chat: ${task.title}`,
              modelExecutionTarget: 'cloud',
            },
            'cloud'
          );

          const aiMessage: ChatMessage = {
            id: `msg-${Date.now() + 1}`,
            text: `I've added "${task.title}" to your task list. It's scheduled and ready to go.`,
            sender: 'ai',
            timestamp: new Date(),
          };

          setMessages((prev) => [...prev, aiMessage]);
        } else {
          const aiMessage: ChatMessage = {
            id: `msg-${Date.now() + 1}`,
            text: 'I understood you wanted to create a task, but I need more details. Could you describe what you want to track?',
            sender: 'ai',
            timestamp: new Date(),
          };

          setMessages((prev) => [...prev, aiMessage]);
        }
      } else if (isIntegrationQuery(text)) {
        const integrationResponse = generateIntegrationResponse(text);

        const aiMessage: ChatMessage = {
          id: `msg-${Date.now() + 1}`,
          text: integrationResponse,
          sender: 'ai',
          timestamp: new Date(),
        };

        setMessages((prev) => [...prev, aiMessage]);
      } else {
        const aiMessage: ChatMessage = {
          id: `msg-${Date.now() + 1}`,
          text: generateMockResponse(text),
          sender: 'ai',
          timestamp: new Date(),
        };

        setMessages((prev) => [...prev, aiMessage]);
      }
    } catch {
      const errorMessage: ChatMessage = {
        id: `msg-${Date.now() + 1}`,
        text: 'I apologize, but I encountered an issue processing your request. Please try again.',
        sender: 'ai',
        timestamp: new Date(),
      };

      setMessages((prev) => [...prev, errorMessage]);
    } finally {
      setIsLoading(false);
    }
  }, [detectTaskFromMessage]);

  return (
    <AIEngineContext.Provider
      value={{
        messages,
        tasks,
        isLoading,
        isSearching,
        sendMessage,
        forceWebSearch,
      }}
    >
      {children}
    </AIEngineContext.Provider>
  );
}

export function useAIEngine() {
  const context = useContext(AIEngineContext);
  if (!context) {
    throw new Error('useAIEngine must be used within AIEngineProvider');
  }
  return context;
}

function needsWebSearch(text: string): boolean {
  const searchTriggers = [
    /\b(search|find|look up|google|what is|who is|when is|where is|how to|latest|news|current|today)\b/i,
    /\b(weather|stock|price|score|update|release)\b/i,
    /\?$/,
  ];

  return searchTriggers.some((pattern) => pattern.test(text));
}

function isTaskCreationIntent(text: string): boolean {
  const taskTriggers = [
    /\b(remind|reminder|task|todo|to-do|schedule|add|create|don't forget|need to|have to)\b/i,
    /\b(\d{1,2}(:\d{2})?\s*(am|pm)?\b)/i,
  ];

  return taskTriggers.some((pattern) => pattern.test(text)) && text.length > 5;
}

function isIntegrationQuery(text: string): boolean {
  const integrationTriggers = [
    /\b(github|pr|pull request|commit|repo|repository)\b/i,
    /\b(gmail|email|inbox|mail)\b/i,
    /\b(telegram|whatsapp|message|send)\b/i,
  ];

  return integrationTriggers.some((pattern) => pattern.test(text));
}

function generateIntegrationResponse(text: string): string {
  const lower = text.toLowerCase();

  if (/\b(github|pr|pull request|commit|repo)\b/i.test(lower)) {
    return 'Fetching GitHub summary...';
  }

  if (/\b(gmail|email|inbox|mail)\b/i.test(lower)) {
    return 'Checking your Gmail...';
  }

  if (/\b(telegram)\b/i.test(lower)) {
    return 'Checking Telegram...';
  }

  if (/\b(whatsapp)\b/i.test(lower)) {
    return 'Checking WhatsApp...';
  }

  return 'I understand you\'re asking about an integration. Which service would you like to connect: GitHub, Gmail, Telegram, or WhatsApp?';
}

function generateMockResponse(input: string): string {
  const lower = input.toLowerCase();

  if (lower.includes('hello') || lower.includes('hi') || lower.includes('hey')) {
    return 'Hello! I\'m NeuralTask, your personal AI assistant. How can I help you today? You can ask me to create tasks, search the web, or check your integrations.';
  }
  if (lower.includes('help') || lower.includes('what can you do')) {
    return 'I can help you with:\n\n• Creating and managing tasks\n• Searching the web for real-time information\n• Checking your GitHub repos and email\n• Integrating with Telegram and WhatsApp\n• Running on-device AI with LiteRT\n\nJust type naturally and I\'ll understand!';
  }
  if (lower.includes('model') || lower.includes('local') || lower.includes('device')) {
    return `Local on-device AI is supported through LiteRT. Would you like me to download a model for offline use?`;
  }
  if (lower.includes('notify') || lower.includes('notification') || lower.includes('remind')) {
    return 'I can set up notifications for your tasks. Cloud-powered background tasks are fully supported. For local AI tasks, I\'ll send you a notification and run the model when you open the app to ensure optimal performance and battery life.';
  }

  return 'I understand. I\'m processing your request. Is there anything specific you\'d like me to search for, schedule, or integrate?';
}
