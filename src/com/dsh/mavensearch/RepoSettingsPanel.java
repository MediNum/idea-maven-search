package com.dsh.mavensearch;

import com.intellij.openapi.application.ApplicationManager;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * 仓库设置页（工具内二级页面）：
 * 仓库地址以表格展示（每行一个地址，点击单元格可直接修改，点击其它区域自动保存）；
 * 每行带延迟测试按钮（按钮内显示延迟时间，点击重新测试）；保存后自动进行延迟测试。
 */
public class RepoSettingsPanel extends JPanel {
    private static final int COL_URL = 0;
    private static final int COL_LAT = 1;

    private final DefaultTableModel model = new DefaultTableModel(new Object[]{"仓库地址", "延迟"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return column == COL_URL;
        }

        @Override
        public void setValueAt(Object value, int row, int column) {
            Object old = getValueAt(row, column);
            super.setValueAt(value, row, column);
            if (column == COL_URL && (old == null || !old.equals(value))) {
                dirty = true;
            }
        }
    };
    private final JTextField urlField = new JTextField(28);
    private final JTable table = new JTable(model);
    private final Runnable onSave;
    private boolean dirty = false;

    /** 是否有未保存的修改（添加/删除/修改过仓库地址）。 */
    public boolean isDirty() {
        return dirty;
    }

    public RepoSettingsPanel(List<SearchPanel.Repo> currentRepos, Runnable onSave) {
        super(new BorderLayout(6, 6));
        this.onSave = onSave;

        // ---- 顶部：标题 + 添加 ----
        JPanel north = new JPanel(new BorderLayout(0, 4));
        JLabel title = new JLabel("Maven 仓库设置");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 14f));
        north.add(title, BorderLayout.NORTH);
        JPanel addRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        addRow.add(new JLabel("仓库地址:"));
        addRow.add(urlField);
        JButton addBtn = new JButton("添加");
        addBtn.addActionListener(e -> {
            String u = urlField.getText().trim();
            if (u.isEmpty()) return;
            if (!u.startsWith("http://") && !u.startsWith("https://")) u = "https://" + u;
            model.addRow(new Object[]{u, "测试"});
            urlField.setText("");
            dirty = true;
        });
        addRow.add(addBtn);
        addRow.add(new JLabel("  （点击地址单元格可直接修改，点击其它区域自动保存）"));
        north.add(addRow, BorderLayout.SOUTH);
        add(north, BorderLayout.NORTH);

        // ---- 中部：仓库地址表格 ----
        for (SearchPanel.Repo r : currentRepos) model.addRow(new Object[]{r.baseUrl, "测试"});
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setRowHeight(26);
        table.getColumnModel().getColumn(COL_URL).setPreferredWidth(480);
        table.getColumnModel().getColumn(COL_LAT).setPreferredWidth(110);
        // 点击地址单元格直接进入编辑；点击其它区域自动保存编辑内容
        table.putClientProperty("JTable.autoStartsEdit", Boolean.TRUE);
        table.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);
        // 延迟列渲染为按钮样式，点击即重新测试该行仓库
        table.getColumnModel().getColumn(COL_LAT).setCellRenderer(new LatencyRenderer());
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (!SwingUtilities.isLeftMouseButton(e)) return;
                int row = table.rowAtPoint(e.getPoint());
                int col = table.columnAtPoint(e.getPoint());
                if (row >= 0 && col == COL_LAT) testRow(row);
            }
        });
        JPanel center = new JPanel(new BorderLayout(0, 2));
        center.add(new JScrollPane(table), BorderLayout.CENTER);
        add(center, BorderLayout.CENTER);

        // ---- 底部：操作按钮 ----
        JPanel south = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        JButton delBtn = new JButton("删除所选");
        delBtn.addActionListener(e -> {
            int r = table.getSelectedRow();
            if (r >= 0) {
                if (table.isEditing()) table.getCellEditor().stopCellEditing();
                model.removeRow(r);
                dirty = true;
            }
        });
        south.add(delBtn);
        JButton restoreBtn = new JButton("恢复默认");
        restoreBtn.addActionListener(e -> {
            model.setRowCount(0);
            for (String d : SearchPanel.DEFAULT_REPOS) model.addRow(new Object[]{d, "测试"});
            dirty = true;
        });
        south.add(restoreBtn);
        JButton testAllBtn = new JButton("测试全部延迟");
        testAllBtn.addActionListener(e -> testAll());
        south.add(testAllBtn);
        JButton saveBtn = new JButton("保存");
        saveBtn.addActionListener(e -> {
            if (onSave != null) onSave.run();
            dirty = false;
            testAll(); // 保存仓库地址后自动进行延迟测试，按钮内显示延迟时间
        });
        south.add(saveBtn);
        add(south, BorderLayout.SOUTH);

        // 打开设置页自动测试全部延迟（按钮内显示）
        testAll();
    }

    /** 返回当前全部仓库地址列表（按表格顺序）。 */
    public List<String> getRepoUrls() {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < model.getRowCount(); i++) {
            Object v = model.getValueAt(i, COL_URL);
            if (v != null) out.add(String.valueOf(v).trim());
        }
        return out;
    }

    /** 重新测试指定行的仓库延迟并更新按钮。 */
    private void testRow(final int row) {
        final String url = String.valueOf(model.getValueAt(row, COL_URL)).trim();
        if (url.isEmpty()) return;
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            long ms = CodereadClient.measureLatency(SearchPanel.probeUrlFor(repoOf(url)));
            final String label = ms >= 0 ? ms + " ms" : "不可达";
            SwingUtilities.invokeLater(() -> {
                if (row < model.getRowCount()) model.setValueAt(label, row, COL_LAT);
            });
        });
    }

    /** 测试全部仓库延迟，逐行更新按钮显示。 */
    private void testAll() {
        final List<String> urls = getRepoUrls();
        if (urls.isEmpty()) return;
        for (int i = 0; i < model.getRowCount(); i++) model.setValueAt("…", i, COL_LAT);
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            for (int i = 0; i < urls.size(); i++) {
                final String u = urls.get(i);
                final long ms = CodereadClient.measureLatency(SearchPanel.probeUrlFor(repoOf(u)));
                final String label = ms >= 0 ? ms + " ms" : "不可达";
                final int row = i;
                SwingUtilities.invokeLater(() -> {
                    if (row < model.getRowCount()) model.setValueAt(label, row, COL_LAT);
                });
            }
        });
    }

    private static SearchPanel.Repo repoOf(String url) {
        String kind = SearchPanel.classifyKind(url);
        return new SearchPanel.Repo("r", SearchPanel.displayName(url, kind), kind, url);
    }

    /** 延迟列渲染为按钮样式（按钮内显示延迟时间）。 */
    private static class LatencyRenderer extends DefaultTableCellRenderer {
        private final JButton button = new JButton();

        LatencyRenderer() {
            button.setFocusable(false);
            button.setOpaque(false);
            button.setContentAreaFilled(false);
            button.setBorderPainted(false);
        }

        @Override
        public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean foc, int row, int col) {
            button.setText(v == null ? "测试" : String.valueOf(v));
            return button;
        }
    }
}
