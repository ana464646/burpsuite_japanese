package burp;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.ui.contextmenu.ContextMenuEvent;
import burp.api.montoya.ui.contextmenu.ContextMenuItemsProvider;
import burp.api.montoya.ui.contextmenu.MessageEditorHttpRequestResponse;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class TranslationContextMenuProvider implements ContextMenuItemsProvider {

    private final MontoyaApi api;

    public TranslationContextMenuProvider(MontoyaApi api) {
        this.api = api;
    }

    @Override
    public List<Component> provideMenuItems(ContextMenuEvent event) {
        List<Component> menuItems = new ArrayList<>();

        Optional<MessageEditorHttpRequestResponse> editorOpt = event.messageEditorRequestResponse();
        if (editorOpt.isPresent()) {
            MessageEditorHttpRequestResponse editor = editorOpt.get();
            if (editor.selectionOffsets().isPresent()) {
                JMenuItem translateItem = new JMenuItem("Translate to Japanese");
                translateItem.addActionListener(e -> {
                    String text = getSelectedText(editor);
                    if (text != null && !text.trim().isEmpty()) {
                        translateAndShowResult(text);
                    }
                });
                menuItems.add(translateItem);
            }
        }

        return menuItems;
    }

    private String getSelectedText(MessageEditorHttpRequestResponse editor) {
        if (editor.selectionOffsets().isPresent()) {
            int start = editor.selectionOffsets().get().startIndexInclusive();
            int end = editor.selectionOffsets().get().endIndexExclusive();
            if (editor.selectionContext() == MessageEditorHttpRequestResponse.SelectionContext.REQUEST) {
                return editor.requestResponse().request().toByteArray().subArray(start, end).toString();
            } else if (editor.selectionContext() == MessageEditorHttpRequestResponse.SelectionContext.RESPONSE && editor.requestResponse().response() != null) {
                return editor.requestResponse().response().toByteArray().subArray(start, end).toString();
            }
        }
        return null;
    }

    private void translateAndShowResult(String text) {
        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() {
                try {
                    return GoogleTranslator.translate(text);
                } catch (Exception ex) {
                    api.logging().logToError("Translation failed: " + ex.getMessage());
                    return "Error: " + ex.getMessage();
                }
            }

            @Override
            protected void done() {
                try {
                    String result = get();
                    TranslationResultDialog.showDialog(result);
                } catch (Exception e) {
                    api.logging().logToError("Failed to display translation: " + e.getMessage());
                }
            }
        }.execute();
    }
}
