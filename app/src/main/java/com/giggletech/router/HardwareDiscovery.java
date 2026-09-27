package com.giggletech.router;

import android.content.Context;
import android.net.*;
import android.net.nsd.*;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Looper;
import java.net.*;
import java.util.*;

/** One eight-second scan, cancelled when its dialog closes. Never sends motor commands. */
@SuppressWarnings("deprecation")
final class HardwareDiscovery implements AutoCloseable {
    record Result(String name, String ip) {}
    interface Listener { void found(Result result); void finished(String message); }
    private final Context context;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final NsdManager nsd;
    private final Set<String> ips = new HashSet<>(), services = new HashSet<>();
    private final ArrayDeque<NsdServiceInfo> queue = new ArrayDeque<>();
    private NsdManager.DiscoveryListener browser;
    private WifiManager.MulticastLock lock;
    private volatile boolean closed;
    private volatile DatagramSocket socket;
    private boolean resolving;
    HardwareDiscovery(Context context, Listener listener) {
        this.context = context.getApplicationContext(); this.listener = listener;
        nsd = context.getSystemService(NsdManager.class);
    }
    void start() {
        ConnectivityManager cm = context.getSystemService(ConnectivityManager.class);
        Network wifi = null;
        for (Network network : cm.getAllNetworks()) {
            NetworkCapabilities caps = cm.getNetworkCapabilities(network);
            if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) { wifi = network; break; }
        }
        if (wifi == null) { finish("Join the same Wi-Fi as your GiggleTech hardware, then try again."); return; }
        try {
            lock = context.getSystemService(WifiManager.class).createMulticastLock("GiggleTech:HardwareScan");
            lock.setReferenceCounted(false); lock.acquire();
            browser = new NsdManager.DiscoveryListener() {
                public void onDiscoveryStarted(String type) {}
                public void onDiscoveryStopped(String type) {}
                public void onStartDiscoveryFailed(String type, int code) { RouterService.log("Hardware service discovery unavailable: " + code); }
                public void onStopDiscoveryFailed(String type, int code) {}
                public void onServiceLost(NsdServiceInfo info) {}
                public void onServiceFound(NsdServiceInfo info) {
                    main.post(() -> {
                        if (closed || services.size() >= 64 || !services.add(info.getServiceName())) return;
                        queue.add(info); resolveNext();
                    });
                }
            };
            nsd.discoverServices("_http._tcp.", NsdManager.PROTOCOL_DNS_SD, browser);
            Network selected = wifi;
            new Thread(() -> hostname(selected), "giggletech-hardware-discovery").start();
            main.postDelayed(() -> finish(ips.isEmpty()
                ? "No GiggleTech devices found. Check that the hardware is powered on and on the same Wi-Fi. Guest-network isolation can block discovery. You can also enter its IP manually."
                : "Scan complete. Select a device to configure it."), 8000);
        } catch (Exception e) { finish("Could not start device discovery: " + e.getMessage()); }
    }
    private void resolveNext() {
        if (closed || resolving || queue.isEmpty()) return;
        resolving = true; NsdServiceInfo next = queue.removeFirst();
        try {
            nsd.resolveService(next, new NsdManager.ResolveListener() {
                public void onResolveFailed(NsdServiceInfo info, int code) { main.post(() -> { resolving = false; resolveNext(); }); }
                public void onServiceResolved(NsdServiceInfo info) {
                    // getHostName may reverse-resolve; inspect the already supplied hostname instead.
                    InetAddress host = info.getHost();
                    String identity = info.getServiceName() + " " + (host == null ? "" : host.toString());
                    main.post(() -> {
                        if (host instanceof Inet4Address && MdnsHostname.branded(identity)) add(info.getServiceName(), host.getHostAddress());
                        resolving = false; resolveNext();
                    });
                }
            });
        } catch (Exception e) { resolving = false; resolveNext(); }
    }
    private void add(String name, String ip) {
        if (closed) return;
        try { Settings.validateIp(ip, "Discovered IP"); } catch (IllegalArgumentException invalid) { return; }
        if (ips.add(ip)) listener.found(new Result(name, ip));
    }
    private void hostname(Network network) {
        try (DatagramSocket probe = new DatagramSocket()) {
            socket = probe;
            if (closed) return;
            network.bindSocket(probe); probe.setSoTimeout(300);
            InetAddress multicast = InetAddress.getByName("224.0.0.251"); byte[] query = MdnsHostname.query();
            long end = System.nanoTime() + 7_500_000_000L, next = 0;
            while (!closed && System.nanoTime() < end) {
                if (System.nanoTime() >= next) { probe.send(new DatagramPacket(query, query.length, multicast, 5353)); next = System.nanoTime() + 2_000_000_000L; }
                byte[] data = new byte[9000]; DatagramPacket reply = new DatagramPacket(data, data.length);
                try { probe.receive(reply); } catch (SocketTimeoutException timeout) { continue; }
                if (reply.getPort() != 5353) continue;
                for (MdnsHostname.Found item : MdnsHostname.answers(data, reply.getLength())) main.post(() -> add(item.hostname(), item.ip()));
            }
        } catch (Exception e) { if (!closed) RouterService.log("Hardware hostname lookup unavailable: " + e.getMessage()); }
        finally { socket = null; }
    }
    private void finish(String message) { if (closed) return; close(); listener.finished(message); }
    @Override public void close() {
        closed = true; main.removeCallbacksAndMessages(null); queue.clear();
        if (socket != null) socket.close();
        if (browser != null) { try { nsd.stopServiceDiscovery(browser); } catch (Exception ignored) {} browser = null; }
        if (lock != null && lock.isHeld()) lock.release();
    }
}
