package com.dotdrive.launcher;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class FavoriteSlotsTest {
    private final List<String> base = Arrays.asList("a","b","c","d");
    private final Set<String> installed = new HashSet<>(Arrays.asList("a","b","c","d","x"));
    @Test public void replacementKeepsChosenSlot() {
        assertEquals(Arrays.asList("a","b","x","d"), FavoriteSlots.apply(base,Arrays.asList("","","x",""),installed));
    }
    @Test public void swappingExistingAppsDoesNotDuplicate() {
        assertEquals(Arrays.asList("b","a","c","d"), FavoriteSlots.apply(base,Arrays.asList("b","a","",""),installed));
    }
    @Test public void missingOrDuplicateSavedAppsDoNotCorruptGrid() {
        assertEquals(base, FavoriteSlots.apply(base,Arrays.asList("gone","","",""),installed));
        assertEquals(Arrays.asList("b","a","c","d"), FavoriteSlots.apply(base,Arrays.asList("b","b","",""),installed));
    }
}
