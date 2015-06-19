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

public class EncryptionConstants {
    public static final String ENCRYPTION_TYPE = "ENC";

    public static final String META_SIGNATURE_ALGORITHM = "SHA256withRSA";
    public static final String KEY_ENCRYPTION_CIPHER = "RSA/ECB/OAEPWithSHA-1AndMGF1Padding";

    public static final String META_ENCRYPTION_PREFIX = "x-emc-enc-";

    public static final String META_ENCRYPTION_KEY_ID = META_ENCRYPTION_PREFIX + "key-id";
    public static final String META_ENCRYPTION_OBJECT_KEY = META_ENCRYPTION_PREFIX + "object-key";
    public static final String META_ENCRYPTION_IV = META_ENCRYPTION_PREFIX + "iv";
    public static final String META_ENCRYPTION_UNENC_SIZE = META_ENCRYPTION_PREFIX + "unencrypted-size";
    public static final String META_ENCRYPTION_UNENC_SHA1 = META_ENCRYPTION_PREFIX + "unencrypted-sha1";
    public static final String META_ENCRYPTION_META_SIG = META_ENCRYPTION_PREFIX + "metadata-signature";
}
