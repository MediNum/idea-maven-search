# Maven Search — IntelliJ IDEA 插件

[![Build Plugin](https://github.com/MediNum/idea-maven-search/actions/workflows/build.yml/badge.svg)](https://github.com/MediNum/idea-maven-search/actions/workflows/build.yml)
[English](README.en.md)

与 IDEA 官方 Maven Search 同用法（Tools → Maven Search）的 Maven 组件搜索工具，
支持**多数据源仓库 + 延迟自动择优**。

## 功能

- **Tools → Maven Search** 打开搜索面板，输入即搜索（停输 350ms 自动出结果）
- **Search Everywhere 集成**：双击 Shift 打开 IDEA 全局搜索，切到 **Maven** Tab
  输入关键词实时搜索组件，回车直接打开工具窗口并搜索该组件
- **多数据源仓库**：mvn.coderead.cn（默认，打开立即生效）+ Maven Central +
  自定义仓库；自动测速，按延迟择优
- **搜索历史**：点击搜索框弹出历史查询下拉（最新在前、去重、上限 20 条、持久化），
  底部"清除历史"一键清空
- **版本与复制**：点击组件自动加载全部版本并选中最新；**双击版本自动复制 Maven
  XML** 并弹窗提示；支持复制 Maven XML / Gradle 片段
- **下载 jar**：自定义镜像 → 官方 → 阿里云依次回退
- **设置页**：⚙ 内嵌二级页面，仓库地址表格化管理（点击单元格编辑、自动保存），
  每行带延迟测试按钮；新增**首选项**：可添加**额外默认仓库地址**（每次打开工具
  自动加载，不随恢复默认丢失）、可开关**打开工具时自动测试仓库延迟**（默认开）
- **Class 模式**：按类名搜索

## 安装

1. `File → Settings → Plugins`（`Ctrl+Alt+S`）
2. ⚙ → **Install Plugin from Disk...** 选择 `MavenSearch-1.5.9.jar`
3. 重启 IDEA，打开任意项目 → `Tools` → **Maven Search**

> ⚠️ IDEA 2026.2 的 "Install Plugin from Disk" 只认 `.jar` 后缀
> （`.zip` 会报 "Fail to load plugin descriptor"）。

## 更新记录

- **1.5.9** 新增功能：设置页新增"首选项"—— 可添加额外默认仓库地址（每次打开工具
  自动加载）；可开关"打开工具窗口时自动测试仓库延迟"（默认开）
- **1.5.8** 新增功能：集成到 IDEA Search Everywhere（双击 Shift）—— 新增 "Maven"
  Tab，输入关键词实时搜索 Maven 组件，回车直接打开工具窗口并搜索该组件
- **1.5.7** 修复插件描述格式，通过 JetBrains Marketplace 上传校验
- **1.5.6** 默认数据源固定为 mvn.coderead.cn（打开立即生效，测速完成后按延迟择优）
- **1.5.5** "清除历史"移入搜索下拉框底部（灰色居中，点击即清空）
- **1.5.4** 修复"清除历史"按钮被挤出可点击区域（顶栏改用 GridBagLayout）
- **1.5.3** 修复"清除历史"按钮未生效（去掉确认弹窗，直接清空）
- **1.5.2** 搜索框打开默认为空；新增"清除历史"按钮
- **1.5.1** 搜索框宽度 36 并去除"搜索"按钮；延迟测试中提示等待并自动搜索
- **1.5.0** 回退 1.4.9 搜索框改动，修复搜索框消失问题
- **1.4.9** 搜索框加宽、去除"搜索"按钮（已回退）
- **1.4.8** 搜索框下拉历史记录
- **1.4.7** ← 清空输入并回首页
- **1.4.6** 描述位置调整
- **1.4.5** 双击版本复制 Maven XML；首页显示版本号
- **1.4.4** 点击版本自动复制 Maven XML
- **1.4.3** 设置页表格化
- **1.4.2** 设置页布局美化
- **1.4.1** 仓库设置表格化 + 延迟按钮
- **1.4.0** 仓库设置升级，新增 6 个默认镜像
- **1.3.3** 设置页返回逻辑
- **1.3.2** 首页使用方式提示
- **1.3.1** ← / ⚙ 布局调整
- **1.3.0** 仓库设置与延迟择优
- **1.2.1** 修复返回后点击无反应
- **1.2.0** 新增 Maven Central 数据源
- **1.1.0** 输入即搜索
- **1.0.0** 首个版本

## 构建

```
powershell -ExecutionPolicy Bypass -File build.ps1
```

用本机 IDEA 自带 jar + JBR 编译，零下载，产出 `MavenSearch-1.5.9.jar`。
CI（GitHub Actions）：推送 `v*` tag 自动构建并生成 Release。

## 技术说明

- 纯 Java 无第三方依赖：`HttpURLConnection` + 内置 `MiniJson`
- 数据源：mvn.coderead.cn（/search JSON + /version HTML）、Maven Central
  （Solr 搜索 + maven-metadata.xml）、自定义仓库（maven-metadata.xml）
- 延迟择优：后台并行测速 → 状态栏轮询显示 → 自动切换最快仓库；
  设置用 `PropertiesComponent` 持久化
- 兼容性：`since-build="261.0"`，仅依赖 `com.intellij.modules.platform`
