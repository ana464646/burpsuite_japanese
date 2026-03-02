package burp;

import burp.api.montoya.scanner.audit.issues.AuditIssue;
import burp.api.montoya.scanner.audit.issues.AuditIssueConfidence;
import burp.api.montoya.scanner.audit.issues.AuditIssueSeverity;

public final class AuditIssueJapaneseTranslator {
    private AuditIssueJapaneseTranslator() {
    }

    public static String translateName(AuditIssue issue) throws Exception {
        String nameEn = safe(issue.name());
        if (nameEn.isBlank()) {
            return "";
        }
        return GoogleTranslator.translate(nameEn);
    }

    public static String translateIssue(AuditIssue issue) throws Exception {
        String nameEn = safe(issue.name());
        String detailEn = safe(issue.detail());
        String remediationEn = safe(issue.remediation());

        String backgroundEn = "";
        String typicalRemediationEn = "";
        try {
            if (issue.definition() != null) {
                backgroundEn = safe(issue.definition().background());
                typicalRemediationEn = safe(issue.definition().remediation());
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

        if (!nameEn.isBlank()) {
            sb.append("■名称\n");
            sb.append("- EN: ").append(nameEn).append("\n");
            sb.append("- JA: ").append(nameJa).append("\n\n");
        }

        if (!detailEn.isBlank()) {
            sb.append("■詳細\n");
            sb.append("- EN:\n").append(detailEn).append("\n\n");
            sb.append("- JA:\n").append(detailJa).append("\n\n");
        }

        if (!backgroundEn.isBlank()) {
            sb.append("■背景\n");
            sb.append("- EN:\n").append(backgroundEn).append("\n\n");
            sb.append("- JA:\n").append(backgroundJa).append("\n\n");
        }

        if (!remediationEn.isBlank() || !typicalRemediationEn.isBlank()) {
            sb.append("■対策\n");
            if (!remediationEn.isBlank()) {
                sb.append("- EN(個別):\n").append(remediationEn).append("\n\n");
                sb.append("- JA(個別):\n").append(remediationJa).append("\n\n");
            }
            if (!typicalRemediationEn.isBlank()) {
                sb.append("- EN(一般):\n").append(typicalRemediationEn).append("\n\n");
                sb.append("- JA(一般):\n").append(typicalRemediationJa).append("\n\n");
            }
        }

        return sb.toString().trim();
    }

    private static String safe(String s) {
        return s == null ? "" : s;
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

