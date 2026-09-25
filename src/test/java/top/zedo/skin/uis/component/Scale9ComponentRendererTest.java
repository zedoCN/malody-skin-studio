package top.zedo.skin.uis.component;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class Scale9ComponentRendererTest {
    @Test
    void keepsSourceBorderPixelsWhenStretchingTheCenter() {
        assertArrayEquals(new double[]{0, 10, 80, 90},
                Scale9ComponentRenderer.destinationCuts(new int[]{0, 10, 20, 30}, 90));
        assertArrayEquals(new double[]{0, 5, 85, 90},
                Scale9ComponentRenderer.destinationCuts(new int[]{0, 5, 25, 30}, 90));
    }

    @Test
    void smallTargetKeepsCutsOrdered() {
        assertArrayEquals(new double[]{0, 10, 10, 20},
                Scale9ComponentRenderer.destinationCuts(new int[]{0, 10, 90, 100}, 20));
    }
}
