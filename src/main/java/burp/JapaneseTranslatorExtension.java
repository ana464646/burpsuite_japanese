package burp;

import burp.api.montoya.BurpExtension;
import burp.api.montoya.MontoyaApi;

public class JapaneseTranslatorExtension implements BurpExtension {
    @Override
    public void initialize(MontoyaApi api) {
        api.extension().setName("Japanese Translator");
        
        IssueTranslationTab issueTab = new IssueTranslationTab(api);

        api.userInterface().registerSuiteTab("日本語（診断結果）", issueTab.uiComponent());
        api.userInterface().registerContextMenuItemsProvider(new TranslationContextMenuProvider(api, issueTab));

        api.scanner().registerAuditIssueHandler(issueTab);
        
        api.logging().logToOutput("Japanese Translator extension loaded successfully.");
    }
}
