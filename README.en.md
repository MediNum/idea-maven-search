# Maven Search — IntelliJ IDEA Plugin

A Maven artifact search tool for IntelliJ IDEA with the same usage as the official
"Maven Search" plugin (`Tools → Maven Search`). It supports **multiple data-source
repositories** (mvn.coderead.cn, Maven Central, Aliyun/Huawei/Tencent mirrors, and any
custom repository) with **automatic latency-based selection**.

Chinese version: [README.md](README.md)

## Features

- **Tools → Maven Search** opens a docked tool window (falls back to a dialog if the
  tool window is not registered)
- **Multi-repository**: default repos include mvn.coderead.cn, search.maven.org,
  repo1.maven.org, repo.maven.apache.org, central.sonatype.com, Aliyun public,
  Huawei Cloud, Tencent Nexus — all editable in **⚙ Settings** (in-panel second-level
  page, not a dialog; the repository list is shown as a table — click a cell to edit,
  click elsewhere to auto-save; each row has a latency-test button showing the
  measured latency; saving auto-runs the latency test)
- **Latency auto-selection**: on opening, the tool auto-tests all repositories, rotates
  each one's latency in the bottom status bar (1s each), then shows only the
  lowest-latency repository and switches to it as the data source; if you type while
  the test is running, a centered notice "正在进行延迟测试，请稍后…" is shown with the
  per-repository progress, and the search runs automatically when the test finishes
- **Type-to-search**: the search box is empty by default (history is in the dropdown —
  click the search box to see past queries, recorded on Enter/history selection/result
  click, newest first, deduplicated, capped at 20, persisted; a "清除历史" item at the
  bottom of the dropdown clears all history); searches automatically 350ms after you
  stop typing; Class mode searches by class name (e.g. `JSONObject`)
- **Auto version load**: clicking an artifact loads all versions and auto-selects the
  newest; **double-clicking a version copies its Maven XML** to the clipboard with a
  "复制成功" popup; the description is shown between the version list and the Maven XML
  box; Maven XML / Gradle snippets and jar download (custom mirror → official → Aliyun)
  are one click away
- **Default data source**: http://mvn.coderead.cn (effective immediately on open;
  after the latency test, the fastest repository wins)

## Install

> ⚠️ IntelliJ 2026.2's "Install Plugin from Disk" only loads files with a **`.jar`**
> extension (a `.zip` goes down a different branch and reports
> "Fail to load plugin descriptor"). The artifact is `MavenSearch-1.5.6.jar`
> (zip and jar are the same format).

1. IDEA: `File → Settings → Plugins` (or `Ctrl+Alt+S`)
2. Click the gear ⚙ → **Install Plugin from Disk...**
3. Select **`MavenSearch-1.5.6.jar`**
4. Restart IDEA
5. Open any project → `Tools` menu → **Maven Search**

You can also grab the latest `.jar` from the [GitHub Releases](../../releases)
(built automatically by CI on every `v*` tag).

## Building

### Locally (no network/Gradle required)

```
powershell -ExecutionPolicy Bypass -File build.ps1
```

`build.ps1` compiles against the platform jars of the locally installed IDEA
(`$IDEA\lib\*`) with its bundled JBR (Java 25) and packages with the JDK's `jar.exe`,
producing `MavenSearch-1.5.6.jar`. Adjust `$IDEA` / `$JBR` at the top of the script if
your IDEA path differs.

### CI (GitHub Actions)

Push a tag like `v1.5.6` — the workflow `.github/workflows/build.yml` builds the
plugin with the IntelliJ Platform Gradle plugin (JDK 25 + Gradle), uploads the jar as
an artifact, and creates a GitHub Release with the installable `.jar`.

## Changelog

- **1.5.6** Default data source is `http://mvn.coderead.cn` (effective immediately;
  latency test still auto-runs and may switch to a faster repository)
- **1.5.5** "Clear history" moved into the bottom of the search dropdown (gray,
  centered) — no longer a first-page button
- **1.5.4** Fix: the "clear history" button was pushed out of the clickable area —
  top bar now uses GridBagLayout (search box flexes)
- **1.5.3** Fix: "clear history" not working — removed the confirm dialog, clears
  directly with exception protection
- **1.5.2** Fix: search box empty by default on open (history stays in the dropdown);
  added "clear history"
- **1.5.1** Search box width 36 (prototype string; fixes the disappearing box) and no
  "search" button; latency-test wait notice in the page center with progress
- **1.5.0** Fix: reverted 1.4.9 search-box changes (restored the search button)
- **1.4.8** History dropdown on the search box (click to see past queries)
- **1.4.4** Click a version to auto-copy its Maven XML
- **1.4.0** Settings shows all repositories (defaults + custom, editable/deletable/
  restorable); 6 more default mirrors
- **1.3.x** Settings second-level page, latency test + auto-selection, input-to-search,
  UI polish
- **1.0.0** Initial version: search / versions / snippet copy / jar download

## License

[MIT](LICENSE)
