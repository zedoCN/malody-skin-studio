package top.zedo.skin.v;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VEditHistoryTest {
    @Test
    void redoBranchIsClearedByNewEditAndPreviewDoesNotAddStep() {
        VEditHistory<String> history = new VEditHistory<>();
        history.reset("original");
        history.record("first", null);
        history.replaceCurrent("first previewed");
        history.record("second", null);
        assertEquals("first previewed", history.undo());
        history.record("alternative", null);
        assertFalse(history.canRedo());
        assertEquals("first previewed", history.undo());
        assertEquals("original", history.undo());
        assertFalse(history.canUndo());
    }

    @Test
    void oldSnapshotsAreBounded() {
        VEditHistory<Integer> history = new VEditHistory<>();
        history.reset(0);
        for (int i = 1; i <= 150; i++) history.record(i, null);
        int steps = 0;
        while (history.canUndo()) {
            history.undo();
            steps++;
        }
        assertEquals(100, steps);
        assertEquals(51, history.redo());
    }
}
