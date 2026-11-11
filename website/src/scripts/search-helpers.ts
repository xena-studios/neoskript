// Shared by the syntax reference's filter and search.
// "/" focuses the page's search field, unless someone is already typing somewhere.
export function focusOnSlash(input: HTMLInputElement) {
  document.addEventListener("keydown", (event) => {
    if (event.key !== "/" || event.ctrlKey || event.metaKey || event.altKey) return;
    const target = event.target as HTMLElement | null;
    if (target && (target.isContentEditable || /^(INPUT|TEXTAREA|SELECT)$/.test(target.tagName))) return;
    event.preventDefault();
    input.focus();
    input.select();
  });
}

/** Lower-case words, split at anything that isn't a letter or digit: "%item types%'[s]" is item, types, s. */
export const split = (text: string) => text.toLowerCase().split(/[^\p{L}\p{N}]+/u).filter(Boolean);
