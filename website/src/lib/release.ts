// The development build the download page offers: the rolling "dev"
// prerelease that CI publishes from main (.github/workflows/ci.yml,
// dev-build). Read once at build time; the site redeploys after each CI run.
// No build yet (404) is a real state the page shows. Any other failure fails
// the build in CI, so the page never silently loses its download.
const REPO = "xena-studios/neoskript";

export type DevBuild = {
  jar: { name: string; url: string; size: number };
  sha256: string | null;
  commit: string;
  published: Date;
};

async function api(path: string): Promise<Response> {
  const headers: Record<string, string> = { Accept: "application/vnd.github+json", "User-Agent": "neoskript-website" };
  if (process.env.GITHUB_TOKEN) headers.Authorization = `Bearer ${process.env.GITHUB_TOKEN}`;
  return fetch(`https://api.github.com/repos/${REPO}${path}`, { headers });
}

export async function devBuild(): Promise<DevBuild | null> {
  try {
    const res = await api("/releases/tags/dev");
    if (res.status === 404) return null;
    if (!res.ok) throw new Error(`GitHub answered ${res.status} for the dev release`);
    const release = await res.json();
    const jar = release.assets.find((a: { name: string }) => a.name.endsWith(".jar"));
    if (!jar) return null;
    const sums = release.assets.find((a: { name: string }) => a.name === "SHA256SUMS");
    let sha256: string | null = null;
    if (sums) {
      const text = await (await fetch(sums.browser_download_url)).text();
      sha256 = text.split("\n").find((l) => l.endsWith(jar.name))?.split(/\s+/)[0] ?? null;
    }
    const commit = (release.body?.match(/commit\/([0-9a-f]{40})/) ?? [])[1] ?? release.target_commitish;
    return {
      jar: { name: jar.name, url: jar.browser_download_url, size: jar.size },
      sha256,
      commit,
      published: new Date(jar.updated_at ?? release.published_at),
    };
  } catch (error) {
    if (process.env.CI) throw error;
    console.warn(`download page: no dev build (${(error as Error).message})`);
    return null;
  }
}
