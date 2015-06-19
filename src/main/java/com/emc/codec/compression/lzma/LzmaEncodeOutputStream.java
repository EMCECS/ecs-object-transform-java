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

import com.emc.codec.compression.CompressionException;
import com.emc.codec.compression.CompressionOutputStream;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;

public class LzmaEncodeOutputStream extends CompressionOutputStream {
    private OutputStream postPipeStream;
    private LzmaProfile compressionProfile;
    private int bufferSize;
    private PipedOutputStream outputPipe;
    private EncoderThread encoderThread;

    public LzmaEncodeOutputStream(OutputStream out, String encodeSpec, LzmaProfile compressionProfile, int bufferSize) {
        super(out, encodeSpec);
        this.compressionProfile = compressionProfile;
        this.bufferSize = bufferSize;
        initStreams(out);
    }

    @Override
    protected OutputStream getCompressionStream(OutputStream output) throws IOException {
        this.postPipeStream = output;

        // The LZMA Encoder reads from an input stream and writes to an output stream and
        // thus does not make a good filter.  We need to create a pipe and and use an
        // auxiliary thread to compress the data.
        PipedInputStream inputPipe = new PipedInputStream(bufferSize);
        outputPipe = new PipedOutputStream(inputPipe);

        encoderThread = new EncoderThread(compressionProfile, inputPipe, output);
        encoderThread.start();

        return outputPipe;
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        checkForError();
        super.write(b, off, len);
    }

    @Override
    public void write(byte[] b) throws IOException {
        checkForError();
        super.write(b);
    }

    @Override
    public void write(int b) throws IOException {
        checkForError();
        super.write(b);
    }

    @Override
    public void close() throws IOException {

        // make sure we flush the pipe
        try {
            outputPipe.close();
            encoderThread.join();
        } catch (InterruptedException e) {
            throw new CompressionException("interrupted while waiting for encoder thread", e);
        }

        super.close();

        // make sure we close the wrapped stream (this doesn't happen anywhere else)
        postPipeStream.close();

        // Free the encoder
        encoderThread = null;
    }

    protected void checkForError() {
        if (encoderThread != null && encoderThread.isErrorSet()) {
            Throwable t = encoderThread.getError();
            if (t instanceof RuntimeException) throw (RuntimeException) t;
            throw new CompressionException("Compression error", t);
        }
    }
}
