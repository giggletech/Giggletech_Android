package com.giggletech.router;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public final class Settings {
    public String sender = "", globalPowerParameter = "max_speed";
    public int port = 9001, timeout = 5;
    public boolean oscQuery = true;
    public final List<Device> devices = new ArrayList<>();
    public void validate() {
        validateIp(sender, "Quest / VRChat IP");
        validateParameter(globalPowerParameter, true);
        if (port < 1024 || port > 65535) throw new IllegalArgumentException("Receive port must be 1024–65535.");
        if (timeout < 1 || timeout > 30) throw new IllegalArgumentException("Timeout must be 1–30 seconds.");
        if (devices.isEmpty()) throw new IllegalArgumentException("Add a GiggleTech device first.");
        HashSet<String> ips = new HashSet<>();
        for (Device d : devices) { d.validate(); if (!ips.add(d.ip)) throw new IllegalArgumentException("Each device must have a different IP."); }
    }
    static void validateIp(String ip, String label) {
        String[] parts = ip.split("\\.", -1);
        if (parts.length != 4) throw new IllegalArgumentException(label + " must be an IPv4 address.");
        int[] n = new int[4];
        for (int i = 0; i < 4; i++) {
            if (!parts[i].matches("0|[1-9][0-9]{0,2}")) throw new IllegalArgumentException(label + " must be an IPv4 address.");
            n[i] = Integer.parseInt(parts[i]);
            if (n[i] > 255) throw new IllegalArgumentException(label + " must be an IPv4 address.");
        }
        if (n[0] == 0 || n[0] >= 224 || n[3] == 255) throw new IllegalArgumentException(label + " must be a unicast address.");
    }
    static void validateParameter(String value, boolean optional) {
        if (optional && value.isEmpty()) return;
        if (!value.matches("[A-Za-z0-9_/-]{1,128}") || value.startsWith("/") || value.endsWith("/") || value.contains("//"))
            throw new IllegalArgumentException("Use a parameter name without /avatar/parameters/ (letters, digits, _, -, /).");
    }
    public static String path(String parameter) { return "/avatar/parameters/" + parameter; }
}
