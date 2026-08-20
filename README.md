# Maven Search — IntelliJ IDEA 插件

[![Build Plugin](https://github.com/MediNum/idea-maven-search/actions/workflows/build.yml/badge.svg)](https://github.com/MediNum/idea-maven-search/actions/workflows/build.yml)
[English](README.en.md)

与 IDEA 官方 Maven Search 同用法（Tools → Maven Search）的 Maven 组件搜索工具，
支持**多数据源仓库 + 主要数据源首选项**。

## 功能

- **Tools → Maven Search** 打开搜索面板，输入即搜索（停输 350ms 自动出结果）
- **Search Everywhere 集成**：双击 Shift 打开 IDEA 全局搜索，切到 **Maven** Tab
  输入关键词实时搜索组件，回车直接打开工具窗口并搜索该组件
- **多数据源仓库**：mvn.coderead.cn **不再内置为默认数据源**；**主要数据源完全由
  设置页首选项决定**（默认首选项为空 → 主要数据源也为空）；设置页仓库地址表每行
  左侧 **+** 号点击即可加入首选项启用为主要数据源，每行左侧 **−** 可删除；工具
  右下状态栏与首选项**同步挂钩**（同一份持久化数据），未添加时提示
  "主要数据源 | 请添加主要数据源"；默认列表只保留可搜索仓库（search.maven.org），
  搜索源自动回退 Maven Central
- **搜索历史**：点击搜索框弹出历史查询下拉（最新在前、去重、上限 20 条、持久化），
  底部"清除历史"一键清空
- **版本与复制**：点击组件自动加载全部版本并选中最新；**双击版本自动复制 Maven
  XML** 并弹窗提示；支持复制 Maven XML / Gradle 片段
- **下载 jar**：自定义镜像 → 官方 → 阿里云依次回退
- **设置页**：⚙ 内嵌二级页面，仓库地址表与首选项表**等高**；仓库地址表格化管理
  （点击单元格编辑、自动保存），每行带延迟测试按钮；**首选项（主要数据源）**：
  点击 + 启用为首选、− 删除、点击任意区域自动保存、"测试全部延迟"旁有保存按钮；
  **打开工具时测试主要数据源连接**开关（默认开，只测首选项中的仓库，不再全部测速）
- **Class 模式**：按类名搜索

## 安装

1. `File → Settings → Plugins`（`Ctrl+Alt+S`）
2. ⚙ → **Install Plugin from Disk...** 选择 `MavenSearch-2.0.0.jar`
3. 重启 IDEA，打开任意项目 → `Tools` → **Maven Search**

> ⚠️ IDEA 2026.2 的 "Install Plugin from Disk" 只认 `.jar` 后缀
> （`.zip` 会报 "Fail to load plugin descriptor"）。

## 更新记录

- **2.0.0** 主要版本（整合 1.5.x 全部功能与修复）：
  - 数据源体系重构：主要数据源完全由设置页首选项决定（默认首选项为空 → 主要
    数据源也为空）；工具右下状态栏与首选项同步挂钩；添加主要数据源后自动切换
    并测延迟；去除不可搜索的镜像仓库，搜索源自动回退 Maven Central
  - 设置页升级：仓库地址表 + 首选项（主要数据源）等高布局、+ 启用 / − 删除、
    点击任意区域自动保存、每行延迟测试按钮；默认数据源 mvn.coderead.cn 不再内置
  - Search Everywhere（双击 Shift）集成：新增 "Maven" Tab 实时搜索组件
  - 搜索体验：历史记录下拉、"清除历史"、输入即搜索；修复下拉历史串项、
    搜索结果无法进入二级页等问题
  - 代码精简：移除旧版轮询测速等死代码；纯 Java 无第三方依赖
- **1.5.0** 界面与功能：搜索框默认空、历史记录下拉、"清除历史"、输入即搜索、
  双击版本复制 Maven XML、设置页表格化、多数据源 + 延迟择优
- **1.0.0** 首个版本：搜索 / 版本列表 / 依赖片段复制 / jar 下载

## 构建

```
powershell -ExecutionPolicy Bypass -File build.ps1
```

用本机 IDEA 自带 jar + JBR 编译，零下载，产出 `MavenSearch-2.0.0.jar`。
CI（GitHub Actions）：推送 `v*` tag 自动构建并生成 Release。

## 技术说明

- 纯 Java 无第三方依赖：`HttpURLConnection` + 内置 `MiniJson`
- 数据源：mvn.coderead.cn（/search JSON + /version HTML）、Maven Central
  （Solr 搜索 + maven-metadata.xml）、自定义仓库（maven-metadata.xml）
- 主要数据源由首选项决定：打开工具只测首选项仓库连接，按延迟自动切换；
  设置用 `PropertiesComponent` 持久化
- 兼容性：`since-build="261.0"`，仅依赖 `com.intellij.modules.platform`
