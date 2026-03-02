package burp;

import burp.api.montoya.BurpExtension;
import burp.api.montoya.MontoyaApi;

public class JapaneseTranslatorExtension implements BurpExtension {
    @Override
    public void initialize(MontoyaApi api) {
        api.extension().setName("Japanese Translator");
        
        // Register the context menu provider
        api.userInterface().registerContextMenuItemsProvider(
                new TranslationContextMenuProvider(api)
        );
        
        api.logging().logToOutput("Japanese Translator extension loaded successfully.");
    }
}
