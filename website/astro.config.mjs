// @ts-check
// neoskript.dev: the landing page, downloads and guides, as static files served
// by a Cloudflare Worker (wrangler.jsonc). The guides are Starlight, under
// /docs; the landing and download pages are plain Astro pages in src/pages.
import starlight from "@astrojs/starlight";
import { defineConfig } from "astro/config";
import starlightLinksValidator from "starlight-links-validator";

export default defineConfig({
  site: "https://neoskript.dev",
  trailingSlash: "always",
  // Hashed build files go in /assets, not Astro's default /_astro. Stylesheets
  // stay files, never inline <style>. The CSP (public/_headers) still allows
  // 'unsafe-inline' styles only because Starlight writes style attributes.
  build: { inlineStylesheets: "never", assets: "assets" },
  // Fonts and images stay files, not data: URLs: the CSP's font-src is 'self'.
  vite: { build: { assetsInlineLimit: 0 } },
  integrations: [
    starlight({
      title: "NeoSkript Docs",
      description: "A fast, modern drop-in replacement for Skript. Your .sk scripts run unchanged, only faster. For Paper and Folia.",
      logo: { dark: "./src/brand/logo-dark.svg", light: "./src/brand/logo-light.svg", replacesTitle: true },
      favicon: "/favicon.svg",
      customCss: ["./src/styles/theme.css"],
      expressiveCode: {
        themes: ["github-dark-default", "github-light-default"],
        // GitHub's token colours, on the site's own surfaces: raised night (or the light theme's tint) under a
        // hairline, the frame's bar in the page colour, and flat, as everything else (DESIGN.md).
        styleOverrides: {
          borderRadius: "12px",
          borderColor: "var(--sl-color-hairline)",
          codeBackground: "var(--sl-color-gray-6)",
          frames: {
            frameBoxShadowCssValue: "none",
            shadowColor: "transparent",
            editorBackground: "var(--sl-color-gray-6)",
            editorTabBarBackground: "var(--sl-color-black)",
            editorTabBarBorderBottomColor: "var(--sl-color-hairline)",
            editorActiveTabBackground: "var(--sl-color-gray-6)",
            editorActiveTabIndicatorTopColor: "var(--sl-color-accent)",
            terminalBackground: "var(--sl-color-gray-6)",
            terminalTitlebarBackground: "var(--sl-color-black)",
            terminalTitlebarBorderBottomColor: "var(--sl-color-hairline)",
            terminalTitlebarDotsForeground: "var(--sl-color-gray-4)",
          },
        },
      },
      // The site's nav in the header and mobile menu, and the site footer under each guide.
      components: {
        SocialIcons: "./src/components/starlight/SocialIcons.astro",
        Footer: "./src/components/starlight/Footer.astro",
      },
      editLink: { baseUrl: "https://github.com/xena-studios/neoskript/edit/main/website/" },
      lastUpdated: true,
      // Custom pages (src/pages) can't be validated; the rest of the site is.
      plugins: [starlightLinksValidator({ exclude: ["/", "/download/", "/syntax/", "/syntax/**", "/parser/", "/llms.txt"] })],
      sidebar: [
        { label: "Start here", items: ["docs", "docs/install", "docs/moving-from-skript", "docs/ai"] },
        { label: "Tools", items: ["docs/editors", "docs/parser", "docs/parser-checks"] },
      ],
    }),
  ],
});
