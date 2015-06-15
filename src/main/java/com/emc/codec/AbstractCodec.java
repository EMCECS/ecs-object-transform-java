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

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;

public abstract class AbstractCodec<M extends EncodeMetadata> implements Encoder<M>, Decoder<M>, Comparable<AbstractCodec<M>> {
    @Override
    public boolean canEncode(String encodeSpec) {
        return canProcess(encodeSpec);
    }

    @Override
    public boolean canDecode(String encodeSpec) {
        return canProcess(encodeSpec);
    }

    protected abstract boolean canProcess(String encodeSpec);

    @Override
    public long getEncodedSize(long originalSize, Map<String, Object> codecProperties) {
        return getEncodedSize(originalSize, getDefaultEncodeSpec(), codecProperties);
    }

    @Override
    public EncodeOutputStream<M> getEncodingStream(OutputStream originalStream, Map<String, Object> codecProperties) {
        return getEncodingStream(originalStream, getDefaultEncodeSpec(), codecProperties);
    }

    @Override
    public EncodeInputStream<M> getEncodingStream(InputStream originalStream, Map<String, Object> codecProperties) {
        return getEncodingStream(originalStream, getDefaultEncodeSpec(), codecProperties);
    }

    @Override
    public int compareTo(AbstractCodec<M> o) {
        return getPriority() - o.getPriority();
    }
}
