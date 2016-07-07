/*
 * Copyright (c) 2015-2016, EMC Corporation.
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

import java.security.KeyPair;
import java.security.Provider;
import java.security.interfaces.RSAPublicKey;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BasicKeyProvider implements KeyProvider {

    private static final Logger log = LoggerFactory.getLogger(BasicKeyProvider.class);

    private KeyPair masterKey;
    private String masterKeyFingerprint;
    private Map<String, KeyPair> keyMap = new HashMap<String, KeyPair>();
    private Provider provider;

    public BasicKeyProvider() {
    }

    public BasicKeyProvider(KeyPair masterKey, KeyPair... decryptionKeys) {
        setMasterKey(masterKey);
        for (KeyPair keyPair : decryptionKeys) {
            addKey(keyPair);
        }
    }

    protected String getFingerprint(KeyPair keyPair) {
        return EncryptionUtil.getRsaPublicKeyFingerprint((RSAPublicKey) keyPair.getPublic());
    }

    private void checkKey(KeyPair keyPair) {
        if (!(keyPair.getPublic() instanceof RSAPublicKey))
            throw new IllegalArgumentException("Only RSA KeyPairs are allowed, not " + keyPair.getPublic().getAlgorithm());
    }

    /**
     * Check for acceptable RSA key lengths. 1024-bit keys are not secure
     * anymore and 512-bit keys are unacceptable. Newer JDKs have already
     * removed support for the 512-bit keys and the 1024-bit keys may be removed
     * in the future:
     * http://mail.openjdk.java.net/pipermail/security-dev/2012-December/006195.html
     */
    private void checkKeyLength(KeyPair keyPair) {
        // RSA key length is defined as the modulus of the public key
        int keySize = ((RSAPublicKey) keyPair.getPublic()).getModulus().bitLength();
        if (keySize < 1024) {
            throw new IllegalArgumentException("The minimum RSA key size supported is 1024 bits. Your key is " + keySize + " bits");
        } else if (keySize == 1024) {
            log.warn("1024-bit RSA key detected. Support for 1024-bit RSA keys may soon be removed from the JDK. Please upgrade to a stronger key (e.g. 2048-bit).");
        }
    }

    @Override
    public KeyPair getMasterKey() {
        return masterKey;
    }

    public void setMasterKey(KeyPair masterKey) {
        checkKey(masterKey);
        checkKeyLength(masterKey);
        this.masterKey = masterKey;
        this.masterKeyFingerprint = getFingerprint(masterKey);
        addKey(masterKey);
    }

    @Override
    public String getMasterKeyFingerprint() {
        return masterKeyFingerprint;
    }

    @Override
    public KeyPair getKey(String keyFingerprint) {
        return keyMap.get(keyFingerprint);
    }

    public void addKey(KeyPair keyPair) {
        checkKey(keyPair);
        keyMap.put(getFingerprint(keyPair), keyPair);
    }

    public void removeKey(KeyPair keyPair) {
        keyMap.remove(getFingerprint(keyPair));
    }

    public Provider getProvider() {
        return provider;
    }

    public void setProvider(Provider provider) {
        this.provider = provider;
    }

    public BasicKeyProvider withMasterKey(KeyPair masterKey) {
        setMasterKey(masterKey);
        return this;
    }

    public BasicKeyProvider withKeys(KeyPair... keys) {
        this.keyMap.clear();
        for (KeyPair key : keys) addKey(key);
        return this;
    }

    public BasicKeyProvider withProvider(Provider provider) {
        setProvider(provider);
        return this;
    }
}
