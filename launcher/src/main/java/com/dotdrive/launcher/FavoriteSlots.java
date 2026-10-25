package com.dotdrive.launcher;

import java.util.*;

/** Favorites retain their chosen positions, including when replacing an automatic suggestion. */
public final class FavoriteSlots {
    private FavoriteSlots() {}
    public static List<String> apply(List<String> baseline, List<String> saved, Set<String> installed) {
        List<String> result = new ArrayList<>(baseline);
        Set<String> used = new HashSet<>();
        for (int slot = 0; slot < Math.min(saved.size(), result.size()); slot++) {
            String pkg = saved.get(slot);
            if (!installed.contains(pkg) || !used.add(pkg)) continue;
            int other = result.indexOf(pkg);
            String previous = result.get(slot);
            result.set(slot, pkg);
            if (other >= 0 && other != slot) result.set(other, previous);
        }
        return result;
    }
}
