package com.giggletech.router;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Emulator-only loopback end-to-end checks; does not contact physical hardware. */
public final class IntegrationTest extends Instrumentation {
    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); start(); }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static void waitFor(java.util.function.BooleanSupplier predicate, String message) throws Exception {
        long end = System.currentTimeMillis() + 6000;
        while (!predicate.getAsBoolean() && System.currentTimeMillis() < end) Thread.sleep(50);
        check(predicate.getAsBoolean(), message);
    }
    private static int receive(DatagramSocket hardware) throws Exception {
        byte[] bytes = new byte[1024]; DatagramPacket packet = new DatagramPacket(bytes, bytes.length); hardware.receive(packet);
        List<Osc.Message> messages = Osc.decode(bytes, packet.getLength());
        check(!messages.isEmpty() && messages.get(0).path().equals("/motor"), "Hardware OSC path");
        return ((Number)messages.get(0).value()).intValue();
    }
    private static void waitLevel(DatagramSocket hardware, boolean positive) throws Exception {
        long end = System.currentTimeMillis() + 4000;
        while (System.currentTimeMillis() < end) { int value = receive(hardware); if (positive ? value > 0 : value == 0) return; }
        throw new AssertionError("Motor level not observed: positive=" + positive);
    }
    private void stop(Context c) throws Exception {
        c.startService(new Intent(c, RouterService.class).setAction("STOP"));
        waitFor(() -> !RouterService.running, "Service did not stop"); Thread.sleep(300);
    }
    @Override public void onStart() {
        Context c = getTargetContext(); Settings previous = SettingsStore.load(c); Activity activity = null; Bundle result = new Bundle(); int resultCode = Activity.RESULT_OK;
        try {
            activity = startActivitySync(new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            verifyStandaloneTest(c);
            verifyHardwareDiscovery(c);
            Settings s = new Settings(); s.sender = "127.0.0.1"; s.oscQuery = false; s.port = 19001; s.timeout = 1;
            Device d = new Device(); d.ip = "127.0.0.1"; d.velocity = false; s.devices.add(d); SettingsStore.save(c, s);
            try (DatagramSocket hardware = new DatagramSocket(8888, InetAddress.getByName("127.0.0.1")); DatagramSocket input = new DatagramSocket()) {
                hardware.setSoTimeout(4000); c.startForegroundService(new Intent(c, RouterService.class).setAction("START"));
                waitFor(() -> !RouterService.endpoint.isEmpty(), "Receiver startup failed: " + RouterService.status);
                waitLevel(hardware, false);
                byte[] bytes = Osc.encode(Settings.path(d.parameter), .8f);
                input.send(new DatagramPacket(bytes, bytes.length, InetAddress.getByName("127.0.0.1"), 19001));
                waitLevel(hardware, true); check(RouterService.packets == 1, "Accepted packet count");
                waitLevel(hardware, false); // silence timeout
                c.startService(new Intent(c, RouterService.class).setAction("TEST").putExtra("index", 0));
                waitLevel(hardware, true); waitLevel(hardware, false); // timed hardware test
                try (DatagramSocket wrongSender = new DatagramSocket(0, InetAddress.getByName("127.0.0.2"))) {
                    wrongSender.send(new DatagramPacket(bytes, bytes.length, InetAddress.getByName("127.0.0.1"), 19001));
                    Thread.sleep(200); check(RouterService.packets == 1, "Wrong sender was accepted");
                }
                input.send(new DatagramPacket(bytes, bytes.length, InetAddress.getByName("127.0.0.1"), 19001)); waitLevel(hardware, true);
                stop(c); waitLevel(hardware, false);
                s.oscQuery = true; SettingsStore.save(c, s);
                c.startForegroundService(new Intent(c, RouterService.class).setAction("START"));
                waitFor(() -> !RouterService.endpoint.isEmpty(), "OSCQuery service restart");
                check(!RouterService.endpoint.endsWith(":19001"), "OSCQuery should allocate a port");
                stop(c);
            }
            // Read the real OSCQuery server without relying on emulator mDNS transport.
            try (OscQuery query = new OscQuery(c, s, InetAddress.getByName("127.0.0.1"), 19001)) {
                java.lang.reflect.Field serverField = OscQuery.class.getDeclaredField("server"); serverField.setAccessible(true);
                int port = ((ServerSocket)serverField.get(query)).getLocalPort();
                try (Socket client = new Socket("127.0.0.1", port)) {
                    client.setSoTimeout(3000); client.getOutputStream().write("GET /?HOST_INFO HTTP/1.1\r\nHost: localhost\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
                    java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream(); byte[] buffer = new byte[2048]; int count;
                    while ((count = client.getInputStream().read(buffer)) >= 0) out.write(buffer, 0, count);
                    String response = new String(out.toByteArray(), StandardCharsets.UTF_8);
                    check(response.contains("200 OK") && response.contains("\"OSC_PORT\":19001"), "OSCQuery HOST_INFO response");
                }
            }
            result.putString("stream", "PASS: standalone test without VRChat, test cancellation, hardware discovery, routing, timeout, test pulse, sender filtering, stop/restart, OSCQuery HTTP\n");
        } catch (Throwable e) {
            result.putString("stream", "FAIL: " + android.util.Log.getStackTraceString(e)); resultCode = Activity.RESULT_CANCELED;
        } finally {
            if (RouterService.running) try { stop(c); } catch (Exception ignored) {}
            SettingsStore.save(c, previous);
            if (activity != null) { Activity a = activity; runOnMainSync(a::finish); }
        }
        finish(resultCode, result);
    }
    private void verifyStandaloneTest(Context c) throws Exception {
        Settings settings = new Settings(); // No Quest/PC address configured.
        settings.port = -1; settings.timeout = 0; // Unrelated routing settings must not gate hardware tests.
        Device device = new Device(); device.ip = "127.0.0.1"; device.parameter = ""; device.power = 1;
        settings.devices.add(device); SettingsStore.save(c, settings);
        try (DatagramSocket hardware = new DatagramSocket(8888, InetAddress.getByName(device.ip))) {
            hardware.setSoTimeout(3000);
            c.startForegroundService(new Intent(c, RouterService.class).setAction("TEST").putExtra("index", 0));
            int value = receive(hardware); check(value > 0 && value <= 2, "Standalone test must respect low power cap");
            check(!RouterService.running && RouterService.endpoint.isEmpty(), "Standalone test must not start OSC receiver");
            for (int i = 0; i < 3; i++) check(receive(hardware) == 0, "Standalone pulse must send repeated stops");
            waitFor(() -> !RouterService.testing, "Standalone test service cleanup");
            c.startForegroundService(new Intent(c, RouterService.class).setAction("TEST").putExtra("index", 0));
            check(receive(hardware) > 0, "Repeat standalone test");
            c.startService(new Intent(c, RouterService.class).setAction("STOP"));
            for (int i = 0; i < 3; i++) check(receive(hardware) == 0, "Cancelled test must stop motor");
            waitFor(() -> !RouterService.testing, "Cancelled test service cleanup");
        }
    }
    private void verifyHardwareDiscovery(Context c) throws Exception {
        android.net.nsd.NsdManager nsd = c.getSystemService(android.net.nsd.NsdManager.class);
        android.net.nsd.NsdServiceInfo advertised = new android.net.nsd.NsdServiceInfo();
        advertised.setServiceName("GiggleTech-Test-Hardware"); advertised.setServiceType("_http._tcp."); advertised.setPort(18888);
        java.util.concurrent.CountDownLatch registered = new java.util.concurrent.CountDownLatch(1), found = new java.util.concurrent.CountDownLatch(1);
        android.net.nsd.NsdManager.RegistrationListener registration = new android.net.nsd.NsdManager.RegistrationListener() {
            public void onServiceRegistered(android.net.nsd.NsdServiceInfo i) { registered.countDown(); }
            public void onRegistrationFailed(android.net.nsd.NsdServiceInfo i, int error) {}
            public void onServiceUnregistered(android.net.nsd.NsdServiceInfo i) {}
            public void onUnregistrationFailed(android.net.nsd.NsdServiceInfo i, int error) {}
        };
        HardwareDiscovery scan = new HardwareDiscovery(c, new HardwareDiscovery.Listener() {
            public void found(HardwareDiscovery.Result item) { if (item.name().startsWith("GiggleTech-Test-Hardware")) found.countDown(); }
            public void finished(String message) {}
        });
        try {
            nsd.registerService(advertised, android.net.nsd.NsdManager.PROTOCOL_DNS_SD, registration);
            check(registered.await(5, java.util.concurrent.TimeUnit.SECONDS), "Simulated hardware advertisement");
            runOnMainSync(scan::start);
            check(found.await(10, java.util.concurrent.TimeUnit.SECONDS), "Hardware service discovery did not find simulated device");
        } finally { runOnMainSync(scan::close); nsd.unregisterService(registration); }
    }
}
