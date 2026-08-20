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
 * 主仓库地址表：每行左侧带 "+" 按钮，点击将仓库复制到首选项表（启用为备用数据源）；
 * 首选项表：用户启用的备用仓库（每次打开工具只测这些仓库的连接，不再全部测速）。
 * 两表风格一致：点击地址单元格可直接修改、点击其它区域自动保存；每行带延迟测试按钮。
 */
public class RepoSettingsPanel extends JPanel {
    // 主表列
    private static final int MAIN_COL_PLUS = 0;
    private static final int MAIN_COL_URL = 1;
    private static final int MAIN_COL_LAT = 2;
    // 首选项表列
    private static final int EXTRA_COL_DEL = 0;
    private static final int EXTRA_COL_URL = 1;
    private static final int EXTRA_COL_LAT = 2;

    // ---- 主仓库地址表（全部仓库，含内置默认 + 自定义） ----
    private final DefaultTableModel model = new DefaultTableModel(new Object[]{"启用", "仓库地址", "延迟"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return column == MAIN_COL_URL;
        }

        @Override
        public void setValueAt(Object value, int row, int column) {
            Object old = getValueAt(row, column);
            super.setValueAt(value, row, column);
            if (column == MAIN_COL_URL && (old == null || !old.equals(value))) {
                dirty = true;
            }
        }
    };
    private final JTable table = new JTable(model);

    // ---- 首选项表：主要数据源（含默认数据源，独立持久化，可删除） ----
    private final DefaultTableModel extraModel = new DefaultTableModel(new Object[]{"", "主要数据源地址", "延迟"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return column == EXTRA_COL_URL;
        }

        @Override
        public void setValueAt(Object value, int row, int column) {
            Object old = getValueAt(row, column);
            super.setValueAt(value, row, column);
            if (column == EXTRA_COL_URL && (old == null || !old.equals(value))) {
                dirty = true;
            }
        }
    };
    private final JTable extraTable = new JTable(extraModel);

    private final JTextField urlField = new JTextField(28);
    private final JTextField extraField = new JTextField(28);
    private final JCheckBox autoTestBox = new JCheckBox("打开工具时测试备用仓库连接（不再测试全部仓库）");
    /** 首选项为空时的提示。 */
    private final JLabel extraEmptyHint = new JLabel("⚠ 请添加主要数据源");
    private final Runnable onSave;
    private boolean dirty = false;

    /** 是否有未保存的修改（添加/删除/修改过仓库地址）。 */
    public boolean isDirty() {
        return dirty;
    }

    public RepoSettingsPanel(List<SearchPanel.Repo> currentRepos, Runnable onSave) {
        super(new java.awt.GridBagLayout());
        this.onSave = onSave;

        // 与工具右下状态栏挂钩：首选项表读取同一份持久化数据。
        // 默认首选项为空（不内置任何数据源），由用户点击主表 + 或手动输入添加。

        // ==================== 上半区：主仓库地址表 ====================
        for (SearchPanel.Repo r : currentRepos) {
            model.addRow(new Object[]{"+", r.baseUrl, "测试"});
        }
        configureTable(table, model, MAIN_COL_URL, MAIN_COL_LAT, '+');
        JPanel main = new JPanel(new BorderLayout(0, 4));
        // 标题 + 添加行
        JPanel mainNorth = new JPanel();
        mainNorth.setLayout(new BoxLayout(mainNorth, BoxLayout.Y_AXIS));
        mainNorth.add(sectionTitle("仓库地址表（全部仓库，点击左侧 + 启用为首选数据源）"));
        JPanel addRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        addRow.add(new JLabel("仓库地址:"));
        addRow.add(urlField);
        JButton addBtn = new JButton("添加");
        addBtn.addActionListener(e -> addUrl(model, urlField, MAIN_COL_URL));
        addRow.add(addBtn);
        addRow.add(new JLabel("  （点击地址单元格可直接修改，点击其它区域自动保存）"));
        mainNorth.add(addRow);
        main.add(mainNorth, BorderLayout.NORTH);
        // 表格（占满剩余高度）
        main.add(new JScrollPane(table), BorderLayout.CENTER);
        // 主表操作按钮行
        JPanel mainBtns = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        JButton delBtn = new JButton("删除所选");
        delBtn.addActionListener(e -> deleteSelected(table, model));
        mainBtns.add(delBtn);
        JButton restoreBtn = new JButton("恢复默认");
        restoreBtn.addActionListener(e -> {
            model.setRowCount(0);
            for (String d : SearchPanel.DEFAULT_REPOS) model.addRow(new Object[]{"+", d, "测试"});
            dirty = true;
        });
        mainBtns.add(restoreBtn);
        JButton testAllBtn = new JButton("测试全部延迟");
        testAllBtn.addActionListener(e -> testAll(model, MAIN_COL_URL, MAIN_COL_LAT));
        mainBtns.add(testAllBtn);
        JButton saveBtn = new JButton("保存");
        saveBtn.addActionListener(e -> {
            if (onSave != null) onSave.run();
            dirty = false;
            testAll(model, MAIN_COL_URL, MAIN_COL_LAT); // 保存后自动测主表延迟
        });
        mainBtns.add(saveBtn);
        main.add(mainBtns, BorderLayout.SOUTH);
        leftAlign(mainNorth);

        // ==================== 下半区：首选项（主要数据源） ====================
        configureTable(extraTable, extraModel, EXTRA_COL_URL, EXTRA_COL_LAT, '-');
        // 首选项表初始展示：读取与工具右下状态栏相同的持久化数据。
        // 注意：必须调用 SearchPanel 的静态方法读持久化，不能调用本类的同名
        // 实例方法（读表格，此时尚未填充）。
        for (String u : SearchPanel.getExtraDefaultRepos()) {
            extraModel.addRow(new Object[]{"−", u, "测试"});
        }
        JPanel pref = new JPanel(new BorderLayout(0, 4));
        // 标题 + 提示 + 添加行
        JPanel prefNorth = new JPanel();
        prefNorth.setLayout(new BoxLayout(prefNorth, BoxLayout.Y_AXIS));
        prefNorth.add(sectionTitle("首选项（主要数据源）"));
        JLabel extraHint = new JLabel("主要数据源：打开工具时只测试这些仓库的连接（含默认数据源）；左侧 − 可删除");
        extraHint.setFont(extraHint.getFont().deriveFont(Font.PLAIN, 11f));
        prefNorth.add(extraHint);
        prefNorth.add(Box.createVerticalStrut(2));
        JPanel extraAddRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        extraAddRow.add(new JLabel("仓库地址:"));
        extraAddRow.add(extraField);
        JButton addExtraBtn = new JButton("添加");
        addExtraBtn.addActionListener(e -> addUrl(extraModel, extraField, EXTRA_COL_URL));
        extraAddRow.add(addExtraBtn);
        extraAddRow.add(new JLabel("  （也可点击上方仓库地址表的 + 快捷添加）"));
        prefNorth.add(extraAddRow);
        pref.add(prefNorth, BorderLayout.NORTH);
        // 表格（与主表等高，占满剩余高度）
        pref.add(new JScrollPane(extraTable), BorderLayout.CENTER);
        // 首选项为空时的提示 + 操作行
        extraEmptyHint.setFont(extraEmptyHint.getFont().deriveFont(Font.PLAIN, 12f));
        extraEmptyHint.setForeground(new java.awt.Color(0xB00020));
        extraEmptyHint.setVisible(extraModel.getRowCount() == 0);
        JPanel prefSouth = new JPanel();
        prefSouth.setLayout(new BoxLayout(prefSouth, BoxLayout.Y_AXIS));
        prefSouth.add(extraEmptyHint);
        JPanel extraBtns = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        JButton delExtraBtn = new JButton("删除所选");
        delExtraBtn.addActionListener(e -> deleteSelected(extraTable, extraModel));
        extraBtns.add(delExtraBtn);
        JButton testExtraBtn = new JButton("测试全部延迟");
        testExtraBtn.addActionListener(e -> testAll(extraModel, EXTRA_COL_URL, EXTRA_COL_LAT));
        extraBtns.add(testExtraBtn);
        JButton saveExtraBtn = new JButton("保存");
        saveExtraBtn.addActionListener(e -> savePreferences());
        extraBtns.add(saveExtraBtn);
        extraBtns.add(autoTestBox);
        prefSouth.add(extraBtns);
        pref.add(prefSouth, BorderLayout.SOUTH);
        autoTestBox.setSelected(SearchPanel.isAutoTestEnabled());
        autoTestBox.addActionListener(e -> dirty = true);
        leftAlign(prefNorth);
        leftAlign(prefSouth);
        // 点击首选项表格任意区域 → 自动保存（添加/修改/删除数据源后无需手动点保存）
        extraTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (dirty) savePreferences();
            }
        });
        // 首选项输入框失去焦点 → 自动保存
        extraField.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusLost(java.awt.event.FocusEvent e) {
                if (dirty) savePreferences();
            }
        });

        // 两个区域上下均分（等高）
        java.awt.GridBagConstraints gc = new java.awt.GridBagConstraints();
        gc.gridx = 0;
        gc.weightx = 1;
        gc.weighty = 0.5;
        gc.fill = java.awt.GridBagConstraints.BOTH;
        gc.insets = new java.awt.Insets(4, 6, 4, 6);
        gc.gridy = 0;
        add(main, gc);
        gc.gridy = 1;
        add(pref, gc);

        // 打开设置页自动测试全部延迟（首选项表 + 主表，按钮内显示）
        testAll(extraModel, EXTRA_COL_URL, EXTRA_COL_LAT);
        testAll(model, MAIN_COL_URL, MAIN_COL_LAT);
    }

    /** 让 BoxLayout 面板内的直接子组件全部左对齐。 */
    private static void leftAlign(JPanel box) {
        for (Component c : box.getComponents()) {
            if (c instanceof javax.swing.JComponent) {
                ((javax.swing.JComponent) c).setAlignmentX(Component.LEFT_ALIGNMENT);
            }
        }
    }

    /** 与仓库表一致的节标题样式。 */
    private static JLabel sectionTitle(String text) {
        JLabel l = new JLabel(text);
        l.setFont(l.getFont().deriveFont(Font.BOLD, 13f));
        return l;
    }

    /**
     * 表格通用配置：URL 列可编辑、延迟列渲染为按钮、点击延迟列测试；
     * 主表（action='+'）启用列渲染为 + 按钮（加入首选项）；
     * 首选项表（action='-'）删除列渲染为 − 按钮（从首选项删除）。
     */
    private void configureTable(JTable t, DefaultTableModel m, int urlCol, int latCol, char action) {
        t.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        t.setRowHeight(26);
        t.getColumnModel().getColumn(urlCol).setPreferredWidth(460);
        t.getColumnModel().getColumn(latCol).setPreferredWidth(110);
        if (action == '+') {
            t.getColumnModel().getColumn(0).setPreferredWidth(46);
            t.getColumnModel().getColumn(0).setMaxWidth(46);
            t.getColumnModel().getColumn(0).setCellRenderer(new ActionRenderer("+", "点击加入首选项（启用为主要数据源）"));
        } else if (action == '-') {
            t.getColumnModel().getColumn(0).setPreferredWidth(46);
            t.getColumnModel().getColumn(0).setMaxWidth(46);
            t.getColumnModel().getColumn(0).setCellRenderer(new ActionRenderer("−", "从首选项中删除该数据源"));
        }
        // 点击地址单元格直接进入编辑；点击其它区域自动保存编辑内容
        t.putClientProperty("JTable.autoStartsEdit", Boolean.TRUE);
        t.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);
        t.getColumnModel().getColumn(latCol).setCellRenderer(new LatencyRenderer());
        final int fUrlCol = urlCol;
        final int fLatCol = latCol;
        t.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (!SwingUtilities.isLeftMouseButton(e)) return;
                int row = t.rowAtPoint(e.getPoint());
                int col = t.columnAtPoint(e.getPoint());
                if (row < 0) return;
                if (action == '+' && col == 0) {
                    // 点击 +：复制该行仓库地址到首选项表（启用为主要数据源）
                    Object v = m.getValueAt(row, fUrlCol);
                    if (v != null) enableAsPrimary(String.valueOf(v).trim());
                } else if (action == '-' && col == 0) {
                    // 点击 −：从首选项表删除该数据源
                    m.removeRow(row);
                    dirty = true;
                    updateEmptyHint();
                } else if (col == fLatCol) {
                    testRow(m, row, fUrlCol, fLatCol);
                }
            }
        });
    }

    /** 点击主表 +：把仓库地址加入首选项表（去重，已有则选中提示）。 */
    private void enableAsPrimary(String url) {
        if (url.isEmpty()) return;
        for (int i = 0; i < extraModel.getRowCount(); i++) {
            Object v = extraModel.getValueAt(i, EXTRA_COL_URL);
            if (v != null && url.equals(String.valueOf(v).trim())) {
                extraTable.setRowSelectionInterval(i, i); // 已在首选项中，选中提示
                return;
            }
        }
        extraModel.addRow(new Object[]{"−", url, "测试"});
        dirty = true;
        updateEmptyHint();
    }

    /** 向指定表格添加一行仓库地址（去重、自动补 https://）。 */
    private void addUrl(DefaultTableModel m, JTextField field, int urlCol) {
        String u = field.getText().trim();
        if (u.isEmpty()) return;
        if (!u.startsWith("http://") && !u.startsWith("https://")) u = "https://" + u;
        for (int i = 0; i < m.getRowCount(); i++) {
            Object v = m.getValueAt(i, urlCol);
            if (v != null && u.equals(String.valueOf(v).trim())) {
                field.setText("");
                return; // 已存在，忽略重复
            }
        }
        if (m == model) {
            m.addRow(new Object[]{"+", u, "测试"});
        } else {
            m.addRow(new Object[]{"−", u, "测试"});
            updateEmptyHint();
        }
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
            updateEmptyHint();
        }
    }

    /** 首选项表为空时显示"请添加主要数据源"提示。 */
    private void updateEmptyHint() {
        extraEmptyHint.setVisible(extraModel.getRowCount() == 0);
    }

    /**
     * 保存首选项：将首选项（主要数据源）表内容持久化，并通知 SearchPanel
     * 重新加载仓库、切换数据源并测延迟。供"保存"按钮与点击任意区域自动保存调用。
     */
    private void savePreferences() {
        if (onSave != null) onSave.run();
        dirty = false;
        updateEmptyHint();
    }

    /** 返回主仓库地址列表（按表格顺序）。 */
    public List<String> getRepoUrls() {
        return urlsOf(model, MAIN_COL_URL);
    }

    /** 返回首选项中的备用仓库地址列表。 */
    public List<String> getExtraDefaultRepos() {
        return urlsOf(extraModel, EXTRA_COL_URL);
    }

    /** 返回首选项：打开工具时是否测试备用仓库连接。 */
    public boolean isAutoTestEnabled() {
        return autoTestBox.isSelected();
    }

    private static List<String> urlsOf(DefaultTableModel m, int urlCol) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < m.getRowCount(); i++) {
            Object v = m.getValueAt(i, urlCol);
            if (v != null) out.add(String.valueOf(v).trim());
        }
        return out;
    }

    /** 重新测试指定表格中指定行的仓库延迟并更新按钮。 */
    private void testRow(final DefaultTableModel m, final int row, final int urlCol, final int latCol) {
        final String url = String.valueOf(m.getValueAt(row, urlCol)).trim();
        if (url.isEmpty()) return;
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            long ms = CodereadClient.measureLatency(SearchPanel.probeUrlFor(repoOf(url)));
            final String label = ms >= 0 ? ms + " ms" : "不可达";
            SwingUtilities.invokeLater(() -> {
                if (row < m.getRowCount()) m.setValueAt(label, row, latCol);
            });
        });
    }

    /** 测试指定表格全部仓库延迟，逐行更新按钮显示。 */
    private void testAll(final DefaultTableModel m, final int urlCol, final int latCol) {
        final List<String> urls = urlsOf(m, urlCol);
        if (urls.isEmpty()) return;
        for (int i = 0; i < m.getRowCount(); i++) m.setValueAt("…", i, latCol);
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            for (int i = 0; i < urls.size(); i++) {
                final String u = urls.get(i);
                final long ms = CodereadClient.measureLatency(SearchPanel.probeUrlFor(repoOf(u)));
                final String label = ms >= 0 ? ms + " ms" : "不可达";
                final int row = i;
                SwingUtilities.invokeLater(() -> {
                    if (row < m.getRowCount()) m.setValueAt(label, row, latCol);
                });
            }
        });
    }

    private static SearchPanel.Repo repoOf(String url) {
        String kind = SearchPanel.classifyKind(url);
        return new SearchPanel.Repo(SearchPanel.displayName(url, kind), kind, url);
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

    /** 操作列渲染为按钮样式（+ 启用 / − 删除）。 */
    private static class ActionRenderer extends DefaultTableCellRenderer {
        private final JButton button = new JButton();

        ActionRenderer(String text, String tooltip) {
            button.setText(text);
            button.setFocusable(false);
            button.setOpaque(false);
            button.setContentAreaFilled(false);
            button.setBorderPainted(false);
            button.setToolTipText(tooltip);
        }

        @Override
        public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean foc, int row, int col) {
            return button;
        }
    }
}
