package burp;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.scanner.audit.AuditIssueHandler;
import burp.api.montoya.scanner.audit.issues.AuditIssue;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class IssueTranslationTab implements AuditIssueHandler {
    private final MontoyaApi api;

    private final JPanel root;
    private final DefaultTableModel model;
    private final JTable table;
    private final JTextArea originalArea;
    private final JTextArea translatedArea;

    private final List<AuditIssue> issues = new ArrayList<>();

    public IssueTranslationTab(MontoyaApi api) {
        this.api = api;

        model = new DefaultTableModel(new Object[]{"重大度", "確信度", "対象", "名称(EN)"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        table = new JTable(model);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

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

        JButton refresh = new JButton("更新（Site map の Issue を再取得）");
        refresh.addActionListener(e -> reloadFromSiteMap());

        JButton translateSelected = new JButton("選択Issueを日本語化");
        translateSelected.addActionListener(e -> translateSelectedIssue());

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.add(refresh);
        top.add(translateSelected);

        root = new JPanel(new BorderLayout(8, 8));
        root.add(top, BorderLayout.NORTH);
        root.add(mainSplit, BorderLayout.CENTER);

        api.userInterface().applyThemeToComponent(root);

        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                showSelectedOriginal();
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
                    for (AuditIssue i : loaded) addIssue(i);
                } catch (Exception e) {
                    api.logging().logToError("Failed to update issue list: " + e.getMessage());
                }
            }
        }.execute();
    }

    private void addIssue(AuditIssue issue) {
        issues.add(issue);
        model.addRow(new Object[]{
                severity(issue),
                confidence(issue),
                safe(issue.baseUrl()),
                safe(issue.name())
        });
    }

    private void showSelectedOriginal() {
        int row = table.getSelectedRow();
        if (row < 0 || row >= issues.size()) {
            originalArea.setText("");
            translatedArea.setText("");
            return;
        }
        AuditIssue issue = issues.get(row);
        originalArea.setText(buildOriginal(issue));
        translatedArea.setText("");
    }

    private void translateSelectedIssue() {
        int row = table.getSelectedRow();
        if (row < 0 || row >= issues.size()) return;
        AuditIssue issue = issues.get(row);

        translatedArea.setText("翻訳中...");
        new SwingWorker<String, Void>() {
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
                    translatedArea.setText(get());
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
}

