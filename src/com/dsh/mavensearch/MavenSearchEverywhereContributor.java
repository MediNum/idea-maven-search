package com.dsh.mavensearch;

import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributor;
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributorFactory;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.Content;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.DefaultListCellRenderer;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.ListCellRenderer;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Search Everywhere（双击 Shift）集成：新增 "Maven" Tab，输入关键词实时搜索
 * Maven 组件，回车后打开 Maven Search 工具窗口并进入该词条的二级页面。
 * <p>
 * 兼容性：IDEA 2023.2 之前 fetchElements 为
 * {@code (String, boolean, ProgressIndicator, Consumer)}，2023.2+ 改为
 * {@code (String, ProgressIndicator, Processor)}；本类同时实现两个重载，
 * 以便在 IDEA 2021~2026 全版本可用（since-build 211.0）。
 */
public class MavenSearchEverywhereContributor implements SearchEverywhereContributor<CodereadClient.Artifact> {

    public static final String PROVIDER_ID = "DSH.MavenSearch";

    private final Project project;

    public MavenSearchEverywhereContributor(Project project) {
        this.project = project;
    }

    @Override
    public @NotNull String getSearchProviderId() {
        return PROVIDER_ID;
    }

    @Override
    public @NotNull String getGroupName() {
        return "Maven";
    }

    @Override
    public int getSortWeight() {
        return 100;
    }

    @Override
    public boolean showInFindResults() {
        return false;
    }

    @Override
    public boolean isDumbAware() {
        return true;
    }

    @Override
    public boolean isEmptyPatternSupported() {
        return false;
    }

    /**
     * 新版（IDEA 2023.2+）fetchElements 签名：实时搜索组件并逐条交给 consumer。
     */
    @Override
    public void fetchElements(@NotNull String pattern, @NotNull ProgressIndicator progressIndicator,
                              @NotNull Processor<? super CodereadClient.Artifact> consumer) {
        for (CodereadClient.Artifact a : doSearch(pattern, progressIndicator)) {
            if (!consumer.process(a)) break;
        }
    }

    /**
     * 旧版（IDEA 2021~2023.1）fetchElements 签名：同样实时搜索。
     * 该方法不作为 @Override（当前编译平台接口无此签名），仅为二进制兼容旧版 IDE。
     */
    @SuppressWarnings("unused")
    public void fetchElements(@NotNull String pattern, boolean everywhere,
                              @NotNull ProgressIndicator progressIndicator,
                              @NotNull Consumer<? super CodereadClient.Artifact> consumer) {
        for (CodereadClient.Artifact a : doSearch(pattern, progressIndicator)) {
            consumer.accept(a);
        }
    }

    /** 公共搜索实现：按首选项选择搜索源（默认首选项为空 → 不搜索）。 */
    private List<CodereadClient.Artifact> doSearch(String pattern, ProgressIndicator progressIndicator) {
        List<CodereadClient.Artifact> results = new ArrayList<>();
        String kw = pattern == null ? "" : pattern.trim();
        if (kw.isEmpty()) return results;
        // 主要数据源由设置页首选项决定（默认首选项为空 → 不搜索，与工具主面板一致）
        List<String> primary = SearchPanel.getExtraDefaultRepos();
        if (primary.isEmpty()) return results;
        try {
            for (String url : primary) {
                String kind = SearchPanel.classifyKind(url);
                if ("coderead".equals(kind)) {
                    results = CodereadClient.search(kw, false);
                    break;
                } else if ("central".equals(kind)) {
                    results = CentralClient.search(kw);
                    break;
                }
            }
            if (progressIndicator != null && progressIndicator.isCanceled()) return new ArrayList<>();
        } catch (Exception ignore) {
            // 网络失败/解析失败：Search Everywhere 中静默无结果即可
        }
        return results;
    }

    @Override
    public boolean processSelectedItem(@NotNull CodereadClient.Artifact selected, int modifiers,
                                       @NotNull String searchText) {
        if (project == null || project.isDisposed()) return false;
        ToolWindow tw = ToolWindowManager.getInstance(project).getToolWindow(MavenSearchAction.TOOL_WINDOW_ID);
        if (tw == null) return false;
        tw.activate(null);
        // 工具窗口首次激活后内容才由 Factory 创建，延迟到 EDT 再打开。
        // 直接进入该词条的二级页面（版本列表 + 复制 Maven XML / 下载），
        // 而不是一级搜索结果页；搜索框保留用户输入的原关键词（如 "hutool"）。
        SwingUtilities.invokeLater(() -> {
            if (project.isDisposed() || tw.isDisposed()) return;
            Content content = tw.getContentManager().getContent(0);
            if (content != null && content.getComponent() instanceof SearchPanel) {
                ((SearchPanel) content.getComponent()).openFromEverywhere(selected, searchText);
            }
        });
        return true;
    }

    @Override
    public @NotNull ListCellRenderer<? super CodereadClient.Artifact> getElementsRenderer() {
        return new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean isSelected, boolean cellHasFocus) {
                JLabel label = (JLabel) super.getListCellRendererComponent(
                        list, value, index, isSelected, cellHasFocus);
                if (value instanceof CodereadClient.Artifact) {
                    CodereadClient.Artifact a = (CodereadClient.Artifact) value;
                    label.setText(a.artifactId + "  (" + a.groupId + ")");
                }
                return label;
            }
        };
    }

    /** 兼容旧版：单独一个 Tab 展示（旧版可能要求实现该方法）。 */
    @Override
    public boolean isShownInSeparateTab() {
        return true;
    }

    @Nullable
    @Override
    public Object getDataForItem(@NotNull CodereadClient.Artifact element, @NotNull String dataId) {
        return null;
    }

    @Override
    public void dispose() {
    }

    /** Factory：由 plugin.xml 的 searchEverywhereContributor 扩展点实例化。 */
    public static final class Factory implements SearchEverywhereContributorFactory<CodereadClient.Artifact> {
        @Override
        public @NotNull SearchEverywhereContributor<CodereadClient.Artifact> createContributor(
                @NotNull AnActionEvent event) {
            Project project = event.getData(CommonDataKeys.PROJECT);
            if (project == null) {
                // 极端情况无项目上下文：用不可用实例（Search Everywhere 打开时必有项目）
                project = com.intellij.openapi.project.ProjectManager.getInstance().getDefaultProject();
            }
            return new MavenSearchEverywhereContributor(project);
        }

        @Override
        public boolean isAvailable(@NotNull Project project) {
            return !project.isDisposed();
        }
    }
}
