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

import com.emc.codec.CodecChain;
import com.emc.codec.EncodeInputStream;
import com.emc.codec.EncodeMetadata;
import com.emc.codec.EncodeOutputStream;
import org.apache.log4j.Logger;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import javax.crypto.Cipher;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Provider;
import java.security.SecureRandom;
import java.security.interfaces.RSAPublicKey;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static org.junit.Assert.*;

public class EncryptionCodecTest {
    private static final Logger logger = Logger.getLogger(EncryptionCodecTest.class);

    private Properties keyprops;
    private KeyPair masterKey;
    private KeyPair oldKey;
    protected BasicKeyProvider keyProvider;
    protected Provider provider;
    protected String encodeSpec = EncryptionCodec.encodeSpec(EncryptionCodec.AES_CBC_PKCS5_CIPHER);
    protected Map<String, Object> codecProperties = new HashMap<String, Object>();

    @Before
    public void setUp() throws Exception {
        // Load some keys.
        keyprops = new Properties();
        keyprops.load(this.getClass().getClassLoader().getResourceAsStream("keys.properties"));

        masterKey = EncryptionUtil.rsaKeyPairFromBase64(keyprops.getProperty("masterkey.public"),
                keyprops.getProperty("masterkey.private"));
        oldKey = EncryptionUtil.rsaKeyPairFromBase64(keyprops.getProperty("oldkey.public"),
                keyprops.getProperty("oldkey.private"));

        keyProvider = new BasicKeyProvider().withProvider(provider);

        codecProperties.put(EncryptionCodec.PROP_SECURITY_PROVIDER, provider);
        codecProperties.put(EncryptionCodec.PROP_KEY_PROVIDER, keyProvider);
    }

    @Test
    public void testRejectSmallKey() throws Exception {
        // An RSA key < 1024 bits should be rejected as a master key.
        KeyPair smallKey;
        try {
            smallKey = EncryptionUtil.rsaKeyPairFromBase64(keyprops.getProperty("smallkey.public"),
                    keyprops.getProperty("smallkey.private"));
        } catch (Exception e) {
            // Good!
            logger.info("Key was properly rejected by JVM: " + e);
            return;
        }

        try {
            keyProvider.setMasterKey(smallKey);
        } catch (Exception e) {
            // Good!
            logger.info("Key was properly rejected by factory: " + e);
            return;
        }

        fail("RSA key < 1024 bits should have been rejected by factory");
    }

    @Test
    public void testSetMasterEncryptionKey() throws Exception {
        keyProvider.setMasterKey(masterKey);

        String masterFingerprint = EncryptionUtil.getRsaPublicKeyFingerprint((RSAPublicKey) masterKey.getPublic());
        Assert.assertEquals(masterFingerprint, keyProvider.getMasterKeyFingerprint());
        Assert.assertNotNull(keyProvider.getKey(masterFingerprint));
    }

    @Test
    public void testAddMasterDecryptionKey() throws Exception {
        keyProvider.withKeys(oldKey).setMasterKey(masterKey);

        String masterFingerprint = EncryptionUtil.getRsaPublicKeyFingerprint((RSAPublicKey) masterKey.getPublic());
        String oldFingerprint = EncryptionUtil.getRsaPublicKeyFingerprint((RSAPublicKey) oldKey.getPublic());
        Assert.assertEquals(masterFingerprint, keyProvider.getMasterKeyFingerprint());
        Assert.assertNotNull(keyProvider.getKey(masterFingerprint));
        Assert.assertNotNull(keyProvider.getKey(oldFingerprint));
    }

    @Test
    public void testGetOutputTransformPush() throws Exception {
        keyProvider.withMasterKey(masterKey).withKeys(oldKey);
        EncryptionCodec codec = new EncryptionCodec();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Map<String, String> metadata = new HashMap<String, String>();
        metadata.put("name1", "value1");
        metadata.put("name2", "value2");

        // Get some data to encrypt.
        InputStream classin = this.getClass().getClassLoader().getResourceAsStream("uncompressed.txt");
        ByteArrayOutputStream classByteStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int c;
        while ((c = classin.read(buffer)) != -1) {
            classByteStream.write(buffer, 0, c);
        }
        byte[] uncompressedData = classByteStream.toByteArray();
        classin.close();

        EncodeOutputStream encryptedStream = codec.getEncodingStream(out, encodeSpec, codecProperties);
        encryptedStream.write(uncompressedData);

        EncodeMetadata encMeta = encryptedStream.getEncodeMetadata();
        assertFalse("encode metadata should not be complete", encMeta.isComplete());

        encryptedStream.close();

        // add encode metadata to map
        metadata.putAll(encryptedStream.getEncodeMetadata().toMap());

        assertEquals("Uncompressed digest incorrect", "027e997e6b1dfc97b93eb28dc9a6804096d85873",
                metadata.get(EncryptionConstants.META_ENCRYPTION_UNENC_SHA1));
        assertEquals("Uncompressed size incorrect", 2516125,
                Long.parseLong(metadata.get(EncryptionConstants.META_ENCRYPTION_UNENC_SIZE)));
        assertNotNull("Missing IV", metadata.get(EncryptionConstants.META_ENCRYPTION_IV));
        assertEquals("Incorrect master encryption key ID",
                EncryptionUtil.getRsaPublicKeyFingerprint((RSAPublicKey) masterKey.getPublic()),
                metadata.get(EncryptionConstants.META_ENCRYPTION_KEY_ID));
        assertNotNull("Missing object key", metadata.get(EncryptionConstants.META_ENCRYPTION_OBJECT_KEY));
        assertNotNull("Missing metadata signature", metadata.get(EncryptionConstants.META_ENCRYPTION_META_SIG));
        assertEquals("name1 incorrect", "value1", metadata.get("name1"));
        assertEquals("name2 incorrect", "value2", metadata.get("name2"));

        String transformConfig = encryptedStream.getEncodeMetadata().getEncodeSpec();
        assertEquals("Transform config string incorrect", "ENC:AES/CBC/PKCS5Padding", transformConfig);

        logger.info("Encoded metadata: " + metadata);

    }
    
    @Test
    public void testGetOutputTransformPull() throws Exception {
        // Get some data to encrypt.
        InputStream classIn = this.getClass().getClassLoader().getResourceAsStream("uncompressed.txt");
        ByteArrayOutputStream classByteStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int c;
        while ((c = classIn.read(buffer)) != -1) {
            classByteStream.write(buffer, 0, c);
        }
        byte[] uncompressedData = classByteStream.toByteArray();
        classIn.close();

        keyProvider.withMasterKey(masterKey).withKeys(oldKey);
        EncryptionCodec codec = new EncryptionCodec();

        ByteArrayInputStream in = new ByteArrayInputStream(uncompressedData);

        Map<String, String> metadata = new HashMap<String, String>();
        metadata.put("name1", "value1");
        metadata.put("name2", "value2");
        EncodeInputStream<EncryptionMetadata> encStream = codec.getEncodingStream(in, encodeSpec, codecProperties);

        c = 0;
        while (c != -1) {
            c = encStream.read(buffer);
        }

        EncodeMetadata encMeta = encStream.getEncodeMetadata();
        assertFalse("encode metadata should not be complete", encMeta.isComplete());

        encStream.close();

        metadata.putAll(encStream.getEncodeMetadata().toMap());

        assertEquals("Uncompressed digest incorrect", "027e997e6b1dfc97b93eb28dc9a6804096d85873",
                metadata.get(EncryptionConstants.META_ENCRYPTION_UNENC_SHA1));
        assertEquals("Uncompressed size incorrect", 2516125,
                Long.parseLong(metadata.get(EncryptionConstants.META_ENCRYPTION_UNENC_SIZE)));
        assertNotNull("Missing IV", metadata.get(EncryptionConstants.META_ENCRYPTION_IV));
        assertEquals("Incorrect master encryption key ID",
                EncryptionUtil.getRsaPublicKeyFingerprint((RSAPublicKey) masterKey.getPublic()),
                metadata.get(EncryptionConstants.META_ENCRYPTION_KEY_ID));
        assertNotNull("Missing object key", metadata.get(EncryptionConstants.META_ENCRYPTION_OBJECT_KEY));
        assertNotNull("Missing metadata signature", metadata.get(EncryptionConstants.META_ENCRYPTION_META_SIG));
        assertEquals("name1 incorrect", "value1", metadata.get("name1"));
        assertEquals("name2 incorrect", "value2", metadata.get("name2"));

        String transformConfig = encStream.getEncodeMetadata().getEncodeSpec();
        assertEquals("Transform config string incorrect", "ENC:AES/CBC/PKCS5Padding", transformConfig);

        logger.info("Encoded metadata: " + metadata);
    }

    @Test
    public void testGetInputTransform() throws Exception {
        Map<String, String> objectMetadata = new HashMap<String, String>();
        objectMetadata.put("x-emc-enc-object-key", "holbhxpDq92g0dPRuqmtAt23KaNSm3JjKULdzadKdJyn3SINDSbaHnjmDU/Aa5pNSmq8ij+RWdkBlsrk9g6m/tjQm1gMPbeW+IhWJhInL0Mvsa+olZ+cLkztnKz/yHQ6vj9R0m8OboATweV1TTkRx9RFrr2nEBY7jKHNxd8JOJ/1I3gteuhsLKKE9oF2uS0UTVnCTZ3S6tf0W4P9D0PTW9LXQaA3KkFD+4tEbqZfC4ov88CLRAL72YC6KCF1LDZdqGzvqKf2j+92xIiy99+5LatVJPUebVucM8Equ6lAcETjNEsIwLPSNz2P9/saYI8XdLyZQDsOMg/32BbtVCVs7g==");
        objectMetadata.put("x-emc-enc-key-id", "000317457b5645b7b5c4daf4cf6780c05438effd");
        objectMetadata.put("x-emc-enc-unencrypted-size", "2516125");
        objectMetadata.put("x-emc-enc-unencrypted-sha1", "027e997e6b1dfc97b93eb28dc9a6804096d85873");
        objectMetadata.put("x-emc-enc-metadata-signature", "G98fTE0w8HzdzbqXv5x5AKl2/MudwrEIJ3nZciI1H9HQKg+i3Jmvi+/miEOeyMv8+lOVDj6CiBUMBpUsqrx46NODC+0MiA3L2JotW2DUJqfvfaTKtgbbFdSUYHshDDw3zZXcULX/flk5vjTYTICBSjedn9tg+VTA24ivk4IPexmPR7BKN+UmRZv6nPuvV1soGWg69K+5qv47lQf2rC4yO7FUXRJA10+nES1/8UmB3NylCwgI/a7UKu00o8kYABqgzVNbWgB4GjCqOtNcWJGSz8Xku3nWySetFLVs0wcwioZ3KyHIf+6p4XzbHx1ie4t9fhZuAYoOTPqDTu0o0QB/pg==");
        objectMetadata.put("x-emc-enc-iv", "omQ2kgZauWkK58/m+Eichg==");
        objectMetadata.put("name1", "value1");
        objectMetadata.put("name2", "value2");

        InputStream encryptedObject = this.getClass().getClassLoader().getResourceAsStream("encrypted.txt.aes128");

        keyProvider.setMasterKey(masterKey);
        EncryptionCodec codec = new EncryptionCodec();

        // Load the transform.
        InputStream inStream = codec.getDecodingStream(encryptedObject,
                new EncryptionMetadata("ENC:AES/CBC/PKCS5Padding", objectMetadata), codecProperties);

        byte[] buffer = new byte[4096];
        int c;
        
        // Decrypt into a buffer
        ByteArrayOutputStream decryptedData = new ByteArrayOutputStream();
        while ((c = inStream.read(buffer)) != -1) {
            decryptedData.write(buffer, 0, c);
        }

        // Get original data to check.
        InputStream originalStream = this.getClass().getClassLoader().getResourceAsStream("uncompressed.txt");
        ByteArrayOutputStream classByteStream = new ByteArrayOutputStream();
        while ((c = originalStream.read(buffer)) != -1) {
            classByteStream.write(buffer, 0, c);
        }
        byte[] originalData = classByteStream.toByteArray();
        originalStream.close();
        
        assertArrayEquals("Decrypted data incorrect", originalData, decryptedData.toByteArray());
    }

    /**
     * Test the rejection of a master KeyPair that's not an RSA key (e.g. a DSA
     * key)
     */
    @Test
    public void testRejectNonRsaMasterKey() throws Exception {
        KeyPairGenerator keyGenerator = KeyPairGenerator.getInstance("DSA");
        keyGenerator.initialize(512, new SecureRandom());
        KeyPair myKeyPair = keyGenerator.generateKeyPair();

        try {
            new BasicKeyProvider(myKeyPair).withProvider(provider);

            fail("DSA keys should not be allowed.");
        } catch (Exception e) {
            // Good!
            logger.info("DSA key was properly rejected by codec: " + e);
        }
    }

    /**
     * Test encrypting with one key, changing the master encryption key, then
     * decrypting. The old key should be found and used as the decryption key.
     */
    @Test
    public void testRekey() throws Exception {
        keyProvider.setMasterKey(oldKey);
        EncryptionCodec codec = new EncryptionCodec();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Map<String, String> metadata = new HashMap<String, String>();
        metadata.put("name1", "value1");
        metadata.put("name2", "value2");

        // Get some data to encrypt.
        InputStream classIn = this.getClass().getClassLoader().getResourceAsStream("uncompressed.txt");
        ByteArrayOutputStream classByteStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int c;
        while ((c = classIn.read(buffer)) != -1) {
            classByteStream.write(buffer, 0, c);
        }
        byte[] uncompressedData = classByteStream.toByteArray();
        classIn.close();

        EncodeOutputStream<EncryptionMetadata> encryptedStream = codec.getEncodingStream(out, encodeSpec, codecProperties);
        encryptedStream.write(uncompressedData);
        encryptedStream.close();
        
        byte[] encryptedObject = out.toByteArray();
        metadata.putAll(encryptedStream.getEncodeMetadata().toMap());
        CodecChain.addEncodeSpec(metadata, encodeSpec);
        
        // Now, rekey.
        keyProvider.setMasterKey(masterKey);
        Map<String, String> newMetadata = new HashMap<String, String>();
        newMetadata.putAll(metadata);
        codec.rekey(newMetadata, codecProperties);
        
        // Verify that the key ID and encrypted object key changed.
        assertNotEquals("Master key ID should have changed",
                metadata.get(EncryptionConstants.META_ENCRYPTION_KEY_ID),
                newMetadata.get(EncryptionConstants.META_ENCRYPTION_KEY_ID));
        assertNotEquals("Encrypted object key should have changed",
                metadata.get(EncryptionConstants.META_ENCRYPTION_OBJECT_KEY),
                newMetadata.get(EncryptionConstants.META_ENCRYPTION_OBJECT_KEY));
        
        // Decrypt with updated key
        ByteArrayInputStream encodedInput = new ByteArrayInputStream(encryptedObject);
        InputStream inStream = codec.getDecodingStream(encodedInput, new EncryptionMetadata(encodeSpec, newMetadata), codecProperties);

        ByteArrayOutputStream decodedOut = new ByteArrayOutputStream();
        while ((c = inStream.read(buffer)) != -1) {
            decodedOut.write(buffer, 0, c);
        }
        
        byte[] decodedData = decodedOut.toByteArray();
        
        assertArrayEquals("Decrypted output incorrect", uncompressedData, decodedData);
    }
    
    /**
     * Test encrypting with one key, changing the master encryption key, then
     * decrypting. The old key should be found and used as the decryption key.
     */
    @Test
    public void testRekey256() throws Exception {
        String cipherSpec = EncryptionUtil.getCipherSpec(encodeSpec);
        Assume.assumeTrue("256-bit AES is not supported", Cipher.getMaxAllowedKeyLength(cipherSpec) > 128);

        keyProvider.setMasterKey(oldKey);
        codecProperties.put(EncryptionCodec.PROP_KEY_SIZE, 256);
        EncryptionCodec codec = new EncryptionCodec();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Map<String, String> metadata = new HashMap<String, String>();
        metadata.put("name1", "value1");
        metadata.put("name2", "value2");
        EncodeOutputStream<EncryptionMetadata> encryptedStream = codec.getEncodingStream(out, encodeSpec, codecProperties);

        // Get some data to encrypt.
        InputStream classin = this.getClass().getClassLoader().getResourceAsStream("uncompressed.txt");
        ByteArrayOutputStream classByteStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int c;
        while ((c = classin.read(buffer)) != -1) {
            classByteStream.write(buffer, 0, c);
        }
        byte[] uncompressedData = classByteStream.toByteArray();
        classin.close();

        encryptedStream.write(uncompressedData);
        encryptedStream.close();
        
        byte[] encryptedObject = out.toByteArray();
        EncryptionMetadata encodeInfo = encryptedStream.getEncodeMetadata();
        metadata.putAll(encodeInfo.toMap());
        CodecChain.addEncodeSpec(metadata, encodeSpec);

        // Now, rekey.
        keyProvider.setMasterKey(masterKey);
        Map<String, String> metadata2 = new HashMap<String, String>();
        metadata2.putAll(metadata);
        codec.rekey(metadata2, codecProperties);
        
        // Verify that the key ID and encrypted object key changed.
        assertNotEquals("Master key ID should have changed",
                metadata.get(EncryptionConstants.META_ENCRYPTION_KEY_ID),
                metadata2.get(EncryptionConstants.META_ENCRYPTION_KEY_ID));
        assertNotEquals("Encrypted object key should have changed",
                metadata.get(EncryptionConstants.META_ENCRYPTION_OBJECT_KEY),
                metadata2.get(EncryptionConstants.META_ENCRYPTION_OBJECT_KEY));
        
        // Decrypt with updated key
        ByteArrayInputStream encodedInput = new ByteArrayInputStream(encryptedObject);
        InputStream decodeStream = codec.getDecodingStream(encodedInput,
                new EncryptionMetadata(encodeInfo.getEncodeSpec(), metadata2), codecProperties);

        ByteArrayOutputStream decodedOut = new ByteArrayOutputStream();
        while ((c = decodeStream.read(buffer)) != -1) {
            decodedOut.write(buffer, 0, c);
        }
        
        byte[] decodedData = decodedOut.toByteArray();
        
        assertArrayEquals("Decrypted output incorrect", uncompressedData, decodedData);
    }
}
