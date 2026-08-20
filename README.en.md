# Maven Search — IntelliJ IDEA Plugin

[![Build Plugin](https://github.com/MediNum/idea-maven-search/actions/workflows/build.yml/badge.svg)](https://github.com/MediNum/idea-maven-search/actions/workflows/build.yml)
[中文](README.md)

A Maven artifact search tool for IntelliJ IDEA with the same usage as the official
"Maven Search" plugin (`Tools → Maven Search`). Supports **multiple data-source
repositories** with **preferences-driven primary data sources**.

## Features

- **Tools → Maven Search** opens the search panel; type-to-search (results appear
  automatically 350ms after you stop typing)
- **Search Everywhere integration**: press Shift twice to open IDEA global search,
  switch to the **Maven** tab, type a keyword to search artifacts live, and press
  Enter to open the tool window and search that artifact
- **Multiple repositories**: mvn.coderead.cn is **no longer a built-in default data
  source**; **primary data sources are fully determined by the settings preferences**
  (empty preferences by default → no primary data source); click the **+** at the
  left of each row in the settings repository table to add it to preferences as a
  primary data source, and **−** to remove; the tool window status bar and the
  preferences stay **in sync** (same persisted data); when nothing is added it shows
  "主要数据源 | 请添加主要数据源"; the default list keeps only searchable
  repositories (search.maven.org) and the search source falls back to Maven Central
- **Search history**: click the search box to see past queries (newest first,
  deduplicated, capped at 20, persisted); "清除历史" at the bottom clears all
- **Versions & copy**: click an artifact to load all versions and auto-select the
  newest; **double-click a version to copy its Maven XML** with a popup; copy
  Maven XML / Gradle snippets
- **jar download**: custom mirror → official → Aliyun fallback
- **Settings**: ⚙ in-panel second-level page; the repository table and the
  preferences table are **equal height**; repositories as an editable table (click a
  cell to edit, auto-save; latency-test button per row); **preferences (primary data
  sources)**: **+** enables as primary, **−** deletes, clicking anywhere auto-saves,
  a "保存" button sits next to "测试全部延迟"; toggle for connection-testing primary
  data sources on open (on by default, only tests repositories in preferences)
- **Class mode**: search by class name

## Install

1. `File → Settings → Plugins` (or `Ctrl+Alt+S`)
2. Gear ⚙ → **Install Plugin from Disk...** → select `MavenSearch-2.0.0.jar`
3. Restart IDEA, open any project → `Tools` → **Maven Search**

> ⚠️ IntelliJ 2026.2's "Install Plugin from Disk" only loads files with a **`.jar`**
> extension (a `.zip` reports "Fail to load plugin descriptor").

## Changelog

- **2.0.0** Major release (consolidating all 1.5.x features and fixes):
  - Data source redesign: primary data sources are fully determined by the settings
    preferences (empty by default → none); the status bar and preferences stay in
    sync; adding a primary data source auto-switches and latency-tests; non-searchable
    mirror repositories removed, search falls back to Maven Central
  - Settings upgrade: equal-height repository/preferences tables, + enable / −
    delete, auto-save on click anywhere, per-row latency buttons; mvn.coderead.cn is
    no longer built in as the default
  - Search Everywhere (double Shift) integration: a "Maven" tab searches artifacts
    live
  - Search UX: history dropdown, "clear history", type-to-search; fixed dropdown
    history cross-selection and result-to-detail navigation issues
  - Code cleanup: removed dead code (legacy polling); pure Java, no third-party deps
- **1.5.0** UI & features: empty search box by default, history dropdown, "clear
  history", type-to-search, double-click a version to copy Maven XML, settings as a
  table, multiple repositories with latency selection
- **1.0.0** Initial version: search / versions / snippet copy / jar download

## Building

```
powershell -ExecutionPolicy Bypass -File build.ps1
```

Compiles against the locally installed IDEA jars + its JBR (zero download),
producing `MavenSearch-2.0.0.jar`.
CI (GitHub Actions): push a `v*` tag to build and create a Release.

## Technical notes

- Pure Java, no third-party dependencies: `HttpURLConnection` + built-in `MiniJson`
- Data sources: mvn.coderead.cn (/search JSON + /version HTML), Maven Central
  (Solr search + maven-metadata.xml), custom repositories (maven-metadata.xml)
- Primary data sources come from preferences: on open only preference repositories
  are connection-tested, then the lowest-latency one is auto-selected;
  settings persisted via `PropertiesComponent`
- Compatibility: `since-build="261.0"`, only depends on `com.intellij.modules.platform`

## License

[MIT](LICENSE)
