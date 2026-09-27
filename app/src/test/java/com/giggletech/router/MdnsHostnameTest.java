package com.giggletech.router;

import org.junit.Test;
import static org.junit.Assert.*;
import java.nio.ByteBuffer;
import java.util.*;

public class MdnsHostnameTest {
    private byte[] response() {
        byte[] question = MdnsHostname.query();
        ByteBuffer out = ByteBuffer.allocate(question.length + 16);
        out.put(question); out.putShort(2, (short)0x8400); out.putShort(6, (short)1);
        out.putShort((short)0xc00c).putShort((short)1).putShort((short)0x8001).putInt(120).putShort((short)4);
        out.put(new byte[]{(byte)192,(byte)168,1,69}); return out.array();
    }
    @Test public void compressedAnswerMatchesFirmwareHostname() {
        byte[] data = response(); List<MdnsHostname.Found> results = MdnsHostname.answers(data, data.length);
        assertEquals(1, results.size()); assertEquals("giggletech.local", results.get(0).hostname()); assertEquals("192.168.1.69", results.get(0).ip());
    }
    @Test public void malformedPacketsAndCompressionLoopsAreRejected() {
        byte[] data = response();
        for (int length = 0; length < data.length; length++) assertTrue(MdnsHostname.answers(data, length).isEmpty());
        data[12] = (byte)0xc0; data[13] = 12; assertTrue(MdnsHostname.answers(data, data.length).isEmpty());
        Random random = new Random(7);
        for (int i = 0; i < 1000; i++) { byte[] packet = new byte[random.nextInt(500)]; random.nextBytes(packet); MdnsHostname.answers(packet, packet.length); }
    }
    @Test public void queriesUnrelatedHostsAndExpiredRecordsAreIgnored() {
        byte[] question = MdnsHostname.query(); assertTrue(MdnsHostname.answers(question, question.length).isEmpty());
        byte[] data = response(); data[13] = 'x'; assertTrue(MdnsHostname.answers(data, data.length).isEmpty());
        data = response(); int ttl = question.length + 6; Arrays.fill(data, ttl, ttl + 4, (byte)0); assertTrue(MdnsHostname.answers(data, data.length).isEmpty());
    }
}
