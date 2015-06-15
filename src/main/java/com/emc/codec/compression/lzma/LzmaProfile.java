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

import SevenZip.Compression.LZMA.Encoder;

public class LzmaProfile {
    /**
     * Map LZMA compression parameters into the standard 0-9 compression levels.
     */
    public static LzmaProfile LZMA_COMPRESSION_PROFILE[] = {
            new LzmaProfile(16 * 1024, 5, Encoder.EMatchFinderTypeBT2), // 0
            new LzmaProfile(64 * 1024, 64, Encoder.EMatchFinderTypeBT2), // 1
            new LzmaProfile(512 * 1024, 128, Encoder.EMatchFinderTypeBT2), // 2
            new LzmaProfile(1024 * 1024, 128, Encoder.EMatchFinderTypeBT2), // 3
            new LzmaProfile(8 * 1024 * 1024, 128, Encoder.EMatchFinderTypeBT2), // 4
            new LzmaProfile(16 * 1024 * 1024, 128, Encoder.EMatchFinderTypeBT2), // 5
            new LzmaProfile(24 * 1024 * 1024, 192, Encoder.EMatchFinderTypeBT2), // 6
            new LzmaProfile(32 * 1024 * 1024, 224, Encoder.EMatchFinderTypeBT4), // 7
            new LzmaProfile(48 * 1024 * 1024, 256, Encoder.EMatchFinderTypeBT4), // 8
            new LzmaProfile(64 * 1024 * 1024, 273, Encoder.EMatchFinderTypeBT4) // 9
    };

    public static LzmaProfile fromCompressionLevel(int compressionLevel) {
        return LZMA_COMPRESSION_PROFILE[compressionLevel];
    }

    public static long memoryRequiredForLzma(int compressionLevel) {
        return memoryRequiredForLzma(LZMA_COMPRESSION_PROFILE[compressionLevel]);
    }

    public static long memoryRequiredForLzma(LzmaProfile profile) {
        return (long) (profile.dictionarySize * 12.5);
    }

    int dictionarySize;
    int fastBytes;
    int matchFinder;
    int lc;
    int lp;
    int pb;

    public LzmaProfile(int dictionarySize, int fastBytes, int matchFinder) {
        this(dictionarySize, fastBytes, matchFinder, 3, 0, 2);
    }

    public LzmaProfile(int dictionarySize, int fastBytes, int matchFinder, int lc, int lp, int pb) {
        this.dictionarySize = dictionarySize;
        this.fastBytes = fastBytes;
        this.matchFinder = matchFinder;
        this.lc = lc;
        this.lp = lp;
        this.pb = pb;
    }

    public int getDictionarySize() {
        return dictionarySize;
    }

    public int getFastBytes() {
        return fastBytes;
    }

    public int getMatchFinder() {
        return matchFinder;
    }

    public int getLc() {
        return lc;
    }

    public int getLp() {
        return lp;
    }

    public int getPb() {
        return pb;
    }
}
