package com.giggletech.router;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Small bounded DNS codec for the firmware's giggletech.local hostname fallback. */
final class MdnsHostname {
    static byte[] query() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] header = {0,0,0,0,0,1,0,0,0,0,0,0}; out.write(header, 0, header.length);
        for (String label : new String[]{"giggletech", "local"}) {
            byte[] bytes = label.getBytes(StandardCharsets.US_ASCII); out.write(bytes.length); out.write(bytes, 0, bytes.length);
        }
        out.write(0); out.write(0); out.write(1); out.write(0x80); out.write(1); // A, IN, unicast response requested
        return out.toByteArray();
    }
    record Found(String hostname, String ip) {}
    static List<Found> answers(byte[] packet, int length) {
        List<Found> found = new ArrayList<>();
        try {
            Reader r = new Reader(Arrays.copyOf(packet, length));
            r.skip(2); int flags = r.u16();
            if ((flags & 0x800f) != 0x8000) return found;
            int questions = r.u16(), records = r.u16() + r.u16() + r.u16();
            if (questions > 128 || records > 256) return found;
            for (int i = 0; i < questions; i++) { r.name(); r.skip(4); }
            for (int i = 0; i < records; i++) {
                String name = r.name(); int type = r.u16(), clazz = r.u16();
                long ttl = ((long)r.u16() << 16) | r.u16(); int size = r.u16();
                if (type == 1 && (clazz & 0x7fff) == 1 && ttl > 0 && size == 4 && branded(name)) {
                    String ip = r.u8() + "." + r.u8() + "." + r.u8() + "." + r.u8();
                    Settings.validateIp(ip, "Discovered IP"); found.add(new Found(name, ip));
                } else r.skip(size);
            }
        } catch (RuntimeException invalid) { found.clear(); }
        return found;
    }
    static boolean branded(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        return n.contains("giggletech") || n.contains("gigglepuck") || n.contains("gigglespark");
    }
    private static final class Reader {
        final byte[] data; int pos;
        Reader(byte[] data) { this.data = data; }
        int u8() { if (pos >= data.length) throw new IllegalArgumentException(); return data[pos++] & 255; }
        int u16() { return (u8() << 8) | u8(); }
        void skip(int n) { if (n < 0 || pos + n > data.length) throw new IllegalArgumentException(); pos += n; }
        String name() {
            int cursor = pos, resume = -1; StringBuilder out = new StringBuilder();
            for (int hops = 0; hops < 128; hops++) {
                if (cursor >= data.length) throw new IllegalArgumentException();
                int size = data[cursor++] & 255;
                if (size == 0) { pos = resume < 0 ? cursor : resume; return out.toString(); }
                if ((size & 0xc0) == 0xc0) {
                    if (cursor >= data.length) throw new IllegalArgumentException();
                    if (resume < 0) resume = cursor + 1;
                    cursor = ((size & 63) << 8) | (data[cursor] & 255); continue;
                }
                if (size > 63 || cursor + size > data.length || out.length() + size > 253) throw new IllegalArgumentException();
                if (out.length() > 0) out.append('.');
                out.append(new String(data, cursor, size, StandardCharsets.UTF_8)); cursor += size;
            }
            throw new IllegalArgumentException("DNS compression loop");
        }
    }
}
