package burp;

import burp.api.montoya.scanner.audit.issues.AuditIssue;
import burp.api.montoya.scanner.audit.issues.AuditIssueConfidence;
import burp.api.montoya.scanner.audit.issues.AuditIssueSeverity;

public final class AuditIssueJapaneseTranslator {
    private AuditIssueJapaneseTranslator() {
    }

    public static String translateName(AuditIssue issue) throws Exception {
        String nameEn = normalizeText(issue.name());
        if (nameEn.isBlank()) {
            return "";
        }
        return GoogleTranslator.translate(nameEn);
    }

    public static String translateIssue(AuditIssue issue) throws Exception {
        String nameEn = normalizeText(issue.name());
        String detailEn = normalizeText(issue.detail());
        String remediationEn = normalizeText(issue.remediation());

        String backgroundEn = "";
        String typicalRemediationEn = "";
        try {
            if (issue.definition() != null) {
                backgroundEn = normalizeText(issue.definition().background());
                typicalRemediationEn = normalizeText(issue.definition().remediation());
            }
        } catch (Exception ignored) {
            // definition() が取れない/空でも継続
        }

        String nameJa = translateName(issue);
        String detailJa = detailEn.isBlank() ? "" : GoogleTranslator.translate(detailEn);
        String remediationJa = remediationEn.isBlank() ? "" : GoogleTranslator.translate(remediationEn);
        String backgroundJa = backgroundEn.isBlank() ? "" : GoogleTranslator.translate(backgroundEn);
        String typicalRemediationJa = typicalRemediationEn.isBlank() ? "" : GoogleTranslator.translate(typicalRemediationEn);

        StringBuilder sb = new StringBuilder();
        sb.append("【診断結果（日本語）】\n");
        sb.append("重大度: ").append(severityJa(issue.severity())).append("\n");
        sb.append("確信度: ").append(confidenceJa(issue.confidence())).append("\n");
        sb.append("対象: ").append(safe(issue.baseUrl())).append("\n\n");

        if (!nameJa.isBlank()) {
            sb.append("■名称（日本語）\n");
            sb.append(nameJa).append("\n\n");
        }

        if (!detailJa.isBlank()) {
            sb.append("■詳細（日本語）\n");
            sb.append(detailJa).append("\n\n");
        }

        if (!backgroundJa.isBlank()) {
            sb.append("■背景（日本語）\n");
            sb.append(backgroundJa).append("\n\n");
        }

        if (!remediationJa.isBlank() || !typicalRemediationJa.isBlank()) {
            sb.append("■対策（日本語）\n");
            if (!remediationJa.isBlank()) {
                sb.append(remediationJa).append("\n\n");
            }
            if (!typicalRemediationJa.isBlank()) {
                sb.append(typicalRemediationJa).append("\n\n");
            }
        }

        return sb.toString().trim();
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    private static String normalizeText(String s) {
        String v = safe(s);
        if (v.isEmpty()) {
            return v;
        }
        // 改行にしたいタグ
        v = v.replaceAll("(?i)<br\\s*/?>", "\n");
        v = v.replaceAll("(?i)</p>", "\n");
        // 開始タグは削除
        v = v.replaceAll("(?i)<p[^>]*>", "");
        // 残りのタグをすべて除去
        v = v.replaceAll("(?s)<[^>]+>", "");
        return v.trim();
    }

    private static String severityJa(AuditIssueSeverity s) {
        if (s == null) return "不明";
        return switch (s) {
            case HIGH -> "高";
            case MEDIUM -> "中";
            case LOW -> "低";
            case INFORMATION -> "情報";
            case FALSE_POSITIVE -> "誤検知";
        };
    }

    private static String confidenceJa(AuditIssueConfidence c) {
        if (c == null) return "不明";
        return switch (c) {
            case CERTAIN -> "確実";
            case FIRM -> "高い";
            case TENTATIVE -> "低い";
        };
    }
}

