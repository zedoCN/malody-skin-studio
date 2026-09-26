package top.zedo.skin.v;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Fields whose source and runtime values have the same known unit. */
final class VRuntimeModuleChanges {
    private static final float DIMENSION_TOLERANCE = 0.01f;

    private VRuntimeModuleChanges() { }

    static List<Change> between(VRuntimeSnapshot.Module module) {
        VRuntimeSnapshot.Source source = module.source();
        VRuntimeSnapshot.Runtime runtime = module.runtime();
        List<Change> changes = new ArrayList<>(3);
        if (source.hasImage()) {
            addDimension(changes, "宽", source.imageWidth(), source.imageWidthUnit(), runtime.width());
            addDimension(changes, "高", source.imageHeight(), source.imageHeightUnit(), runtime.height());
        }
        if (source.alpha() != runtime.alpha())
            changes.add(new Change("透明度", source.alpha(), runtime.alpha(), false));
        return List.copyOf(changes);
    }

    private static void addDimension(List<Change> changes, String label, float source,
                                     String unit, float runtime) {
        if ("Unit".equals(unit) && source > 0 && Math.abs(runtime - source) > DIMENSION_TOLERANCE)
            changes.add(new Change(label, source, runtime, true));
    }

    record Change(String label, float source, float runtime, boolean dimension) {
        String description() {
            if (!dimension) return String.format(Locale.ROOT, "%s %.0f → %.0f", label, source, runtime);
            return String.format(Locale.ROOT, "%s %.2f → %.2f Unit (×%.3f)",
                    label, source, runtime, runtime / source);
        }
    }
}
