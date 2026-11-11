// The syntax reference's search box and filters (components/syntax/SearchBar.astro), shared by a category page
// (syntax-filter.ts) and /syntax/ (syntax-search.ts): reading them, keeping them in the URL, the Filters toggle
// and the Clear filters pill.
export type Filters = { q: string; category: string; since: string; examples: boolean; current: boolean; cancellable: boolean };

export function filterBar() {
  const input = document.querySelector<HTMLInputElement>("#q");
  const count = document.querySelector<HTMLElement>("#q-count");
  const toggle = document.querySelector<HTMLButtonElement>("#filters-btn");
  const panel = document.querySelector<HTMLElement>("#filters");
  const clear = document.querySelector<HTMLButtonElement>("#clear");
  const category = document.querySelector<HTMLSelectElement>("#f-category");
  const since = document.querySelector<HTMLSelectElement>("#f-since");
  const checks = {
    examples: document.querySelector<HTMLInputElement>("#f-examples"),
    current: document.querySelector<HTMLInputElement>("#f-current"),
    cancellable: document.querySelector<HTMLInputElement>("#f-cancellable"),
  };
  if (!input || !count || !toggle || !panel || !clear || !category || !since) return null;

  const read = (): Filters => ({
    q: input.value.trim(),
    category: category.value,
    since: since.value,
    examples: checks.examples?.checked ?? false,
    current: checks.current?.checked ?? false,
    cancellable: checks.cancellable?.checked ?? false,
  });
  const active = (f: Filters) => Boolean(f.q || f.category || f.since || f.examples || f.current || f.cancellable);
  const filtersSet = (f: Filters) => Boolean(f.category || f.since || f.examples || f.current || f.cancellable);

  const write = (f: Filters) => {
    const url = new URL(location.href);
    const set = (key: string, value: string | boolean) => {
      if (value === "" || value === false) url.searchParams.delete(key);
      else url.searchParams.set(key, value === true ? "" : value);
    };
    set("q", f.q);
    set("category", f.category);
    set("since", f.since);
    set("examples", f.examples);
    set("current", f.current);
    set("cancellable", f.cancellable);
    history.replaceState(history.state, "", url.href.replace(/=(?=&|$)/g, ""));
    clear.hidden = !active(f);
  };

  // What the URL says, into the fields.
  const params = new URL(location.href).searchParams;
  input.value = params.get("q") ?? "";
  if ([...category.options].some((o) => o.value === params.get("category"))) category.value = params.get("category")!;
  if ([...since.options].some((o) => o.value === params.get("since"))) since.value = params.get("since")!;
  for (const [key, box] of Object.entries(checks)) if (box) box.checked = params.has(key);
  const initial = read();
  clear.hidden = !active(initial);
  const setOpen = (open: boolean) => {
    toggle.setAttribute("aria-expanded", String(open));
    panel.hidden = !open;
  };
  setOpen(filtersSet(initial));
  toggle.addEventListener("click", () => setOpen(panel.hidden));

  const listeners: (() => void)[] = [];
  const changed = () => listeners.forEach((l) => l());
  input.addEventListener("input", changed);
  for (const field of [category, since, ...Object.values(checks)]) field?.addEventListener("change", changed);
  input.addEventListener("keydown", (event) => {
    if (event.key === "Escape" && input.value) {
      input.value = "";
      changed();
    }
  });
  clear.addEventListener("click", () => {
    input.value = "";
    category.value = "";
    since.value = "";
    for (const box of Object.values(checks)) if (box) box.checked = false;
    changed();
    input.focus();
  });

  return { input, count, read, write, active, onChange: (l: () => void) => listeners.push(l) };
}

/** Says something to screen readers, once, from a status region made the first time it's needed. */
let region: HTMLElement | undefined;
export function announce(text: string) {
  if (!region) {
    region = document.createElement("p");
    region.className = "sr-only";
    region.setAttribute("role", "status");
    document.body.append(region);
  }
  region.textContent = text;
}
