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

import com.emc.codec.EncodeMetadata;
import com.emc.codec.compression.deflate.DeflateCodec;
import com.emc.codec.compression.deflate.DeflateOutputStream;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class DeflateOutputStreamTest {

    private byte[] data;

    @Before
    public void setUp() throws Exception {
        // get some data to compress.
        InputStream classin = this.getClass().getClassLoader()
                .getResourceAsStream("uncompressed.txt");
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
    public void testWrite() throws Exception {
        // Since we're just wrapping the standard Java deflater output stream, we
        // only need to test metadata generation after compression.
        ByteArrayOutputStream compressedData = new ByteArrayOutputStream();
        DeflateOutputStream out = new DeflateOutputStream(compressedData, DeflateCodec.encodeSpec(5), 5);
        out.write(data);

        EncodeMetadata encMeta = out.getEncodeMetadata();
        assertFalse("encode metadata should not be complete", encMeta.isComplete());

        out.close();

        Map<String, String> m = out.getEncodeMetadata().toMap();
        assertEquals("Uncompressed digest incorrect", "027e997e6b1dfc97b93eb28dc9a6804096d85873",
                m.get(CompressionConstants.META_COMPRESSION_UNCOMP_SHA1));
        assertEquals("Compression ratio incorrect", "91.1%",
                m.get(CompressionConstants.META_COMPRESSION_COMP_RATIO));
        assertEquals("Uncompressed size incorrect", 2516125, Long.parseLong(m
                .get(CompressionConstants.META_COMPRESSION_UNCOMP_SIZE)));
        assertEquals("Compressed size incorrect", 223548, Long.parseLong(m
                .get(CompressionConstants.META_COMPRESSION_COMP_SIZE)));

    }

}
