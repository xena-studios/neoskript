// The site's navigation, shared by the custom pages' header and footer
// (Base.astro, SiteFooter.astro) and the guides' header and mobile menu
// (components/starlight/SocialIcons.astro), so the two can't drift apart.
export const repo = "https://github.com/xena-studios/neoskript";

// `wide` links leave the header where it's narrowest (a phone, or the guides'
// header beside the search); the footer always has them.
export const nav: { href: string; label: string; wide?: boolean }[] = [
  { href: "/docs/", label: "Docs", wide: true },
  { href: "/syntax/", label: "Syntax" },
  { href: "/parser/", label: "Parser" },
  { href: "/addons/", label: "Addons", wide: true },
  { href: "/download/", label: "Download" },
];

/** Whether `href` is the section the page at `path` is in. */
export const current = (path: string, href: string) => path.startsWith(href);
