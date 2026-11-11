// The site's navigation, shared by the custom pages' header and footer
// (Base.astro, SiteFooter.astro) and the guides' header and mobile menu
// (components/starlight/SocialIcons.astro), so the two can't drift apart.
export const repo = "https://github.com/xena-studios/neoskript";

export const nav = [
  { href: "/docs/", label: "Docs" },
  { href: "/syntax/", label: "Syntax" },
  { href: "/parser/", label: "Parser" },
  { href: "/download/", label: "Download" },
];

/** Whether `href` is the section the page at `path` is in. */
export const current = (path: string, href: string) => path.startsWith(href);
