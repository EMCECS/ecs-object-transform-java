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

import com.emc.codec.EncodeMetadata;

import javax.crypto.SecretKey;
import javax.xml.bind.DatatypeConverter;
import java.security.Provider;
import java.security.PublicKey;
import java.security.interfaces.RSAPrivateKey;
import java.util.HashMap;
import java.util.Map;

public class EncryptionMetadata extends EncodeMetadata {
    private long originalSize;
    private byte[] originalDigest;
    private String masterKeyFingerprint;
    private String encryptedKey;
    private byte[] initVector;
    private String signature;

    public EncryptionMetadata(String encodeSpec) {
        super(encodeSpec);
    }

    public EncryptionMetadata(String encodeSpec, Map<String, String> metaMap) {
        this(encodeSpec);

        initVector = EncryptionUtil.urlSafeDecodeBase64(metaMap.get(EncryptionConstants.META_ENCRYPTION_IV));
        if (initVector == null) throw new EncryptionException("no initialization vector set on object.");

        masterKeyFingerprint = metaMap.get(EncryptionConstants.META_ENCRYPTION_KEY_ID);
        if (masterKeyFingerprint == null) throw new EncryptionException("no master key ID set on object.");

        encryptedKey = metaMap.get(EncryptionConstants.META_ENCRYPTION_OBJECT_KEY);
        if (encryptedKey == null) throw new EncryptionException("no encryption key set on object.");

        String originalDigestStr = metaMap.get(EncryptionConstants.META_ENCRYPTION_UNENC_SHA1);
        if (originalDigestStr == null) throw new EncryptionException("no SHA1 digest set on object.");
        originalDigest = DatatypeConverter.parseHexBinary(originalDigestStr);

        String originalSizeStr = metaMap.get(EncryptionConstants.META_ENCRYPTION_UNENC_SIZE);
        if (originalSizeStr == null) throw new EncryptionException("no original size set on object.");
        originalSize = Long.parseLong(originalSizeStr);

        signature = metaMap.get(EncryptionConstants.META_ENCRYPTION_META_SIG);
        if (signature == null) throw new EncryptionException("no signature set on object.");
    }

    @Override
    public boolean isComplete() {
        return originalDigest != null
                && masterKeyFingerprint != null
                && encryptedKey != null
                && initVector != null
                && signature != null;
    }

    @Override
    public Map<String, String> toMap() {
        Map<String, String> metaMap = new HashMap<String, String>();
        metaMap.put(EncryptionConstants.META_ENCRYPTION_IV, EncryptionUtil.urlSafeEncodeBase64(initVector));
        metaMap.put(EncryptionConstants.META_ENCRYPTION_KEY_ID, masterKeyFingerprint);
        metaMap.put(EncryptionConstants.META_ENCRYPTION_OBJECT_KEY, encryptedKey);
        metaMap.put(EncryptionConstants.META_ENCRYPTION_UNENC_SHA1, DatatypeConverter.printHexBinary(originalDigest).toLowerCase());
        metaMap.put(EncryptionConstants.META_ENCRYPTION_UNENC_SIZE, "" + originalSize);
        metaMap.put(EncryptionConstants.META_ENCRYPTION_META_SIG, signature);
        return metaMap;
    }

    public SecretKey getSecretKey(RSAPrivateKey privateKey, Provider provider) {
        String cipherSpec = EncryptionUtil.getCipherSpec(getEncodeSpec());
        return EncryptionUtil.decryptKey(encryptedKey, EncryptionUtil.getBaseAlgorithm(cipherSpec), provider, privateKey);
    }

    public void setSecretKey(SecretKey key, PublicKey publicKey, Provider provider) {
        this.encryptedKey = EncryptionUtil.encryptKey(key, provider, publicKey);
    }

    /**
     * Call to generate a signature for all of this encryption info and assign it to the signature property (will be
     * included in the map returned by {@link #toMap()}).
     */
    public void sign(RSAPrivateKey privateKey, Provider provider) {
        signature = generateSignature(privateKey, provider);
    }

    /**
     * Call to verify the signature contained in this encryption info. This will generate a new signature with the
     * parameters provided and compare it with the signature property. If they do not match, EncryptionException is
     * thrown.
     */
    public void verifySignature(RSAPrivateKey privateKey, Provider provider) {
        String generated = generateSignature(privateKey, provider);
        if (!signature.equals(generated))
            throw new EncryptionException(String.format("signature does not match (assigned=%s, generated=%s)",
                    signature, generated));
    }

    protected String generateSignature(RSAPrivateKey privateKey, Provider provider) {
        Map<String, String> metaMap = toMap();
        metaMap.remove(EncryptionConstants.META_ENCRYPTION_META_SIG);
        return EncryptionUtil.signMetadata(metaMap, privateKey, provider);
    }

    public long getOriginalSize() {
        return originalSize;
    }

    public void setOriginalSize(long originalSize) {
        this.originalSize = originalSize;
    }

    public byte[] getOriginalDigest() {
        return originalDigest;
    }

    public void setOriginalDigest(byte[] originalDigest) {
        this.originalDigest = originalDigest;
    }

    public String getMasterKeyFingerprint() {
        return masterKeyFingerprint;
    }

    public void setMasterKeyFingerprint(String masterKeyFingerprint) {
        this.masterKeyFingerprint = masterKeyFingerprint;
    }

    public String getEncryptedKey() {
        return encryptedKey;
    }

    public void setEncryptedKey(String encryptedKey) {
        this.encryptedKey = encryptedKey;
    }

    public byte[] getInitVector() {
        return initVector;
    }

    public void setInitVector(byte[] initVector) {
        this.initVector = initVector;
    }

    public String getSignature() {
        return signature;
    }

    public void setSignature(String signature) {
        this.signature = signature;
    }
}
