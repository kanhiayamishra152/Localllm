import { Platform } from 'react-native';

export interface SearchResult {
  title: string;
  url: string;
  snippet: string;
}

export interface SearchResponse {
  query: string;
  results: SearchResult[];
  summary: string;
}

const DUCKDUCKGO_HTML_URL = 'https://html.duckduckgo.com/html/';
const USER_AGENT =
  Platform.OS === 'android' || Platform.OS === 'ios'
    ? 'Mozilla/5.0 (compatible; NeuralTask/1.0)'
    : 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36';

export async function agenticWebSearch(query: string, maxResults = 5): Promise<SearchResponse> {
  const sanitizedQuery = query.trim();
  if (!sanitizedQuery) {
    return { query: sanitizedQuery, results: [], summary: 'Empty query provided.' };
  }

  const formData = new FormData();
  formData.append('q', sanitizedQuery);
  formData.append('b', '');
  formData.append('kl', 'en-us');

  try {
    const response = await fetch(DUCKDUCKGO_HTML_URL, {
      method: 'POST',
      headers: {
        'User-Agent': USER_AGENT,
        'Content-Type': 'application/x-www-form-urlencoded',
      },
      body: new URLSearchParams(formData as any).toString(),
    });

    if (!response.ok) {
      throw new Error(`DuckDuckGo search failed with status ${response.status}`);
    }

    const html = await response.text();
    const results = parseDuckDuckGoHTML(html, maxResults);
    const summary = summarizeResults(results, sanitizedQuery);

    return { query: sanitizedQuery, results, summary };
  } catch (error) {
    console.error('Agentic web search error:', error);
    return {
      query: sanitizedQuery,
      results: [],
      summary: `I attempted to search the web, but encountered an issue: ${error instanceof Error ? error.message : 'Unknown error'}. Please try again later.`,
    };
  }
}

function parseDuckDuckGoHTML(html: string, maxResults: number): SearchResult[] {
  const results: SearchResult[] = [];

  const resultRegex = /<a rel="nofollow" class="result__a" href="([^"]+)"[^>]*>([\s\S]*?)<\/a>[\s\S]*?<a class="result__snippet"[^>]*>([\s\S]*?)<\/a>/g;

  let match: RegExpExecArray | null;
  while ((match = resultRegex.exec(html)) !== null && results.length < maxResults) {
    let url = match[1];
    let title = stripHtml(match[2]).trim();
    let snippet = stripHtml(match[3]).trim();

    if (url.includes('duckduckgo.com/l/?uddg=')) {
      try {
        const urlObj = new URL(url);
        url = decodeURIComponent(urlObj.searchParams.get('uddg') || url);
      } catch {
        // keep original url
      }
    }

    if (title && url && !url.includes('duckduckgo.com')) {
      results.push({ title, url, snippet });
    }
  }

  return results;
}

function stripHtml(html: string): string {
  return html
    .replace(/<[^>]*>/g, ' ')
    .replace(/&amp;/g, '&')
    .replace(/&lt;/g, '<')
    .replace(/&gt;/g, '>')
    .replace(/&quot;/g, '"')
    .replace(/&#39;/g, "'")
    .replace(/\s+/g, ' ')
    .trim();
}

function summarizeResults(results: SearchResult[], query: string): string {
  if (results.length === 0) {
    return `I searched for "${query}" but didn't find any clear results. Could you rephrase your question?`;
  }

  const topResults = results.slice(0, 3);
  const summaries = topResults.map((r, idx) => `${idx + 1}. ${r.title}: ${r.snippet}`).join('\n\n');

  return `Here's what I found for "${query}":\n\n${summaries}\n\nI found ${results.length} result(s) in total.`;
}
