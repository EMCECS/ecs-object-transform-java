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

import java.security.*;
import java.util.Enumeration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KeystoreKeyProvider extends BasicKeyProvider {

    private static final Logger log = LoggerFactory.getLogger(KeystoreKeyProvider.class);

    private KeyStore keyStore;
    private char[] keyStorePass;

    /**
     * Loads all key pairs from the specified key store and identifies the master key by the specified alias.
     * <p>
     * This assumes that all keys share the same password.
     */
    public KeystoreKeyProvider(KeyStore keyStore, char[] keyStorePass, String masterKeyAlias)
            throws KeyStoreException, NoSuchAlgorithmException, InvalidKeyException, UnrecoverableKeyException {
        this.keyStore = keyStore;
        this.keyStorePass = keyStorePass;

        // add keys from key store
        for (Enumeration<String> aliases = keyStore.aliases(); aliases.hasMoreElements(); ) {
            String alias = aliases.nextElement();
            try {
                addKey(getKeyFromAlias(alias));
            } catch (GeneralSecurityException e) {
                log.warn("cannot retrieve key " + alias, e);
            }
        }

        // set master key
        setMasterKeyAlias(masterKeyAlias);
    }

    public void setMasterKeyAlias(String masterKeyAlias)
            throws KeyStoreException, NoSuchAlgorithmException, InvalidKeyException, UnrecoverableKeyException {
        KeyPair masterKey = getKeyFromAlias(masterKeyAlias);

        // make sure the master key alias exists.
        if (masterKey == null)
            throw new InvalidKeyException("No certificate found in keystore for alias " + masterKeyAlias);

        setMasterKey(masterKey);
    }

    public KeyPair getKeyFromAlias(String alias)
            throws KeyStoreException, NoSuchAlgorithmException, InvalidKeyException, UnrecoverableKeyException {
        if (!keyStore.containsAlias(alias)) return null;

        // get public and private key to create key pair
        java.security.cert.Certificate keyCert = keyStore.getCertificate(alias);
        if (keyCert == null) throw new InvalidKeyException("Certificate for alias " + alias + " not found");

        PrivateKey privateKey = (PrivateKey) keyStore.getKey(alias, keyStorePass);
        if (privateKey == null) throw new InvalidKeyException("Private key for alias " + alias + " not found");

        return new KeyPair(keyCert.getPublicKey(), privateKey);
    }
}
