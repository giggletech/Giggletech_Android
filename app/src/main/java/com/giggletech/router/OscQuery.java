package com.giggletech.router;

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

final class OscQuery implements AutoCloseable {
    private final ServerSocket server;
    private final NsdManager nsd;
    private final List<NsdManager.RegistrationListener> registrations = new ArrayList<>();
    private volatile boolean closed;
    private volatile Socket active;
    private final JSONObject root = new JSONObject(), host;

    OscQuery(Context c, Settings settings, InetAddress local, int port) throws Exception {
        nsd = c.getSystemService(NsdManager.class);
        root.put("FULL_PATH", "/").put("CONTENTS", new JSONObject());
        add("/avatar/change", "s");
        if (!settings.globalPowerParameter.isEmpty()) add(Settings.path(settings.globalPowerParameter), "f");
        for (Device d : settings.devices) {
            add(Settings.path(d.parameter), "f");
            if (!d.powerParameter.isEmpty()) add(Settings.path(d.powerParameter), "f");
        }
        String name = "GiggleTech-" + port;
        host = new JSONObject().put("NAME", name).put("OSC_IP", local.getHostAddress()).put("OSC_PORT", port)
            .put("OSC_TRANSPORT", "UDP").put("EXTENSIONS", new JSONObject().put("ACCESS", true));
        server = new ServerSocket(0, 8, local);
        try {
            register(name, "_oscjson._tcp.", server.getLocalPort()); register(name, "_osc._udp.", port);
            new Thread(this::serve, "oscquery-http").start();
        } catch (Exception e) { close(); throw e; }
    }
    private void add(String path, String type) throws Exception {
        JSONObject node = root; String full = "";
        for (String part : path.substring(1).split("/")) {
            full += "/" + part;
            if (!node.has("CONTENTS")) node.put("CONTENTS", new JSONObject());
            JSONObject children = node.getJSONObject("CONTENTS");
            if (!children.has(part)) children.put(part, new JSONObject().put("FULL_PATH", full));
            node = children.getJSONObject(part);
        }
        node.put("TYPE", type).put("ACCESS", 2);
    }
    private void register(String name, String type, int port) {
        NsdServiceInfo info = new NsdServiceInfo(); info.setServiceName(name); info.setServiceType(type); info.setPort(port);
        NsdManager.RegistrationListener listener = new NsdManager.RegistrationListener() {
            public void onServiceRegistered(NsdServiceInfo i) { RouterService.log("Discovery: " + i.getServiceName()); }
            public void onRegistrationFailed(NsdServiceInfo i, int e) { RouterService.log("Discovery registration failed (" + e + "). Use manual port or restart."); }
            public void onServiceUnregistered(NsdServiceInfo i) {}
            public void onUnregistrationFailed(NsdServiceInfo i, int e) { RouterService.log("Discovery cleanup: " + e); }
        };
        registrations.add(listener); nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener);
    }
    private void serve() {
        while (!closed) {
            try (Socket socket = server.accept()) {
                active = socket; socket.setSoTimeout(1500);
                ByteArrayOutputStream request = new ByteArrayOutputStream(); int b;
                long deadline = System.nanoTime() + 2_000_000_000L;
                while (request.size() < 4096 && System.nanoTime() < deadline && (b = socket.getInputStream().read()) != -1) {
                    request.write(b);
                    byte[] a = request.toByteArray(); int n = a.length;
                    if (n >= 4 && a[n-4] == 13 && a[n-3] == 10 && a[n-2] == 13 && a[n-1] == 10) break;
                }
                String[] first = new String(request.toByteArray(), StandardCharsets.US_ASCII).split("\r\n", 2)[0].split(" ");
                String status = "200 OK", body = "{}";
                if (first.length != 3) status = "400 Bad Request";
                else if (!first[0].equals("GET")) status = "405 Method Not Allowed";
                else {
                    String[] target = first[1].split("\\?", 2);
                    JSONObject node = root;
                    for (String part : target[0].split("/")) if (!part.isEmpty()) {
                        JSONObject children = node == null ? null : node.optJSONObject("CONTENTS");
                        node = children == null ? null : children.optJSONObject(part);
                    }
                    if (target[0].equals("/") && target.length == 2 && target[1].equals("HOST_INFO")) body = host.toString();
                    else if (node == null) status = "404 Not Found";
                    else if (target.length == 1 || target[1].isEmpty()) body = node.toString();
                    else if (node.has(target[1])) body = new JSONObject().put(target[1], node.get(target[1])).toString();
                    else { status = "204 No Content"; body = ""; }
                }
                byte[] payload = body.getBytes(StandardCharsets.UTF_8);
                socket.getOutputStream().write(("HTTP/1.1 " + status + "\r\nContent-Type: application/json\r\nContent-Length: " + payload.length + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
                socket.getOutputStream().write(payload);
            } catch (Exception ignored) { /* A bad/slow discovery client must not interrupt routing. */ }
            finally { active = null; }
        }
    }
    public void close() {
        closed = true;
        try { server.close(); } catch (Exception ignored) {}
        try { if (active != null) active.close(); } catch (Exception ignored) {}
        for (NsdManager.RegistrationListener listener : registrations) try { nsd.unregisterService(listener); } catch (Exception ignored) {}
        registrations.clear();
    }
}
