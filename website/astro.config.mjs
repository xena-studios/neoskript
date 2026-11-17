// @ts-check
// neoskript.dev: the landing page, downloads and guides, as static files served
// by a Cloudflare Worker (wrangler.jsonc). The guides are Starlight, under
// /docs; the landing and download pages are plain Astro pages in src/pages.
import starlight from "@astrojs/starlight";
import { defineConfig } from "astro/config";
import starlightLinksValidator from "starlight-links-validator";
import { readFileSync } from "node:fs";
import { dark, light } from "./src/lib/code-themes.mjs";

// Skript in the guides' code blocks (```sk or ```skript), from the grammar the VS Code extension ships.
const skript = {
  ...JSON.parse(readFileSync(new URL("../editors/vscode/syntaxes/skript.tmLanguage.json", import.meta.url), "utf8")),
  name: "skript",
  aliases: ["sk"],
};

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
        // The brand's code colours (src/lib/code-themes.mjs), Skript included, on the site's raised surface
        // under a hairline. Editor frames show the file name as a tab; terminal frames are the console panes
        // the custom pages use: a title strip over the output, flat, as everything else (DESIGN.md).
        themes: [dark, light],
        shiki: { langs: [skript] },
        styleOverrides: {
          borderRadius: "12px",
          borderColor: "var(--sl-color-hairline)",
          codeBackground: "var(--sl-color-gray-6)",
          codeFontFamily: "var(--__sl-font-mono)",
          codeFontSize: "0.875rem",
          codeLineHeight: "1.7",
          codePaddingBlock: "1rem",
          codePaddingInline: "1.25rem",
          uiFontFamily: "var(--__sl-font)",
          focusBorder: "var(--sl-color-accent)",
          frames: {
            frameBoxShadowCssValue: "none",
            shadowColor: "transparent",
            editorBackground: "var(--sl-color-gray-6)",
            editorTabBarBackground: "var(--sl-color-black)",
            editorTabBarBorderBottomColor: "var(--sl-color-hairline)",
            editorTabBorderRadius: "0",
            editorActiveTabBackground: "var(--sl-color-gray-6)",
            editorActiveTabForeground: "var(--sl-color-white)",
            editorActiveTabIndicatorTopColor: "transparent",
            editorActiveTabIndicatorBottomColor: "var(--sl-color-accent)",
            editorActiveTabIndicatorHeight: "2px",
            terminalBackground: "var(--sl-color-gray-6)",
            terminalTitlebarBackground: "var(--sl-color-black)",
            terminalTitlebarForeground: "var(--sl-color-gray-3)",
            terminalTitlebarBorderBottomColor: "var(--sl-color-hairline)",
            terminalTitlebarDotsForeground: "var(--sl-color-gray-5)",
            terminalTitlebarDotsOpacity: "1",
            inlineButtonBorder: "var(--sl-color-hairline)",
            inlineButtonForeground: "var(--sl-color-gray-3)",
            tooltipSuccessBackground: "var(--sl-color-accent)",
            tooltipSuccessForeground: "var(--sl-color-black)",
          },
          textMarkers: {
            markBackground: "color-mix(in srgb, var(--sl-color-accent) 14%, transparent)",
            markBorderColor: "var(--sl-color-accent)",
            insBackground: "color-mix(in srgb, var(--sl-color-accent) 14%, transparent)",
            insBorderColor: "var(--sl-color-accent)",
            delBackground: "color-mix(in srgb, var(--sl-color-gray-3) 12%, transparent)",
            delBorderColor: "var(--sl-color-gray-4)",
          },
        },
      },
      // The site's nav in the header and mobile menu, and the site footer under each guide.
      components: {
        SocialIcons: "./src/components/starlight/SocialIcons.astro",
        Footer: "./src/components/starlight/Footer.astro",
        PageTitle: "./src/components/starlight/PageTitle.astro",
      },
      editLink: { baseUrl: "https://github.com/xena-studios/neoskript/edit/main/website/" },
      lastUpdated: true,
      // Custom pages (src/pages) can't be validated; the rest of the site is.
      plugins: [starlightLinksValidator({ exclude: ["/", "/download/", "/syntax/", "/syntax/**", "/parser/", "/llms.txt"] })],
      sidebar: [
        { label: "Start here", items: ["docs", "docs/install", "docs/moving-from-skript", "docs/ai"] },
        { label: "Tools", items: ["docs/editors", "docs/parser", "docs/parser-checks"] },
        { label: "Addons", items: ["docs/addon-api"] },
      ],
    }),
  ],
});
