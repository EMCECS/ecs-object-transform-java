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

package com.emc.codec.encryption;

import com.emc.codec.EncodeOutputStream;
import com.emc.codec.util.CountingOutputStream;

import javax.crypto.Cipher;
import javax.crypto.CipherOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class EncryptionOutputStream extends EncodeOutputStream<EncryptionMetadata> {
    private EncryptionMetadata metadata;
    boolean closed = false;
    private DigestOutputStream digestStream;
    private CountingOutputStream counterStream;

    public EncryptionOutputStream(OutputStream originalStream, String encodeSpec, Cipher cipher, String encryptedKey) {
        super(originalStream);
        metadata = new EncryptionMetadata(encodeSpec);
        metadata.setEncryptedKey(encryptedKey);
        metadata.setInitVector(cipher.getIV());

        // Create the stream chain:
        // CountingOutputStream->DigestOutputStream->CipherOutputStream->[user stream].
        try {
            CipherOutputStream cipherStream = new CipherOutputStream(originalStream, cipher);
            digestStream = new DigestOutputStream(cipherStream, MessageDigest.getInstance("SHA1"));
            counterStream = new CountingOutputStream(digestStream);
            out = counterStream;
        } catch (NoSuchAlgorithmException e) {
            throw new EncryptionException("Unable to initialize digest", e);
        }
    }

    @Override
    public void close() throws IOException {
        if(closed) return;
        closed = true;

        super.close();

        // this should only be executed once
        metadata.setOriginalSize(counterStream.getByteCount());
        metadata.setOriginalDigest(digestStream.getMessageDigest().digest());

        notifyListeners();
    }

    @Override
    public EncryptionMetadata getEncodeMetadata() {
        if (!closed) throw new IllegalStateException("Cannot getEncodeMetadata until stream is closed");
        return metadata;
    }
}
