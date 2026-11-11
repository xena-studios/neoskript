// The tools' icons (the parser's menu bar and the syntax reference's sidebar and cards), authored like the
// External arrow: 16x16 strokes in currentColor, 1.5 wide with round caps, no fills. Astro pages render them
// with components/Icon.astro; scripts that build DOM use icon() below.
export const icons = {
  save: "M3 2.5h8l2.5 2.5v8.5h-11zM5 2.5v3.5h5v-3.5M5 13.5v-4h6v4",
  gear: "M8 5.5a2.5 2.5 0 1 0 0 5a2.5 2.5 0 1 0 0-5M8 1.5v2M8 12.5v2M1.5 8h2M12.5 8h2M3.4 3.4l1.4 1.4M11.2 11.2l1.4 1.4M3.4 12.6l1.4-1.4M11.2 4.8l1.4-1.4",
  plus: "M8 3v10M3 8h10",
  close: "M4 4l8 8M12 4l-8 8",
  chevron: "M4 6l4 4 4-4",
  parse: "M2.5 3.5l5 4.5-5 4.5zM8.5 3.5l5 4.5-5 4.5z",
  link: "M6.5 9.5l3-3M7 4.5l1.2-1.2a2.6 2.6 0 0 1 3.7 3.7L10.7 8.2M9 11.5l-1.2 1.2a2.6 2.6 0 0 1-3.7-3.7L5.3 7.8",
  search: "M7 2.5a4.5 4.5 0 1 0 0 9a4.5 4.5 0 1 0 0-9M10.3 10.3l3.2 3.2",
  filter: "M2 3.5h12M4.5 8h7M7 12.5h2",
  files: "M5 4.5v-2h6.5l2 2v8h-3M2.5 5.5h6l2 2v6.5h-8z",
  // The syntax categories.
  event: "M9 1.5l-5 7.5h4l-1 5.5 5-7.5h-4z",
  condition: "M8 1.5l6.5 6.5-6.5 6.5-6.5-6.5z",
  effect: "M4 2.5l9 5.5-9 5.5z",
  expression: "M5.5 2.5c-2 0-2 1.5-2 3s-1 2.5-2 2.5c1 0 2 1 2 2.5s0 3 2 3M10.5 2.5c2 0 2 1.5 2 3s1 2.5 2 2.5c-1 0-2 1-2 2.5s0 3-2 3",
  type: "M2.5 2.5h4.5v4.5h-4.5zM11.25 2.5a2.25 2.25 0 1 0 0 4.5a2.25 2.25 0 1 0 0-4.5M4.75 9l2.75 4.5h-5.5zM9 9h4.5v4.5h-4.5z",
  section: "M2.5 3h11M5.5 6.5h8M5.5 10h8M2.5 13.5h11M2.5 6.5v3.5",
  structure: "M2.5 2.5h11v3.5h-11zM2.5 9.5h4.5v4h-4.5zM9 9.5h4.5v4h-4.5z",
  function: "M10.5 2.5c-1.8 0-2.3.8-2.6 2.6L6.6 11c-.3 1.7-.8 2.5-2.6 2.5M4.5 6.5h6",
  guide: "M2 3c2-.7 4-.7 6 .5v10c-2-1.2-4-1.2-6-.5zM14 3c-2-.7-4-.7-6 .5v10c2-1.2 4-1.2 6-.5z",
  parser: "M5.5 4.5l-3.5 3.5 3.5 3.5M10.5 4.5l3.5 3.5-3.5 3.5",
} as const;

export type IconName = keyof typeof icons;

/** The icon as an SVG element, for scripts that build DOM. Decorative: the control it's in carries the label. */
export function icon(name: IconName, className = "icon"): SVGSVGElement {
  const ns = "http://www.w3.org/2000/svg";
  const svg = document.createElementNS(ns, "svg");
  svg.setAttribute("viewBox", "0 0 16 16");
  svg.setAttribute("width", "16");
  svg.setAttribute("height", "16");
  svg.setAttribute("aria-hidden", "true");
  svg.setAttribute("focusable", "false");
  svg.setAttribute("class", className);
  const path = document.createElementNS(ns, "path");
  path.setAttribute("d", icons[name]);
  svg.append(path);
  return svg;
}
