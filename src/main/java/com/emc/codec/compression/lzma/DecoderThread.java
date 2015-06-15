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

package com.emc.codec.compression.lzma;

import SevenZip.Compression.LZMA.Decoder;
import com.emc.codec.compression.CompressionException;
import org.apache.log4j.Logger;

import java.io.*;

public class DecoderThread extends Thread {
    private static final Logger log = Logger.getLogger(DecoderThread.class);

    public static final ThreadGroup THREAD_GROUP = new ThreadGroup("LZMA-Decompress");

    private InputStream input;
    private OutputStream output;
    private boolean errorSet = false;
    private Throwable error;

    public DecoderThread(InputStream input, OutputStream output) throws IOException {
        super(THREAD_GROUP, (Runnable) null);
        setDaemon(true);

        this.input = input;
        this.output = output;
    }

    @Override
    public void run() {
        try {
            Decoder decoder = new Decoder();

            // Read the compression settings from the stream
            byte[] properties = new byte[5];
            int c = input.read(properties);
            if (c != properties.length)
                throw new CompressionException("Unable to read compression settings from stream");

            if (!decoder.SetDecoderProperties(properties))
                throw new CompressionException("LZMA decoder rejected compression settings from stream");

            decoder.Code(input, output, -1);
        } catch (Throwable t) {
            log.error("error during decompression", t);
            error = t;
            errorSet = true;
        } finally {
            try { // make sure we close any piped streams to prevent deadlock
                if (input instanceof PipedInputStream) input.close();
                if (output instanceof PipedOutputStream) output.close();
            } catch (Throwable t) {
                log.warn("could not close pipe stream", t);
            }
        }
    }

    public boolean isErrorSet() {
        return errorSet;
    }

    public Throwable getError() {
        return error;
    }
}
