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

package com.emc.codec;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TestUtil {

    private static final Logger l4j = LoggerFactory.getLogger(TestUtil.class);

    public static byte[] getOriginalData() throws IOException {
        // Get some data to compress.
        InputStream classin = getOriginalStream();
        ByteArrayOutputStream classByteStream = new ByteArrayOutputStream();

        copyStream(classin, classByteStream, true);

        return classByteStream.toByteArray();
    }

    public static InputStream getOriginalStream() {
        return TestUtil.class.getClassLoader().getResourceAsStream("uncompressed.txt");
    }

    public static void copyStream(InputStream input, OutputStream output, boolean closeStreams) throws IOException {
        byte[] buffer = new byte[4096];
        int c;
        try {
            while ((c = input.read(buffer)) != -1) {
                output.write(buffer, 0, c);
            }
        } finally {
            if (closeStreams) {
                try {
                    input.close();
                } catch (Throwable t) {
                    l4j.warn("could not close input", t);
                }
                try {
                    output.close();
                } catch (Throwable t) {
                    l4j.warn("could not close output", t);
                }
            }
        }
    }
}
