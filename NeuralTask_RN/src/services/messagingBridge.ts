import { getIntegrationKey } from './secureStore';

export interface TelegramMessage {
  messageId: number;
  chatId: string;
  text: string;
  date: string;
  from: { id: number; username?: string; firstName?: string };
}

export interface TelegramSendResult {
  ok: boolean;
  messageId?: number;
  error?: string;
}

export interface WhatsAppMessage {
  id: string;
  from: string;
  timestamp: string;
  text: string;
  type: 'text' | 'interactive' | 'image';
}

export interface WhatsAppSendResult {
  id: string;
  status: 'sent' | 'delivered' | 'read' | 'failed';
  error?: string;
}

export async function sendTelegramMessage(text: string, chatId?: string): Promise<TelegramSendResult> {
  const botToken = await getIntegrationKey('telegram', 'botToken');
  const targetChatId = chatId || (await getIntegrationKey('telegram', 'chatId'));

  if (!botToken || !targetChatId) {
    return { ok: false, error: 'Telegram bot token or chat ID not configured. Please add them in Settings.' };
  }

  try {
    const response = await fetch(`https://api.telegram.org/bot${botToken}/sendMessage`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        chat_id: targetChatId,
        text,
        parse_mode: 'Markdown',
      }),
    });

    const data = await response.json();

    if (data.ok && data.result?.message_id) {
      return { ok: true, messageId: data.result.message_id };
    }

    return { ok: false, error: data.description || 'Unknown Telegram error' };
  } catch (error) {
    console.error('[Telegram] Failed to send message:', error);
    return { ok: false, error: error instanceof Error ? error.message : 'Network error' };
  }
}

export async function fetchTelegramUpdates(offset?: number, limit = 20): Promise<TelegramMessage[]> {
  const botToken = await getIntegrationKey('telegram', 'botToken');

  if (!botToken) {
    throw new Error('Telegram bot token not configured. Please add your token in Settings.');
  }

  try {
    const params = new URLSearchParams({
      timeout: '30',
      allowed_updates: JSON.stringify(['message']),
      limit: String(limit),
    });

    if (offset) {
      params.append('offset', String(offset));
    }

    const response = await fetch(`https://api.telegram.org/bot${botToken}/getUpdates?${params.toString()}`);
    const data = await response.json();

    if (!data.ok || !data.result) {
      throw new Error(data.description || 'Failed to fetch Telegram updates');
    }

    return data.result.map((update: any) => ({
      messageId: update.update_id,
      chatId: String(update.message?.chat?.id || ''),
      text: update.message?.text || update.message?.caption || '',
      date: new Date((update.message?.date || 0) * 1000).toISOString(),
      from: {
        id: update.message?.from?.id || 0,
        username: update.message?.from?.username,
        firstName: update.message?.from?.first_name,
      },
    }));
  } catch (error) {
    console.error('[Telegram] Failed to fetch updates:', error);
    throw new Error(`Failed to fetch Telegram updates: ${error instanceof Error ? error.message : 'Unknown error'}`);
  }
}

export async function generateTelegramSummary(): Promise<string> {
  try {
    const messages = await fetchTelegramUpdates(undefined, 10);

    if (messages.length === 0) {
      return 'No recent Telegram messages.';
    }

    const latest = messages[0];
    let summary = `Latest Telegram message from ${latest.from.username || latest.from.firstName || 'unknown'}:\n\n`;
    summary += `"${latest.text.slice(0, 200)}"\n\n`;
    summary += `Received: ${new Date(latest.date).toLocaleString()}\n`;
    summary += `Total recent messages fetched: ${messages.length}`;

    return summary;
  } catch (error) {
    console.error('[Telegram] Failed to generate summary:', error);
    return `I couldn't access Telegram. ${error instanceof Error ? error.message : 'Please check your bot token in Settings.'}`;
  }
}

export async function sendWhatsAppMessage(to: string, text: string): Promise<WhatsAppSendResult> {
  const token = await getIntegrationKey('whatsapp', 'whatsappToken');
  const phoneNumberId = await getIntegrationKey('whatsapp', 'phoneNumberId');

  if (!token || !phoneNumberId) {
    return { id: '', status: 'failed', error: 'WhatsApp token or phone number ID not configured. Please add them in Settings.' };
  }

  try {
    const response = await fetch(
      `https://graph.facebook.com/v19.0/${phoneNumberId}/messages`,
      {
        method: 'POST',
        headers: {
          Authorization: `Bearer ${token}`,
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          messaging_product: 'whatsapp',
          recipient_type: 'individual',
          to,
          type: 'text',
          text: { body: text },
        }),
      }
    );

    const data = await response.json();

    if (data.messages && data.messages[0]?.id) {
      return { id: data.messages[0].id, status: 'sent' };
    }

    return { id: '', status: 'failed', error: data.error?.message || 'Unknown WhatsApp error' };
  } catch (error) {
    console.error('[WhatsApp] Failed to send message:', error);
    return { id: '', status: 'failed', error: error instanceof Error ? error.message : 'Network error' };
  }
}

export async function generateWhatsAppSummary(): Promise<string> {
  const token = await getIntegrationKey('whatsapp', 'whatsappToken');
  const phoneNumberId = await getIntegrationKey('whatsapp', 'phoneNumberId');

  if (!token || !phoneNumberId) {
    return 'WhatsApp integration is not configured. Please add your WhatsApp Business API token in Settings to enable messaging.';
  }

  try {
    const response = await fetch(
      `https://graph.facebook.com/v19.0/${phoneNumberId}/messages?limit=10`,
      {
        headers: {
          Authorization: `Bearer ${token}`,
        },
      }
    );

    const data = await response.json();

    if (data.error) {
      throw new Error(data.error.message || 'WhatsApp API error');
    }

    const messages = data.data || [];

    if (messages.length === 0) {
      return 'No recent WhatsApp messages.';
    }

    const recent = messages[0];
    let summary = 'Recent WhatsApp Business activity:\n\n';
    summary += `• Latest message received: ${new Date(recent.timestamp).toLocaleString()}\n`;
    summary += `• Status: ${recent.status}\n`;
    summary += `• Total recent messages: ${messages.length}\n\n`;
    summary += 'You can send task updates and quick to-dos via WhatsApp from NeuralTask.';

    return summary;
  } catch (error) {
    console.error('[WhatsApp] Failed to generate summary:', error);
    return `I couldn't access WhatsApp. ${error instanceof Error ? error.message : 'Please check your API credentials in Settings.'}`;
  }
}
