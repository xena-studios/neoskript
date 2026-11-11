// Searches all of /syntax from its index page, over src/pages/syntax/search.json: names first, then ids and
// patterns. Every word typed has to start a word of the entry's. The category and "Added in" filters narrow it,
// and with only filters set it lists everything they match. Results are links; arrow keys move between them
// and the field.
import { focusOnSlash, split } from "./search-helpers";
import { filterBar } from "./syntax-searchbar";

type Row = { n: string; c: string; s: string; p: string[]; i: string; v: string };
type Indexed = Row & { name: string; nameWords: string[]; words: string[] };
const LIMIT = 40;
// Words that patterns have and names don't: "location of" should find Location first.
const SMALL = new Set(["a", "an", "the", "of", "to", "in", "on", "at", "is", "are", "and", "or"]);

const list = document.querySelector<HTMLOListElement>("#results");
const bar = filterBar();

if (list && bar) {
  const { input, count: status } = bar;
  const labels: Record<string, string> = JSON.parse(list.dataset.labels ?? "{}");
  let rows: Indexed[] | null = null;
  let loading: Promise<void> | null = null;
  const load = () =>
    (loading ??= fetch("/syntax/search.json")
      .then((r) => r.json())
      .then((data: Row[]) => {
        rows = data.map((r) => ({
          ...r,
          name: r.n.toLowerCase(),
          nameWords: split(r.n),
          words: split(`${r.n} ${r.i} ${r.p.join(" ")}`),
        }));
      })
      .catch(() => {
        status.textContent = "Couldn't load the search index. The categories below have everything.";
      }));

  const score = (row: Indexed, q: string, query: string[]) => {
    if (!query.every((w) => row.words.some((x) => x.startsWith(w)))) return -1;
    if (!q) return 0;
    const content = query.filter((w) => !SMALL.has(w));
    if (row.name === q || (content.length > 0 && row.name === content.join(" "))) return 0;
    if (row.name.startsWith(q)) return 1;
    if (query.every((w) => row.nameWords.some((x) => x.startsWith(w)))) return 2;
    if (content.length > 0 && content.every((w) => row.nameWords.some((x) => x.startsWith(w)))) return 2.5;
    if (row.p.some((p) => p.toLowerCase().includes(q))) return 3;
    return 4;
  };

  const span = (className: string, text: string) => {
    const node = document.createElement("span");
    node.className = className;
    node.textContent = text;
    return node;
  };

  const render = () => {
    const f = bar.read();
    bar.write(f);
    const q = f.q.toLowerCase();
    list.replaceChildren();
    if (!bar.active(f)) {
      status.textContent = "";
      list.hidden = true;
      return;
    }
    if (!rows) {
      status.textContent = "Loading…";
      load().then(render);
      return;
    }
    const query = split(q);
    const found = rows
      .filter((row) => (!f.category || row.c === f.category) && (!f.since || row.v === f.since))
      .map((row) => ({ row, score: score(row, q, query) }))
      .filter((r) => r.score >= 0)
      .sort((a, b) => a.score - b.score || a.row.n.length - b.row.n.length || a.row.n.localeCompare(b.row.n));
    for (const { row } of found.slice(0, LIMIT)) {
      const li = document.createElement("li");
      const a = document.createElement("a");
      a.href = `/syntax/${row.c}/#${row.s}`;
      const badges = span("r-badges", "");
      badges.append(span("", labels[row.c] ?? row.c));
      if (row.v !== "unknown") badges.append(span("", `Skript ${row.v}`));
      const pattern = document.createElement("code");
      pattern.className = "r-pattern";
      pattern.textContent = row.p[0] ?? "";
      a.append(span("r-name", row.n), badges, pattern);
      li.append(a);
      list.append(li);
    }
    list.hidden = found.length === 0;
    status.textContent =
      found.length === 0 ? "Nothing matches." : found.length > LIMIT ? `${found.length} matches, showing the first ${LIMIT}.` : `${found.length} ${found.length === 1 ? "match" : "matches"}.`;
  };

  input.addEventListener("focus", () => void load(), { once: true });
  bar.onChange(render);
  input.addEventListener("keydown", (event) => {
    if (event.key === "ArrowDown") {
      event.preventDefault();
      list.querySelector<HTMLAnchorElement>("a")?.focus();
    } else if (event.key === "Enter") {
      list.querySelector<HTMLAnchorElement>("a")?.click();
    }
  });
  list.addEventListener("keydown", (event) => {
    const links = [...list.querySelectorAll<HTMLAnchorElement>("a")];
    const at = links.indexOf(document.activeElement as HTMLAnchorElement);
    if (event.key === "ArrowDown" && at < links.length - 1) {
      event.preventDefault();
      links[at + 1].focus();
    } else if (event.key === "ArrowUp") {
      event.preventDefault();
      (at > 0 ? links[at - 1] : input).focus();
    } else if (event.key === "Escape") {
      input.focus();
    }
  });
  if (bar.active(bar.read())) render();
  focusOnSlash(input);
}
