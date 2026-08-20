package com.dsh.mavensearch;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;

import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Maven 搜索面板：多数据源仓库（内置 + 用户自定义）、输入即搜索、版本/依赖片段、下载；
 * 打开时自动测试各仓库延迟，轮询显示（每个 1 秒），结束后切换为延迟最小的仓库。
 */
public class SearchPanel extends JPanel {
    private static final Logger LOG = Logger.getInstance("com.dsh.mavensearch.SearchPanel");
    private static final String KEY_REPOS = "com.dsh.maven-search.repos";
    private static final String KEY_HISTORY = "com.dsh.maven-search.history";
    /** 首选项：额外默认仓库地址（每次启动自动加载，持久化）。 */
    private static final String KEY_EXTRA_REPOS = "com.dsh.maven-search.extraRepos";
    /** 首选项：打开工具窗口时是否自动测试仓库延迟（持久化，默认开）。 */
    private static final String KEY_AUTO_TEST = "com.dsh.maven-search.autoTest";
    /** 搜索下拉框最底部的"清除历史"项标识。 */
    private static final String SENTINEL = "— 清除历史记录 —";

    /** 工具默认仓库地址（可在设置中修改/恢复）。 */
    static final String[] DEFAULT_REPOS = {
            "http://mvn.coderead.cn",
            "https://search.maven.org",
            "https://repo1.maven.org/maven2",
            "https://repo.maven.apache.org/maven2",
            "https://central.sonatype.com",
            "https://maven.aliyun.com/repository/public",
            "https://repo.huaweicloud.com/repository/maven",
            "https://mirrors.cloud.tencent.com/nexus/repository/maven-public"
    };

    /** 首次打开工具时居中显示的使用方式（13 号字，灰度），含插件版本号。 */
    private static String welcomeHtml() {
        return "<html><div style='text-align:center;font-size:13px;line-height:2.0;color:#909090'>"
                + "<div style='font-size:16px;font-weight:bold;margin-bottom:10px;color:#707070'>Maven Search 使用方式 "
                + "<span style='font-size:12px;font-weight:normal;color:#909090'>v" + pluginVersion() + "</span></div>"
                + "输入关键词搜索 Maven 组件（自动选择延迟最低的仓库）<br/>"
                + "· 搜索框默认为空，点击下拉查看历史查询记录，下拉底部可清除历史<br/>"
                + "· 输入 artifactId / groupId，停止输入 350ms 自动搜索<br/>"
                + "· 点击结果进入版本页，双击版本自动复制 Maven XML（弹窗提示）<br/>"
                + "· 一键复制 Gradle 片段，右侧按钮可下载 jar<br/>"
                + "· Class 模式可按类名搜索（如 JSONObject）<br/>"
                + "· ⚙ 设置可添加自定义仓库地址并测试延迟<br/>"
                + "· 底部实时显示各仓库延迟，自动选用延迟最低的数据源"
                + "</div></html>";
    }

    /** 读取插件版本号（从插件描述符获取，避免每次升级手动改文案）。 */
    private static String pluginVersion() {
        try {
            com.intellij.openapi.extensions.PluginId pid =
                    com.intellij.openapi.extensions.PluginId.getId("com.dsh.maven-search");
            com.intellij.ide.plugins.IdeaPluginDescriptor d =
                    com.intellij.ide.plugins.PluginManagerCore.getPlugin(pid);
            if (d != null && d.getVersion() != null && !d.getVersion().isEmpty()) return d.getVersion();
        } catch (Throwable ignore) {
            // 回退到写死版本
        }
        return "1.4.5";
    }

    private static final String NO_RESULTS_HTML =
            "<html><div style='text-align:center;font-size:15px;color:#909090'>没有找到匹配的组件，换个关键词试试</div></html>";

    // ---------------- 仓库模型 ----------------

    static final class Repo {
        final String id;
        final String name;
        final String kind;    // coderead | central | generic
        final String baseUrl;
        long latencyMs = -1;
        boolean reachable = false;

        Repo(String id, String name, String kind, String baseUrl) {
            this.id = id;
            this.name = name;
            this.kind = kind;
            this.baseUrl = baseUrl;
        }
    }

    /** 根据 URL 判断仓库类型：coderead / search.maven.org 自动识别，其余为通用仓库。 */
    static String classifyKind(String url) {
        String u = url.toLowerCase();
        if (u.contains("coderead")) return "coderead";
        if (u.contains("search.maven.org")) return "central";
        return "generic";
    }

    /** 仓库显示名：内置仓库用友好名，通用仓库用去掉协议/尾斜杠的地址。 */
    static String displayName(String url, String kind) {
        if ("coderead".equals(kind)) return "mvn.coderead.cn";
        if ("central".equals(kind)) return "Maven Central";
        String u = url;
        int scheme = u.indexOf("://");
        if (scheme >= 0) u = u.substring(scheme + 3);
        while (u.endsWith("/")) u = u.substring(0, u.length() - 1);
        return u;
    }

    /** 仓库延迟探测地址。 */
    static String probeUrlFor(Repo r) {
        if ("coderead".equals(r.kind)) return r.baseUrl + "/search?keyword=fastjson";
        if ("central".equals(r.kind)) return r.baseUrl + "/solrsearch/select?q=fastjson&rows=1&wt=json";
        return r.baseUrl;
    }

    static boolean isSearchable(Repo r) {
        return "coderead".equals(r.kind) || "central".equals(r.kind);
    }

    // ---------------- UI ----------------

    private final Project project;
    private final JComboBox<String> searchCombo = new JComboBox<>();
    private String lastQuery = "";
    private final JComboBox<String> modeBox = new JComboBox<>(new String[]{"JAR", "Class"});
    private final JButton settingsBtn = new JButton("⚙");
    private final JButton backBtn = new JButton("←");
    private final DefaultListModel<CodereadClient.Artifact> resultsModel = new DefaultListModel<>();
    private final JList<CodereadClient.Artifact> resultsList = new JList<>(resultsModel);
    private final DefaultListModel<CodereadClient.VersionInfo> versionsModel = new DefaultListModel<>();
    private final JList<CodereadClient.VersionInfo> versionsList = new JList<>(versionsModel);
    private final JLabel crumb = new JLabel(" ");
    private final JLabel status = new JLabel("输入关键词搜索 Maven 组件（自动选择延迟最低的仓库）");
    private final JLabel repoStatus = new JLabel(" ");
    private final JTextArea descArea = new JTextArea(3, 40);
    private final JTextArea mavenArea = new JTextArea(5, 40);
    private final JTextArea gradleArea = new JTextArea(3, 40);
    private final JButton copyMavenBtn = new JButton("复制 Maven XML");
    private final JButton copyGradleBtn = new JButton("复制 Gradle");
    private final JButton downloadBtn = new JButton("下载 jar");
    private final CardLayout cards = new CardLayout();
    private final JPanel cardHost = new JPanel(cards);
    private String currentCard = "search";
    private final JPanel settingsCard = new JPanel(new BorderLayout());
    private RepoSettingsPanel settingsPage;
    // 搜索页内部：空态（使用方式/无结果）与结果列表
    private final CardLayout searchCards = new CardLayout();
    private final JPanel searchInner = new JPanel(searchCards);
    private final JPanel emptyPanel = new JPanel(new BorderLayout());
    private final JLabel emptyLabel = new JLabel(welcomeHtml(), SwingConstants.CENTER);
    private CodereadClient.Artifact current;
    private int searchSeq = 0;
    private boolean searchPending = false;
    private boolean searchBusy = false;
    private boolean latencyTesting = false;
    private final Timer debounce = new Timer(350, e -> requestSearch());

    // ---------------- 仓库列表与延迟 ----------------
    private final List<Repo> repos = new ArrayList<>();
    private Repo activeRepo;   // 延迟最小的仓库（数据源 / 下载）
    private Repo searchRepo;   // 延迟最小的可搜索仓库（搜索用）
    private final Timer rotationTimer = new Timer(1000, null);
    private final int[] rotationIdx = {0};
    private List<Repo> rotationList = new ArrayList<>();

    public SearchPanel(Project project) {
        this.project = project;
        mavenArea.setEditable(false);
        gradleArea.setEditable(false);
        // Maven 介绍：自动换行、无边框，位于版本列表下方、Maven XML 框上方
        descArea.setEditable(false);
        descArea.setLineWrap(true);
        descArea.setWrapStyleWord(true);
        descArea.setOpaque(false);
        descArea.setFocusable(false);
        descArea.setBorder(null);
        resultsList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        versionsList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        repoStatus.setFont(repoStatus.getFont().deriveFont(Font.PLAIN, 11f));

        setLayout(new BorderLayout(0, 4));

        JPanel top = new JPanel(new GridBagLayout());
        GridBagConstraints tg = new GridBagConstraints();
        tg.gridy = 0;
        tg.insets = new Insets(2, 4, 2, 4);
        tg.gridx = 0;
        top.add(backBtn, tg);   // 返回按钮在搜索框左侧，仅显示符号
        tg.gridx = 1;
        tg.weightx = 1;         // 搜索框占满剩余宽度（弹性伸缩，不再挤掉其它按钮）
        tg.fill = GridBagConstraints.HORIZONTAL;
        top.add(searchCombo, tg);
        tg.weightx = 0;
        tg.fill = GridBagConstraints.NONE;
        tg.gridx = 2;
        top.add(modeBox, tg);
        tg.gridx = 3;
        tg.anchor = GridBagConstraints.EAST;
        top.add(settingsBtn, tg); // 设置按钮在最右侧，仅显示符号
        add(top, BorderLayout.NORTH);

        JPanel searchCard = new JPanel(new BorderLayout());
        emptyPanel.setBorder(BorderFactory.createEmptyBorder(24, 24, 24, 24));
        emptyPanel.add(emptyLabel, BorderLayout.CENTER);
        searchInner.add(emptyPanel, "empty");
        searchInner.add(new JScrollPane(resultsList), "list");
        searchCards.show(searchInner, "empty"); // 首次打开：居中显示使用方式
        searchCard.add(searchInner, BorderLayout.CENTER);

        JPanel snippets = new JPanel(new GridBagLayout());
        GridBagConstraints g = new GridBagConstraints();
        g.gridx = 0;
        g.fill = GridBagConstraints.HORIZONTAL;
        g.weightx = 1;
        g.insets = new Insets(2, 2, 2, 2);
        g.gridy = 0;
        snippets.add(descArea, g); // Maven 介绍：版本复制框下方、Maven XML 框上方
        g.gridy = 1;
        snippets.add(new JLabel("Maven XML"), g);
        g.gridy = 2;
        snippets.add(new JScrollPane(mavenArea), g);
        g.gridy = 3;
        snippets.add(new JLabel("Gradle"), g);
        g.gridy = 4;
        snippets.add(new JScrollPane(gradleArea), g);
        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        btnRow.add(copyMavenBtn);
        btnRow.add(copyGradleBtn);
        btnRow.add(downloadBtn);
        g.gridy = 5;
        snippets.add(btnRow, g);

        JPanel detailCard = new JPanel(new BorderLayout(0, 4));
        detailCard.add(crumb, BorderLayout.NORTH);
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, new JScrollPane(versionsList), snippets);
        split.setResizeWeight(0.55);
        detailCard.add(split, BorderLayout.CENTER);

        cardHost.add(searchCard, "search");
        cardHost.add(detailCard, "detail");
        cardHost.add(settingsCard, "settings");
        add(cardHost, BorderLayout.CENTER);

        JPanel south = new JPanel(new BorderLayout(8, 0));
        south.add(status, BorderLayout.CENTER);
        south.add(repoStatus, BorderLayout.EAST);
        add(south, BorderLayout.SOUTH);

        debounce.setRepeats(false);
        backBtn.setToolTipText("返回");
        settingsBtn.setToolTipText("仓库设置");
        // 搜索框：可编辑下拉框，点击弹出历史查询；输入即搜索
        searchCombo.setEditable(true);
        searchCombo.setMaximumRowCount(12);
        // 搜索框宽度 36：用原型字符串撑宽（避免 setColumns 导致布局异常/搜索框消失）
        searchCombo.setPrototypeDisplayValue("123456789012345678901234567890123456");
        JTextField editor = (JTextField) searchCombo.getEditor().getEditorComponent();
        editor.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                // 点击搜索框弹出历史查询下拉列表
                if (searchCombo.getItemCount() > 0 && !searchCombo.isPopupVisible()) {
                    searchCombo.showPopup();
                }
            }
        });
        // 下拉中"清除历史"项：灰色居中样式
        searchCombo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean isSelected, boolean cellHasFocus) {
                JLabel l = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (SENTINEL.equals(value)) {
                    l.setForeground(new Color(0x909090));
                    l.setHorizontalAlignment(SwingConstants.CENTER);
                }
                return l;
            }
        });
        loadHistory();
        ensureSentinel(); // 下拉框最底部固定"清除历史"项
        setSearchText(""); // 打开工具搜索框默认为空（历史记录保留在下拉中）
        searchCombo.addActionListener(e -> {
            String cur = searchText();
            if (SENTINEL.equals(cur)) {
                clearHistory(); // 点击下拉底部的清除项：直接清空
                return;
            }
            recordHistory(cur); // 回车/选择历史项：记录并搜索
            requestSearch();
        });
        modeBox.addActionListener(e -> requestSearch());
        settingsBtn.addActionListener(e -> {
            if ("settings".equals(currentCard)) {
                returnFromSettings(); // 再点设置：返回一级页面（有修改先询问保存）
            } else {
                openSettingsPage();
            }
        });
        backBtn.addActionListener(e -> {
            if ("settings".equals(currentCard)) {
                returnFromSettings();
            } else {
                // 输入框有内容时：单击返回自动清空输入并回到使用方式提示页
                if (!searchText().isEmpty()) {
                    setSearchText("");
                    requestSearch(); // 立即清空结果并显示提示页
                }
                showCard("search");
                resultsList.clearSelection();
            }
        });
        // 输入即搜索：停止输入 350ms 后自动搜索
        editor.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                debounce.restart();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                debounce.restart();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                debounce.restart();
            }
        });
        // 点击任意结果行都进入二级复制页面（修复返回后再次点击同一行无反应）
        resultsList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (!SwingUtilities.isLeftMouseButton(e) || e.getClickCount() != 1) return;
                int row = resultsList.locationToIndex(e.getPoint());
                if (row < 0 || row >= resultsModel.getSize()) return;
                CodereadClient.Artifact a = resultsModel.getElementAt(row);
                if (a != null) openArtifact(a);
            }
        });
        versionsList.getSelectionModel().addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;
            CodereadClient.VersionInfo v = versionsList.getSelectedValue();
            if (v != null) showVersion(v);
        });
        // 双击版本自动复制 Maven XML（弹窗提示复制成功）
        versionsList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (!SwingUtilities.isLeftMouseButton(e) || e.getClickCount() != 2) return;
                int row = versionsList.locationToIndex(e.getPoint());
                if (row < 0 || row >= versionsModel.getSize()) return;
                CodereadClient.VersionInfo v = versionsModel.getElementAt(row);
                if (v != null) copyMavenXml(v);
            }
        });
        copyMavenBtn.addActionListener(e -> copy(mavenArea.getText()));
        copyGradleBtn.addActionListener(e -> copy(gradleArea.getText()));
        downloadBtn.addActionListener(e -> doDownload());

        // 延迟轮询显示计时器：每个仓库显示 1 秒
        rotationTimer.addActionListener(e -> {
            if (rotationIdx[0] < rotationList.size()) {
                Repo r = rotationList.get(rotationIdx[0]++);
                repoStatus.setText(r.reachable
                        ? "⏱ " + r.name + "：" + r.latencyMs + " ms"
                        : "❌ " + r.name + "：不可达");
                // 已输入内容且仍在测延迟 → 页面正中更新测试过程
                if (latencyTesting && !searchText().isEmpty()) {
                    emptyLabel.setText(latencyWaitHtml(rotationList, rotationIdx[0]));
                    searchCards.show(searchInner, "empty");
                }
            } else {
                rotationTimer.stop();
                selectBest(rotationList);
            }
        });

        loadRepos();
        // 首选项：是否在打开工具窗口时自动测试仓库延迟（默认开）
        if (isAutoTestEnabled()) {
            probeRepositories(); // 打开工具自动测试延迟
        } else {
            repoStatus.setText("自动测速已关闭（⚙ 设置中开启）");
            activeRepo = searchRepo;
        }
    }

    // ---------------- 仓库管理与延迟测试 ----------------

    private void loadRepos() {
        repos.clear();
        List<String> urls = new ArrayList<>();
        String saved = PropertiesComponent.getInstance().getValue(KEY_REPOS, "");
        if (saved != null && !saved.trim().isEmpty()) {
            for (String line : saved.split("\\n")) {
                String u = line.trim();
                if (!u.isEmpty()) urls.add(u);
            }
        }
        if (urls.isEmpty()) {
            for (String d : DEFAULT_REPOS) urls.add(d);
        }
        // 保证至少保留一个可搜索仓库，避免搜索不可用
        boolean hasSearchable = false;
        for (String u : urls) {
            String k = classifyKind(u);
            if ("coderead".equals(k) || "central".equals(k)) {
                hasSearchable = true;
                break;
            }
        }
        if (!hasSearchable) {
            urls.add(0, "http://mvn.coderead.cn");
            urls.add(1, "https://search.maven.org");
        }
        // 合并首选项中的额外默认仓库地址（去重，每次启动自动加载）
        for (String u : getExtraDefaultRepos()) {
            if (!u.isEmpty() && !urls.contains(u)) urls.add(u);
        }
        for (String u : urls) {
            String kind = classifyKind(u);
            repos.add(new Repo("r-" + repos.size(), displayName(u, kind), kind, u));
        }
        // 默认数据源：http://mvn.coderead.cn（存在时），否则取第一个可搜索仓库；
        // 之后仍会自动测速并按延迟择优
        searchRepo = null;
        for (Repo r : repos) {
            if ("coderead".equals(r.kind)) {
                searchRepo = r;
                break;
            }
        }
        if (searchRepo == null) {
            for (Repo r : repos) {
                if (isSearchable(r)) {
                    searchRepo = r;
                    break;
                }
            }
        }
        activeRepo = searchRepo;
    }

    /** 打开设置二级页面（面板内切换，非弹窗）。 */
    private void openSettingsPage() {
        settingsCard.removeAll();
        settingsPage = new RepoSettingsPanel(repos, () -> {
            applySettings(); // 保存首选项 + 仓库列表 + 重新加载仓库
            // 保存后留在设置页（自动延迟测试在设置页内完成），由 ⚙ / ← 返回
        });
        settingsCard.add(settingsPage, BorderLayout.CENTER);
        settingsCard.revalidate();
        settingsCard.repaint();
        showCard("settings");
    }

    /** 应用设置页的全部修改：首选项（额外默认仓库 + 自动测速开关）+ 仓库地址列表。 */
    private void applySettings() {
        saveExtraDefaultRepos(settingsPage.getExtraDefaultRepos());
        setAutoTestEnabled(settingsPage.isAutoTestEnabled());
        saveUserRepos(settingsPage.getRepoUrls());
        loadRepos();
        probeRepositories();
    }

    private void showCard(String name) {
        currentCard = name;
        cards.show(cardHost, name);
    }

    /** 从设置页返回一级页面；有未保存修改时先询问是否保存。 */
    private void returnFromSettings() {
        if (settingsPage != null && settingsPage.isDirty()) {
            int r = Messages.showYesNoCancelDialog(this,
                    "仓库设置已修改，是否保存？", "Maven 仓库设置", Messages.getQuestionIcon());
            if (r == Messages.YES) {
                applySettings();
                showCard("search");
            } else if (r == Messages.NO) {
                showCard("search");
            }
            // CANCEL：留在设置页
        } else {
            showCard("search");
        }
    }

    private void saveUserRepos(List<String> urls) {
        StringBuilder sb = new StringBuilder();
        for (String u : urls) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(u);
        }
        PropertiesComponent.getInstance().setValue(KEY_REPOS, sb.toString());
    }

    // ---------------- 首选项（额外默认仓库 / 自动延迟测试） ----------------

    /** 读取首选项：额外默认仓库地址（持久化，\n 分隔）。 */
    static List<String> getExtraDefaultRepos() {
        List<String> out = new ArrayList<>();
        String saved = PropertiesComponent.getInstance().getValue(KEY_EXTRA_REPOS, "");
        if (saved != null) {
            for (String line : saved.split("\\n")) {
                String u = line.trim();
                if (!u.isEmpty()) out.add(u);
            }
        }
        return out;
    }

    /** 保存首选项：额外默认仓库地址列表。 */
    static void saveExtraDefaultRepos(List<String> urls) {
        StringBuilder sb = new StringBuilder();
        for (String u : urls) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(u);
        }
        PropertiesComponent.getInstance().setValue(KEY_EXTRA_REPOS, sb.toString());
    }

    /** 读取首选项：打开工具窗口时是否自动测试仓库延迟（默认开）。 */
    static boolean isAutoTestEnabled() {
        return PropertiesComponent.getInstance().getBoolean(KEY_AUTO_TEST, true);
    }

    /** 保存首选项：打开工具窗口时是否自动测试仓库延迟。 */
    static void setAutoTestEnabled(boolean enabled) {
        PropertiesComponent.getInstance().setValue(KEY_AUTO_TEST, enabled);
    }

    // ---------------- 搜索历史 ----------------

    private String searchText() {
        Object o = searchCombo.getEditor().getItem();
        return o == null ? "" : String.valueOf(o).trim();
    }

    private void setSearchText(String s) {
        searchCombo.getEditor().setItem(s == null ? "" : s);
    }

    /**
     * Search Everywhere（双击 Shift）集成入口：填入关键词并立即触发搜索。
     * 若正在延迟测试，会显示等待提示，测速结束后自动搜索。
     */
    public void searchFromEverywhere(String keyword) {
        setSearchText(keyword == null ? "" : keyword.trim());
        searchSeq++;
        searchPending = true;
        showCard("search");
        requestSearch();
    }

    /** 从持久化配置加载历史查询。 */
    private void loadHistory() {
        String saved = PropertiesComponent.getInstance().getValue(KEY_HISTORY, "");
        if (saved == null || saved.trim().isEmpty()) return;
        for (String line : saved.split("\\n")) {
            String q = line.trim();
            if (!q.isEmpty() && !SENTINEL.equals(q)) searchCombo.addItem(q);
        }
    }

    /** 确保下拉框最底部固定"清除历史"项（唯一且位于末尾）。 */
    private void ensureSentinel() {
        for (int i = 0; i < searchCombo.getItemCount(); i++) {
            if (SENTINEL.equals(searchCombo.getItemAt(i))) {
                if (i != searchCombo.getItemCount() - 1) {
                    searchCombo.removeItemAt(i);
                    searchCombo.addItem(SENTINEL);
                }
                return;
            }
        }
        searchCombo.addItem(SENTINEL);
    }

    /** 清除全部历史查询记录（保留下拉底部的清除项，保留当前输入）。 */
    private void clearHistory() {
        try {
            LOG.info("clearHistory: items before = " + searchCombo.getItemCount());
            debounce.stop();
            searchCombo.setPopupVisible(false);
            DefaultComboBoxModel<String> m = (DefaultComboBoxModel<String>) searchCombo.getModel();
            m.removeAllElements();          // 清空下拉模型（历史项）
            searchCombo.addItem(SENTINEL);  // 下拉底部保留清除项
            searchCombo.setSelectedIndex(-1);
            String cur = searchText();
            searchCombo.getEditor().setItem(cur); // 保留当前输入内容
            debounce.stop();
            PropertiesComponent.getInstance().setValue(KEY_HISTORY, ""); // 清空持久化
            LOG.info("clearHistory: items after = " + searchCombo.getItemCount());
            status.setText("历史查询记录已清除");
        } catch (Throwable t) {
            LOG.error("clearHistory failed", t);
            status.setText("清除历史失败: " + t.getMessage());
        }
    }

    /** 记录一条历史查询（最新在前、去重、上限 20 条、持久化；不记录清除项）。 */
    private void recordHistory(String q) {
        if (q == null || q.trim().isEmpty() || SENTINEL.equals(q.trim())) return;
        final String t = q.trim();
        if (searchCombo.getItemCount() > 0 && t.equals(searchCombo.getItemAt(0))) return;
        searchCombo.insertItemAt(t, 0);
        for (int i = 1; i < searchCombo.getItemCount(); ) {
            String item = searchCombo.getItemAt(i);
            if (SENTINEL.equals(item)) {
                i++; // 跳过末位清除项
                continue;
            }
            if (t.equals(item)) searchCombo.removeItemAt(i);
            else i++;
        }
        // 真实历史上限 20 条（末位保留清除项）
        while (searchCombo.getItemCount() > 21) {
            searchCombo.removeItemAt(searchCombo.getItemCount() - 2);
        }
        ensureSentinel();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < searchCombo.getItemCount(); i++) {
            String item = searchCombo.getItemAt(i);
            if (SENTINEL.equals(item)) continue; // 清除项不持久化
            if (sb.length() > 0) sb.append('\n');
            sb.append(item);
        }
        PropertiesComponent.getInstance().setValue(KEY_HISTORY, sb.toString());
    }

    /** 在网络上测试全部仓库延迟，轮询显示（每个 1 秒），结束后切换为延迟最小的仓库。 */
    private void probeRepositories() {
        latencyTesting = true; // 延迟测试进行中：输入时页面正中提示等待
        repoStatus.setText("正在测试仓库延迟…");
        final List<Repo> snapshot = new ArrayList<>(repos);
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            for (Repo r : snapshot) {
                long ms = CodereadClient.measureLatency(probeUrlFor(r));
                r.latencyMs = ms;
                r.reachable = ms >= 0;
            }
            SwingUtilities.invokeLater(() -> {
                rotationIdx[0] = 0;
                rotationList = new ArrayList<>(snapshot);
                rotationTimer.restart();
            });
        });
    }

    private void selectBest(List<Repo> list) {
        latencyTesting = false; // 延迟测试结束
        Repo best = null;
        for (Repo r : list) {
            if (r.reachable && (best == null || r.latencyMs < best.latencyMs)) best = r;
        }
        if (best == null) {
            activeRepo = null;
            searchRepo = null;
            repoStatus.setText("❌ 所有仓库不可达，请检查网络或到设置中添加仓库");
            emptyLabel.setText(welcomeHtml());
            status.setText("输入关键词搜索 Maven 组件（自动选择延迟最低的仓库）");
            return;
        }
        activeRepo = best;
        // 搜索源：延迟最小的可搜索仓库
        searchRepo = null;
        for (Repo r : list) {
            if (r.reachable && isSearchable(r) && (searchRepo == null || r.latencyMs < searchRepo.latencyMs)) {
                searchRepo = r;
            }
        }
        if (searchRepo == null) searchRepo = best;
        String text = "✅ 数据源: " + best.name + " (" + best.latencyMs + " ms)";
        if (searchRepo != best) text += "｜搜索: " + searchRepo.name;
        repoStatus.setText(text);
        // 若已有输入，用新数据源自动搜索；否则恢复默认欢迎页
        if (!searchText().isEmpty()) requestSearch();
        else {
            emptyLabel.setText(welcomeHtml());
            status.setText("输入关键词搜索 Maven 组件（自动选择延迟最低的仓库）");
        }
    }

    /** 延迟测试过程中的页面正中提示（标题 + 各仓库测试进度）。 */
    private static String latencyWaitHtml(List<Repo> list, int measured) {
        StringBuilder sb = new StringBuilder(
                "<html><div style='text-align:center;font-size:15px;line-height:2.0;color:#909090'>"
                        + "<div style='font-size:16px;font-weight:bold;color:#707070;margin-bottom:6px'>正在进行延迟测试，请稍后…</div>");
        for (int i = 0; i < list.size(); i++) {
            Repo r = list.get(i);
            String mark;
            if (i < measured) mark = (r.reachable ? "✓ " + r.latencyMs + " ms" : "✗ 不可达");
            else if (i == measured) mark = "⏱ 测试中…";
            else mark = "…";
            sb.append(r.name).append(": ").append(mark).append("<br/>");
        }
        sb.append("</div></html>");
        return sb.toString();
    }

    // ---------------- 搜索 ----------------

    /** 输入变化 / 手动触发时调用：防抖计时器会合并连续输入，自动搜索。 */
    private void requestSearch() {
        searchSeq++;
        searchPending = true;
        final String kw = searchText();
        if (SENTINEL.equals(kw)) { // 忽略下拉清除项文本，避免误搜索
            searchPending = false;
            return;
        }
        if (kw.isEmpty()) {
            searchPending = false;
            searchBusy = false;
            resultsModel.clear();
            emptyLabel.setText(welcomeHtml());
            searchCards.show(searchInner, "empty");
            status.setText("输入关键词搜索 Maven 组件（自动选择延迟最低的仓库）");
            return;
        }
        // 延迟测试未结束时：页面正中提示等待并显示测试过程，结束后自动搜索
        if (latencyTesting) {
            searchPending = false;
            emptyLabel.setText(rotationList.isEmpty()
                    ? "<html><div style='text-align:center;font-size:15px;color:#909090'>正在进行延迟测试，请稍后…</div></html>"
                    : latencyWaitHtml(rotationList, rotationIdx[0]));
            searchCards.show(searchInner, "empty");
            showCard("search");
            return;
        }
        if (searchBusy) {
            return; // 有搜索在跑；结束后 startSearch 会自动补发最新请求
        }
        startSearch();
    }

    private void startSearch() {
        if (!searchPending) return;
        searchPending = false;
        final String kw = searchText();
        if (kw.isEmpty()) return;
        final Repo src = searchRepo;
        if (src == null || !isSearchable(src)) {
            searchBusy = false;
            status.setText("当前没有可用的搜索仓库，请到 ⚙ 设置 中检查仓库可达性");
            return;
        }
        final int seq = searchSeq;
        final boolean cls = "Class".equals(modeBox.getSelectedItem());
        if ("central".equals(src.kind) && cls) {
            searchBusy = false;
            status.setText("Maven Central 不支持类名搜索，请切换 JAR 模式或选择 mvn.coderead.cn");
            return;
        }
        searchBusy = true;
        status.setText("正在搜索…（" + src.name + "）");
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            try {
                final List<CodereadClient.Artifact> list = searchWith(src, kw, cls);
                SwingUtilities.invokeLater(() -> {
                    if (seq != searchSeq) return; // 过期响应，丢弃
                    lastQuery = kw; // 记录本次成功的查询，供点击结果时写入历史
                    resultsModel.clear();
                    for (CodereadClient.Artifact a : list) resultsModel.addElement(a);
                    showCard("search");
                    if (list.isEmpty()) {
                        emptyLabel.setText(NO_RESULTS_HTML);
                        searchCards.show(searchInner, "empty");
                    } else {
                        searchCards.show(searchInner, "list");
                    }
                    status.setText("找到 " + list.size() + " 个结果（" + src.name + "）");
                    searchBusy = false;
                    if (searchPending) startSearch();
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    if (seq != searchSeq) return;
                    status.setText("搜索失败: " + ex.getMessage());
                    searchBusy = false;
                    if (searchPending) startSearch();
                });
            }
        });
    }

    private static List<CodereadClient.Artifact> searchWith(Repo src, String kw, boolean cls) throws IOException {
        if ("central".equals(src.kind)) return CentralClient.search(kw);
        return CodereadClient.search(kw, cls);
    }

    // ---------------- 版本详情 ----------------

    private void openArtifact(CodereadClient.Artifact a) {
        current = a;
        // 点击了某次查询的结果 → 将该查询记入历史
        if (!lastQuery.isEmpty()) recordHistory(lastQuery);
        crumb.setText(a.artifactId + "  (" + a.groupId + ")");
        versionsModel.clear();
        mavenArea.setText("");
        gradleArea.setText("");
        descArea.setText("");
        status.setText("正在加载版本…");
        showCard("detail");
        final Repo src = activeRepo != null ? activeRepo : searchRepo;
        if (src == null) {
            status.setText("没有可用的数据源仓库");
            return;
        }
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            try {
                final CodereadClient.VersionDetail d = versionsWith(src, a.groupId, a.artifactId);
                SwingUtilities.invokeLater(() -> {
                    versionsModel.clear();
                    for (CodereadClient.VersionInfo v : d.versions) versionsModel.addElement(v);
                    descArea.setText(d.description); // Maven 介绍显示在版本框与 XML 框之间
                    status.setText("共 " + d.versions.size() + " 个版本（" + src.name + "）");
                    // 版本自动更新：自动选中第一个版本，依赖片段立即显示
                    if (!versionsModel.isEmpty()) {
                        versionsList.setSelectedIndex(0);
                    }
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> status.setText("加载版本失败: " + ex.getMessage()));
            }
        });
    }

    private static CodereadClient.VersionDetail versionsWith(Repo src, String g, String a) throws IOException {
        try {
            if ("coderead".equals(src.kind)) return CodereadClient.versions(g, a);
            if ("generic".equals(src.kind)) return CodereadClient.versionsFromMetadataAt(src.baseUrl, g, a);
            return CentralClient.versions(g, a);
        } catch (IOException e) {
            // 当前仓库取版本失败（如 portal 类地址无标准布局）时，回退到官方仓库元数据
            return CodereadClient.versionsFromMetadata(g, a);
        }
    }

    private void showVersion(CodereadClient.VersionInfo v) {
        mavenArea.setText(v.maven);
        gradleArea.setText(v.gradle);
    }

    /** 双击版本时自动复制 Maven XML 片段，并弹窗提示复制成功。 */
    private void copyMavenXml(CodereadClient.VersionInfo v) {
        if (v == null || v.maven == null || v.maven.isEmpty()) return;
        Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new StringSelection(v.maven), null);
        String coord = (current == null ? "" : current.artifactId + ":") + v.version;
        status.setText("已复制 Maven XML（" + coord + "）");
        Messages.showInfoMessage(this, "已复制 Maven XML：" + coord, "复制成功");
    }

    private void copy(String text) {
        if (text == null || text.isEmpty()) return;
        Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new StringSelection(text), null);
        status.setText("已复制到剪贴板");
    }

    // ---------------- 下载 ----------------

    private void doDownload() {
        if (current == null) return;
        CodereadClient.VersionInfo v = versionsList.getSelectedValue();
        if (v == null) {
            status.setText("请先选择一个版本");
            return;
        }
        if (!downloadBtn.isEnabled()) return;
        downloadBtn.setEnabled(false);
        status.setText("正在下载 " + v.version + " …");
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            try {
                String preferred = (activeRepo != null && "generic".equals(activeRepo.kind)) ? activeRepo.baseUrl : null;
                String[] urls = CodereadClient.jarUrls(preferred, current.groupId, current.artifactId, v.version);
                String home = System.getProperty("user.home");
                File dir = new File(home, "Downloads");
                if (!dir.exists() && !dir.mkdirs()) dir = new File(home);
                File f = new File(dir, safeName(current.artifactId) + "-" + safeName(v.version) + ".jar");
                IOException last = null;
                String used = null;
                for (String url : urls) {
                    try (OutputStream out = new FileOutputStream(f)) {
                        CodereadClient.downloadTo(url, out);
                        used = url;
                        break;
                    } catch (IOException e) {
                        last = e;
                    }
                }
                if (used == null) throw last;
                final String path = f.getAbsolutePath();
                final String srcName = used.contains("aliyun") ? "阿里云镜像"
                        : (used.contains("repo1") ? "Maven Central 官方" : used);
                SwingUtilities.invokeLater(() -> {
                    status.setText("已下载: " + path + "（来源 " + srcName + "）");
                    downloadBtn.setEnabled(true);
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    status.setText("下载失败: " + ex.getMessage() + "（可复制 URL 自行下载）");
                    downloadBtn.setEnabled(true);
                });
            }
        });
    }

    private static String safeName(String s) {
        return s == null ? "artifact" : s.replaceAll("[\\\\/:*?\"<>|]", "_");
    }
}
