/*
 * Copyright (c) 2015, EMC Corporation.
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *
 * + Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimer.
 * + Redistributions in binary form must reproduce the above copyright
 * notice, this list of conditions and the following disclaimer in the
 * documentation and/or other materials provided with the distribution.
 * + The name of EMC Corporation may not be used to endorse or promote
 * products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
 * TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 *
 */

/**
 * 
 */
package com.emc.codec.compression;

import SevenZip.Compression.LZMA.Encoder;
import com.emc.codec.compression.lzma.LzmaCodec;
import com.emc.codec.compression.lzma.LzmaEncodeOutputStream;
import com.emc.codec.compression.lzma.LzmaProfile;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author cwikj
 * 
 */
public class LzmaEncodeOutputStreamTest {
    byte[] data;

    /**
     * @throws java.lang.Exception
     */
    @Before
    public void setUp() throws Exception {
        // get some data to compress.
        InputStream classin = this.getClass().getClassLoader().getResourceAsStream("uncompressed.txt");
        ByteArrayOutputStream classByteStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int c;
        while ((c = classin.read(buffer)) != -1) {
            classByteStream.write(buffer, 0, c);
        }
        data = classByteStream.toByteArray();
        
        classin.close();
    }

    @Test
    public void testMemoryRequiredInt() {
        assertEquals(209715200, LzmaProfile.memoryRequiredForLzma(5));
        assertEquals(838860800, LzmaProfile.memoryRequiredForLzma(9));
    }

    @Test
    public void testMemoryRequiredLzmaProfile() {
        assertEquals(0, LzmaProfile.memoryRequiredForLzma(new LzmaProfile(0, 0, 0)));
        assertEquals(6710886400L, LzmaProfile.memoryRequiredForLzma(new LzmaProfile(512 * 1024 * 1024, 0, 0)));
    }

    @Test
    public void testCompressMode0() throws Exception {
        runCompressMode(0);
    }

    @Test
    public void testCompressMode1() throws Exception {
        runCompressMode(1);
    }

    @Test
    public void testCompressMode2() throws Exception {
        runCompressMode(2);
    }

    @Test
    public void testCompressMode3() throws Exception {
        runCompressMode(3);
    }

    @Test
    public void testCompressMode4() throws Exception {
        runCompressMode(4);
    }

    @Test
    public void testCompressMode5() throws Exception {
        runCompressMode(5);
    }

    @Test
    public void testCompressMode6() throws Exception {
        runCompressMode(6);
    }

    @Test
    public void testCompressMode7() throws Exception {
        runCompressMode(7);
    }

    @Test
    public void testCompressMode8() throws Exception {
        runCompressMode(8);
    }

    @Test
    public void testCompressMode9() throws Exception {
        runCompressMode(9);
    }

    @Test
    public void testCustomCompressMode() throws Exception {
        System.out.println("Testing custom profile");
        // Small memory footprint but work harder (max fastBits and 64-bit matcher)
        runCompressMode(new LzmaProfile(8 * 1024, 273, Encoder.EMatchFinderTypeBT4),
                new LzmaCodec().getDefaultEncodeSpec());
    }

    @Test
    public void testCompressionMetadata() throws Exception {
        LzmaEncodeOutputStream lzma = runCompressMode(new LzmaProfile(16 * 1024, 128, Encoder.EMatchFinderTypeBT2),
                new LzmaCodec().getDefaultEncodeSpec());
        Map<String, String> m = lzma.getEncodeMetadata().toMap();
        assertEquals("Uncompressed digest incorrect", "027e997e6b1dfc97b93eb28dc9a6804096d85873",
                m.get(CompressionConstants.META_COMPRESSION_UNCOMP_SHA1));
        assertEquals("Compression ratio incorrect", "93.9%", m.get(CompressionConstants.META_COMPRESSION_COMP_RATIO));
        assertEquals("Uncompressed size incorrect", 2516125, Long.parseLong(m.get(CompressionConstants.META_COMPRESSION_UNCOMP_SIZE)));
        assertEquals("Compressed size incorrect", 154656, Long.parseLong(m.get(CompressionConstants.META_COMPRESSION_COMP_SIZE)));
    }

    private void runCompressMode(int i) throws IOException {
        System.out.println("Testing compression level " + i);

        runCompressMode(LzmaProfile.fromCompressionLevel(i), LzmaCodec.encodeSpec(i));
    }

    private LzmaEncodeOutputStream runCompressMode(LzmaProfile profile, String encodeSpec)
            throws IOException {
        long requiredMemory = LzmaProfile.memoryRequiredForLzma(profile);
        requiredMemory += 2516125 * 2; // account for buffers
        System.out.println("Estimated memory usage: " + (requiredMemory / (1024 * 1024)) + "MB");

        // Make sure there's enough RAM otherwise skip.
        Runtime.getRuntime().gc();
        long startMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        long availableMemory = Runtime.getRuntime().maxMemory() - startMemory;
        System.out.println("Available memory: " + (availableMemory / (1024 * 1024)) + "MB");

        Assume.assumeTrue("Skipping test because there is not enough available heap", availableMemory > requiredMemory);

        long now = System.currentTimeMillis();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        LzmaEncodeOutputStream lout = new LzmaEncodeOutputStream(out, encodeSpec, profile, LzmaCodec.DEFAULT_PIPE_BUFFER_SIZE);
        lout.write(data);
        long usedMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory() - startMemory;
        System.out.println("Memory used: " + (usedMemory / (1024 * 1024)) + "MB");
        lout.close();

        byte[] compressed = out.toByteArray();

        System.out.println(String.format("Original size: %d Compressed size: %d", data.length, compressed.length));
        System.out.println("Compression Ratio: " + (100 - (compressed.length * 100 / data.length)) + "%");
        System.out.println("Time: " + (System.currentTimeMillis() - now) + "ms");
        assertTrue("compressed data not smaller than original", compressed.length < data.length);

        return lout;
    }
}
