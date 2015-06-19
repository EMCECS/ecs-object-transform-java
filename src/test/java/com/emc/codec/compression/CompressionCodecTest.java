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

package com.emc.codec.compression;

import com.emc.codec.AbstractCodec;
import com.emc.codec.CodecChain;
import com.emc.codec.EncodeOutputStream;
import com.emc.codec.compression.deflate.DeflateCodec;
import com.emc.codec.compression.lzma.LzmaCodec;
import com.emc.codec.compression.lzma.LzmaProfile;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

import static org.junit.Assert.*;

public class CompressionCodecTest {

    @Before
    public void setUp() throws Exception {
    }

    @After
    public void tearDown() throws Exception {
    }

    @Test
    public void testSetCompressionLevel() {
        for (int i = 0; i < 10; i++) {
            assertNotNull(LzmaProfile.fromCompressionLevel(i));
        }

        // Bounds
        try {
            LzmaProfile.fromCompressionLevel(-1);
            fail("invalid compression accepted, mode: LZMA, level: -1");
        } catch (Exception e) {
            // expected
        }
        try {
            LzmaProfile.fromCompressionLevel(10);
            fail("invalid compression accepted, mode: LZMA, level: 10");
        } catch (Exception e) {
            // expected
        }
        try {
            CompressionUtil.getEncodeSpec(DeflateCodec.SUBSPEC, -1);
            fail("invalid compression accepted, mode: Deflate, level: -1");
        } catch (Exception e) {
            // expected
        }
        try {
            CompressionUtil.getEncodeSpec(DeflateCodec.SUBSPEC, 10);
            fail("invalid compression accepted, mode: Deflate, level: 10");
        } catch (Exception e) {
            // expected
        }
    }

    @Test
    public void testGetEncodeStream() throws IOException {
        EncodeOutputStream<CompressionMetadata> compStream = new LzmaCodec().getEncodingStream(new ByteArrayOutputStream(), null);
        assertNotNull("LZMA encode stream is null", compStream);
        compStream = new DeflateCodec().getEncodingStream(new ByteArrayOutputStream(), null);
        assertNotNull("Deflate encode stream is null", compStream);
    }

    @Test
    public void testGetDecodeStream() throws IOException {
        byte[] dummyData = new byte[]{0, 0, 0, 0, 0};
        InputStream decompStream = new LzmaCodec().getDecodingStream(new ByteArrayInputStream(dummyData), new CompressionMetadata("COMP:LZMA/9"), null);
        assertNotNull("LZMA decode stream is null", decompStream);
        decompStream = new DeflateCodec().getDecodingStream(new ByteArrayInputStream(dummyData), new CompressionMetadata("COMP:Deflate/9"), null);
        assertNotNull("LZMA decode stream is null", decompStream);
    }

    @Test
    public void testCanDecode() {
        AbstractCodec lzmaCodec = new LzmaCodec();
        AbstractCodec deflateCodec = new DeflateCodec();
        assertTrue("LzmaCodec should decode 'COMP:LZMA/9'", lzmaCodec.canDecode("COMP:LZMA/9"));
        assertTrue("DeflateCodec should decode 'COMP:Deflate/5'", deflateCodec.canDecode("COMP:Deflate/5"));
        assertTrue("DeflateCodec should decode 'COMP:Deflate'", deflateCodec.canDecode("COMP:Deflate"));

        // Unsupported:
        assertFalse("LzmaCodec should not decode 'COMP'", lzmaCodec.canDecode("COMP"));
        assertFalse("LzmaCodec should not decode 'COMP:'", lzmaCodec.canDecode("COMP:"));
        assertFalse("LzmaCodec should not decode 'COMP:BZip2/2'", lzmaCodec.canDecode("COMP:BZip2/2"));
        assertFalse("LzmaCodec should not decode 'COMP:GZ/9'", lzmaCodec.canDecode("COMP:GZ/9"));
        assertFalse("LzmaCodec should not decode 'ENC:Deflate'", lzmaCodec.canDecode("ENC:Deflate"));
        assertFalse("LzmaCodec should not decode 'SomethingCrazy'", lzmaCodec.canDecode("SomethingCrazy"));
        assertFalse("LzmaCodec should not decode ':'", lzmaCodec.canDecode(":"));
        assertFalse("LzmaCodec should not decode ''", lzmaCodec.canDecode(""));
        assertFalse("DeflateCodec should not decode 'COMP'", deflateCodec.canDecode("COMP"));
        assertFalse("DeflateCodec should not decode 'COMP:'", deflateCodec.canDecode("COMP:"));
        assertFalse("DeflateCodec should not decode 'COMP:BZip2/2'", deflateCodec.canDecode("COMP:BZip2/2"));
        assertFalse("DeflateCodec should not decode 'COMP:GZ/9'", deflateCodec.canDecode("COMP:GZ/9"));
        assertFalse("DeflateCodec should not decode 'ENC:Deflate'", deflateCodec.canDecode("ENC:Deflate"));
        assertFalse("DeflateCodec should not decode 'SomethingCrazy'", deflateCodec.canDecode("SomethingCrazy"));
        assertFalse("DeflateCodec should not decode ':'", deflateCodec.canDecode(":"));
        assertFalse("DeflateCodec should not decode ''", deflateCodec.canDecode(""));
    }

    @Test
    public void testEncodeDecodeLzma() throws Exception {
        // Test the codec front-to-back.
        CodecChain codecChain = new CodecChain(LzmaCodec.encodeSpec(2));

        // Some generic metadata
        Map<String, String> metadata = new HashMap<String, String>();
        metadata.put("name1", "value1");
        metadata.put("name2", "value2");

        // Get some data to compress.
        InputStream classin = this.getClass().getClassLoader().getResourceAsStream("uncompressed.txt");
        ByteArrayOutputStream classByteStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int c;
        while ((c = classin.read(buffer)) != -1) {
            classByteStream.write(buffer, 0, c);
        }
        byte[] uncompressedData = classByteStream.toByteArray();
        classin.close();

        // Compress
        ByteArrayOutputStream compressedOutput = new ByteArrayOutputStream();
        OutputStream encodeStream = codecChain.getEncodeStream(compressedOutput, metadata);

        assertNotNull(encodeStream);
        encodeStream.write(uncompressedData);
        encodeStream.close();

        assertEquals("Uncompressed digest incorrect", "027e997e6b1dfc97b93eb28dc9a6804096d85873",
                metadata.get(CompressionConstants.META_COMPRESSION_UNCOMP_SHA1));
        assertEquals("Uncompressed size incorrect", 2516125, Long.parseLong(metadata
                .get(CompressionConstants.META_COMPRESSION_UNCOMP_SIZE)));
        assertEquals("Compression ratio incorrect", "95.1%", metadata.get(CompressionConstants.META_COMPRESSION_COMP_RATIO));
        assertEquals("Compressed size incorrect", 124271, Long.parseLong(metadata.get(CompressionConstants.META_COMPRESSION_COMP_SIZE)));
        assertEquals("name1 incorrect", "value1", metadata.get("name1"));
        assertEquals("name2 incorrect", "value2", metadata.get("name2"));

        String transformConfig = metadata.get(CodecChain.META_TRANSFORM_MODE);
        assertEquals("Transform config string incorrect", "COMP:LZMA/2", transformConfig);

        // Decompress
        ByteArrayInputStream compressedInput = new ByteArrayInputStream(compressedOutput.toByteArray());
        InputStream decompressedStream = codecChain.getDecodeStream(compressedInput, metadata);
        assertNotNull(decompressedStream);

        byte[] uncompressedData2 = new byte[uncompressedData.length];

        c = 0;
        while (c < uncompressedData2.length) {
            int x = decompressedStream.read(uncompressedData2, c, uncompressedData2.length - c);
            if (x == -1) {
                break;
            }
            c += x;
        }

        assertEquals("stream length incorrect after decompression", uncompressedData.length, c);
        assertArrayEquals("data incorrect after decompression", uncompressedData, uncompressedData2);

        // encode meta should be removed
        assertEquals("Encode metadata still present, but should be removed", 2, metadata.size());
        assertEquals("name1 incorrect", "value1", metadata.get("name1"));
        assertEquals("name2 incorrect", "value2", metadata.get("name2"));
    }

    @Test
    public void testEncodeDecodeDeflate() throws Exception {
        int compressionLevel = 8;

        // Test the factory front-to-back.
        CodecChain deflateChain = new CodecChain(DeflateCodec.encodeSpec(compressionLevel));

        // Some generic metadata
        Map<String, String> metadata = new HashMap<String, String>();
        metadata.put("name1", "value1");
        metadata.put("name2", "value2");

        // Get some data to compress.
        InputStream classin = this.getClass().getClassLoader()
                .getResourceAsStream("uncompressed.txt");
        ByteArrayOutputStream classByteStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int c;
        while ((c = classin.read(buffer)) != -1) {
            classByteStream.write(buffer, 0, c);
        }
        byte[] uncompressedData = classByteStream.toByteArray();
        classin.close();

        // Compress
        ByteArrayOutputStream compressedOutput = new ByteArrayOutputStream();
        OutputStream outStream = deflateChain.getEncodeStream(compressedOutput, metadata);
        assertNotNull(outStream);
        outStream.write(uncompressedData);
        outStream.close();

        // Compress manually using Deflate directly
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DeflaterOutputStream dos = new DeflaterOutputStream(baos, new Deflater(compressionLevel));
        dos.write(uncompressedData);
        dos.close();

        assertArrayEquals("Compressed data incorrect", baos.toByteArray(), compressedOutput.toByteArray());
        assertEquals("Uncompressed digest incorrect", "027e997e6b1dfc97b93eb28dc9a6804096d85873",
                metadata.get(CompressionConstants.META_COMPRESSION_UNCOMP_SHA1));
        assertEquals("Compression ratio incorrect", "92.0%", metadata.get(CompressionConstants.META_COMPRESSION_COMP_RATIO));
        assertEquals("Uncompressed size incorrect", 2516125, Long.parseLong(metadata.get(CompressionConstants.META_COMPRESSION_UNCOMP_SIZE)));
        assertEquals("Compressed size incorrect", 201969, Long.parseLong(metadata.get(CompressionConstants.META_COMPRESSION_COMP_SIZE)));
        assertEquals("name1 incorrect", "value1", metadata.get("name1"));
        assertEquals("name2 incorrect", "value2", metadata.get("name2"));


        String transformConfig = metadata.get(CodecChain.META_TRANSFORM_MODE);
        assertEquals("Transform config string incorrect", "COMP:Deflate/8", transformConfig);

        // Decompress
        ByteArrayInputStream compressedInput = new ByteArrayInputStream(compressedOutput.toByteArray());
        InputStream decompressedStream = deflateChain.getDecodeStream(compressedInput, metadata);
        assertNotNull(decompressedStream);
        byte[] uncompressedData2 = new byte[uncompressedData.length];

        c = 0;
        while (c < uncompressedData2.length) {
            int x = decompressedStream.read(uncompressedData2, c, uncompressedData2.length - c);
            if (x == -1) {
                break;
            }
            c += x;
        }

        assertEquals("stream length incorrect after decompression", uncompressedData.length, c);
        assertArrayEquals("data incorrect after decompression", uncompressedData, uncompressedData2);

        // encode meta should be removed
        assertEquals("Encode metadata still present, but should be removed", 2, metadata.size());
        assertEquals("name1 incorrect", "value1", metadata.get("name1"));
        assertEquals("name2 incorrect", "value2", metadata.get("name2"));
    }

    @Test
    public void testGetPriority() {
        assertEquals("default priority incorrect", 100, new DeflateCodec().getPriority());
        assertEquals("default priority incorrect", 100, new LzmaCodec().getPriority());
    }
}
