package com.dsh.mavensearch;

import com.intellij.openapi.application.ApplicationManager;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
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
 * 首选项（额外默认仓库地址、打开工具是否自动测速）与仓库地址表采用统一风格：
 * 每行一个地址，点击单元格可直接修改、点击其它区域自动保存；每行带延迟测试按钮
 * （按钮内显示延迟时间，点击重新测试）；保存后自动进行延迟测试。
 */
public class RepoSettingsPanel extends JPanel {
    private static final int COL_URL = 0;
    private static final int COL_LAT = 1;

    // ---- 主仓库地址表（当前生效，保存后覆盖默认） ----
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
    private final JTable table = new JTable(model);

    // ---- 首选项：额外默认仓库地址表（独立持久化，不随恢复默认丢失） ----
    private final DefaultTableModel extraModel = new DefaultTableModel(new Object[]{"额外默认仓库地址", "延迟"}, 0) {
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
    private final JTable extraTable = new JTable(extraModel);

    private final JTextField urlField = new JTextField(28);
    private final JTextField extraField = new JTextField(28);
    private final JCheckBox autoTestBox = new JCheckBox("打开工具窗口时自动测试仓库延迟");
    private final Runnable onSave;
    private boolean dirty = false;

    /** 是否有未保存的修改（添加/删除/修改过仓库地址）。 */
    public boolean isDirty() {
        return dirty;
    }

    public RepoSettingsPanel(List<SearchPanel.Repo> currentRepos, Runnable onSave) {
        super(new BorderLayout(6, 6));
        this.onSave = onSave;

        // ==================== 顶部：仓库地址表标题与添加行 ====================
        JPanel north = new JPanel();
        north.setLayout(new BoxLayout(north, BoxLayout.Y_AXIS));

        // ---- 主仓库地址表 ----
        north.add(sectionTitle("仓库地址表（当前生效，保存后覆盖默认）"));
        JPanel addRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        addRow.add(new JLabel("仓库地址:"));
        addRow.add(urlField);
        JButton addBtn = new JButton("添加");
        addBtn.addActionListener(e -> addUrl(model, urlField));
        addRow.add(addBtn);
        addRow.add(new JLabel("  （点击地址单元格可直接修改，点击其它区域自动保存）"));
        north.add(addRow);
        add(north, BorderLayout.NORTH);

        // ==================== 中部：主仓库地址表格 + 操作按钮 ====================
        for (SearchPanel.Repo r : currentRepos) model.addRow(new Object[]{r.baseUrl, "测试"});
        configureTable(table, model);
        JPanel center = new JPanel(new BorderLayout(0, 4));
        center.add(new JScrollPane(table), BorderLayout.CENTER);
        // 主表操作按钮行
        JPanel mainBtns = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        JButton delBtn = new JButton("删除所选");
        delBtn.addActionListener(e -> deleteSelected(table, model));
        mainBtns.add(delBtn);
        JButton restoreBtn = new JButton("恢复默认");
        restoreBtn.addActionListener(e -> {
            model.setRowCount(0);
            for (String d : SearchPanel.DEFAULT_REPOS) model.addRow(new Object[]{d, "测试"});
            dirty = true;
        });
        mainBtns.add(restoreBtn);
        JButton testAllBtn = new JButton("测试全部延迟");
        testAllBtn.addActionListener(e -> testAll(model));
        mainBtns.add(testAllBtn);
        JButton saveBtn = new JButton("保存");
        saveBtn.addActionListener(e -> {
            if (onSave != null) onSave.run();
            dirty = false;
            testAll(model); // 保存仓库地址后自动进行延迟测试，按钮内显示延迟时间
        });
        mainBtns.add(saveBtn);
        center.add(mainBtns, BorderLayout.SOUTH);
        add(center, BorderLayout.CENTER);

        // ==================== 底部：首选项区（仓库地址表下方） ====================
        JPanel south = new JPanel();
        south.setLayout(new BoxLayout(south, BoxLayout.Y_AXIS));
        south.add(sectionTitle("首选项"));
        JLabel extraHint = new JLabel("额外默认仓库地址：每次打开工具时自动加载（不随“恢复默认”丢失）");
        extraHint.setFont(extraHint.getFont().deriveFont(Font.PLAIN, 11f));
        south.add(extraHint);
        south.add(Box.createVerticalStrut(2));
        JPanel extraAddRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        extraAddRow.add(new JLabel("仓库地址:"));
        extraAddRow.add(extraField);
        JButton addExtraBtn = new JButton("添加");
        addExtraBtn.addActionListener(e -> addUrl(extraModel, extraField));
        extraAddRow.add(addExtraBtn);
        extraAddRow.add(new JLabel("  （点击地址单元格可直接修改，点击其它区域自动保存）"));
        south.add(extraAddRow);
        // 额外默认仓库表格：可编辑 + 延迟测试按钮（与主表一致）
        configureTable(extraTable, extraModel);
        JPanel extraTableWrap = new JPanel(new BorderLayout());
        extraTableWrap.add(new JScrollPane(extraTable), BorderLayout.CENTER);
        extraTableWrap.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, 96));
        south.add(extraTableWrap);
        // 首选项操作行
        JPanel extraBtns = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        JButton delExtraBtn = new JButton("删除所选");
        delExtraBtn.addActionListener(e -> deleteSelected(extraTable, extraModel));
        extraBtns.add(delExtraBtn);
        JButton testExtraBtn = new JButton("测试全部延迟");
        testExtraBtn.addActionListener(e -> testAll(extraModel));
        extraBtns.add(testExtraBtn);
        extraBtns.add(autoTestBox);
        south.add(extraBtns);
        autoTestBox.setSelected(SearchPanel.isAutoTestEnabled());
        autoTestBox.addActionListener(e -> dirty = true);
        add(south, BorderLayout.SOUTH);

        // 打开设置页自动测试全部延迟（首选项表 + 主表，按钮内显示）
        testAll(extraModel);
        testAll(model);
    }

    /** 与仓库表一致的节标题样式。 */
    private static JLabel sectionTitle(String text) {
        JLabel l = new JLabel(text);
        l.setFont(l.getFont().deriveFont(Font.BOLD, 13f));
        return l;
    }

    /** 表格通用配置：可编辑首列、延迟列渲染为按钮、点击行测试延迟。 */
    private void configureTable(JTable t, DefaultTableModel m) {
        t.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        t.setRowHeight(26);
        t.getColumnModel().getColumn(COL_URL).setPreferredWidth(460);
        t.getColumnModel().getColumn(COL_LAT).setPreferredWidth(110);
        // 点击地址单元格直接进入编辑；点击其它区域自动保存编辑内容
        t.putClientProperty("JTable.autoStartsEdit", Boolean.TRUE);
        t.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);
        t.getColumnModel().getColumn(COL_LAT).setCellRenderer(new LatencyRenderer());
        t.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (!SwingUtilities.isLeftMouseButton(e)) return;
                int row = t.rowAtPoint(e.getPoint());
                int col = t.columnAtPoint(e.getPoint());
                if (row >= 0 && col == COL_LAT) testRow(m, row);
            }
        });
    }

    /** 向指定表格添加一行仓库地址（去重、自动补 https://）。 */
    private void addUrl(DefaultTableModel m, JTextField field) {
        String u = field.getText().trim();
        if (u.isEmpty()) return;
        if (!u.startsWith("http://") && !u.startsWith("https://")) u = "https://" + u;
        for (int i = 0; i < m.getRowCount(); i++) {
            if (u.equals(m.getValueAt(i, COL_URL))) {
                field.setText("");
                return; // 已存在，忽略重复
            }
        }
        m.addRow(new Object[]{u, "测试"});
        field.setText("");
        dirty = true;
    }

    /** 删除指定表格的选中行。 */
    private void deleteSelected(JTable t, DefaultTableModel m) {
        int r = t.getSelectedRow();
        if (r >= 0) {
            if (t.isEditing()) t.getCellEditor().stopCellEditing();
            m.removeRow(r);
            dirty = true;
        }
    }

    /** 返回主仓库地址列表（按表格顺序）。 */
    public List<String> getRepoUrls() {
        return urlsOf(model);
    }

    /** 返回首选项中的额外默认仓库地址列表。 */
    public List<String> getExtraDefaultRepos() {
        return urlsOf(extraModel);
    }

    /** 返回首选项：打开工具时是否自动测速。 */
    public boolean isAutoTestEnabled() {
        return autoTestBox.isSelected();
    }

    private static List<String> urlsOf(DefaultTableModel m) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < m.getRowCount(); i++) {
            Object v = m.getValueAt(i, COL_URL);
            if (v != null) out.add(String.valueOf(v).trim());
        }
        return out;
    }

    /** 重新测试指定表格中指定行的仓库延迟并更新按钮。 */
    private void testRow(final DefaultTableModel m, final int row) {
        final String url = String.valueOf(m.getValueAt(row, COL_URL)).trim();
        if (url.isEmpty()) return;
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            long ms = CodereadClient.measureLatency(SearchPanel.probeUrlFor(repoOf(url)));
            final String label = ms >= 0 ? ms + " ms" : "不可达";
            SwingUtilities.invokeLater(() -> {
                if (row < m.getRowCount()) m.setValueAt(label, row, COL_LAT);
            });
        });
    }

    /** 测试指定表格全部仓库延迟，逐行更新按钮显示。 */
    private void testAll(final DefaultTableModel m) {
        final List<String> urls = urlsOf(m);
        if (urls.isEmpty()) return;
        for (int i = 0; i < m.getRowCount(); i++) m.setValueAt("…", i, COL_LAT);
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            for (int i = 0; i < urls.size(); i++) {
                final String u = urls.get(i);
                final long ms = CodereadClient.measureLatency(SearchPanel.probeUrlFor(repoOf(u)));
                final String label = ms >= 0 ? ms + " ms" : "不可达";
                final int row = i;
                SwingUtilities.invokeLater(() -> {
                    if (row < m.getRowCount()) m.setValueAt(label, row, COL_LAT);
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
