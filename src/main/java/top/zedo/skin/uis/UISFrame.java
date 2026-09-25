package top.zedo.skin.uis;

import javafx.scene.image.Image;

import java.util.List;

public class UISFrame {
    List<Image> frames;

    public List<Image> getFrames() {
        return frames;
    }

    double interval;
    int currentIndex;
    boolean loop = true;

    public UISFrame() {
        frames = null;
    }

    public UISFrame(UISComponent component, String name) {
        this.interval = component.getDouble("interval", 30);
        frames = component.getImageList(name);
    }

    @Override
    public String toString() {
        return "UISFrame{" +
                "frames=" + frames +
                ", interval=" + interval +
                ", loop=" + loop +
                '}';
    }

    public void setCurrentFrameTo(int index) {
        currentIndex = index;
    }

    public boolean update(long time) {
        if (frames == null || frames.isEmpty() || interval <= 0) return false;
        int next = time < 0 ? 0 : (int) Math.min(Integer.MAX_VALUE, Math.floor(time / interval));
        if (loop) next %= frames.size();
        else next = Math.min(next, frames.size() - 1);
        boolean changed = currentIndex != next;
        currentIndex = next;
        return changed && currentIndex == frames.size() - 1;
    }

    public Image getCurrentFrame() {
        if (frames == null) return null;
        Image result = null;
        if (currentIndex < frames.size() && currentIndex >= 0) {
            result = frames.get(currentIndex);
        }
        return result;
    }

    public Image getFrame(int index) {
        if (frames == null) return null;
        Image result = null;
        if (index < frames.size() && index >= 0) {
            result = frames.get(index);
        }
        return result;
    }
}
