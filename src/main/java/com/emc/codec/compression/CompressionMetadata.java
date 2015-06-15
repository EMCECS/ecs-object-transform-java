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
import com.emc.codec.encryption.EncryptionException;
import com.emc.codec.encryption.EncryptionUtil;
import com.emc.codec.util.CodecUtil;

import javax.xml.bind.DatatypeConverter;
import java.util.HashMap;
import java.util.Map;

public class CompressionMetadata extends EncodeMetadata {
    private long originalSize;
    private long compressedSize;
    private double compressionRatio;
    private byte[] originalDigest;

    public CompressionMetadata(String encodeSpec) {
        super(encodeSpec);
        if (!CompressionConstants.COMPRESSION_TYPE.equals(CodecUtil.getEncodeType(encodeSpec)))
            throw new IllegalArgumentException("encodeSpec is not a compression type");
    }

    public CompressionMetadata(String encodeSpec, Map<String, String> metaMap) {
        this(encodeSpec);

        String originalSizeStr = metaMap.get(CompressionConstants.META_COMPRESSION_UNCOMP_SIZE);
        if (originalSizeStr == null) throw new CompressionException("no original size set on object.");
        originalSize = Long.parseLong(originalSizeStr);

        String compressedSizeStr = metaMap.get(CompressionConstants.META_COMPRESSION_COMP_SIZE);
        if (compressedSizeStr == null) throw new CompressionException("no compressed size set on object");
        compressedSize = Long.parseLong(compressedSizeStr);

        String compressionRatioStr = metaMap.get(CompressionConstants.META_COMPRESSION_COMP_RATIO);
        if (compressionRatioStr == null) throw new CompressionException("no compression ratio set on object");
        compressionRatioStr = compressionRatioStr.replaceAll("%$", "");
        compressionRatio = Float.parseFloat(compressionRatioStr);

        String originalDigestStr = metaMap.get(CompressionConstants.META_COMPRESSION_UNCOMP_SHA1);
        if (originalDigestStr == null) throw new EncryptionException("no SHA1 digest set on object.");
        originalDigest = DatatypeConverter.parseHexBinary(originalDigestStr);
    }

    @Override
    public Map<String, String> toMap() {
        Map<String, String> metaMap = new HashMap<String, String>();
        metaMap.put(CompressionConstants.META_COMPRESSION_UNCOMP_SIZE, "" + originalSize);
        metaMap.put(CompressionConstants.META_COMPRESSION_COMP_SIZE, "" + compressedSize);
        metaMap.put(CompressionConstants.META_COMPRESSION_COMP_RATIO, String.format("%.1f%%", compressionRatio));
        metaMap.put(CompressionConstants.META_COMPRESSION_UNCOMP_SHA1, EncryptionUtil.toHexPadded(originalDigest));
        return metaMap;
    }

    protected void calculateCompressionRatio() {
        if (originalSize > 0 && compressedSize > 0) compressionRatio = 100.0 - (compressedSize * 100.0 / originalSize);
    }

    public long getOriginalSize() {
        return originalSize;
    }

    public void setOriginalSize(long originalSize) {
        this.originalSize = originalSize;
        calculateCompressionRatio();
    }

    public long getCompressedSize() {
        return compressedSize;
    }

    public void setCompressedSize(long compressedSize) {
        this.compressedSize = compressedSize;
        calculateCompressionRatio();
    }

    public double getCompressionRatio() {
        return compressionRatio;
    }

    public byte[] getOriginalDigest() {
        return originalDigest;
    }

    public void setOriginalDigest(byte[] originalDigest) {
        this.originalDigest = originalDigest;
    }
}
