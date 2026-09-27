package com.giggletech.router;

import android.app.*;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.net.wifi.WifiManager;
import android.os.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public final class RouterService extends Service {
    public static volatile boolean running;
    public static volatile boolean testing;
    public static volatile String status = "Ready when you are", endpoint = "", lastMessage = "Waiting for OSC";
    public static volatile long packets;
    private static final ArrayDeque<String> logs = new ArrayDeque<>();
    public static final ConcurrentHashMap<String, Integer> levels = new ConcurrentHashMap<>();
    public static final ConcurrentHashMap<String, String> reachability = new ConcurrentHashMap<>();
    private volatile boolean stopping;
    private volatile DatagramSocket receiver;
    private DatagramSocket sender;
    private OscQuery query;
    private PowerManager.WakeLock wake;
    private WifiManager.MulticastLock multicast;
    private Settings settings;
    private final List<Motor> motors = new ArrayList<>();
    private final List<InetAddress> addresses = new ArrayList<>();
    private Thread worker;
    private ExecutorService pinger;
    private volatile int testIndex = -1;
    private long testUntil;

    public static synchronized void log(String text) {
        if (logs.size() >= 60) logs.removeFirst();
        logs.addLast(new java.text.SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date()) + "  " + text);
    }
    public static synchronized String logs() { return String.join("\n", logs); }
    @Override public IBinder onBind(Intent intent) { return null; }
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? "" : intent.getAction();
        if ("STOP".equals(action)) { requestStop(); return START_NOT_STICKY; }
        boolean testOnly = "TEST".equals(action);
        int selected = intent == null ? -1 : intent.getIntExtra("index", -1);
        if (testOnly && running) { testIndex = selected; return START_NOT_STICKY; }
        if (worker != null) return START_NOT_STICKY;
        settings = SettingsStore.load(this);
        try {
            if (testOnly) {
                if (selected < 0 || selected >= settings.devices.size()) throw new IllegalArgumentException("Select a saved device to test.");
                Device device = settings.devices.get(selected);
                Settings.validateIp(device.ip, "Device IP");
                Device.range(device.power, 0, 100, "Power"); Device.range(device.scale, 0, 100, "Scale");
            } else settings.validate();
        } catch (Exception e) { status = e.getMessage(); log(status); stopSelf(); return START_NOT_STICKY; }
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel("routing", "Active haptic routing", NotificationManager.IMPORTANCE_LOW));
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent stop = PendingIntent.getService(this, 1, new Intent(this, RouterService.class).setAction("STOP"), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification notification = new Notification.Builder(this, "routing").setSmallIcon(R.drawable.ic_bolt).setContentTitle(testOnly ? "Testing GiggleTech device" : "GiggleTech is routing")
            .setContentText(testOnly ? "Half-second hardware test" : "Connected to your haptic devices · Tap to manage").setContentIntent(open).setOngoing(true)
            .addAction(new Notification.Action.Builder(null, "Stop all motors", stop).build()).build();
        if (Build.VERSION.SDK_INT >= 29) startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);
        else startForeground(1, notification);
        stopping = false; testing = testOnly; running = !testOnly; status = testOnly ? "Testing device…" : "Connecting…";
        if (!testOnly) { packets = 0; levels.clear(); reachability.clear(); }
        worker = new Thread(testOnly ? () -> testDevice(settings.devices.get(selected)) : this::route, "giggletech-router"); worker.start();
        return START_NOT_STICKY;
    }
    private void testDevice(Device device) {
        InetAddress address = null;
        boolean failed = false;
        try {
            wake = getSystemService(PowerManager.class).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "GiggleTech:DeviceTest");
            wake.acquire(5000);
            address = InetAddress.getByName(device.ip); sender = new DatagramSocket();
            if (stopping) return;
            int tx = Math.round(Math.min(device.power, 10) / 100 * .66f * device.scale / 100 * 255);
            byte[] bytes = Osc.encode("/motor", tx);
            sender.send(new DatagramPacket(bytes, bytes.length, address, 8888)); levels.put(device.ip, tx);
            log("Standalone half-second test: " + device.name);
            long end = System.nanoTime() + 500_000_000L;
            while (!stopping && System.nanoTime() < end) Thread.sleep(10);
        } catch (Exception e) { failed = true; status = "Test failed: " + e.getMessage(); log(status); }
        finally {
            // Keep the socket alive for spaced stop retries, even if the activity closes.
            if (sender != null && address != null) for (int repeat = 0; repeat < 3; repeat++) {
                try {
                    byte[] bytes = Osc.encode("/motor", 0);
                    sender.send(new DatagramPacket(bytes, bytes.length, address, 8888));
                } catch (Exception e) { failed = true; status = "Could not send motor stop: " + e.getMessage(); log(status); }
                try { Thread.sleep(40); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }
            levels.put(device.ip, 0);
            if (sender != null) sender.close();
            if (wake != null && wake.isHeld()) wake.release();
            if (!failed && !stopping) status = "Test commands sent · no device acknowledgement";
            // Keep busy true until onDestroy, so a new start cannot race service teardown.
            stopForeground(STOP_FOREGROUND_REMOVE); stopSelf();
        }
    }
    private void route() {
        try {
            InetAddress peer = InetAddress.getByName(settings.sender);
            InetAddress local;
            try (DatagramSocket probe = new DatagramSocket()) { probe.connect(peer, 9000); local = probe.getLocalAddress(); }
            if (local.isAnyLocalAddress()) throw new IllegalStateException("Join the same Wi-Fi as your Quest and devices.");
            receiver = new DatagramSocket(new InetSocketAddress(local, settings.oscQuery ? 0 : settings.port)); receiver.setSoTimeout(50);
            sender = new DatagramSocket();
            for (Device d : settings.devices) { addresses.add(InetAddress.getByName(d.ip)); motors.add(new Motor()); }
            wake = getSystemService(PowerManager.class).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "GiggleTech:Router"); wake.setReferenceCounted(false); wake.acquire(12 * 60 * 60 * 1000L);
            multicast = getSystemService(WifiManager.class).createMulticastLock("GiggleTech:Discovery"); multicast.setReferenceCounted(false); multicast.acquire();
            if (settings.oscQuery) query = new OscQuery(this, settings, local, receiver.getLocalPort());
            endpoint = local.getHostAddress() + ":" + receiver.getLocalPort(); status = "Routing · " + endpoint; log(status);
            for (int i = 0; i < motors.size(); i++) sendMotor(i, 0);
            pinger = Executors.newSingleThreadExecutor(); pinger.submit(() -> pingLoop(peer));
            byte[] buffer = new byte[65507];
            long nextStop = 0, lastRenew = System.nanoTime();
            while (!stopping) {
                long now = System.nanoTime();
                if (now - lastRenew > 60 * 60 * 1_000_000_000L) { wake.acquire(12 * 60 * 60 * 1000L); lastRenew = now; }
                int test = testIndex; testIndex = -1;
                if (test >= 0 && test < motors.size()) {
                    // Test uses the same timeout loop as incoming OSC; never a detached delayed stop.
                    for (int i = 0; i < motors.size(); i++) { motors.get(i).reset(); sendMotor(i, 0); }
                    Motor m = motors.get(test); m.lastSignal = now;
                    int tx = Math.round(Math.min(settings.devices.get(test).power, 10) / 100 * .66f * settings.devices.get(test).scale / 100 * 255);
                    sendMotor(test, tx); testUntil = now + 500_000_000L; log("Half-second test: " + settings.devices.get(test).name);
                }
                if (testUntil > 0 && now >= testUntil) { testUntil = 0; for (int i = 0; i < motors.size(); i++) { motors.get(i).reset(); sendMotor(i, 0); } }
                for (int i = 0; i < motors.size(); i++) if (motors.get(i).expired(now, settings.timeout)) { motors.get(i).reset(); sendMotor(i, 0); }
                // Repeated zero packets help latch hardware off and tolerate UDP packet loss.
                if (now >= nextStop) { for (int i = 0; i < motors.size(); i++) if (motors.get(i).output == 0) sendMotor(i, 0); nextStop = now + 250_000_000L; }
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                try { receiver.receive(packet); } catch (SocketTimeoutException ignored) { continue; }
                if (!packet.getAddress().equals(peer)) continue;
                List<Osc.Message> messages = Osc.decode(buffer, packet.getLength());
                if (!messages.isEmpty()) packets++;
                for (Osc.Message message : messages) {
                    lastMessage = message.path() + "  " + message.value();
                    if (message.path().equals("/avatar/change")) {
                        for (int i = 0; i < motors.size(); i++) { motors.get(i).reset(); motors.get(i).remotePower = 1; sendMotor(i, 0); }
                        continue;
                    }
                    if (!(message.value() instanceof Number)) continue;
                    float value = ((Number)message.value()).floatValue();
                    for (int i = 0; i < motors.size(); i++) {
                        Device d = settings.devices.get(i); Motor m = motors.get(i);
                        if ((!settings.globalPowerParameter.isEmpty() && message.path().equals(Settings.path(settings.globalPowerParameter))) || (!d.powerParameter.isEmpty() && message.path().equals(Settings.path(d.powerParameter)))) {
                            m.remotePower = Math.max(0, Math.min(1, value));
                            int cap = Math.round(d.power / 100 * m.remotePower * .66f * d.scale / 100 * 255);
                            if (m.output > cap) sendMotor(i, cap);
                        }
                        if (testUntil == 0 && message.path().equals(Settings.path(d.parameter))) sendMotor(i, m.sample(d, value, System.nanoTime()));
                    }
                }
            }
        } catch (Exception e) { if (!stopping) { status = "Stopped: " + e.getMessage(); log(status); } }
        finally {
            stopping = true;
            if (pinger != null) { pinger.shutdownNow(); try { pinger.awaitTermination(2, TimeUnit.SECONDS); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); } }
            for (int repeat = 0; repeat < 3; repeat++) for (int i = 0; i < addresses.size(); i++) try { sendMotor(i, 0); } catch (Exception ignored) {}
            if (query != null) query.close();
            if (receiver != null) receiver.close(); if (sender != null) sender.close();
            if (multicast != null && multicast.isHeld()) multicast.release(); if (wake != null && wake.isHeld()) wake.release();
            running = false; endpoint = ""; stopForeground(STOP_FOREGROUND_REMOVE); stopSelf();
        }
    }
    private void sendMotor(int index, int level) throws Exception {
        if (stopping && level > 0) return;
        byte[] bytes = Osc.encode("/motor", level);
        sender.send(new DatagramPacket(bytes, bytes.length, addresses.get(index), 8888));
        motors.get(index).output = level; levels.put(settings.devices.get(index).ip, level);
    }
    private void pingLoop(InetAddress peer) {
        Map<String, Boolean> previous = new HashMap<>();
        while (!stopping && !Thread.currentThread().isInterrupted()) {
            for (int i = 0; i < addresses.size() && !stopping; i++) {
                Device d = settings.devices.get(i);
                try {
                    boolean online = addresses.get(i).isReachable(800);
                    reachability.put(d.ip, online ? "Reachable" : "No ping reply");
                    Boolean old = previous.put(d.ip, online);
                    if (!d.onlineParameter.isEmpty() && (old == null || old != online)) {
                        if (online) { sendOnline(peer, d.onlineParameter, false); Thread.sleep(50); }
                        sendOnline(peer, d.onlineParameter, online);
                    }
                } catch (Exception e) { reachability.put(d.ip, "Ping unavailable"); }
            }
            try { Thread.sleep(5000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
        }
    }
    private void sendOnline(InetAddress peer, String parameter, boolean value) throws Exception {
        byte[] bytes = Osc.encode(Settings.path(parameter), value); sender.send(new DatagramPacket(bytes, bytes.length, peer, 9000));
    }
    private void requestStop() {
        stopping = true; status = "Stopped"; log("Stop requested; sending motor-off commands.");
        if (receiver != null) receiver.close();
        if (worker == null) stopSelf();
    }
    @Override public void onDestroy() { stopping = true; testing = false; if (receiver != null) receiver.close(); super.onDestroy(); }
}
