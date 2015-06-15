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

import com.emc.codec.AbstractCodec;
import com.emc.codec.EncodeInputStream;
import com.emc.codec.EncodeOutputStream;
import com.emc.codec.compression.CompressionConstants;
import com.emc.codec.compression.CompressionMetadata;
import com.emc.codec.compression.CompressionUtil;
import com.emc.codec.util.CodecUtil;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;

public class LzmaCodec extends AbstractCodec<CompressionMetadata> {
    public static final String SUBSPEC = "LZMA";
    public static final int PRIORITY = 100;

    public static final String PROP_PIPE_BUFFER_SIZE = "com.emc.codec.lzma.LzmaCodec.pipeBufferSize";
    public static final String PROP_CUSTOM_PROFILE = "com.emc.codec.lzma.LzmaCodec.customProfile";

    public static final int DEFAULT_PIPE_BUFFER_SIZE = 64 * 1024;

    public static String encodeSpec(int compressionLevel) {
        return CompressionUtil.getEncodeSpec(SUBSPEC, compressionLevel);
    }

    public static int getPipeBufferSize(Map<String, Object> codecProperties) {
        return CodecUtil.getCodecProperty(PROP_PIPE_BUFFER_SIZE, codecProperties, DEFAULT_PIPE_BUFFER_SIZE);
    }

    public static void setPipeBufferSize(Map<String, Object> codecProperties, int pipeBufferSize) {
        codecProperties.put(PROP_PIPE_BUFFER_SIZE, pipeBufferSize);
    }

    public static LzmaProfile getCustomProfile(Map<String, Object> codecProperties) {
        return CodecUtil.getCodecProperty(PROP_CUSTOM_PROFILE, codecProperties, null);
    }

    public static void setCustomProfile(Map<String, Object> codecProperties, LzmaProfile customProfile) {
        codecProperties.put(PROP_CUSTOM_PROFILE, customProfile);
    }

    @Override
    public boolean canProcess(String encodeSpec) {
        String algorithm = CodecUtil.getEncodeAlgorithm(encodeSpec);
        return CompressionConstants.COMPRESSION_TYPE.equals(CodecUtil.getEncodeType(encodeSpec))
                && algorithm != null && algorithm.startsWith(SUBSPEC);
    }

    @Override
    public String getDefaultEncodeSpec() {
        return encodeSpec(CompressionConstants.DEFAULT_COMPRESSION_LEVEL);
    }

    @Override
    public int getPriority() {
        return PRIORITY;
    }

    @Override
    public CompressionMetadata createEncodeMetadata(String encodeSpec, Map<String, String> metadata) {
        return new CompressionMetadata(encodeSpec, metadata);
    }

    @Override
    public long getDecodedSize(CompressionMetadata metadata) {
        return metadata.getOriginalSize();
    }

    @Override
    public OutputStream getDecodingStream(OutputStream originalStream, CompressionMetadata metadata,
                                          Map<String, Object> codecProperties) {
        return new LzmaDecodeOutputStream(originalStream, getPipeBufferSize(codecProperties));
    }

    @Override
    public InputStream getDecodingStream(InputStream originalStream, CompressionMetadata metadataetadata,
                                         Map<String, Object> codecProperties) {
        return new LzmaDecodeInputStream(originalStream, getPipeBufferSize(codecProperties));
    }

    @Override
    public boolean isSizePredictable() {
        return false;
    }

    @Override
    public long getEncodedSize(long originalSize, String encodeSpec, Map<String, Object> codecProperties) {
        throw new UnsupportedOperationException("compressed size is unpredictable");
    }

    @Override
    public EncodeOutputStream<CompressionMetadata> getEncodingStream(OutputStream originalStream, String encodeSpec,
                                                                     Map<String, Object> codecProperties) {
        if (!canProcess(encodeSpec)) throw new IllegalArgumentException("cannot process " + encodeSpec);
        LzmaProfile profile = getCustomProfile(codecProperties);
        if (profile == null) {
            int compressionLevel = CompressionUtil.getCompressionLevel(encodeSpec, CompressionConstants.DEFAULT_COMPRESSION_LEVEL);
            profile = LzmaProfile.fromCompressionLevel(compressionLevel);
        }
        return new LzmaEncodeOutputStream(originalStream, encodeSpec, profile, getPipeBufferSize(codecProperties));
    }

    @Override
    public EncodeInputStream<CompressionMetadata> getEncodingStream(InputStream originalStream, String encodeSpec,
                                                                    Map<String, Object> codecProperties) {
        if (!canProcess(encodeSpec)) throw new IllegalArgumentException("cannot process " + encodeSpec);
        LzmaProfile profile = getCustomProfile(codecProperties);
        if (profile == null) {
            int compressionLevel = CompressionUtil.getCompressionLevel(encodeSpec, CompressionConstants.DEFAULT_COMPRESSION_LEVEL);
            profile = LzmaProfile.fromCompressionLevel(compressionLevel);
        }
        return new LzmaEncodeInputStream(originalStream, encodeSpec, profile, getPipeBufferSize(codecProperties));
    }
}
