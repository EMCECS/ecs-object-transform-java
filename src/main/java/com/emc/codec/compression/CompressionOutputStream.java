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

import com.emc.codec.EncodeOutputStream;
import com.emc.codec.util.CountingOutputStream;

import java.io.IOException;
import java.io.OutputStream;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public abstract class CompressionOutputStream extends EncodeOutputStream<CompressionMetadata> {
    private CompressionMetadata metadata;
    private boolean closed = false;
    private CountingOutputStream uncompressedCounter;
    private CountingOutputStream compressedCounter;
    private DigestOutputStream digester;

    /**
     * Implementation constructors must call {@link #initStreams(OutputStream)}!!
     */
    public CompressionOutputStream(OutputStream output, String encodeSpec) {
        super(output);
        this.metadata = new CompressionMetadata(encodeSpec);
    }

    protected abstract OutputStream getCompressionStream(OutputStream output) throws IOException;

    protected void initStreams(OutputStream originalStream) {
        try {
            // Create the stream chain:
            // DigestOutputStream->CountingOutputStream->[compression output stream]->CountingOutputStream->[user stream]
            compressedCounter = new CountingOutputStream(originalStream);
            OutputStream compressionStream = getCompressionStream(compressedCounter);
            uncompressedCounter = new CountingOutputStream(compressionStream);
            digester = new DigestOutputStream(uncompressedCounter, MessageDigest.getInstance("SHA1"));
            out = digester;
        } catch (NoSuchAlgorithmException e) {
            throw new CompressionException("Unable to initialize digest", e);
        } catch (IOException e) {
            throw new CompressionException("Could not create compression stream", e);
        }
    }

    @Override
    public void close() throws IOException {
        if (closed) return;
        closed = true;

        super.close();

        // this should only be executed once
        metadata.setOriginalSize(uncompressedCounter.getByteCount());
        metadata.setCompressedSize(compressedCounter.getByteCount());
        metadata.setOriginalDigest(digester.getMessageDigest().digest());

        notifyListeners();
    }

    @Override
    public CompressionMetadata getEncodeMetadata() {
        if (!closed) throw new IllegalStateException("Cannot call getEncodeMetadata until stream is closed");

        return metadata;
    }
}
