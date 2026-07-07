import { Octokit } from 'octokit';
import { getIntegrationKey } from './secureStore';

export interface GitHubIssue {
  id: number;
  number: number;
  title: string;
  body: string | null;
  state: 'open' | 'closed';
  createdAt: string;
  updatedAt: string;
  htmlUrl: string;
  labels: { name: string; color: string }[];
  assignees?: { login: string }[];
}

export interface GitHubPullRequest {
  id: number;
  number: number;
  title: string;
  body: string | null;
  state: 'open' | 'closed' | 'merged';
  createdAt: string;
  updatedAt: string;
  htmlUrl: string;
  user: { login: string };
  additions: number;
  deletions: number;
  changedFiles: number;
}

export interface GitHubCommit {
  sha: string;
  message: string;
  author: { name: string; email: string } | null;
  createdAt: string;
  htmlUrl: string;
}

export interface GitHubRepository {
  id: number;
  name: string;
  fullName: string;
  htmlUrl: string;
  description: string | null;
  private: boolean;
  updatedAt: string;
  stargazersCount: number;
  forksCount: number;
}

export interface GitHubConfig {
  owner: string;
  repo: string;
}

let octokitInstance: Octokit | null = null;

export async function getGitHubOctokit(): Promise<Octokit | null> {
  if (octokitInstance) {
    return octokitInstance;
  }

  const token = await getIntegrationKey('github', 'token');

  if (!token) {
    return null;
  }

  octokitInstance = new Octokit({ auth: token });
  return octokitInstance;
}

export async function resetGitHubOctokit(): Promise<void> {
  octokitInstance = null;
}

export async function fetchGitHubIssues(config: GitHubConfig, state?: 'open' | 'closed' | 'all'): Promise<GitHubIssue[]> {
  const octokit = await getGitHubOctokit();

  if (!octokit) {
    throw new Error('GitHub token not configured. Please add your token in Settings.');
  }

  try {
    const { data } = await octokit.rest.issues.listForRepo({
      owner: config.owner,
      repo: config.repo,
      state: state || 'open',
      per_page: 50,
      sort: 'created',
      direction: 'desc',
    });

    return data.map((issue) => ({
      id: issue.id,
      number: issue.number,
      title: issue.title,
      body: issue.body ?? null,
      state: issue.state as 'open' | 'closed',
      createdAt: issue.created_at,
      updatedAt: issue.updated_at,
      htmlUrl: issue.html_url,
      labels: issue.labels.map((label) => ({
        name: typeof label === 'string' ? label : (label.name ?? ''),
        color: typeof label === 'string' ? '' : (label.color ?? ''),
      })),
      assignees: issue.assignees?.map((assignee) => ({ login: assignee.login })),
    }));
  } catch (error) {
    console.error('[GitHub] Failed to fetch issues:', error);
    throw new Error(`Failed to fetch issues: ${error instanceof Error ? error.message : 'Unknown error'}`);
  }
}

export async function fetchGitHubPullRequests(config: GitHubConfig, state?: 'open' | 'closed' | 'all'): Promise<GitHubPullRequest[]> {
  const octokit = await getGitHubOctokit();

  if (!octokit) {
    throw new Error('GitHub token not configured. Please add your token in Settings.');
  }

  try {
    const { data } = await octokit.rest.pulls.list({
      owner: config.owner,
      repo: config.repo,
      state: state || 'open',
      per_page: 50,
      sort: 'created',
      direction: 'desc',
    });

    return data.map((pr) => {
      const prData = pr as any;
      return {
        id: pr.id,
        number: pr.number,
        title: pr.title,
        body: pr.body ?? null,
        state: pr.merged_at ? 'merged' : (pr.state as 'open' | 'closed'),
        createdAt: pr.created_at,
        updatedAt: pr.updated_at,
        htmlUrl: pr.html_url,
        user: { login: pr.user?.login || 'unknown' },
        additions: prData.additions ?? 0,
        deletions: prData.deletions ?? 0,
        changedFiles: prData.changed_files ?? 0,
      };
    });
  } catch (error) {
    console.error('[GitHub] Failed to fetch pull requests:', error);
    throw new Error(`Failed to fetch pull requests: ${error instanceof Error ? error.message : 'Unknown error'}`);
  }
}

export async function fetchGitHubRecentCommits(config: GitHubConfig, branch?: string): Promise<GitHubCommit[]> {
  const octokit = await getGitHubOctokit();

  if (!octokit) {
    throw new Error('GitHub token not configured. Please add your token in Settings.');
  }

  try {
    const { data } = await octokit.rest.repos.listCommits({
      owner: config.owner,
      repo: config.repo,
      sha: branch,
      per_page: 30,
    });

    return data.map((commit) => ({
      sha: commit.sha,
      message: commit.commit.message,
      author: commit.commit.author ? {
        name: commit.commit.author.name ?? '',
        email: commit.commit.author.email ?? '',
      } : null,
      createdAt: commit.commit.author?.date || '',
      htmlUrl: commit.html_url,
    }));
  } catch (error) {
    console.error('[GitHub] Failed to fetch commits:', error);
    throw new Error(`Failed to fetch commits: ${error instanceof Error ? error.message : 'Unknown error'}`);
  }
}

export async function fetchGitHubRepositories(): Promise<GitHubRepository[]> {
  const octokit = await getGitHubOctokit();

  if (!octokit) {
    throw new Error('GitHub token not configured. Please add your token in Settings.');
  }

  try {
    const { data } = await octokit.rest.repos.listForAuthenticatedUser({
      per_page: 100,
      sort: 'updated',
      direction: 'desc',
    });

    return data.map((repo) => ({
      id: repo.id,
      name: repo.name,
      fullName: repo.full_name,
      htmlUrl: repo.html_url,
      description: repo.description,
      private: repo.private,
      updatedAt: repo.updated_at ?? '',
      stargazersCount: repo.stargazers_count,
      forksCount: repo.forks_count,
    }));
  } catch (error) {
    console.error('[GitHub] Failed to fetch repositories:', error);
    throw new Error(`Failed to fetch repositories: ${error instanceof Error ? error.message : 'Unknown error'}`);
  }
}

export async function generateGitHubSummary(config?: GitHubConfig): Promise<string> {
  try {
    if (!config) {
      const repos = await fetchGitHubRepositories();
      return `You have ${repos.length} repositories on GitHub. ${repos.filter((r) => !r.private).length} are public. Use Settings to select a repository for detailed PR and issue tracking.`;
    }

    const [issues, prs, commits] = await Promise.all([
      fetchGitHubIssues(config, 'open').catch(() => [] as GitHubIssue[]),
      fetchGitHubPullRequests(config, 'open').catch(() => [] as GitHubPullRequest[]),
      fetchGitHubRecentCommits(config).catch(() => [] as GitHubCommit[]),
    ]);

    const openIssues = issues.filter((i) => i.state === 'open').length;
    const openPRs = prs.filter((p) => p.state === 'open').length;

    let summary = `Repository **${config.owner}/${config.repo}** status:\n\n`;
    summary += `• Open issues: ${openIssues}\n`;
    summary += `• Open pull requests: ${openPRs}\n`;
    summary += `• Recent commits: ${commits.length}\n`;

    if (openIssues > 0) {
      const highPriority = issues.filter((i) => i.state === 'open' && i.labels.some((l) => l.name.toLowerCase().includes('priority') || l.name.toLowerCase().includes('high'))).slice(0, 3);
      if (highPriority.length > 0) {
        summary += `\nHigh-priority issues:\n`;
        highPriority.forEach((issue) => {
          summary += `  • #${issue.number}: ${issue.title}\n`;
        });
      }
    }

    if (openPRs > 0) {
      summary += `\nActive pull requests:\n`;
      prs.slice(0, 3).forEach((pr) => {
        summary += `  • #${pr.number}: ${pr.title} by ${pr.user.login}\n`;
      });
    }

    return summary;
  } catch (error) {
    console.error('[GitHub] Failed to generate summary:', error);
    return `I couldn't fetch GitHub data. ${error instanceof Error ? error.message : 'Please check your token in Settings.'}`;
  }
}
