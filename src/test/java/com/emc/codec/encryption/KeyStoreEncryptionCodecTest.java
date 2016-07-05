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

import com.emc.codec.CodecChain;
import com.emc.codec.EncodeMetadata;
import com.emc.codec.EncodeOutputStream;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Cipher;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.security.InvalidKeyException;
import java.security.KeyStore;
import java.security.Provider;
import java.security.interfaces.RSAPublicKey;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;


public class KeyStoreEncryptionCodecTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(
            KeyStoreEncryptionCodecTest.class);
    
    private KeyStore keystore;
    private String keystorePassword = "viprviprvipr";
    private String keyAlias = "masterkey";
    protected KeystoreKeyProvider keyProvider;
    protected Provider provider;
    protected String encodeSpec = EncryptionCodec.encodeSpec(EncryptionCodec.AES_CBC_PKCS5_CIPHER);
    protected Map<String, Object> codecProperties = new HashMap<String, Object>();

    @Before
    public void setUp() throws Exception {
        // Init keystore
        keystore = KeyStore.getInstance("jks");
        String keystoreFile = "keystore.jks";
        InputStream in = this.getClass().getClassLoader().getResourceAsStream(keystoreFile);
        if(in == null) {
            throw new FileNotFoundException(keystoreFile);
        }
        keystore.load(in, keystorePassword.toCharArray());
        LOGGER.debug("Keystore Loaded");
        for(Enumeration<String> aliases = keystore.aliases(); aliases.hasMoreElements();) {
            String alias = aliases.nextElement();
            RSAPublicKey publicKey = (RSAPublicKey) keystore.getCertificate(alias).getPublicKey();
            String fingerprint = EncryptionUtil.getRsaPublicKeyFingerprint(publicKey);
            LOGGER.debug("Found key: {} (fp: {})", alias, fingerprint);
        }

        keyProvider = new KeystoreKeyProvider(keystore, keystorePassword.toCharArray(), keyAlias);

        codecProperties.put(EncryptionCodec.PROP_SECURITY_PROVIDER, provider);
        codecProperties.put(EncryptionCodec.PROP_KEY_PROVIDER, keyProvider);
    }

    @Test
    public void testRekey() throws Exception {
        keyProvider.setMasterKeyAlias("oldkey");
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

        EncodeOutputStream<EncryptionMetadata> encryptedStream = codec.getEncodingStream(out, encodeSpec, codecProperties);
        encryptedStream.write(uncompressedData);
        encryptedStream.close();
        
        byte[] encryptedObject = out.toByteArray();
        String encodeSpec = encryptedStream.getEncodeMetadata().getEncodeSpec();
        metadata.putAll(encryptedStream.getEncodeMetadata().toMap());
        CodecChain.addEncodeSpec(metadata, encodeSpec);

        // Now, rekey.
        keyProvider.setMasterKeyAlias(keyAlias);
        Map<String, String> rekeyedMeta = new HashMap<String, String>();
        rekeyedMeta.putAll(metadata);
        codec.rekey(rekeyedMeta, codecProperties);
        
        // Verify that the key ID and encrypted object key changed.
        assertNotEquals("Master key ID should have changed",
                metadata.get(EncryptionConstants.META_ENCRYPTION_KEY_ID), rekeyedMeta.get(EncryptionConstants.META_ENCRYPTION_KEY_ID));
        assertNotEquals("Encrypted object key should have changed",
                metadata.get(EncryptionConstants.META_ENCRYPTION_OBJECT_KEY), rekeyedMeta.get(EncryptionConstants.META_ENCRYPTION_OBJECT_KEY));
        
        // Decrypt with updated key
        ByteArrayInputStream encodedInput = new ByteArrayInputStream(encryptedObject);
        InputStream inStream = codec.getDecodingStream(encodedInput, new EncryptionMetadata(encodeSpec, rekeyedMeta), codecProperties);
        
        ByteArrayOutputStream decodedOut = new ByteArrayOutputStream();
        while ((c = inStream.read(buffer)) != -1) {
            decodedOut.write(buffer, 0, c);
        }
        
        byte[] decodedData = decodedOut.toByteArray();
        
        assertArrayEquals("Decrypted output incorrect", uncompressedData, decodedData);
    }

    @Test
    public void testRekey256() throws Exception {
        String cipherSpec = EncryptionUtil.getCipherSpec(encodeSpec);
        Assume.assumeTrue("256-bit AES is not supported", Cipher.getMaxAllowedKeyLength(cipherSpec) > 128);

        keyProvider.setMasterKeyAlias("oldkey");
        codecProperties.put(EncryptionCodec.PROP_KEY_SIZE, 256);
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

        EncodeOutputStream<EncryptionMetadata> encryptedStream = codec.getEncodingStream(out, encodeSpec, codecProperties);
        encryptedStream.write(uncompressedData);
        encryptedStream.close();
        
        byte[] encryptedObject = out.toByteArray();
        String encodeSpec = encryptedStream.getEncodeMetadata().getEncodeSpec();
        metadata.putAll(encryptedStream.getEncodeMetadata().toMap());
        CodecChain.addEncodeSpec(metadata, encodeSpec);
        
        // Now, rekey.
        keyProvider.setMasterKeyAlias(keyAlias);
        Map<String, String> rekeyedMeta = new HashMap<String, String>();
        rekeyedMeta.putAll(metadata);
        codec.rekey(rekeyedMeta, codecProperties);
        
        // Verify that the key ID and encrypted object key changed.
        assertNotEquals("Master key ID should have changed",
                metadata.get(EncryptionConstants.META_ENCRYPTION_KEY_ID),
                rekeyedMeta.get(EncryptionConstants.META_ENCRYPTION_KEY_ID));
        assertNotEquals("Encrypted object key should have changed",
                metadata.get(EncryptionConstants.META_ENCRYPTION_OBJECT_KEY),
                rekeyedMeta.get(EncryptionConstants.META_ENCRYPTION_OBJECT_KEY));
        
        // Decrypt with updated key
        ByteArrayInputStream encodedInput = new ByteArrayInputStream(encryptedObject);
        InputStream inStream = codec.getDecodingStream(encodedInput, new EncryptionMetadata(encodeSpec, rekeyedMeta), codecProperties);
        
        ByteArrayOutputStream decodedOut = new ByteArrayOutputStream();
        while ((c = inStream.read(buffer)) != -1) {
            decodedOut.write(buffer, 0, c);
        }
        
        byte[] decodedData = decodedOut.toByteArray();
        
        assertArrayEquals("Decrypted output incorrect", uncompressedData, decodedData);
    }

    @Test
    public void testKeyStoreEncryptionFactory() throws Exception {
        // Should fail if key not found.
        try {
            new KeystoreKeyProvider(keystore, keystorePassword.toCharArray(), "NoKey").withProvider(provider);
            fail("Should not init with invalid key alias");
        } catch (InvalidKeyException e) {
            // OK
        }
    }

    @Test
    public void testGetOutputTransform() throws Exception {
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

        EncodeOutputStream<EncryptionMetadata> encryptedStream = codec.getEncodingStream(out, encodeSpec, codecProperties);
        encryptedStream.write(uncompressedData);

        EncodeMetadata encMeta = encryptedStream.getEncodeMetadata();
        assertFalse("encode metadata should not be complete", encMeta.isComplete());

        encryptedStream.close();

        metadata.putAll(encryptedStream.getEncodeMetadata().toMap());

        assertEquals("Uncompressed digest incorrect", "027e997e6b1dfc97b93eb28dc9a6804096d85873",
                metadata.get(EncryptionConstants.META_ENCRYPTION_UNENC_SHA1));
        assertEquals("Uncompressed size incorrect", 2516125,
                Long.parseLong(metadata.get(EncryptionConstants.META_ENCRYPTION_UNENC_SIZE)));
        assertNotNull("Missing IV", metadata.get(EncryptionConstants.META_ENCRYPTION_IV));
        assertEquals("Incorrect master encryption key ID", "e3a69d422e6d9008e3cdfcbea674ccd9ab4758c3",
                metadata.get(EncryptionConstants.META_ENCRYPTION_KEY_ID));
        assertNotNull("Missing object key", metadata.get(EncryptionConstants.META_ENCRYPTION_OBJECT_KEY));
        assertNotNull("Missing metadata signature", metadata.get(EncryptionConstants.META_ENCRYPTION_META_SIG));
        assertEquals("name1 incorrect", "value1", metadata.get("name1"));
        assertEquals("name2 incorrect", "value2", metadata.get("name2"));

        String transformConfig = encryptedStream.getEncodeMetadata().getEncodeSpec();
        assertEquals("Transform config string incorrect", "ENC:AES/CBC/PKCS5Padding", transformConfig);

        LOGGER.info("Encoded metadata: " + metadata);
    }

    @Test
    public void testGetInputTransform() throws Exception {
        Map<String, String> objectMetadata = new HashMap<String, String>();
        objectMetadata.put("x-emc-enc-object-key", "iyuQuDL9qsfZk2XqnRihfw8ejr+OcrsflXvYD1I5o/Bw+wZPkY4Fm6py8ng25K/iw6kO0zbqq5v5Ywkng0pgrUdHLyR6Aq/dD0vKTK46E6sHZKknlM7NSixR8qaieBwwc2QnhOzyFPIVWSgwo9TqhlPRlOjftRLwU6Nt056BGt5Lhrqn3DeQpTrZW8LDjcpTC1UZtVXe3v9pXB4JEz8M4iFjnFprHykmixlR35RWOw4tIVEbsbcXZwt9RhVsDHj8qnkH66S88y4IOuuU4JJeFMywFXLdDs+MlUrYrA/MvfZNs34WKLYcFICKuLoHoGZ/gReJPbKy64lhSM8gTtYf/Q==");
        objectMetadata.put("x-emc-enc-key-id", "e3a69d422e6d9008e3cdfcbea674ccd9ab4758c3");
        objectMetadata.put("x-emc-enc-unencrypted-size", "2516125");
        objectMetadata.put("x-emc-enc-unencrypted-sha1", "027e997e6b1dfc97b93eb28dc9a6804096d85873");
        objectMetadata.put("x-emc-enc-metadata-signature", "F5IG2SC20oFpjLCc+5aETIy25tjUSodNlpmkae/1g91gkCYtP6NG6aLMQLHwyu789LmSegPQ/flUwcqdDE8nCI9Y2SuVbQIE5wvyB7RXRNqDIBKOan4xiOS/G5BwzzPFs6uL3I0b5Ya/VrJYhnDiRMAC+6L5kDbEVesHkx77qqCxku/SSMzCJ2K7kX/MYKfJdNQgXsFMAZs1PEcJpW8viQVTEYR8YR7bx37y4/lIHBotmC7HtB0RWAIGDFcHrnASyqpyHCYnwYjiPqItWaZy7WxRVM+qkH7IMtJT2XCuuI6VFmNzu57LN8p5ROBKO4l0hTgfgHMOUbpmQwuanb6p9Q==");
        objectMetadata.put("x-emc-enc-iv", "OCoTA8kO0A+ZKkoZKa7VIQ==");
        objectMetadata.put("name1", "value1");
        objectMetadata.put("name2", "value2");

        InputStream encryptedObject = this.getClass().getClassLoader().getResourceAsStream("encrypted.txt.keystore.aes128");

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
     * Test using a certificate that lacks the Subject Key Identifier.  We should have
     * to compute it manually.
     */
    @Test
    public void testGetInputTransformNoCertSKI() throws Exception {
        String mk2Fingerprint = "2e432a386098e4d66a4dc7deec04ab03a9188b39";

        keyProvider.setMasterKeyAlias("masterkey2");

        assertEquals("Wrong master key fingerprint", mk2Fingerprint, keyProvider.getMasterKeyFingerprint());
    }
}
