import * as SecureStore from 'expo-secure-store';

export type IntegrationType = 'github' | 'gmail' | 'telegram' | 'whatsapp';

export interface IntegrationKeys {
  github?: string;
  gmailClientId?: string;
  gmailClientSecret?: string;
  gmailRefreshToken?: string;
  telegramBotToken?: string;
  telegramChatId?: string;
  whatsappToken?: string;
  whatsappPhoneNumberId?: string;
}

const KEY_PREFIX = 'neuraltask_integration_';

export async function saveIntegrationKey(
  type: IntegrationType,
  key: string,
  value: string
): Promise<void> {
  try {
    await SecureStore.setItemAsync(`${KEY_PREFIX}${type}_${key}`, value);
  } catch (error) {
    console.error('[SecureStore] Failed to save key:', error);
    throw new Error(`Failed to save ${type} ${key}`);
  }
}

export async function getIntegrationKey(
  type: IntegrationType,
  key: string
): Promise<string | null> {
  try {
    return await SecureStore.getItemAsync(`${KEY_PREFIX}${type}_${key}`);
  } catch (error) {
    console.error('[SecureStore] Failed to retrieve key:', error);
    return null;
  }
}

export async function deleteIntegrationKey(
  type: IntegrationType,
  key: string
): Promise<void> {
  try {
    await SecureStore.deleteItemAsync(`${KEY_PREFIX}${type}_${key}`);
  } catch (error) {
    console.error('[SecureStore] Failed to delete key:', error);
  }
}

export async function getAllIntegrationKeys(): Promise<IntegrationKeys> {
  try {
    const keys: IntegrationKeys = {};
    const types: IntegrationType[] = ['github', 'gmail', 'telegram', 'whatsapp'];
    const keyNames = [
      'token',
      'clientId',
      'clientSecret',
      'refreshToken',
      'botToken',
      'chatId',
      'whatsappToken',
      'phoneNumberId',
    ];

    for (const type of types) {
      for (const keyName of keyNames) {
        const value = await SecureStore.getItemAsync(`${KEY_PREFIX}${type}_${keyName}`);
        if (value) {
          switch (type) {
            case 'github':
              if (keyName === 'token') keys.github = value;
              break;
            case 'gmail':
              if (keyName === 'clientId') keys.gmailClientId = value;
              if (keyName === 'clientSecret') keys.gmailClientSecret = value;
              if (keyName === 'refreshToken') keys.gmailRefreshToken = value;
              break;
            case 'telegram':
              if (keyName === 'botToken') keys.telegramBotToken = value;
              if (keyName === 'chatId') keys.telegramChatId = value;
              break;
            case 'whatsapp':
              if (keyName === 'whatsappToken') keys.whatsappToken = value;
              if (keyName === 'phoneNumberId') keys.whatsappPhoneNumberId = value;
              break;
          }
        }
      }
    }

    return keys;
  } catch (error) {
    console.error('[SecureStore] Failed to retrieve all keys:', error);
    return {};
  }
}

export async function clearAllIntegrationKeys(): Promise<void> {
  try {
    const types: IntegrationType[] = ['github', 'gmail', 'telegram', 'whatsapp'];
    const keyNames = [
      'token',
      'clientId',
      'clientSecret',
      'refreshToken',
      'botToken',
      'chatId',
      'whatsappToken',
      'phoneNumberId',
    ];

    for (const type of types) {
      for (const keyName of keyNames) {
        await SecureStore.deleteItemAsync(`${KEY_PREFIX}${type}_${keyName}`);
      }
    }
  } catch (error) {
    console.error('[SecureStore] Failed to clear keys:', error);
  }
}
