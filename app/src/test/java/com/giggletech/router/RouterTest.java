package com.giggletech.router;

import org.junit.Test;
import static org.junit.Assert.*;
import java.nio.ByteBuffer;
import java.util.Random;

public class RouterTest {
    @Test public void oscKnownWireFormatAndRoundTrip() {
        byte[] encoded = Osc.encode("/motor", 42);
        assertArrayEquals(new byte[]{47,109,111,116,111,114,0,0,44,105,0,0,0,0,0,42}, encoded);
        assertEquals(42, Osc.decode(encoded, encoded.length).get(0).value());
        for (Object value : new Object[]{.5f, 100, true, false}) {
            byte[] data = Osc.encode("/avatar/parameters/proximity_01", value);
            assertEquals(value, Osc.decode(data, data.length).get(0).value());
        }
    }
    private byte[] bundle(byte[]... elements) {
        int length = 16; for (byte[] element : elements) length += 4 + element.length;
        ByteBuffer buffer = ByteBuffer.allocate(length); buffer.put(new byte[]{35,98,117,110,100,108,101,0}).putLong(1);
        for (byte[] element : elements) buffer.putInt(element.length).put(element);
        return buffer.array();
    }
    @Test public void nestedBundlesAndAtomicMalformedRejection() {
        byte[] packet = bundle(Osc.encode("/a", .2f), bundle(Osc.encode("/b", true)));
        assertEquals(2, Osc.decode(packet, packet.length).size());
        byte[] bad = bundle(Osc.encode("/a", 1), new byte[]{1,2,3,4});
        assertTrue(Osc.decode(bad, bad.length).isEmpty());
        byte[] nan = Osc.encode("/a", Float.NaN); assertTrue(Osc.decode(nan, nan.length).isEmpty());
        for (int n = 0; n < packet.length; n++) Osc.decode(packet, n);
    }
    @Test public void arbitraryDatagramsNeverCrashDecoder() {
        Random random = new Random(98);
        for (int i = 0; i < 10000; i++) { byte[] data = new byte[random.nextInt(500)]; random.nextBytes(data); assertNotNull(Osc.decode(data, data.length)); }
    }
    @Test public void proximityHonorsBandCapAndZero() {
        Device d = new Device(); d.velocity = false; d.power = 1; Motor m = new Motor();
        assertEquals(0, m.sample(d, .1f, 1_000_000_000L));
        int tx = m.sample(d, .9f, 2_000_000_000L); assertTrue(tx <= 2);
        assertEquals(0, m.sample(d, 0, 3_000_000_000L));
        d.power = 0; assertEquals(0, m.sample(d, 1, 4_000_000_000L));
    }
    @Test public void velocityRetreatSymmetryAndSpikeFilter() {
        Device d = new Device(); d.smoothing = 0; d.power = 50;
        Motor a = new Motor(), b = new Motor();
        a.sample(d, .2f, 1_000_000_000L); b.sample(d, .25f, 1_000_000_000L);
        int approach = a.sample(d, .25f, 1_033_000_000L), retreat = b.sample(d, .2f, 1_033_000_000L);
        assertTrue(approach > 0); assertEquals(approach, retreat);
        Motor spike = new Motor(); spike.sample(d, .55f, 1_000_000_000L); assertEquals(0, spike.sample(d, .05f, 1_016_000_000L));
        d.pullAway = false; Motor noRetreat = new Motor(); noRetreat.sample(d, .25f, 1_000_000_000L); assertEquals(0, noRetreat.sample(d, .2f, 1_033_000_000L));
    }
    @Test public void timeoutAndResetClearVelocityHistory() {
        Motor m = new Motor(); Device d = new Device(); m.sample(d, .2f, 1_000_000_000L);
        assertFalse(m.expired(5_999_000_000L, 5)); assertTrue(m.expired(6_000_000_000L, 5));
        m.reset(); assertEquals(0, m.sample(d, .8f, 7_000_000_000L));
    }
    @Test public void outputNeverExceedsPowerAcrossRandomInputs() {
        Random r = new Random(1); Device d = new Device(); Motor m = new Motor();
        for (int i = 1; i <= 10000; i++) {
            d.power = r.nextFloat() * 100; d.scale = r.nextFloat() * 100; d.velocity = r.nextBoolean(); m.remotePower = r.nextFloat();
            int value = m.sample(d, r.nextFloat(), i * 20_000_000L);
            assertTrue(value >= 0); assertTrue(value <= Math.round(d.power / 100 * m.remotePower * .66f * d.scale / 100 * 255));
        }
    }
    @Test public void validationRejectsInvalidDestinationsAndNan() {
        for (String ip : new String[]{"", "256.1.1.1", "1.2.3", "224.0.0.1", "0.0.0.0", "192.168.1.255", "01.2.3.4"}) {
            try { Settings.validateIp(ip, "IP"); fail(ip); } catch (IllegalArgumentException expected) {}
        }
        Device d = new Device(); d.ip = "192.168.1.69"; d.power = Float.NaN;
        try { d.validate(); fail(); } catch (IllegalArgumentException expected) {}
    }
}
