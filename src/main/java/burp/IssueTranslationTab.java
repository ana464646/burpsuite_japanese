package burp;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.scanner.audit.AuditIssueHandler;
import burp.api.montoya.scanner.audit.issues.AuditIssue;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class IssueTranslationTab implements AuditIssueHandler {
    private final MontoyaApi api;

    private final JPanel root;
    private final DefaultTableModel model;
    private final JTable table;
    private final TableRowSorter<DefaultTableModel> sorter;
    private final JTextArea originalArea;
    private final JTextArea translatedArea;

    private final List<AuditIssue> issues = new ArrayList<>();
    private final Map<AuditIssue, String> translationCache = new IdentityHashMap<>();

    private final Timer translateDebounceTimer;
    private SwingWorker<String, Void> currentTranslateWorker;
    private AuditIssue pendingTranslateIssue;
    private AuditIssue currentlyDisplayedIssue;

    public IssueTranslationTab(MontoyaApi api) {
        this.api = api;

        model = new DefaultTableModel(new Object[]{"重大度", "確信度", "対象", "名称(EN)", "名称(JP)"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        table = new JTable(model);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        sorter = new TableRowSorter<>(model);
        sorter.setComparator(0, Comparator.comparingInt(IssueTranslationTab::severityRank));
        sorter.setComparator(1, Comparator.comparingInt(IssueTranslationTab::confidenceRank));
        table.setRowSorter(sorter);

        originalArea = textArea();
        translatedArea = textArea();

        JSplitPane detailsSplit = new JSplitPane(
                JSplitPane.HORIZONTAL_SPLIT,
                wrap("原文", new JScrollPane(originalArea)),
                wrap("日本語", new JScrollPane(translatedArea))
        );
        detailsSplit.setResizeWeight(0.5);

        JSplitPane mainSplit = new JSplitPane(
                JSplitPane.VERTICAL_SPLIT,
                new JScrollPane(table),
                detailsSplit
        );
        mainSplit.setResizeWeight(0.45);

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        JButton refresh = new JButton("更新（Site map の Issue を再取得）");
        refresh.addActionListener(e -> reloadFromSiteMap());
        JButton summarizeAll = new JButton("全Issueを日本語レポート化");
        summarizeAll.addActionListener(e -> summarizeAllIssues());

        JLabel hostLabel = new JLabel("対象ホスト:");
        JTextField hostFilterField = new JTextField(18);
        hostFilterField.setToolTipText("対象URLに含まれる文字で絞り込み（例: example.com）");
        JLabel searchLabel = new JLabel("検索:");
        JTextField searchField = new JTextField(18);
        searchField.setToolTipText("表のどの列でも検索（重大度・名称など）");

        DocumentListener filterListener = new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) { applyFilter(hostFilterField, searchField); }
            @Override
            public void removeUpdate(DocumentEvent e) { applyFilter(hostFilterField, searchField); }
            @Override
            public void changedUpdate(DocumentEvent e) { applyFilter(hostFilterField, searchField); }
        };
        hostFilterField.getDocument().addDocumentListener(filterListener);
        searchField.getDocument().addDocumentListener(filterListener);

        top.add(refresh);
        top.add(summarizeAll);
        top.add(new JSeparator(SwingConstants.VERTICAL));
        top.add(hostLabel);
        top.add(hostFilterField);
        top.add(searchLabel);
        top.add(searchField);

        root = new JPanel(new BorderLayout(8, 8));
        root.add(top, BorderLayout.NORTH);
        root.add(mainSplit, BorderLayout.CENTER);

        api.userInterface().applyThemeToComponent(root);

        translateDebounceTimer = new Timer(450, e -> startTranslateForPendingIssue());
        translateDebounceTimer.setRepeats(false);

        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                handleSelectionChanged();
            }
        });

        reloadFromSiteMap();
    }

    public Component uiComponent() {
        return root;
    }

    public void focus() {
        // Montoya 2023.12.1 では suite tab への「選択」APIが無いので、ここは no-op にする
        root.requestFocusInWindow();
    }

    @Override
    public void handleNewAuditIssue(AuditIssue issue) {
        if (issue == null) return;
        SwingUtilities.invokeLater(() -> addIssue(issue));
    }

    private void reloadFromSiteMap() {
        new SwingWorker<List<AuditIssue>, Void>() {
            @Override
            protected List<AuditIssue> doInBackground() {
                try {
                    return api.siteMap().issues();
                } catch (Exception e) {
                    api.logging().logToError("Failed to load issues from site map: " + e.getMessage());
                    return List.of();
                }
            }

            @Override
            protected void done() {
                try {
                    List<AuditIssue> loaded = get();
                    issues.clear();
                    model.setRowCount(0);
                    translationCache.clear();
                    translatedArea.setText("");
                    originalArea.setText("");
                    for (AuditIssue i : loaded) addIssue(i);
                } catch (Exception e) {
                    api.logging().logToError("Failed to update issue list: " + e.getMessage());
                }
            }
        }.execute();
    }

    private void addIssue(AuditIssue issue) {
        issues.add(issue);
        int row = model.getRowCount();
        model.addRow(new Object[]{
                severity(issue),
                confidence(issue),
                safe(issue.baseUrl()),
                safe(issue.name()),
                ""
        });
        scheduleNameTranslation(issue, row);
    }

    private void handleSelectionChanged() {
        AuditIssue issue = getSelectedIssue();
        if (issue == null) {
            originalArea.setText("");
            translatedArea.setText("");
            return;
        }

        currentlyDisplayedIssue = issue;
        originalArea.setText(buildOriginal(issue));
        translatedArea.setText("翻訳待ち...");

        pendingTranslateIssue = issue;
        translateDebounceTimer.restart();
    }

    private void applyFilter(JTextField hostFilterField, JTextField searchField) {
        String host = hostFilterField.getText().trim();
        String search = searchField.getText().trim();
        if (host.isEmpty() && search.isEmpty()) {
            sorter.setRowFilter(null);
            return;
        }
        sorter.setRowFilter(new RowFilter<DefaultTableModel, Integer>() {
            @Override
            public boolean include(Entry<? extends DefaultTableModel, ? extends Integer> entry) {
                if (!host.isEmpty()) {
                    String target = String.valueOf(entry.getValue(2));
                    if (!target.toLowerCase().contains(host.toLowerCase())) {
                        return false;
                    }
                }
                if (!search.isEmpty()) {
                    boolean found = false;
                    for (int i = 0; i <= 4; i++) {
                        String cell = String.valueOf(entry.getValue(i));
                        if (cell.toLowerCase().contains(search.toLowerCase())) {
                            found = true;
                            break;
                        }
                    }
                    if (!found) {
                        return false;
                    }
                }
                return true;
            }
        });
    }

    private AuditIssue getSelectedIssue() {
        int viewRow = table.getSelectedRow();
        if (viewRow < 0) return null;
        int modelRow = table.convertRowIndexToModel(viewRow);
        if (modelRow < 0 || modelRow >= issues.size()) return null;
        return issues.get(modelRow);
    }

    private void startTranslateForPendingIssue() {
        AuditIssue issue = pendingTranslateIssue;
        if (issue == null || issue != currentlyDisplayedIssue) return;

        String cached = translationCache.get(issue);
        if (cached != null) {
            translatedArea.setText(cached);
            return;
        }

        if (currentTranslateWorker != null) {
            currentTranslateWorker.cancel(true);
        }

        translatedArea.setText("翻訳中...");
        currentTranslateWorker = new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() {
                try {
                    return AuditIssueJapaneseTranslator.translateIssue(issue);
                } catch (Exception e) {
                    api.logging().logToError("Issue translation failed: " + e.getMessage());
                    return "Error: " + e.getMessage();
                }
            }

            @Override
            protected void done() {
                try {
                    if (isCancelled() || issue != currentlyDisplayedIssue) return;
                    String result = get();
                    translationCache.put(issue, result);
                    translatedArea.setText(result);
                } catch (Exception e) {
                    if (issue != currentlyDisplayedIssue) return;
                    translatedArea.setText("Error: " + e.getMessage());
                }
            }
        };
        currentTranslateWorker.execute();
    }

    private void scheduleNameTranslation(AuditIssue issue, int modelRow) {
        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() {
                try {
                    return AuditIssueJapaneseTranslator.translateName(issue);
                } catch (Exception e) {
                    api.logging().logToError("Issue name translation failed: " + e.getMessage());
                    return "";
                }
            }

            @Override
            protected void done() {
                try {
                    String nameJa = get();
                    if (nameJa == null || nameJa.isBlank()) return;
                    if (modelRow < 0 || modelRow >= model.getRowCount()) return;
                    if (issues.get(modelRow) != issue) return;
                    model.setValueAt(nameJa, modelRow, 4);
                } catch (Exception ignored) {
                }
            }
        }.execute();
    }

    private void summarizeAllIssues() {
        if (issues.isEmpty()) {
            translatedArea.setText("Issue がありません。Scanner を実行してから試してください。");
            return;
        }
        final int total = issues.size();
        translatedArea.setText("スキャン結果を日本語で羅列しています... 0/" + total);

        new SwingWorker<String, Integer>() {
            @Override
            protected String doInBackground() {
                StringBuilder sb = new StringBuilder();
                sb.append("【Burp スキャン結果（日本語・羅列）】\n\n");
                int index = 1;
                int done = 0;
                for (AuditIssue issue : issues) {
                    try {
                        String body = translationCache.get(issue);
                        if (body == null || body.isBlank()) {
                            body = AuditIssueJapaneseTranslator.translateIssue(issue);
                            translationCache.put(issue, body);
                        }
                        sb.append("=== Issue ").append(index++).append(" ===\n");
                        sb.append(body).append("\n\n");
                        done++;
                        publish(done);
                    } catch (Exception e) {
                        api.logging().logToError("Failed to translate issue: " + e.getMessage());
                    }
                }
                return sb.toString().trim();
            }

            @Override
            protected void process(List<Integer> chunks) {
                if (chunks == null || chunks.isEmpty()) return;
                int d = chunks.get(chunks.size() - 1);
                translatedArea.setText("スキャン結果を日本語で羅列しています... " + d + "/" + total);
            }

            @Override
            protected void done() {
                try {
                    String result = get();
                    translatedArea.setText(result);
                    TranslationResultDialog.showDialog(result);
                } catch (Exception e) {
                    translatedArea.setText("Error: " + e.getMessage());
                }
            }
        }.execute();
    }

    private static JTextArea textArea() {
        JTextArea ta = new JTextArea();
        ta.setLineWrap(true);
        ta.setWrapStyleWord(true);
        ta.setEditable(false);
        ta.setFont(new Font("SansSerif", Font.PLAIN, 12));
        return ta;
    }

    private static JComponent wrap(String title, JComponent component) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder(title));
        panel.add(component, BorderLayout.CENTER);
        return panel;
    }

    private static String buildOriginal(AuditIssue issue) {
        StringBuilder sb = new StringBuilder();
        sb.append("Name: ").append(safe(issue.name())).append("\n");
        sb.append("Severity: ").append(safe(issue.severity() == null ? "" : issue.severity().name())).append("\n");
        sb.append("Confidence: ").append(safe(issue.confidence() == null ? "" : issue.confidence().name())).append("\n");
        sb.append("Base URL: ").append(safe(issue.baseUrl())).append("\n\n");
        if (!safe(issue.detail()).isBlank()) {
            sb.append("Detail:\n").append(issue.detail()).append("\n\n");
        }
        if (!safe(issue.remediation()).isBlank()) {
            sb.append("Remediation:\n").append(issue.remediation()).append("\n\n");
        }
        try {
            if (issue.definition() != null && !safe(issue.definition().background()).isBlank()) {
                sb.append("Background:\n").append(issue.definition().background()).append("\n\n");
            }
        } catch (Exception ignored) {
        }
        return sb.toString().trim();
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    private static String severity(AuditIssue issue) {
        if (issue == null || issue.severity() == null) return "不明";
        return switch (issue.severity()) {
            case HIGH -> "高";
            case MEDIUM -> "中";
            case LOW -> "低";
            case INFORMATION -> "情報";
            case FALSE_POSITIVE -> "誤検知";
        };
    }

    private static String confidence(AuditIssue issue) {
        if (issue == null || issue.confidence() == null) return "不明";
        return switch (issue.confidence()) {
            case CERTAIN -> "確実";
            case FIRM -> "高い";
            case TENTATIVE -> "低い";
        };
    }

    private static int severityRank(String s) {
        if (s == null) return 0;
        return switch (s) {
            case "高" -> 50;
            case "中" -> 40;
            case "低" -> 30;
            case "情報" -> 20;
            case "誤検知" -> 10;
            default -> 0;
        };
    }

    private static int confidenceRank(String s) {
        if (s == null) return 0;
        return switch (s) {
            case "確実" -> 30;
            case "高い" -> 20;
            case "低い" -> 10;
            default -> 0;
        };
    }
}

