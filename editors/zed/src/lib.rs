// Copyright (c) 2026 Xena Studios
// SPDX-License-Identifier: GPL-3.0-only

//! The Zed extension: Skript's grammar and queries are in `languages/skript`; this starts NeoSkript's language
//! server with Zed's own Node.js. It uses `neoskript-language-server` from the PATH if there is one, else the
//! server from NeoSkript's GitHub release for this version, downloaded once.

use std::fs;
use zed_extension_api::{self as zed, LanguageServerId, Result};

const REPO: &str = "xena-studios/neoskript";
const TAG: &str = concat!("language-server-v", env!("CARGO_PKG_VERSION"));
const ASSET: &str = "neoskript-language-server.cjs";

struct NeoSkript {
    server: Option<String>,
}

impl NeoSkript {
    fn server_path(&mut self, id: &LanguageServerId) -> Result<String> {
        if let Some(path) = &self.server {
            if fs::metadata(path).is_ok_and(|m| m.is_file()) {
                return Ok(path.clone());
            }
        }
        let dir = format!("language-server-{}", env!("CARGO_PKG_VERSION"));
        let path = format!("{dir}/{ASSET}");
        if !fs::metadata(&path).is_ok_and(|m| m.is_file()) {
            zed::set_language_server_installation_status(
                id,
                &zed::LanguageServerInstallationStatus::Downloading,
            );
            let release = zed::github_release_by_tag_name(REPO, TAG)?;
            let asset = release
                .assets
                .iter()
                .find(|a| a.name == ASSET)
                .ok_or_else(|| format!("{TAG} has no {ASSET}"))?;
            fs::create_dir_all(&dir).map_err(|e| format!("couldn't make {dir}: {e}"))?;
            zed::download_file(&asset.download_url, &path, zed::DownloadedFileType::Uncompressed)?;
            // Versions this one replaced.
            if let Ok(entries) = fs::read_dir(".") {
                for entry in entries.flatten() {
                    let name = entry.file_name();
                    let name = name.to_string_lossy();
                    if name.starts_with("language-server-") && name != dir.as_str() {
                        fs::remove_dir_all(entry.path()).ok();
                    }
                }
            }
        }
        let absolute = std::env::current_dir()
            .map_err(|e| e.to_string())?
            .join(&path)
            .to_string_lossy()
            .into_owned();
        self.server = Some(absolute.clone());
        Ok(absolute)
    }
}

impl zed::Extension for NeoSkript {
    fn new() -> Self {
        NeoSkript { server: None }
    }

    fn language_server_command(
        &mut self,
        id: &LanguageServerId,
        worktree: &zed::Worktree,
    ) -> Result<zed::Command> {
        if let Some(path) = worktree.which("neoskript-language-server") {
            return Ok(zed::Command {
                command: path,
                args: vec!["--stdio".into()],
                env: Default::default(),
            });
        }
        let server = self.server_path(id)?;
        Ok(zed::Command {
            command: zed::node_binary_path()?,
            args: vec![server, "--stdio".into()],
            env: Default::default(),
        })
    }
}

zed::register_extension!(NeoSkript);
