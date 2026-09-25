package top.zedo.skin.uis;

import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class UISFrameTest {
    @Test
    void frameFollowsPreviewTimeAndCanRewind() {
        Image first = new WritableImage(1, 1);
        Image second = new WritableImage(1, 1);
        Image third = new WritableImage(1, 1);
        UISFrame frame = new UISFrame();
        frame.frames = List.of(first, second, third);
        frame.interval = 100;

        frame.update(250);
        assertSame(third, frame.getCurrentFrame());
        frame.update(50);
        assertSame(first, frame.getCurrentFrame());
        frame.update(350);
        assertSame(first, frame.getCurrentFrame());
    }
}
