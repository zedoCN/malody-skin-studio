package top.zedo.skin.v;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Bounded snapshots for edits that span V metadata, modules, and Lua. */
final class VEditHistory<T> {
    private static final int MAX_STATES = 101;
    private static final long TYPE_BURST_NANOS = 700_000_000L;
    private final List<T> states = new ArrayList<>();
    private int position;
    private Object mergeGroup;
    private long lastEditNanos;

    void reset(T initial) {
        states.clear();
        states.add(Objects.requireNonNull(initial));
        position = 0;
        breakGroup();
    }

    boolean initialized() { return !states.isEmpty(); }
    boolean canUndo() { return position > 0; }
    boolean canRedo() { return position + 1 < states.size(); }

    void record(T next, Object group) {
        Objects.requireNonNull(next);
        if (!initialized()) throw new IllegalStateException("V 编辑历史尚未初始化");
        if (next.equals(states.get(position))) return;
        long now = System.nanoTime();
        boolean merge = group != null && group == mergeGroup && canUndo()
                && !canRedo() && now - lastEditNanos <= TYPE_BURST_NANOS;
        states.subList(position + 1, states.size()).clear();
        if (merge) states.set(position, next);
        else {
            states.add(next);
            position++;
            if (states.size() > MAX_STATES) {
                states.removeFirst();
                position--;
            }
        }
        mergeGroup = group;
        lastEditNanos = now;
    }

    /** Refresh a snapshot after a preview or selection change without creating an edit. */
    void replaceCurrent(T state) {
        if (!initialized()) return;
        states.set(position, Objects.requireNonNull(state));
    }

    T undo() {
        if (!canUndo()) return null;
        breakGroup();
        return states.get(--position);
    }

    T redo() {
        if (!canRedo()) return null;
        breakGroup();
        return states.get(++position);
    }

    void breakGroup() {
        mergeGroup = null;
        lastEditNanos = 0;
    }
}
