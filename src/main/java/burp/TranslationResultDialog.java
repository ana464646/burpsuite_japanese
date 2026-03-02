package burp;

import javax.swing.*;
import java.awt.*;

public class TranslationResultDialog {

    public static void showDialog(String translatedText) {
        SwingUtilities.invokeLater(() -> {
            JTextArea textArea = new JTextArea(translatedText);
            textArea.setLineWrap(true);
            textArea.setWrapStyleWord(true);
            textArea.setEditable(false);
            textArea.setMargin(new Insets(10, 10, 10, 10));
            textArea.setFont(new Font("SansSerif", Font.PLAIN, 14));

            JScrollPane scrollPane = new JScrollPane(textArea);
            int width = Math.min(600, Math.max(400, translatedText.length() * 2));
            int height = Math.min(400, Math.max(200, translatedText.length() / 2));
            scrollPane.setPreferredSize(new Dimension(width, height));

            JOptionPane.showMessageDialog(null, scrollPane, "Japanese Translation", JOptionPane.INFORMATION_MESSAGE);
        });
    }
}
