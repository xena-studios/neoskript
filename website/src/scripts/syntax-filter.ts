// A /syntax category page: filters its cards as you type (every word typed has to start a word of the entry's
// name, id or patterns, so "name" finds "name of" but not "rename") and by the filters under the search box;
// marks the entry in view in the sidebar; and copies an entry's link from its link icon.
// The search and filters are kept in the URL so a filtered page can be shared.
import { focusOnSlash, split } from "./search-helpers";
import { announce, filterBar } from "./syntax-searchbar";

const main = document.querySelector<HTMLElement>(".main[data-category]");
const bar = filterBar();
if (main && bar) {
  const entries = [...document.querySelectorAll<HTMLElement>(".entry")];
  const words = new Map(entries.map((e) => [e, split(e.dataset.search ?? "")]));
  const names = new Map([...document.querySelectorAll<HTMLElement>(".names li")].map((li) => [li.dataset.slug, li]));
  const empty = document.querySelector<HTMLElement>(".empty");
  const searchAll = document.querySelector<HTMLAnchorElement>("#search-all");
  const many = main.dataset.noun ?? "";
  const one = main.dataset.one ?? many;
  const of = (n: number) => (n === 1 ? one : many);

  const apply = () => {
    const f = bar.read();
    // Another category: go there, taking the search along.
    if (f.category) {
      const url = new URL(`/syntax/${f.category}/`, location.href);
      if (f.q) url.searchParams.set("q", f.q);
      location.assign(url);
      return;
    }
    const query = split(f.q);
    let shown = 0;
    for (const entry of entries) {
      const own = words.get(entry) ?? [];
      const match =
        query.every((q) => own.some((w) => w.startsWith(q))) &&
        (!f.since || entry.dataset.since === f.since) &&
        (!f.examples || entry.dataset.examples !== undefined) &&
        (!f.current || entry.dataset.deprecated === undefined) &&
        (!f.cancellable || entry.dataset.cancellable !== undefined);
      entry.hidden = !match;
      const li = names.get(entry.id);
      if (li) li.hidden = !match;
      if (match) shown++;
    }
    const filtered = bar.active(f);
    bar.count.textContent = filtered ? `${shown} of ${entries.length} ${of(entries.length)}` : `${entries.length} ${of(entries.length)}`;
    if (empty) empty.hidden = shown > 0;
    if (searchAll) searchAll.href = f.q ? `/syntax/?q=${encodeURIComponent(f.q)}` : "/syntax/";
    bar.write(f);
  };

  bar.onChange(apply);
  if (bar.active(bar.read())) apply();
  bar.input.addEventListener("keydown", (event) => {
    if (event.key === "Enter") {
      // Enter jumps to the first entry that's left.
      const first = entries.find((e) => !e.hidden);
      first?.scrollIntoView({ block: "start" });
      first?.querySelector<HTMLAnchorElement>(".anchor")?.focus({ preventScroll: true });
    }
  });
  focusOnSlash(bar.input);

  // The sidebar's names: the first entry in the top two thirds of the window is the active one, kept in view
  // in the list.
  const list = document.querySelector<HTMLElement>(".names");
  let current: HTMLElement | undefined;
  const visible = new Set<Element>();
  const mark = () => {
    const top = entries.find((e) => visible.has(e) && !e.hidden);
    const li = top ? names.get(top.id) : undefined;
    if (li === current) return;
    current?.classList.remove("active");
    current = li;
    if (!li || !list) return;
    li.classList.add("active");
    // Scroll only the list, never the page.
    const above = li.offsetTop - list.offsetTop;
    if (above < list.scrollTop || above + li.offsetHeight > list.scrollTop + list.clientHeight) {
      list.scrollTop = above - list.clientHeight / 3;
    }
  };
  const observer = new IntersectionObserver(
    (seen) => {
      for (const s of seen) {
        if (s.isIntersecting) visible.add(s.target);
        else visible.delete(s.target);
      }
      mark();
    },
    { rootMargin: "0px 0px -30% 0px" },
  );
  for (const entry of entries) observer.observe(entry);
}

// An entry's link icon goes to its anchor, as a link does, and copies the link too.
document.addEventListener("click", (event) => {
  const anchor = (event.target as HTMLElement).closest<HTMLAnchorElement>(".entry .anchor");
  if (!anchor || event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey) return;
  void navigator.clipboard?.writeText(anchor.href).then(
    () => {
      anchor.classList.add("copied");
      announce("Link copied.");
      anchor.setAttribute("title", "Link copied");
      setTimeout(() => {
        anchor.classList.remove("copied");
        anchor.setAttribute("title", "Copy a link to this");
      }, 1600);
    },
    () => {},
  );
});
