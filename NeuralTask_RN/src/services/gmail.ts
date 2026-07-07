import * as WebBrowser from 'expo-web-browser';
import * as AuthSession from 'expo-auth-session';
import * as Crypto from 'expo-crypto';
import * as SecureStore from 'expo-secure-store';
import { getIntegrationKey, saveIntegrationKey } from './secureStore';

export interface GmailProfile {
  emailAddress: string;
  messagesTotal: number;
  threadsTotal: number;
  historyId: string;
}

export interface GmailMessage {
  id: string;
  threadId: string;
  subject: string;
  snippet: string;
  from: string;
  to: string;
  date: string;
  labels: string[];
  isUnread: boolean;
}

export interface GmailAuthState {
  accessToken: string | null;
  refreshToken: string | null;
  expiresAt: number | null;
}

const GMAIL_AUTH_ENDPOINT = 'https://accounts.google.com/o/oauth2/v2/auth';
const GMAIL_TOKEN_ENDPOINT = 'https://oauth2.googleapis.com/token';
const GMAIL_SCOPES = [
  'https://www.googleapis.com/auth/gmail.readonly',
  'https://www.googleapis.com/auth/gmail.modify',
].join(' ');

const REDIRECT_URI = AuthSession.makeRedirectUri({
  native: 'neuraltask:/oauth2redirect',
  preferLocalhost: true,
});

const CODE_VERIFIER_KEY = 'neuraltask_gmail_code_verifier';

export async function startGmailOAuthFlow(): Promise<GmailAuthState> {
  const clientId = await getIntegrationKey('gmail', 'clientId');

  if (!clientId) {
    throw new Error('Gmail Client ID not configured. Please add your credentials in Settings.');
  }

  const codeVerifier = generateCodeVerifier();
  const codeChallenge = await generateCodeChallenge(codeVerifier);

  await SecureStore.setItemAsync(CODE_VERIFIER_KEY, codeVerifier);

  const params = new URLSearchParams({
    client_id: clientId,
    redirect_uri: REDIRECT_URI,
    response_type: 'code',
    scope: GMAIL_SCOPES,
    access_type: 'offline',
    prompt: 'consent',
    code_challenge: codeChallenge,
    code_challenge_method: 'S256',
  });

  const authUrl = `${GMAIL_AUTH_ENDPOINT}?${params.toString()}`;

  try {
    const result = await WebBrowser.openAuthSessionAsync(authUrl, REDIRECT_URI);

    if (result.type === 'success' && result.url) {
      const url = new URL(result.url);
      const code = url.searchParams.get('code');

      if (!code) {
        throw new Error('Authorization missing code.');
      }

      const authState = await exchangeCodeForTokens(code, codeVerifier, clientId);

      if (authState.refreshToken) {
        await saveIntegrationKey('gmail', 'refreshToken', authState.refreshToken);
      }

      return authState;
    }

    throw new Error('User cancelled or failed to complete OAuth flow.');
  } catch (error) {
    console.error('[Gmail] OAuth flow error:', error);
    throw error;
  } finally {
    await SecureStore.deleteItemAsync(CODE_VERIFIER_KEY);
  }
}

export async function getGmailAccessToken(): Promise<string | null> {
  const refreshToken = await getIntegrationKey('gmail', 'refreshToken');
  const clientId = await getIntegrationKey('gmail', 'clientId');
  const clientSecret = await getIntegrationKey('gmail', 'clientSecret');

  if (!refreshToken || !clientId || !clientSecret) {
    return null;
  }

  try {
    const response = await fetch(GMAIL_TOKEN_ENDPOINT, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/x-www-form-urlencoded',
      },
      body: new URLSearchParams({
        grant_type: 'refresh_token',
        refresh_token: refreshToken,
        client_id: clientId,
        client_secret: clientSecret,
      }).toString(),
    });

    if (!response.ok) {
      throw new Error(`Token refresh failed: ${response.status}`);
    }

    const data = await response.json();
    return data.access_token || null;
  } catch (error) {
    console.error('[Gmail] Failed to refresh access token:', error);
    return null;
  }
}

export async function fetchGmailMessages(maxResults = 20): Promise<GmailMessage[]> {
  const accessToken = await getGmailAccessToken();

  if (!accessToken) {
    throw new Error('Gmail not authenticated. Please complete OAuth in Settings.');
  }

  try {
    const response = await fetch(
      `https://gmail.googleapis.com/gmail/v1/users/me/messages?maxResults=${maxResults}&q=is:inbox`,
      {
        headers: {
          Authorization: `Bearer ${accessToken}`,
        },
      }
    );

    if (!response.ok) {
      throw new Error(`Gmail API error: ${response.status}`);
    }

    const data = await response.json();
    const messages = data.messages || [];

    const fullMessages = await Promise.all(
      messages.map((msg: { id: string }) => fetchGmailMessage(msg.id, accessToken))
    );

    return fullMessages.filter(Boolean);
  } catch (error) {
    console.error('[Gmail] Failed to fetch messages:', error);
    throw new Error(`Failed to fetch Gmail messages: ${error instanceof Error ? error.message : 'Unknown error'}`);
  }
}

export async function fetchGmailMessage(messageId: string, accessToken?: string): Promise<GmailMessage | null> {
  const token = accessToken || await getGmailAccessToken();

  if (!token) {
    return null;
  }

  try {
    const response = await fetch(
      `https://gmail.googleapis.com/gmail/v1/users/me/messages/${messageId}?format=metadata`,
      {
        headers: {
          Authorization: `Bearer ${token}`,
        },
      }
    );

    if (!response.ok) {
      return null;
    }

    const data = await response.json();
    const headers = data.payload?.headers || [];

    const getHeader = (name: string): string => {
      const header = headers.find((h: { name: string; value: string }) => h.name.toLowerCase() === name.toLowerCase());
      return header?.value || '';
    };

    return {
      id: data.id,
      threadId: data.threadId,
      subject: getHeader('Subject'),
      snippet: data.snippet || '',
      from: getHeader('From'),
      to: getHeader('To'),
      date: getHeader('Date'),
      labels: data.labelIds || [],
      isUnread: data.labelIds?.includes('UNREAD') || false,
    };
  } catch (error) {
    console.error('[Gmail] Failed to fetch message:', error);
    return null;
  }
}

export async function generateGmailSummary(limit = 10): Promise<string> {
  try {
    const messages = await fetchGmailMessages(limit);

    if (messages.length === 0) {
      return 'Your inbox is empty.';
    }

    const unreadCount = messages.filter((m) => m.isUnread).length;
    let summary = `You have ${messages.length} recent emails, ${unreadCount} unread.\n\n`;

    const topMessages = messages.slice(0, 5);
    topMessages.forEach((msg, idx) => {
      summary += `${idx + 1}. ${msg.subject || '(No subject)'} — from ${msg.from}\n`;
      if (msg.snippet) {
        summary += `   "${msg.snippet.slice(0, 80)}..."\n`;
      }
    });

    return summary;
  } catch (error) {
    console.error('[Gmail] Failed to generate summary:', error);
    return `I couldn't access your Gmail. ${error instanceof Error ? error.message : 'Please check your OAuth credentials in Settings.'}`;
  }
}

function generateCodeVerifier(): string {
  const array = new Uint8Array(32);
  Crypto.getRandomValues(array);
  return base64UrlEncode(array);
}

async function generateCodeChallenge(verifier: string): Promise<string> {
  const hash = await Crypto.digestStringAsync(Crypto.CryptoDigestAlgorithm.SHA256, verifier);
  return hash.replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

function base64UrlEncode(buffer: Uint8Array): string {
  const base64 = btoa(String.fromCharCode(...buffer));
  return base64.replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

async function exchangeCodeForTokens(code: string, codeVerifier: string, clientId: string): Promise<GmailAuthState> {
  const clientSecret = await getIntegrationKey('gmail', 'clientSecret');

  const response = await fetch(GMAIL_TOKEN_ENDPOINT, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/x-www-form-urlencoded',
    },
    body: new URLSearchParams({
      grant_type: 'authorization_code',
      code,
      redirect_uri: REDIRECT_URI,
      client_id: clientId,
      client_secret: clientSecret || '',
      code_verifier: codeVerifier,
    }).toString(),
  });

  if (!response.ok) {
    const text = await response.text();
    throw new Error(`Token exchange failed: ${response.status} ${text}`);
  }

  const data = await response.json();

  return {
    accessToken: data.access_token || null,
    refreshToken: data.refresh_token || null,
    expiresAt: data.expires_in ? Date.now() + data.expires_in * 1000 : null,
  };
}
