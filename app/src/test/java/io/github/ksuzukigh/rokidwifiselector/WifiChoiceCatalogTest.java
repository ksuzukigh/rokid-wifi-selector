package io.github.ksuzukigh.rokidwifiselector;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import org.junit.Test;

public final class WifiChoiceCatalogTest {
    @Test public void normalizeRemovesAndroidQuotesAndUnknownValue() {
        assertEquals("home", WifiChoiceCatalog.normalize("\"home\""));
        assertEquals("", WifiChoiceCatalog.normalize("<unknown ssid>"));
        assertEquals("", WifiChoiceCatalog.normalize(null));
    }

    @Test public void alternativesAreNearbyUniqueAndExcludeCurrent() {
        List<WifiChoiceCatalog.Entry> saved = Arrays.asList(
                new WifiChoiceCatalog.Entry(1, "\"home\""),
                new WifiChoiceCatalog.Entry(1, "\"home\""),
                new WifiChoiceCatalog.Entry(2, "\"phone\""),
                new WifiChoiceCatalog.Entry(2, "\"phone\""),
                new WifiChoiceCatalog.Entry(3, "\"hotel\""));

        List<WifiChoiceCatalog.Entry> result = WifiChoiceCatalog.nearbyAlternatives(
                saved,
                new HashSet<>(Arrays.asList("home", "phone")),
                "home");

        assertEquals(1, result.size());
        assertEquals(2, result.get(0).networkId);
        assertEquals("phone", result.get(0).ssid);
    }

    @Test public void alternativesAreSortedByName() {
        List<WifiChoiceCatalog.Entry> result = WifiChoiceCatalog.nearbyAlternatives(
                Arrays.asList(
                        new WifiChoiceCatalog.Entry(2, "zeta"),
                        new WifiChoiceCatalog.Entry(1, "Alpha")),
                new HashSet<>(Arrays.asList("Alpha", "zeta")),
                "elsewhere");

        assertEquals("Alpha", result.get(0).ssid);
        assertEquals("zeta", result.get(1).ssid);
    }
}
