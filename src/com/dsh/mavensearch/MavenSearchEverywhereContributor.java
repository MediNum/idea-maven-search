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

/**
 * Search Everywhere（双击 Shift）集成：新增 "Maven" Tab，输入关键词实时搜索
 * Maven 组件，回车后打开 Maven Search 工具窗口并搜索该组件。
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

    @Override
    public void fetchElements(@NotNull String pattern, @NotNull ProgressIndicator progressIndicator,
                              @NotNull Processor<? super CodereadClient.Artifact> consumer) {
        String kw = pattern.trim();
        if (kw.isEmpty()) return;
        try {
            List<CodereadClient.Artifact> results = CodereadClient.search(kw, false);
            for (CodereadClient.Artifact a : results) {
                if (progressIndicator.isCanceled()) break;
                if (!consumer.process(a)) break;
            }
        } catch (Exception ignore) {
            // 网络失败/解析失败：Search Everywhere 中静默无结果即可
        }
    }

    @Override
    public boolean processSelectedItem(@NotNull CodereadClient.Artifact selected, int modifiers,
                                       @NotNull String searchText) {
        if (project == null || project.isDisposed()) return false;
        ToolWindow tw = ToolWindowManager.getInstance(project).getToolWindow(MavenSearchAction.TOOL_WINDOW_ID);
        if (tw == null) return false;
        tw.activate(null);
        // 工具窗口首次激活后内容才由 Factory 创建，延迟到 EDT 再填充搜索词
        final String keyword = selected.groupId + ":" + selected.artifactId;
        SwingUtilities.invokeLater(() -> {
            if (project.isDisposed() || tw.isDisposed()) return;
            Content content = tw.getContentManager().getContent(0);
            if (content != null && content.getComponent() instanceof SearchPanel) {
                ((SearchPanel) content.getComponent()).searchFromEverywhere(keyword);
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
