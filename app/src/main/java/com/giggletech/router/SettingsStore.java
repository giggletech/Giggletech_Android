package com.giggletech.router;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;

final class SettingsStore {
    static Settings load(Context c) {
        Settings s = new Settings();
        try {
            JSONObject j = new JSONObject(c.getSharedPreferences("router", 0).getString("settings", "{}"));
            s.sender = j.optString("sender", ""); s.port = j.optInt("port", 9001); s.timeout = j.optInt("timeout", 5);
            s.oscQuery = j.optBoolean("oscQuery", true); s.globalPowerParameter = j.optString("globalPowerParameter", "max_speed");
            JSONArray a = j.optJSONArray("devices");
            if (a != null) for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i); Device d = new Device();
                d.name = o.getString("name"); d.ip = o.getString("ip"); d.parameter = o.getString("parameter");
                d.onlineParameter = o.optString("onlineParameter", ""); d.powerParameter = o.optString("powerParameter", "");
                d.power = (float)o.optDouble("power", 18); d.scale = (float)o.optDouble("scale", 100);
                d.outer = (float)o.optDouble("outer", .13); d.inner = (float)o.optDouble("inner", 1);
                d.sensitivity = (float)o.optDouble("sensitivity", 22); d.smoothing = (float)o.optDouble("smoothing", 145);
                d.softcap = (float)o.optDouble("softcap", 43); d.velocity = o.optBoolean("velocity", true); d.pullAway = o.optBoolean("pullAway", true);
                s.devices.add(d);
            }
        } catch (Exception e) { RouterService.log("Cannot read saved settings: " + e.getMessage()); }
        return s;
    }
    static void save(Context c, Settings s) {
        try {
            JSONObject j = new JSONObject().put("sender", s.sender).put("port", s.port).put("timeout", s.timeout).put("oscQuery", s.oscQuery).put("globalPowerParameter", s.globalPowerParameter);
            JSONArray a = new JSONArray();
            for (Device d : s.devices) a.put(new JSONObject().put("name", d.name).put("ip", d.ip).put("parameter", d.parameter)
                .put("onlineParameter", d.onlineParameter).put("powerParameter", d.powerParameter).put("power", d.power).put("scale", d.scale)
                .put("outer", d.outer).put("inner", d.inner).put("sensitivity", d.sensitivity).put("smoothing", d.smoothing).put("softcap", d.softcap)
                .put("velocity", d.velocity).put("pullAway", d.pullAway));
            j.put("devices", a);
            if (!c.getSharedPreferences("router", 0).edit().putString("settings", j.toString()).commit()) throw new IllegalStateException("Settings could not be saved.");
        } catch (org.json.JSONException e) { throw new IllegalArgumentException("Invalid settings", e); }
    }
}
