package com.giggletech.router;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.nsd.*;
import android.os.*;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import java.util.*;

public final class MainActivity extends Activity {
    private static final int BG = 0xff101411, CARD = 0xff1c231d, GREEN = 0xffbbf56a, TEXT = 0xfff0f4ec, MUTED = 0xffaab7a6;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Settings settings;
    private LinearLayout page, devices;
    private TextView state, detail, packetLabel, logText, endpoint, message;
    private Button start, add, connection, findHardware;
    private HardwareDiscovery hardwareDiscovery;
    private final Map<String, ProgressBar> meters = new HashMap<>();
    private final Map<String, TextView> deviceStatus = new HashMap<>();
    private NsdManager.DiscoveryListener discovery;
    private boolean destroyed;
    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            if (state != null) {
                boolean active = RouterService.running;
                boolean busy = active || RouterService.testing;
                update(state, active ? "Feel the connection." : "Your world.\nMore tangible.");
                update(detail, RouterService.status);
                String action = RouterService.testing ? "STOP TEST" : active ? "STOP ROUTING" : "START ROUTING";
                if (!start.getText().toString().equals(action)) { start.setText(action); start.setBackground(tint(active ? 0xffedb5a4 : GREEN, 18)); }
                update(endpoint, active ? "PHONE RECEIVER   " + RouterService.endpoint : "LOCAL WI-FI  /  OSC + OSCQUERY");
                update(packetLabel, String.format(Locale.US, "%d packets received", RouterService.packets));
                update(message, RouterService.lastMessage);
                update(logText, RouterService.logs());
                add.setEnabled(!busy); connection.setEnabled(!busy); findHardware.setEnabled(!busy);
                for (Device d : settings.devices) {
                    int value = RouterService.levels.getOrDefault(d.ip, 0);
                    if (meters.containsKey(d.ip)) meters.get(d.ip).setProgress(value);
                    if (deviceStatus.containsKey(d.ip)) update(deviceStatus.get(d.ip), active ? RouterService.reachability.getOrDefault(d.ip, "Checking…") + "  ·  Motor " + value + "/255" : "Ready  ·  " + Math.round(d.power) + "% power limit");
                }
            }
            handler.postDelayed(this, 400);
        }
    };
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved); settings = SettingsStore.load(this); draw();
    }
    @Override protected void onResume() { super.onResume(); handler.post(refresh); }
    @Override protected void onPause() { handler.removeCallbacks(refresh); super.onPause(); }
    @Override protected void onDestroy() { destroyed = true; stopDiscovery(); if (hardwareDiscovery != null) hardwareDiscovery.close(); handler.removeCallbacksAndMessages(null); super.onDestroy(); }
    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    private static void update(TextView view, String text) { if (!view.getText().toString().equals(text)) view.setText(text); }
    private GradientDrawable tint(int color, int radius) { GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); return d; }
    private LinearLayout column() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private TextView text(String value, int size, int color) {
        TextView t = new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(color); t.setPadding(0, dp(5), 0, dp(5)); return t;
    }
    private void gap(LinearLayout layout, int size) { View v = new View(this); layout.addView(v, new LinearLayout.LayoutParams(1, dp(size))); }
    private LinearLayout card(LinearLayout parent) {
        LinearLayout l = column(); l.setPadding(dp(20), dp(16), dp(20), dp(18)); l.setBackground(tint(CARD, 22));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.bottomMargin = dp(14); parent.addView(l, p); return l;
    }
    private Button button(LinearLayout parent, String title, boolean primary, Runnable click) {
        Button b = new Button(this); b.setText(title); b.setTextSize(13); b.setAllCaps(false); b.setTextColor(primary ? BG : GREEN);
        b.setBackground(tint(primary ? GREEN : 0xff2a352a, 14)); b.setMinHeight(dp(52));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(54)); p.topMargin = dp(8); parent.addView(b, p);
        b.setOnClickListener(v -> click.run()); return b;
    }
    private void draw() {
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.setBackgroundColor(BG);
        page = column(); page.setPadding(dp(22), dp(20), dp(22), dp(32)); scroll.addView(page);
        scroll.setOnApplyWindowInsetsListener((v, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets i = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout() | WindowInsets.Type.ime());
                scroll.setPadding(i.left, i.top, i.right, i.bottom);
            } else scroll.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(), insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });
        setContentView(scroll);
        TextView brand = text("ϟ  GIGGLETECH", 20, GREEN); brand.setTypeface(null, Typeface.BOLD); brand.setLetterSpacing(.13f); page.addView(brand);
        page.addView(text("HAPTICS, WITHOUT THE DESKTOP", 10, MUTED)); gap(page, 20);
        state = text("Your world.\nMore tangible.", 34, TEXT); state.setTypeface(null, Typeface.BOLD); page.addView(state);
        page.addView(text("Bring your VRChat interactions to life. Connect your Quest and GiggleTech hardware on the same Wi-Fi.", 15, MUTED)); gap(page, 18);
        LinearLayout hero = card(page);
        endpoint = text("LOCAL WI-FI  /  OSC + OSCQUERY", 10, GREEN); hero.addView(endpoint);
        detail = text(RouterService.status, 18, TEXT); hero.addView(detail);
        start = button(hero, "START ROUTING", true, this::toggle);
        hero.addView(text("Keeps routing with the screen off. Stop here or from the notification.", 12, MUTED));
        LinearLayout network = card(page);
        network.addView(text("01  /  CONNECTION", 11, GREEN));
        network.addView(text(settings.sender.isEmpty() ? "Connect your Quest or PC" : "VRChat  ·  " + settings.sender, 18, TEXT));
        network.addView(text(settings.oscQuery ? "OSCQuery discovery · automatic receive port" : "Manual OSC · UDP " + settings.port, 13, MUTED));
        connection = button(network, "Connection settings", false, this::connectionDialog);
        gap(page, 8); page.addView(text("02  /  YOUR DEVICES", 11, GREEN));
        findHardware = button(page, "Find GiggleTech devices", true, this::discoverHardware);
        gap(page, 12);
        devices = column(); page.addView(devices); drawDevices();
        add = button(page, "+ Add a device manually", false, () -> deviceDialog(-1));
        gap(page, 24); LinearLayout activity = card(page);
        activity.addView(text("03  /  LIVE ACTIVITY", 11, GREEN));
        packetLabel = text("0 packets received", 18, TEXT); activity.addView(packetLabel);
        message = text("Waiting for OSC", 12, MUTED); message.setTypeface(Typeface.MONOSPACE); activity.addView(message);
        logText = text(RouterService.logs(), 11, MUTED); logText.setTypeface(Typeface.MONOSPACE); logText.setTextIsSelectable(true); activity.addView(logText);
        button(page, "Setup guide & privacy", false, this::help);
        page.addView(text("GiggleTech for Android  ·  1.0.2", 11, MUTED));
    }
    private void drawDevices() {
        devices.removeAllViews(); meters.clear(); deviceStatus.clear();
        if (settings.devices.isEmpty()) { LinearLayout empty = card(devices); empty.addView(text("A little hardware. A lot of feeling.", 18, TEXT)); empty.addView(text("Find your GigglePuck or GiggleSpark on Wi-Fi, or enter its IP manually.", 14, MUTED)); }
        for (int i = 0; i < settings.devices.size(); i++) {
            int index = i; Device d = settings.devices.get(i); LinearLayout c = card(devices);
            c.addView(text(d.name, 22, TEXT)); c.addView(text(d.ip + "  ·  " + (d.velocity ? "Velocity" : "Proximity"), 13, MUTED));
            c.addView(text(d.parameter, 12, GREEN));
            ProgressBar meter = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal); meter.setMax(255); meter.setProgressTintList(android.content.res.ColorStateList.valueOf(GREEN)); c.addView(meter, new LinearLayout.LayoutParams(-1, dp(12))); meters.put(d.ip, meter);
            TextView label = text("Ready  ·  " + Math.round(d.power) + "% power limit", 12, MUTED); c.addView(label); deviceStatus.put(d.ip, label);
            button(c, "Edit device", false, () -> { if (RouterService.running) toast("Stop routing before changing devices."); else deviceDialog(index); });
            button(c, "Test · ½ second", false, () -> {
                if (RouterService.testing) { toast("A device test is already in progress."); return; }
                try {
                    startForegroundService(new Intent(this, RouterService.class).setAction("TEST").putExtra("index", index));
                } catch (Exception e) { toast("Could not start test: " + e.getMessage()); }
            });
        }
    }
    private void toggle() {
        if (RouterService.running || RouterService.testing) { startService(new Intent(this, RouterService.class).setAction("STOP")); return; }
        try { settings.validate(); SettingsStore.save(this, settings); }
        catch (Exception e) { toast(e.getMessage()); return; }
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 10);
        else launch();
    }
    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == 10) launch();
    }
    private void launch() {
        try { startForegroundService(new Intent(this, RouterService.class).setAction("START")); }
        catch (Exception e) { toast("Could not start: " + e.getMessage()); }
    }
    private EditText field(LinearLayout layout, String label, String value, boolean number) {
        layout.addView(text(label, 12, MUTED)); EditText e = new EditText(this); e.setText(value); e.setTextColor(TEXT); e.setSingleLine(true); e.setTextSize(16);
        e.setInputType(number ? InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        e.setSelectAllOnFocus(true); layout.addView(e, new LinearLayout.LayoutParams(-1, dp(52))); return e;
    }
    private Switch toggleField(LinearLayout l, String label, boolean checked) { Switch s = new Switch(this); s.setText(label); s.setTextColor(TEXT); s.setChecked(checked); s.setPadding(0, dp(14), 0, dp(14)); l.addView(s); return s; }
    private LinearLayout form() { LinearLayout l = column(); l.setPadding(dp(20), dp(8), dp(20), dp(20)); return l; }
    private AlertDialog dialog(String title, LinearLayout form, Runnable save) {
        ScrollView sc = new ScrollView(this); sc.addView(form);
        AlertDialog d = new AlertDialog.Builder(this).setTitle(title).setView(sc).setNegativeButton("Cancel", null).setPositiveButton("Save", null).create();
        d.setOnShowListener(v -> d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(w -> {
            if (RouterService.running) { toast("Stop routing before saving settings."); return; }
            try { save.run(); SettingsStore.save(this, settings); d.dismiss(); draw(); } catch (Exception e) { toast(e.getMessage()); }
        })); d.show(); return d;
    }
    private String value(EditText e) { return e.getText().toString().trim(); }
    private float number(EditText e) { try { return Float.parseFloat(value(e)); } catch (Exception ex) { throw new IllegalArgumentException("Enter valid numbers in all numeric fields."); } }
    private void connectionDialog() {
        LinearLayout f = form();
        f.addView(text("Use the IP of the Quest or computer running VRChat. Only OSC from this address can move the motors.", 14, MUTED));
        EditText ip = field(f, "Quest / VRChat IPv4 address", settings.sender, false);
        button(f, "Find VRChat on Wi-Fi", false, () -> discover(ip));
        Switch query = toggleField(f, "OSCQuery discovery (recommended)", settings.oscQuery);
        EditText port = field(f, "Manual receive port", "" + settings.port, true);
        EditText timeout = field(f, "Stop after silence (seconds)", "" + settings.timeout, true);
        EditText power = field(f, "Global power parameter (optional)", settings.globalPowerParameter, false);
        AlertDialog d = dialog("Connection", f, () -> {
            Settings candidate = new Settings(); candidate.sender = value(ip); Settings.validateIp(candidate.sender, "Quest / VRChat IP");
            candidate.port = Integer.parseInt(value(port)); candidate.timeout = Integer.parseInt(value(timeout));
            if (candidate.port < 1024 || candidate.port > 65535 || candidate.timeout < 1 || candidate.timeout > 30) throw new IllegalArgumentException("Port: 1024–65535. Timeout: 1–30 seconds.");
            Settings.validateParameter(value(power), true);
            settings.sender = candidate.sender; settings.port = candidate.port; settings.timeout = candidate.timeout; settings.oscQuery = query.isChecked(); settings.globalPowerParameter = value(power);
        });
        d.setOnDismissListener(v -> stopDiscovery());
    }
    private void deviceDialog(int index) {
        deviceDialog(index, null);
    }
    private void deviceDialog(int index, Device discovered) {
        Device d = discovered != null ? discovered : index < 0 ? new Device() : settings.devices.get(index); LinearLayout f = form();
        EditText name = field(f, "Device name", d.name, false), ip = field(f, "Hardware IPv4 address", d.ip, false), parameter = field(f, "Proximity parameter", d.parameter, false);
        EditText power = field(f, "Power limit (%)", "" + d.power, true);
        Switch velocity = toggleField(f, "Velocity-based haptics", d.velocity), pull = toggleField(f, "Respond to pull-away", d.pullAway);
        EditText outer = field(f, "Outer proximity (0–1)", "" + d.outer, true), inner = field(f, "Inner proximity (0–1)", "" + d.inner, true);
        EditText sensitivity = field(f, "Velocity sensitivity (1–100)", "" + d.sensitivity, true), smoothing = field(f, "Smoothing (milliseconds)", "" + d.smoothing, true);
        EditText damping = field(f, "Velocity soft cap (100 = no damping)", "" + d.softcap, true), scale = field(f, "Additional output scale (%)", "" + d.scale, true);
        EditText online = field(f, "Online Bool parameter (optional)", d.onlineParameter, false), max = field(f, "Device power parameter (optional)", d.powerParameter, false);
        AlertDialog edit = dialog(index < 0 ? "Add device" : "Edit " + d.name, f, () -> {
            Device n = new Device(); n.name = value(name); n.ip = value(ip); n.parameter = value(parameter); n.power = number(power); n.scale = number(scale);
            n.velocity = velocity.isChecked(); n.pullAway = pull.isChecked(); n.outer = number(outer); n.inner = number(inner);
            n.sensitivity = number(sensitivity); n.smoothing = number(smoothing); n.softcap = number(damping); n.onlineParameter = value(online); n.powerParameter = value(max); n.validate();
            for (int j = 0; j < settings.devices.size(); j++) if (j != index && settings.devices.get(j).ip.equals(n.ip)) throw new IllegalArgumentException("That device IP is already added.");
            if (index < 0) settings.devices.add(n); else settings.devices.set(index, n);
        });
        if (index >= 0) button(f, "Remove device", false, () -> new AlertDialog.Builder(this).setTitle("Remove " + d.name + "?").setMessage("You can add it again later.")
            .setNegativeButton("Cancel", null).setPositiveButton("Remove", (v, w) -> { if (RouterService.running) return; settings.devices.remove(index); SettingsStore.save(this, settings); edit.dismiss(); draw(); }).show());
    }
    private void discoverHardware() {
        if (RouterService.running) { toast("Stop routing before adding devices."); return; }
        if (hardwareDiscovery != null) hardwareDiscovery.close();
        LinearLayout content = form();
        TextView progress = text("Looking for GiggleTech hardware on Wi-Fi (8 seconds)...", 15, TEXT); content.addView(progress);
        content.addView(text("Power on your hardware and connect it to the same Wi-Fi as this phone.", 13, MUTED));
        LinearLayout results = column(); content.addView(results);
        ScrollView scroll = new ScrollView(this); scroll.addView(content);
        AlertDialog picker = new AlertDialog.Builder(this).setTitle("Find GiggleTech devices").setView(scroll).setNegativeButton("Close", null)
            .setNeutralButton("Enter IP manually", (v, which) -> deviceDialog(-1)).create();
        HardwareDiscovery scan = new HardwareDiscovery(this, new HardwareDiscovery.Listener() {
            public void found(HardwareDiscovery.Result result) {
                if (destroyed || !picker.isShowing()) return;
                int existing = -1;
                for (int i = 0; i < settings.devices.size(); i++) if (settings.devices.get(i).ip.equals(result.ip())) { existing = i; break; }
                final int index = existing;
                button(results, result.name() + "\n" + result.ip() + (index >= 0 ? " (already added)" : ""), false, () -> {
                    picker.dismiss();
                    if (RouterService.running) { toast("Stop routing before editing devices."); return; }
                    Device device = new Device(); device.name = result.name(); device.ip = result.ip();
                    int number = 1;
                    Set<String> parameters = new HashSet<>(); for (Device saved : settings.devices) parameters.add(saved.parameter);
                    while (parameters.contains(String.format(Locale.US, "proximity_%02d", number))) number++;
                    device.parameter = String.format(Locale.US, "proximity_%02d", number);
                    deviceDialog(index, index >= 0 ? null : device);
                });
            }
            public void finished(String message) { if (!destroyed && picker.isShowing()) progress.setText(message); }
        });
        hardwareDiscovery = scan;
        picker.setOnDismissListener(v -> { scan.close(); if (hardwareDiscovery == scan) hardwareDiscovery = null; });
        picker.show(); scan.start();
    }
    @SuppressWarnings("deprecation") private void discover(EditText ip) {
        stopDiscovery(); NsdManager nsd = getSystemService(NsdManager.class); toast("Looking for VRChat for 8 seconds…");
        Set<String> seen = new HashSet<>(); List<String> found = new ArrayList<>();
        discovery = new NsdManager.DiscoveryListener() {
            public void onDiscoveryStarted(String t) {}
            public void onDiscoveryStopped(String t) {}
            public void onStartDiscoveryFailed(String t, int e) { runOnUiThread(() -> toast("Discovery unavailable: " + e)); }
            public void onStopDiscoveryFailed(String t, int e) {}
            public void onServiceLost(NsdServiceInfo i) {}
            public void onServiceFound(NsdServiceInfo info) {
                if (info.getServiceName().toLowerCase(Locale.ROOT).contains("vrchat")) nsd.resolveService(info, new NsdManager.ResolveListener() {
                    public void onResolveFailed(NsdServiceInfo i, int e) {}
                    public void onServiceResolved(NsdServiceInfo i) { if (i.getHost() instanceof java.net.Inet4Address) runOnUiThread(() -> { String address = i.getHost().getHostAddress(); if (seen.add(address)) found.add(address); }); }
                });
            }
        };
        NsdManager.DiscoveryListener current = discovery;
        try { nsd.discoverServices("_oscjson._tcp.", NsdManager.PROTOCOL_DNS_SD, current); }
        catch (Exception e) { stopDiscovery(); toast("Could not start discovery. Enter the VRChat IP manually."); return; }
        handler.postDelayed(() -> {
            if (destroyed || discovery != current) return;
            stopDiscovery();
            if (found.isEmpty()) toast("No VRChat found. Enable OSC in VRChat, or enter its IP manually.");
            else new AlertDialog.Builder(this).setTitle("Select VRChat device").setItems(found.toArray(new String[0]), (d, index) -> ip.setText(found.get(index))).setNegativeButton("Cancel", null).show();
        }, 8000);
    }
    private void stopDiscovery() {
        if (discovery != null) { try { getSystemService(NsdManager.class).stopServiceDiscovery(discovery); } catch (Exception ignored) {} discovery = null; }
    }
    private void help() {
        new AlertDialog.Builder(this).setTitle("Your first connection")
            .setMessage("1. Put your phone, Quest (or PC) and GiggleTech hardware on the same Wi-Fi. Avoid guest networks with client isolation.\n\n2. Enable OSC in VRChat. Enter the Quest/PC IP under Connection. OSCQuery lets VRChat find this phone automatically. For manual mode, send OSC to the phone address shown while routing.\n\n3. Under Your devices, tap Find GiggleTech devices and select your hardware. Set its avatar proximity parameter and save. Tap Test to check the hardware without VRChat or routing.\n\n4. Keep routing active while playing. Use Stop before disconnecting Wi-Fi or powering off your phone. Hardware can retain the last command if Android kills the app or the network drops. A firmware watchdog is needed to guarantee motor shutdown.\n\nA missing ping reply does not prove a device is offline. Some networks block ping.\n\nPRIVACY\nSettings stay on this device. OSC, discovery and reachability traffic stays between devices on your network. No accounts, ads, analytics or cloud service. Logs are held in memory and cleared when the process exits. Uninstalling clears saved settings.\n\nThis app controls external GiggleTech hardware; it does not use your phone’s vibration motor. It is not affiliated with VRChat or Meta.")
            .setPositiveButton("Got it", null).show();
    }
    private void toast(String text) { Toast.makeText(this, text, Toast.LENGTH_LONG).show(); }
}
