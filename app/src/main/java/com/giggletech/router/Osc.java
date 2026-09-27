package com.giggletech.router;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Bounded OSC 1.0 codec. A malformed packet is discarded atomically. */
public final class Osc {
    public record Message(String path, Object value) {}
    public static byte[] encode(String path, Object value) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        string(out, path);
        string(out, value instanceof Boolean ? ((boolean)value ? ",T" : ",F") : value instanceof Float ? ",f" : ",i");
        if (!(value instanceof Boolean)) {
            byte[] bytes = ByteBuffer.allocate(4).putInt(value instanceof Float ? Float.floatToIntBits((float)value) : ((Number)value).intValue()).array();
            out.write(bytes, 0, bytes.length);
        }
        return out.toByteArray();
    }
    private static void string(ByteArrayOutputStream out, String s) {
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8); out.write(bytes, 0, bytes.length); out.write(0);
        while (out.size() % 4 != 0) out.write(0);
    }
    private static String string(ByteBuffer b) {
        int start = b.position(), end = start;
        while (b.hasRemaining() && b.get() != 0) end++;
        if (end == b.limit()) throw new IllegalArgumentException("Unterminated OSC string");
        byte[] data = new byte[end - start];
        int next = (end + 4) & ~3;
        if (next > b.limit()) throw new IllegalArgumentException("Truncated OSC padding");
        b.position(start); b.get(data); b.position(next);
        return new String(data, StandardCharsets.UTF_8);
    }
    public static List<Message> decode(byte[] data, int length) {
        List<Message> result = new ArrayList<>();
        try { decode(ByteBuffer.wrap(data, 0, length).slice().order(ByteOrder.BIG_ENDIAN), result, 0); }
        catch (RuntimeException e) { result.clear(); }
        return result;
    }
    private static void decode(ByteBuffer b, List<Message> result, int depth) {
        if (depth > 8 || result.size() > 256) throw new IllegalArgumentException("OSC bundle limit");
        String path = string(b);
        if (path.equals("#bundle")) {
            b.getLong(); // VRChat uses immediate bundles; timestamps do not schedule motor commands.
            while (b.hasRemaining()) {
                int size = b.getInt();
                if (size <= 0 || size > b.remaining() || size % 4 != 0) throw new IllegalArgumentException("Invalid OSC bundle element");
                ByteBuffer part = b.slice().order(ByteOrder.BIG_ENDIAN); part.limit(size);
                decode(part, result, depth + 1); b.position(b.position() + size);
            }
            return;
        }
        if (!path.startsWith("/")) throw new IllegalArgumentException("Invalid OSC address");
        String tags = string(b);
        Object value;
        switch (tags) {
            case ",f": value = b.getFloat(); if (!Float.isFinite((float)value)) throw new IllegalArgumentException("Nonfinite OSC"); break;
            case ",i": value = b.getInt(); break;
            case ",T": value = true; break;
            case ",F": value = false; break;
            case ",s": value = string(b); break;
            default: return;
        }
        if (b.hasRemaining()) throw new IllegalArgumentException("Trailing OSC data");
        result.add(new Message(path, value));
    }
}
