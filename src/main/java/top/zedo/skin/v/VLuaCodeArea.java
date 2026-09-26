package top.zedo.skin.v;

import javafx.animation.PauseTransition;
import javafx.util.Duration;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;
import org.fxmisc.richtext.model.StyleSpansBuilder;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

/** Editable Lua source with lexical coloring and line numbers. */
final class VLuaCodeArea extends CodeArea {
    private final PauseTransition highlightDelay = new PauseTransition(Duration.millis(120));

    VLuaCodeArea() {
        getStyleClass().add("v-lua-code");
        setParagraphGraphicFactory(LineNumberFactory.get(this));
        setWrapText(false);
        highlightDelay.setOnFinished(_ -> highlight());
        textProperty().addListener((_, _, _) -> highlightDelay.playFromStart());
    }

    void setSourceText(String source) {
        if (!source.equals(getText())) replaceText(0, getLength(), source);
        highlightDelay.stop();
        highlight();
    }

    private void highlight() {
        String source = getText();
        StyleSpansBuilder<Collection<String>> styles = new StyleSpansBuilder<>();
        int last = 0;
        for (VLuaSyntax.Token token : VLuaSyntax.scan(source)) {
            styles.add(Collections.emptyList(), token.start() - last);
            styles.add(List.of(token.style()), token.end() - token.start());
            last = token.end();
        }
        styles.add(Collections.emptyList(), source.length() - last);
        setStyleSpans(0, styles.create());
    }
}
