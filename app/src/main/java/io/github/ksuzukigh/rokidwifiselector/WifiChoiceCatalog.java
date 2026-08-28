package io.github.ksuzukigh.rokidwifiselector;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class WifiChoiceCatalog {
    static final class Entry {
        final int networkId;
        final String ssid;

        Entry(int networkId, String ssid) {
            this.networkId = networkId;
            this.ssid = ssid;
        }
    }

    private WifiChoiceCatalog() {}

    static String normalize(String raw) {
        if (raw == null) return "";
        String value = raw.trim();
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            value = value.substring(1, value.length() - 1);
        }
        if ("<unknown ssid>".equalsIgnoreCase(value)) return "";
        return value;
    }

    static List<Entry> nearbyAlternatives(
            Collection<Entry> saved,
            Set<String> nearby,
            String currentSsid) {
        String current = normalize(currentSsid);
        Map<String, Entry> unique = new LinkedHashMap<>();
        for (Entry item : saved) {
            String ssid = normalize(item.ssid);
            if (ssid.isEmpty() || ssid.equals(current) || !nearby.contains(ssid)) continue;
            unique.putIfAbsent(ssid, new Entry(item.networkId, ssid));
        }
        List<Entry> result = new ArrayList<>(unique.values());
        result.sort(Comparator.comparing(item -> item.ssid.toLowerCase(Locale.ROOT)));
        return result;
    }
}
