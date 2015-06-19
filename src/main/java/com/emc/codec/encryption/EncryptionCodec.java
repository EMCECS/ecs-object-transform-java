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

import com.emc.codec.*;
import com.emc.codec.util.CodecUtil;
import org.apache.log4j.LogMF;
import org.apache.log4j.Logger;

import javax.crypto.*;
import javax.crypto.spec.IvParameterSpec;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.*;
import java.security.interfaces.RSAPrivateKey;
import java.util.Map;

public class EncryptionCodec extends AbstractCodec<EncryptionMetadata> {
    private static final Logger log = Logger.getLogger(EncryptionCodec.class);

    public static final int PRIORITY = 1000;

    public static final String SECURE_RANDOM_INSTANCE = "SHA1PRNG";

    public static final String AES_CBC_PKCS5_CIPHER = "AES/CBC/PKCS5Padding";

    public static final String PROP_KEY_SIZE = "com.emc.codec.encryption.EncryptionCodec.keySize";
    public static final String PROP_KEY_PROVIDER = "com.emc.codec.encryption.EncryptionCodec.keyProvider";
    public static final String PROP_SECURITY_PROVIDER = "com.emc.codec.encryption.EncryptionCodec.securityProvider";

    public static final int DEFAULT_KEY_SIZE = 128;

    public static String encodeSpec(String cipherSpec) {
        return CodecUtil.getEncodeSpec(EncryptionConstants.ENCRYPTION_TYPE, cipherSpec);
    }

    public static int getKeySize(Map<String, Object> codecProperties) {
        return CodecUtil.getCodecProperty(PROP_KEY_SIZE, codecProperties, DEFAULT_KEY_SIZE);
    }

    public static void setKeySize(Map<String, Object> codecProperties, int keySize) {
        codecProperties.put(PROP_KEY_SIZE, keySize);
    }

    public static KeyProvider getKeyProvider(Map<String, Object> codecProperties) {
        return CodecUtil.getCodecProperty(PROP_KEY_PROVIDER, codecProperties, null);
    }

    public static void setKeyProvider(Map<String, Object> codecProperties, KeyProvider keyProvider) {
        codecProperties.put(PROP_KEY_PROVIDER, keyProvider);
    }

    public static Provider getSecurityProvider(Map<String, Object> codecProperties) {
        return CodecUtil.getCodecProperty(PROP_SECURITY_PROVIDER, codecProperties, null);
    }

    public static void setPropSecurityProvider(Map<String, Object> codecProperties, Provider securityProvider) {
        codecProperties.put(PROP_SECURITY_PROVIDER, securityProvider);
    }

    @Override
    public boolean canProcess(String encodeSpec) {
        if (!EncryptionConstants.ENCRYPTION_TYPE.equals(CodecUtil.getEncodeType(encodeSpec))) return false;

        String cipherSpec = EncryptionUtil.getCipherSpec(encodeSpec);
        try {
            Cipher.getInstance(cipherSpec);
            return true;
        } catch (Exception e) {
            LogMF.warn(log, "cannot process cipher %s: %s", cipherSpec, e);
        }

        return false;
    }

    @Override
    public String getDefaultEncodeSpec() {
        return encodeSpec(AES_CBC_PKCS5_CIPHER);
    }

    @Override
    public int getPriority() {
        return PRIORITY;
    }

    @Override
    public EncryptionMetadata createEncodeMetadata(String encodeSpec, Map<String, String> metaMap) {
        return new EncryptionMetadata(encodeSpec, metaMap);
    }

    @Override
    public long getDecodedSize(EncryptionMetadata encodeInfo) {
        return encodeInfo.getOriginalSize();
    }

    @Override
    public OutputStream getDecodingStream(OutputStream originalStream, EncryptionMetadata metadata,
                                          Map<String, Object> codecProperties) {
        KeyProvider keyProvider = _getKeyProvider(codecProperties);
        Provider provider = getSecurityProvider(codecProperties);
        return new CipherOutputStream(originalStream, initDecryptCipher(metadata, keyProvider, provider));
    }

    @Override
    public InputStream getDecodingStream(InputStream originalStream, EncryptionMetadata metadata,
                                         Map<String, Object> codecProperties) {
        KeyProvider keyProvider = _getKeyProvider(codecProperties);
        Provider provider = getSecurityProvider(codecProperties);
        return new CipherInputStream(originalStream, initDecryptCipher(metadata, keyProvider, provider));
    }

    @Override
    public boolean isSizePredictable() {
        return true;
    }

    @Override
    public long getEncodedSize(long originalSize, String encodeSpec, Map<String, Object> codecProperties) {
        String cipherSpec = EncryptionUtil.getCipherSpec(encodeSpec);
        Provider provider = getSecurityProvider(codecProperties);
        SecretKey key = generateKey(cipherSpec, getKeySize(codecProperties), provider);
        Cipher cipher = initEncryptCipher(cipherSpec, key, provider);

        long trailer = originalSize % cipher.getBlockSize();
        long trunc = originalSize - trailer;
        try {
            return trunc + cipher.doFinal(new byte[(int) trailer]).length;
        } catch (Exception e) {
            throw new UnsupportedOperationException("Cipher error", e);
        }
    }

    @Override
    public EncodeOutputStream<EncryptionMetadata> getEncodingStream(OutputStream originalStream, String encodeSpec,
                                                                    Map<String, Object> codecProperties) {
        String cipherSpec = EncryptionUtil.getCipherSpec(encodeSpec);
        Provider provider = getSecurityProvider(codecProperties);
        KeyProvider keyProvider = _getKeyProvider(codecProperties);
        SecretKey key = generateKey(cipherSpec, getKeySize(codecProperties), provider);
        Cipher cipher = initEncryptCipher(cipherSpec, key, provider);
        String encryptedKey = encryptKey(key, keyProvider.getMasterKey(), provider);

        EncryptionOutputStream eos = new EncryptionOutputStream(originalStream, encodeSpec, cipher, encryptedKey);
        eos.addListener(new SigningEncodeMetadataListener(keyProvider, provider));
        return eos;
    }

    @Override
    public EncodeInputStream<EncryptionMetadata> getEncodingStream(InputStream originalStream, String encodeSpec,
                                                                   Map<String, Object> codecProperties) {
        String cipherSpec = EncryptionUtil.getCipherSpec(encodeSpec);
        Provider provider = getSecurityProvider(codecProperties);
        KeyProvider keyProvider = _getKeyProvider(codecProperties);
        SecretKey key = generateKey(cipherSpec, getKeySize(codecProperties), provider);
        Cipher cipher = initEncryptCipher(cipherSpec, key, provider);
        String encryptedKey = encryptKey(key, keyProvider.getMasterKey(), provider);

        EncryptionInputStream eis = new EncryptionInputStream(originalStream, encodeSpec, cipher, encryptedKey);
        eis.addListener(new SigningEncodeMetadataListener(keyProvider, provider));
        return eis;
    }

    public void rekey(Map<String, String> metaMap, Map<String, Object> codecProperties) {

        // find the encryption spec in the metadata
        String encryptSpec = null;
        for (String spec : CodecChain.getEncodeSpecs(metaMap)) {
            if (canDecode(spec)) {
                encryptSpec = spec;
                break;
            }
        }

        if (encryptSpec == null) throw new IllegalArgumentException("object is not encrypted");

        EncryptionMetadata metadata = new EncryptionMetadata(encryptSpec, metaMap);
        rekey(metadata, codecProperties);
        metaMap.putAll(metadata.toMap());
    }

    public void rekey(EncryptionMetadata metadata, Map<String, Object> codecProperties) {
        KeyProvider keyProvider = getKeyProvider(codecProperties);
        Provider provider = getSecurityProvider(codecProperties);

        if (metadata.getMasterKeyFingerprint().equals(keyProvider.getMasterKeyFingerprint()))
            throw new DoesNotNeedRekeyException("Object is already using the current master key");

        KeyPair oldKey = keyProvider.getKey(metadata.getMasterKeyFingerprint());

        // make sure we have the old key
        if (oldKey == null)
            throw new EncryptionException(String.format("Master key with fingerprint %s not found",
                    metadata.getMasterKeyFingerprint()));

        // decrypt object key
        SecretKey objectKey = metadata.getSecretKey((RSAPrivateKey) oldKey.getPrivate(), provider);

        // re-encrypt object key with the current master key
        metadata.setSecretKey(objectKey, keyProvider.getMasterKey().getPublic(), provider);
        metadata.setMasterKeyFingerprint(keyProvider.getMasterKeyFingerprint());

        // re-sign metadata
        metadata.sign((RSAPrivateKey) keyProvider.getMasterKey().getPrivate(), provider);
    }

    protected Cipher initEncryptCipher(String cipherSpec, SecretKey key, Provider provider) {
        try {
            Cipher cipher = createCipher(cipherSpec, provider);
            cipher.init(Cipher.ENCRYPT_MODE, key, getSecureRandom(provider));
            return cipher;
        } catch (GeneralSecurityException e) {
            throw new EncryptionException("Error initializing cipher", e);
        }
    }

    protected Cipher initDecryptCipher(EncryptionMetadata metadata, KeyProvider keyProvider, Provider provider) {
        try {
            String cipherSpec = EncryptionUtil.getCipherSpec(metadata.getEncodeSpec());
            Cipher cipher = createCipher(cipherSpec, provider);

            KeyPair masterKey = keyProvider.getKey(metadata.getMasterKeyFingerprint());
            if (masterKey == null)
                throw new EncryptionException(String.format("Could not decrypt object. no master key with ID %s found",
                        metadata.getMasterKeyFingerprint()));

            SecretKey key = metadata.getSecretKey((RSAPrivateKey) masterKey.getPrivate(), provider);

            cipher.init(Cipher.DECRYPT_MODE, key, new IvParameterSpec(metadata.getInitVector()));
            return cipher;
        } catch (GeneralSecurityException e) {
            throw new EncryptionException("Error initializing cipher", e);
        }
    }

    protected Cipher createCipher(String cipherSpec, Provider provider) {
        try {
            if (provider != null) {
                return Cipher.getInstance(cipherSpec, provider);
            } else {
                return Cipher.getInstance(cipherSpec);
            }
        } catch (GeneralSecurityException e) {
            throw new UnsupportedOperationException("Could not get cipher instance for algorithm " + cipherSpec, e);
        }
    }

    protected SecretKey generateKey(String cipherSpec, int keySize, Provider provider) {
        String baseAlgorithm = EncryptionUtil.getBaseAlgorithm(cipherSpec);
        try {
            if (keySize > Cipher.getMaxAllowedKeyLength(cipherSpec))
                throw new InvalidKeyException(String.format("Key size of %d bits is larger than the maximum allowed of %d",
                        keySize, Cipher.getMaxAllowedKeyLength(cipherSpec)));

            KeyGenerator keygen;
            if (provider != null) {
                keygen = KeyGenerator.getInstance(baseAlgorithm, provider);
            } else {
                keygen = KeyGenerator.getInstance(baseAlgorithm);
            }

            keygen.init(keySize, getSecureRandom(provider));
            return keygen.generateKey();
        } catch (GeneralSecurityException e) {
            throw new UnsupportedOperationException("Could not generate key for algorithm " + baseAlgorithm, e);
        }
    }

    public String encryptKey(SecretKey key, KeyPair masterKey, Provider provider) {
        return EncryptionUtil.encryptKey(key, provider, masterKey.getPublic());
    }

    protected KeyProvider _getKeyProvider(Map<String, Object> codecProperties) {
        KeyProvider keyProvider = getKeyProvider(codecProperties);
        if (keyProvider == null) throw new EncryptionException("no key provider specified");
        return keyProvider;
    }

    protected SecureRandom getSecureRandom(Provider provider) {
        try {
            // Per FIPS bulletin 2013-09, make sure we don't use Dual_EC_DRBG
            if (provider != null)
                return SecureRandom.getInstance(SECURE_RANDOM_INSTANCE, provider);
            else return SecureRandom.getInstance(SECURE_RANDOM_INSTANCE);
        } catch (GeneralSecurityException e) {
            throw new UnsupportedOperationException("Could not get secure random instance for " + SECURE_RANDOM_INSTANCE, e);
        }
    }

    protected class SigningEncodeMetadataListener implements EncodeListener<EncryptionMetadata> {
        private KeyProvider keyProvider;
        private Provider provider;

        public SigningEncodeMetadataListener(KeyProvider keyProvider, Provider provider) {
            this.keyProvider = keyProvider;
            this.provider = provider;
        }

        @Override
        public void encodeComplete(EncodeStream<EncryptionMetadata> encodeStream) {
            encodeStream.getEncodeMetadata().setMasterKeyFingerprint(keyProvider.getMasterKeyFingerprint());
            encodeStream.getEncodeMetadata().sign((RSAPrivateKey) keyProvider.getMasterKey().getPrivate(), provider);
        }
    }
}
