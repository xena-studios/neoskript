// Splits a Skript pattern into the parts /syntax colours: words as they're typed, the brackets and bars
// that make parts optional or alternatives, %type% placeholders and <regex> parts.
export type Part = { text: string; kind: "word" | "punct" | "slot" | "regex" };

export function parts(pattern: string): Part[] {
  const out: Part[] = [];
  const push = (text: string, kind: Part["kind"]) => {
    const last = out.at(-1);
    if (last && last.kind === kind && kind !== "slot" && kind !== "regex") last.text += text;
    else out.push({ text, kind });
  };
  let i = 0;
  while (i < pattern.length) {
    const c = pattern[i];
    if (c === "\\" && i + 1 < pattern.length) {
      push(pattern[i + 1], "word");
      i += 2;
    } else if (c === "%") {
      const end = pattern.indexOf("%", i + 1);
      const stop = end < 0 ? pattern.length : end + 1;
      push(pattern.slice(i, stop), "slot");
      i = stop;
    } else if (c === "<") {
      let depth = 0;
      let j = i;
      for (; j < pattern.length; j++) {
        if (pattern[j] === "\\") j++;
        else if (pattern[j] === "<") depth++;
        else if (pattern[j] === ">" && --depth === 0) break;
      }
      push(pattern.slice(i, j + 1), "regex");
      i = j + 1;
    } else if ("[]()|".includes(c)) {
      push(c, "punct");
      i++;
    } else {
      push(c, "word");
      i++;
    }
  }
  return out;
}
