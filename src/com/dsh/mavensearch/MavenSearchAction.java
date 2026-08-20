package com.dsh.mavensearch;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import org.jetbrains.annotations.NotNull;

import javax.swing.JComponent;

public class MavenSearchAction extends AnAction {
    public static final String TOOL_WINDOW_ID = "Maven Search";

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getData(CommonDataKeys.PROJECT);
        if (project == null) return;
        ToolWindow tw = ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID);
        if (tw != null) {
            tw.activate(null);
        } else {
            // Fallback: open as a dialog if the tool window is not registered.
            DialogWrapper dlg = new DialogWrapper(project, false) {
                {
                    init();
                    setTitle("Maven Search");
                }

                @Override
                protected JComponent createCenterPanel() {
                    return new SearchPanel(project);
                }
            };
            dlg.setSize(760, 640);
            dlg.show();
        }
    }
}
