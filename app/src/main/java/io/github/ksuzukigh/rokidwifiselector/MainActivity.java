package io.github.ksuzukigh.rokidwifiselector;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class MainActivity extends Activity {
    private static final String TAG = "RokidWifiSelector";
    private static final int LOCATION_REQUEST = 20;
    private static final long REFRESH_MS = 1200;
    private static final long CONNECT_TIMEOUT_MS = 15000;
    private static final int FLING_DISTANCE = 80;
    private static final int FLING_VELOCITY = 120;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<WifiChoiceCatalog.Entry> choices = new ArrayList<>();
    private WifiManager wifi;
    private TextView currentName;
    private TextView selectionLabel;
    private TextView selectionName;
    private TextView position;
    private TextView guidance;
    private int selectedIndex;
    private boolean receiverRegistered;
    private boolean wifiEnableRequested;
    private boolean switching;
    private String switchingTarget = "";
    private String previousSsid = "";
    private int previousNetworkId = -1;
    private long switchStartedAt;

    private final BroadcastReceiver scanReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (!switching) refresh();
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        wifi = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        makeUi();
    }

    @Override protected void onStart() {
        super.onStart();
        registerReceiver(scanReceiver, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));
        receiverRegistered = true;
    }

    @Override protected void onResume() {
        super.onResume();
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            showPermissionExplanation();
            requestPermissions(
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_REQUEST);
            return;
        }
        startReading();
    }

    @Override protected void onPause() {
        handler.removeCallbacksAndMessages(null);
        super.onPause();
    }

    @Override protected void onStop() {
        if (receiverRegistered) {
            unregisterReceiver(scanReceiver);
            receiverRegistered = false;
        }
        super.onStop();
    }

    @Override public void onRequestPermissionsResult(
            int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_REQUEST
                && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startReading();
        } else {
            currentName.setText(R.string.permission_needed);
            selectionLabel.setText(R.string.permission_reason);
            selectionName.setText(R.string.permission_action);
            position.setText("");
            guidance.setText(R.string.permission_retry);
        }
    }

    private void startReading() {
        handler.removeCallbacksAndMessages(null);
        if (wifi == null) {
            showFailure(getString(R.string.wifi_unavailable));
            return;
        }
        if (!wifi.isWifiEnabled()) {
            currentName.setText(R.string.enabling_wifi);
            selectionLabel.setText("");
            selectionName.setText("");
            position.setText("");
            guidance.setText(R.string.please_wait);
            if (!wifiEnableRequested) {
                wifiEnableRequested = true;
                try {
                    wifi.setWifiEnabled(true);
                } catch (RuntimeException error) {
                    Log.e(TAG, "Could not enable Wi-Fi", error);
                }
            }
            handler.postDelayed(this::startReading, REFRESH_MS);
            return;
        }
        wifiEnableRequested = false;
        try {
            wifi.startScan();
        } catch (RuntimeException error) {
            Log.w(TAG, "Could not request a fresh Wi-Fi scan", error);
        }
        refresh();
    }

    @SuppressWarnings("deprecation")
    private void refresh() {
        if (switching) {
            pollSwitch();
            return;
        }
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            showPermissionExplanation();
            return;
        }

        WifiInfo info = wifi.getConnectionInfo();
        String current = info == null ? "" : WifiChoiceCatalog.normalize(info.getSSID());
        currentName.setText(current.isEmpty() ? getString(R.string.checking_connection) : current);

        List<WifiChoiceCatalog.Entry> saved = new ArrayList<>();
        List<WifiConfiguration> configured;
        List<ScanResult> scanResults;
        try {
            configured = wifi.getConfiguredNetworks();
            scanResults = wifi.getScanResults();
        } catch (SecurityException error) {
            Log.w(TAG, "Wi-Fi read permission is unavailable", error);
            showPermissionExplanation();
            return;
        }
        if (configured != null) {
            for (WifiConfiguration item : configured) {
                saved.add(new WifiChoiceCatalog.Entry(item.networkId, item.SSID));
            }
        }

        Set<String> nearby = new HashSet<>();
        if (scanResults != null) {
            for (ScanResult result : scanResults) {
                String ssid = WifiChoiceCatalog.normalize(result.SSID);
                if (!ssid.isEmpty()) nearby.add(ssid);
            }
        }

        String previouslySelected = choices.isEmpty()
                ? "" : choices.get(Math.min(selectedIndex, choices.size() - 1)).ssid;
        choices.clear();
        choices.addAll(WifiChoiceCatalog.nearbyAlternatives(saved, nearby, current));
        selectedIndex = indexOf(previouslySelected);
        if (selectedIndex < 0) selectedIndex = 0;
        Log.i(TAG, "Catalog refreshed: saved=" + saved.size()
                + " nearby=" + nearby.size() + " choices=" + choices.size());
        renderSelection();

        handler.removeCallbacksAndMessages(null);
        handler.postDelayed(this::refresh, REFRESH_MS);
    }

    private int indexOf(String ssid) {
        for (int index = 0; index < choices.size(); index++) {
            if (choices.get(index).ssid.equals(ssid)) return index;
        }
        return -1;
    }

    private void renderSelection() {
        if (choices.isEmpty()) {
            selectionLabel.setText(R.string.nearby_saved_wifi);
            selectionName.setText(R.string.no_alternative);
            position.setText("");
            guidance.setText(R.string.register_in_hi_rokid);
            return;
        }
        WifiChoiceCatalog.Entry choice = choices.get(selectedIndex);
        selectionLabel.setText(R.string.switch_target);
        selectionName.setText(choice.ssid);
        position.setText(getString(R.string.position_format, selectedIndex + 1, choices.size()));
        guidance.setText(choices.size() > 1
                ? R.string.choose_and_connect
                : R.string.connect_once);
    }

    private void moveSelection(int direction) {
        if (switching || choices.size() < 2) return;
        selectedIndex = (selectedIndex + direction + choices.size()) % choices.size();
        Log.i(TAG, "Selection moved to index=" + selectedIndex);
        renderSelection();
    }

    @SuppressWarnings("deprecation")
    private void connectSelected() {
        if (switching || choices.isEmpty()) return;
        WifiChoiceCatalog.Entry choice = choices.get(selectedIndex);
        Log.i(TAG, "Connect selected: networkId=" + choice.networkId);
        WifiInfo info = wifi.getConnectionInfo();
        previousSsid = info == null ? "" : WifiChoiceCatalog.normalize(info.getSSID());
        previousNetworkId = info == null ? -1 : info.getNetworkId();
        switching = true;
        switchingTarget = choice.ssid;
        switchStartedAt = System.currentTimeMillis();

        selectionLabel.setText(R.string.connecting);
        selectionName.setText(choice.ssid);
        position.setText("");
        guidance.setText(R.string.please_wait);

        boolean accepted;
        try {
            accepted = wifi.enableNetwork(choice.networkId, true);
            if (accepted) wifi.reconnect();
        } catch (RuntimeException error) {
            Log.e(TAG, "Wi-Fi switch request failed", error);
            accepted = false;
        }
        if (!accepted) {
            switching = false;
            showFailure(getString(R.string.switch_start_failed));
            handler.postDelayed(this::startReading, 2500);
            return;
        }
        handler.removeCallbacksAndMessages(null);
        handler.postDelayed(this::pollSwitch, 400);
    }

    @SuppressWarnings("deprecation")
    private void pollSwitch() {
        WifiInfo info = wifi.getConnectionInfo();
        String actual = info == null ? "" : WifiChoiceCatalog.normalize(info.getSSID());
        if (switchingTarget.equals(actual)) {
            switching = false;
            currentName.setText(actual);
            selectionLabel.setText(R.string.connected_success);
            selectionName.setText("");
            position.setText("");
            guidance.setText(R.string.returning_to_apps);
            Log.i(TAG, "Connected to selected saved Wi-Fi");
            handler.postDelayed(this::finish, 2500);
            return;
        }
        if (System.currentTimeMillis() - switchStartedAt >= CONNECT_TIMEOUT_MS) {
            restorePreviousNetwork();
            return;
        }
        handler.postDelayed(this::pollSwitch, 400);
    }

    @SuppressWarnings("deprecation")
    private void restorePreviousNetwork() {
        switching = false;
        if (previousNetworkId >= 0) {
            try {
                wifi.enableNetwork(previousNetworkId, true);
                wifi.reconnect();
            } catch (RuntimeException error) {
                Log.e(TAG, "Could not restore the previous Wi-Fi", error);
            }
        }
        currentName.setText(previousSsid.isEmpty() ? getString(R.string.original_network) : previousSsid);
        selectionLabel.setText(R.string.connection_failed);
        selectionName.setText(R.string.restoring_original);
        position.setText("");
        guidance.setText(R.string.retry_later);
        handler.postDelayed(this::startReading, 3500);
    }

    private void showPermissionExplanation() {
        currentName.setText(R.string.permission_title);
        selectionLabel.setText(R.string.permission_prompt);
        selectionName.setText(R.string.permission_action);
        position.setText("");
        guidance.setText(R.string.permission_privacy);
    }

    private void showFailure(String message) {
        currentName.setText(message);
        selectionLabel.setText("");
        selectionName.setText("");
        position.setText("");
        guidance.setText(R.string.double_tap_to_return);
    }

    private void makeUi() {
        ClickablePanel panel = new ClickablePanel(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER);
        panel.setPadding(30, 24, 30, 24);
        panel.setBackgroundColor(Color.BLACK);
        panel.setClickable(true);
        panel.setFocusable(true);

        TextView title = text(getString(R.string.app_name), 22, Color.rgb(120, 255, 155));
        panel.addView(title);

        TextView currentLabel = text(getString(R.string.connected_label), 15, Color.LTGRAY);
        currentLabel.setPadding(0, 22, 0, 4);
        panel.addView(currentLabel);

        currentName = text(getString(R.string.checking), 27, Color.WHITE);
        panel.addView(currentName);

        selectionLabel = text("", 15, Color.LTGRAY);
        selectionLabel.setPadding(0, 28, 0, 4);
        panel.addView(selectionLabel);

        selectionName = text("", 25, Color.rgb(255, 235, 110));
        panel.addView(selectionName);

        position = text("", 14, Color.LTGRAY);
        position.setPadding(0, 8, 0, 0);
        panel.addView(position);

        guidance = text("", 15, Color.LTGRAY);
        guidance.setPadding(0, 24, 0, 0);
        panel.addView(guidance);

        GestureDetector detector = new GestureDetector(
                this,
                new GestureDetector.SimpleOnGestureListener() {
                    @Override public boolean onDown(MotionEvent event) {
                        return true;
                    }

                    @Override public boolean onSingleTapConfirmed(MotionEvent event) {
                        panel.performClick();
                        return true;
                    }

                    @Override public boolean onFling(
                            MotionEvent first,
                            MotionEvent second,
                            float velocityX,
                            float velocityY) {
                        if (first == null || second == null) return false;
                        float distance = second.getX() - first.getX();
                        if (Math.abs(distance) < FLING_DISTANCE
                                || Math.abs(velocityX) < FLING_VELOCITY) return false;
                        moveSelection(distance > 0 ? -1 : 1);
                        return true;
                    }
                });
        panel.setOnClickListener(view -> connectSelected());
        panel.setOnTouchListener((view, event) -> detector.onTouchEvent(event));
        setContentView(panel);
        panel.requestFocus();
    }

    private TextView text(String value, int size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setGravity(Gravity.CENTER);
        return view;
    }

    private static final class ClickablePanel extends LinearLayout {
        ClickablePanel(Context context) {
            super(context);
        }

        @Override public boolean performClick() {
            super.performClick();
            return true;
        }
    }

    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        Log.i(TAG, "Key received: " + keyCode);
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
            moveSelection(-1);
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            moveSelection(1);
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            connectSelected();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }
}
