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

import org.apache.log4j.Logger;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import java.security.*;
import java.security.interfaces.RSAPublicKey;
import java.util.Properties;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class KeyUtilsTest {
    private static final Logger logger = Logger.getLogger(KeyUtilsTest.class);

    private KeyPair masterKey;
    protected Provider provider;

    @Before
    public void setUp() throws Exception {
        // Load some keys.
        Properties keyprops = new Properties();
        keyprops.load(this.getClass().getClassLoader()
                .getResourceAsStream("keys.properties"));

        masterKey = EncryptionUtil.rsaKeyPairFromBase64(
                keyprops.getProperty("masterkey.public"),
                keyprops.getProperty("masterkey.private"));
    }

    @Test
    public void testGetRsaPublicKeyFingerprint()
            throws NoSuchAlgorithmException {
        assertEquals("Key fingerprint invalid",
                "000317457b5645b7b5c4daf4cf6780c05438effd",
                EncryptionUtil.getRsaPublicKeyFingerprint((RSAPublicKey) masterKey.getPublic()));
    }

    @Test
    public void testToHexPadded() {
        byte[] dataWithoutZeroes = new byte[] { 0x11, 0x22, 0x33 };
        assertEquals("Without zeroes incorrect", "112233",
                EncryptionUtil.toHexPadded(dataWithoutZeroes));
        byte[] dataWithLeadingZero = new byte[] { 0x01, 0x22, 0x33 };
        assertEquals("With leading zero incorrect", "012233",
                EncryptionUtil.toHexPadded(dataWithLeadingZero));
        byte[] dataWithLeadingZeroBytes = new byte[] { 0x00, 0x00, 0x11, 0x22,
                0x33 };
        assertEquals("Data with leading zero bytes incorrect", "0000112233",
                EncryptionUtil.toHexPadded(dataWithLeadingZeroBytes));

    }

    @Test
    public void testEncryptDecryptKey() throws GeneralSecurityException {
        // Make an AES secret key
        KeyGenerator kg;
        if(provider != null) {
            kg = KeyGenerator.getInstance("AES", provider);
        } else {
            kg = KeyGenerator.getInstance("AES");
        }
        kg.init(128);
        SecretKey sk = kg.generateKey();
        logger.info("AES Key: " + EncryptionUtil.toHexPadded(sk.getEncoded()));

        String encryptedKey = EncryptionUtil.encryptKey(sk, provider, masterKey.getPublic());

        SecretKey sk2 = EncryptionUtil.decryptKey(encryptedKey, "AES", provider,
                masterKey.getPrivate());
        
        assertArrayEquals("Key data not equal", sk.getEncoded(), sk2.getEncoded());
        
    }
    
    // Test exception handling for computing SKI of non-key
    @Test(expected=RuntimeException.class)
    public void testBadSki() {
        EncryptionUtil.extractSubjectKeyIdentifier(new byte[5]);
    }
    
    // Test exception handling for decrypting a bad key
    @Test(expected=GeneralSecurityException.class)
    public void testDecodeBadKey() throws Exception {
        EncryptionUtil.rsaKeyPairFromBase64("aaaAAAaa", "bbBBBBbbb");
    }
    
    // Test exception handling if you try to encrypt with an invalid key.
    @Test
    public void testEncryptBadKey() throws GeneralSecurityException {
        // Generate an AES key to encrypt
        KeyGenerator kg;
        try {
            if(provider != null) {
                kg = KeyGenerator.getInstance("AES", provider);
            } else {
                kg = KeyGenerator.getInstance("AES");
            }
        } catch(NoSuchAlgorithmException e) {
            // Don't want to test this exception
            throw new RuntimeException(e);
        }
        kg.init(128);
        SecretKey sk = kg.generateKey();
        
        // Generate a DSA key pair.  Since we use the RSA algorithm, this should not work.
        KeyPairGenerator kpg;
        try {
            if(provider != null) {
                kpg = KeyPairGenerator.getInstance("DSA", provider);
            } else {
                kpg = KeyPairGenerator.getInstance("DSA");
            }
        } catch(NoSuchAlgorithmException e) {
            // don't want to test this exception
            throw new RuntimeException(e);
        }
        KeyPair dsa = kpg.generateKeyPair();
        try {
            EncryptionUtil.encryptKey(sk, null, dsa.getPublic());
            Assert.fail("DSA key should fail");
        } catch (RuntimeException e) {
            // ok
        }
    }
}
