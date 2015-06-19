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

import com.emc.codec.compression.lzma.LzmaCodec;
import com.emc.codec.compression.lzma.LzmaDecodeInputStream;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.InputStream;

import static org.junit.Assert.*;

public class LZMADecodeTest {
    private InputStream uncompressedData;
    private InputStream compressedData;

    @Before
    public void setUp() throws Exception {
        // Get streams for the compressed and uncompressed test data.
        uncompressedData = this.getClass().getClassLoader()
                .getResourceAsStream("uncompressed.txt");
        compressedData = this.getClass().getClassLoader()
                .getResourceAsStream("compressed.txt.lz");
    }
    
    @After
    public void tearDown() {
        try {
            uncompressedData.close();
            uncompressedData = null;
        } catch(Exception e) {
            // Ignore
        }
        
        try {
            compressedData.close();
            compressedData = null;
        } catch(Exception e) {
            // Ignore
        }
    }

    @Test
    public void testRead() throws Exception {
        InputStream decompressed = new LzmaDecodeInputStream(compressedData, LzmaCodec.DEFAULT_PIPE_BUFFER_SIZE);
        
        int in1, in2;
        long offset = 0;
        while((in1 = uncompressedData.read()) != -1) {
            in2 = decompressed.read();
            assertEquals("Mismatch at offset " + offset, in1, in2);
            offset++;
        }
        
        // Should be -1 at EOF
        in2 = decompressed.read();
        assertEquals("Mismatch at EOF", -1, in2);        
        
        // Close should not throw here.
        decompressed.close();
        uncompressedData.close();
    }

    @Test
    public void testReadByteArray() throws Exception {
        InputStream decompressed = new LzmaDecodeInputStream(compressedData, LzmaCodec.DEFAULT_PIPE_BUFFER_SIZE);
        
        byte[] buffer1 = new byte[4096];
        byte[] buffer2 = new byte[4096];
        int c1;
        int c2;
        long offset = 0;
        while((c1 = uncompressedData.read(buffer1)) != -1) {
            c2 = decompressed.read(buffer2);
            assertEquals("Read size mismatch at offset " + offset, c1, c2);
            assertArrayEquals(buffer1, buffer2);
            offset += c1;
        }
        
        // Next read should return -1 for EOF
        c2 = decompressed.read(buffer2);
        assertEquals("Mismatch at EOF", -1, c2);
        
        // Close should not throw here.
        decompressed.close();
        uncompressedData.close();
    }

    @Test
    public void testReadByteArrayIntInt() throws Exception {
        InputStream decompressed = new LzmaDecodeInputStream(compressedData, LzmaCodec.DEFAULT_PIPE_BUFFER_SIZE);
        
        byte[] buffer1 = new byte[4096];
        byte[] buffer2 = new byte[4096];
        int c1;
        int c2;
        long offset = 0;
        while((c1 = uncompressedData.read(buffer1, 1, 2047)) != -1) {
            c2 = 0;
            
            // If you hit a pipe buffer boundary, it might take more than one read.
            while(c2 < c1) {
                c2 += decompressed.read(buffer2, c2+1, 2047-c2);
            }
            
            assertEquals("Read size mismatch at offset " + offset, c1, c2);
            assertArrayEquals(buffer1, buffer2);
            offset += c1;
        }
        
        // Next read should return -1 for EOF
        c2 = decompressed.read(buffer2);
        assertEquals("Mismatch at EOF", -1, c2);
        
        // Close should not throw here.
        decompressed.close();
        uncompressedData.close();
    }

    @Test
    public void testSkip() throws Exception {
        InputStream decompressed = new LzmaDecodeInputStream(compressedData, LzmaCodec.DEFAULT_PIPE_BUFFER_SIZE);
        
        int in1, in2;
        long offset = 0;
        while((in1 = uncompressedData.read()) != -1) {
            in2 = decompressed.read();
            assertEquals("Mismatch at offset " + offset, in1, in2);
            
            // Skip some bytes.
            long offset1 = uncompressedData.skip(7);
            long offset2 = decompressed.skip(7);
            
            assertEquals("Skipped bytes mismatch at offset " + offset, offset1, offset2);
            
            offset += offset1 + 1;
        }
        
        // Should be -1 at EOF
        in2 = decompressed.read();
        assertEquals("Mismatch at EOF", -1, in2);        
        
        // Close should not throw here.
        decompressed.close();
        uncompressedData.close();
    }

    @Test
    public void testMarkSupported() throws Exception {
        InputStream decompressed = new LzmaDecodeInputStream(compressedData, LzmaCodec.DEFAULT_PIPE_BUFFER_SIZE);
        
        assertFalse(decompressed.markSupported());
        
        // Close should not throw here.
        decompressed.close();
    }

}
