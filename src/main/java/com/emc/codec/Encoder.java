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

public interface Encoder<M extends EncodeMetadata> {
    boolean canEncode(String encodeSpec);

    String getDefaultEncodeSpec();

    int getPriority();

    boolean isSizePredictable();

    long getEncodedSize(long originalSize, Map<String, Object> codecProperties);

    long getEncodedSize(long originalSize, String encodeSpec, Map<String, Object> codecProperties);

    /**
     * This version of the method should use {@link #getDefaultEncodeSpec()}
     */
    EncodeOutputStream<M> getEncodingStream(OutputStream originalStream, Map<String, Object> codecProperties);

    /**
     * This version of the method should use {@link #getDefaultEncodeSpec()}
     */
    EncodeInputStream<M> getEncodingStream(InputStream originalStream, Map<String, Object> codecProperties);

    EncodeOutputStream<M> getEncodingStream(OutputStream originalStream, String encodeSpec,
                                            Map<String, Object> codecProperties);

    EncodeInputStream<M> getEncodingStream(InputStream originalStream, String encodeSpec,
                                           Map<String, Object> codecProperties);
}
