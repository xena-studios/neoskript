// Filters a /syntax category page as you type: every word typed has to start a word of the entry's name, id or
// patterns, so "name" finds "name of" but not "rename".
// The filter is kept in ?q= so a filtered page can be shared.
import { focusOnSlash, split } from "./search-helpers";

const input = document.querySelector<HTMLInputElement>("#filter");
if (input) {
  const entries = [...document.querySelectorAll<HTMLElement>(".entry")];
  const words = new Map(entries.map((e) => [e, split(e.dataset.search ?? "")]));
  const names = new Map([...document.querySelectorAll<HTMLElement>(".names li")].map((li) => [li.dataset.slug, li]));
  const count = document.querySelector<HTMLElement>("#filter-count");
  const empty = document.querySelector<HTMLElement>(".empty");
  const many = input.dataset.noun ?? "";
  const one = input.dataset.one ?? many;
  const of = (n: number) => (n === 1 ? one : many);

  const apply = () => {
    const query = split(input.value);
    let shown = 0;
    for (const entry of entries) {
      const own = words.get(entry) ?? [];
      const match = query.every((q) => own.some((w) => w.startsWith(q)));
      entry.hidden = !match;
      const li = names.get(entry.id);
      if (li) li.hidden = !match;
      if (match) shown++;
    }
    if (count) {
      count.textContent = query.length ? `${shown} of ${entries.length} ${of(entries.length)}` : `${entries.length} ${of(entries.length)}`;
    }
    if (empty) empty.hidden = shown > 0;
    const url = new URL(location.href);
    if (query.length) url.searchParams.set("q", input.value.trim());
    else url.searchParams.delete("q");
    history.replaceState(history.state, "", url);
  };

  const initial = new URL(location.href).searchParams.get("q");
  if (initial) {
    input.value = initial;
    apply();
  }
  input.addEventListener("input", apply);
  input.addEventListener("keydown", (event) => {
    if (event.key === "Escape" && input.value) {
      input.value = "";
      apply();
    } else if (event.key === "Enter") {
      // Enter jumps to the first entry that's left.
      const first = entries.find((e) => !e.hidden);
      first?.scrollIntoView({ block: "start" });
      first?.querySelector<HTMLAnchorElement>("h2 a")?.focus({ preventScroll: true });
    }
  });
  focusOnSlash(input);
}
