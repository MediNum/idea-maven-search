# Maven Search — IntelliJ IDEA Plugin

[![Build Plugin](https://github.com/MediNum/idea-maven-search/actions/workflows/build.yml/badge.svg)](https://github.com/MediNum/idea-maven-search/actions/workflows/build.yml)
[中文](README.md)

A Maven artifact search tool for IntelliJ IDEA with the same usage as the official
"Maven Search" plugin (`Tools → Maven Search`). Supports **multiple data-source
repositories** with **automatic latency-based selection**.

## Features

- **Tools → Maven Search** opens the search panel; type-to-search (results appear
  automatically 350ms after you stop typing)
- **Search Everywhere integration**: press Shift twice to open IDEA global search,
  switch to the **Maven** tab, type a keyword to search artifacts live, and press
  Enter to open the tool window and search that artifact
- **Multiple repositories**: mvn.coderead.cn (default, effective immediately on open)
  + Maven Central + custom repositories; auto-tests latency and picks the fastest
- **Search history**: click the search box to see past queries (newest first,
  deduplicated, capped at 20, persisted); "清除历史" at the bottom clears all
- **Versions & copy**: click an artifact to load all versions and auto-select the
  newest; **double-click a version to copy its Maven XML** with a popup; copy
  Maven XML / Gradle snippets
- **jar download**: custom mirror → official → Aliyun fallback
- **Settings**: ⚙ in-panel second-level page; repository list as an editable table
  (click a cell to edit, auto-save; latency-test button per row); new **preferences**:
  add extra default repository URLs (auto-loaded on every open, survives "restore
  defaults") and toggle auto latency-testing on tool window open (on by default)
- **Class mode**: search by class name

## Install

1. `File → Settings → Plugins` (or `Ctrl+Alt+S`)
2. Gear ⚙ → **Install Plugin from Disk...** → select `MavenSearch-1.5.9.jar`
3. Restart IDEA, open any project → `Tools` → **Maven Search**

> ⚠️ IntelliJ 2026.2's "Install Plugin from Disk" only loads files with a **`.jar`**
> extension (a `.zip` reports "Fail to load plugin descriptor").

## Changelog

- **1.5.9** New: settings page now has "preferences" — add extra default repository
  URLs (auto-loaded on every open) and toggle auto latency-testing on open (default on)
- **1.5.8** New: integrated into IDEA Search Everywhere (double Shift) — a new
  "Maven" tab searches artifacts live; Enter opens the tool window and searches
- **1.5.7** Fix plugin description format to pass JetBrains Marketplace validation
- **1.5.6** Default data source fixed to mvn.coderead.cn (effective immediately;
  latency test still auto-runs and may switch to a faster repository)
- **1.5.5** "Clear history" moved into the bottom of the search dropdown
- **1.5.4** Fix: "clear history" button pushed out of the clickable area
  (top bar now uses GridBagLayout)
- **1.5.3** Fix: "clear history" not working (removed the confirm dialog)
- **1.5.2** Search box empty by default on open; added "clear history"
- **1.5.1** Search box width 36, no "search" button; latency-test wait notice
- **1.5.0** Reverted 1.4.9 search-box changes
- **1.4.9** Wider search box, no "search" button (reverted)
- **1.4.8** History dropdown on the search box
- **1.4.7** ← clears input and returns to home
- **1.4.6** Description position adjusted
- **1.4.5** Double-click a version copies Maven XML; home shows the version
- **1.4.4** Click a version to auto-copy its Maven XML
- **1.4.3** Settings as a table
- **1.4.2** Settings layout polish
- **1.4.1** Repository table + latency buttons
- **1.4.0** Settings shows all repositories; 6 more default mirrors
- **1.3.3** Settings page back logic
- **1.3.2** Home usage hint
- **1.3.1** ← / ⚙ layout adjustments
- **1.3.0** Repository settings & latency auto-selection
- **1.2.1** Fix: no response when clicking the same item after going back
- **1.2.0** Added Maven Central data source
- **1.1.0** Type-to-search
- **1.0.0** Initial version

## Building

```
powershell -ExecutionPolicy Bypass -File build.ps1
```

Compiles against the locally installed IDEA jars + its JBR (zero download),
producing `MavenSearch-1.5.9.jar`.
CI (GitHub Actions): push a `v*` tag to build and create a Release.

## Technical notes

- Pure Java, no third-party dependencies: `HttpURLConnection` + built-in `MiniJson`
- Data sources: mvn.coderead.cn (/search JSON + /version HTML), Maven Central
  (Solr search + maven-metadata.xml), custom repositories (maven-metadata.xml)
- Latency selection: parallel testing in background → status bar polling → switch to
  the fastest repository; settings persisted via `PropertiesComponent`
- Compatibility: `since-build="261.0"`, only depends on `com.intellij.modules.platform`

## License

[MIT](LICENSE)
