package com.giggletech.router;

public final class Device {
    public String name = "Headpats", ip = "", parameter = "proximity_01", onlineParameter = "", powerParameter = "";
    public float power = 18, scale = 100, outer = 0.13f, inner = 1, sensitivity = 22, smoothing = 145, softcap = 43;
    public boolean velocity = true, pullAway = true;
    public void validate() {
        Settings.validateIp(ip, "Device IP");
        Settings.validateParameter(parameter, false);
        Settings.validateParameter(onlineParameter, true);
        Settings.validateParameter(powerParameter, true);
        if (name.trim().isEmpty() || name.length() > 64) throw new IllegalArgumentException("Enter a device name (up to 64 characters).");
        range(power, 0, 100, "Power"); range(scale, 0, 100, "Scale");
        range(outer, 0, 1, "Outer proximity"); range(inner, 0, 1, "Inner proximity");
        if (inner <= outer) throw new IllegalArgumentException("Inner proximity must be greater than outer proximity.");
        range(sensitivity, 1, 100, "Sensitivity"); range(smoothing, 0, 2000, "Smoothing"); range(softcap, 1, 100, "Damping cap");
    }
    static void range(float value, float min, float max, String label) {
        if (!Float.isFinite(value) || value < min || value > max) throw new IllegalArgumentException(label + " must be between " + min + " and " + max + ".");
    }
}
