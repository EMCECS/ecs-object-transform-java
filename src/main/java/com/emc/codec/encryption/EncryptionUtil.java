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

import com.emc.codec.util.CodecUtil;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.digest.DigestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.security.*;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.*;

/**
 * @author cwikj
 *
 */
public class EncryptionUtil {

    private static final Logger log = LoggerFactory.getLogger(EncryptionUtil.class);

    /**
     * Computes the fingerprint of an RSA public key.  This should be equivalent to the
     * Subject Key Identifier (SKI) of a key pair stored in an X.509 certificate.  This
     * is done by DER encoding the public key and computing the SHA1 digest of the
     * encoded key.
     * @param pubKey the RSA public key to fingerprint
     * @return the key's fingerprint as a string of hexadecimal characters.
     */
    public static String getRsaPublicKeyFingerprint(RSAPublicKey pubKey) {
        return DigestUtils.sha1Hex(derEncodeRSAPublicKey(pubKey));
    }

    /**
     * Transforms a byte sequence into a sequence of hex digits, MSB first.  The value
     * will be padded with zeroes to the proper number of digits.
     * @param data the bytes to encode into hex
     * @return the bytes as a string of hexadecimal characters.
     */
    public static String toHexPadded(byte[] data) {
        BigInteger bi = new BigInteger(1, data);
        String s = bi.toString(16);
        while(s.length() < (data.length*2)) {
            s = "0" + s;
        }
        
        return s;
    }

    /**
     * Encodes a {@link BigInteger} in DER format
     * @param value the value to encode
     * @return the byte sequence representing the number in DER encoding.
     */
    public static byte[] derEncodeBigInteger(BigInteger value) {
        return derEncodeValue((byte)0x02, value.toByteArray());
    }

    /**
     * Encodes a DER value with the proper length specifier.
     * @param type the DER type specifier byte
     * @param bytes the bytes to encode
     * @return the input bytes prefixed with the DER type and length.
     */
    public static byte[] derEncodeValue(byte type, byte[] bytes) {
        if(bytes.length < 128) {
            byte[] der = new byte[bytes.length + 2];
            der[0] = type; // Integer
            der[1] = (byte) bytes.length;
            System.arraycopy(bytes, 0, der, 2, bytes.length);
            return der;
        } else {
            BigInteger bigLength = BigInteger.valueOf(bytes.length);
            byte[] lengthBytes = bigLength.toByteArray();
            byte[] der = new byte[bytes.length + lengthBytes.length + 2];
            der[0] = type; // Integer
            der[1] = (byte) ((lengthBytes.length) | 0x80); // Length of Length
            System.arraycopy(lengthBytes, 0, der, 2, lengthBytes.length);
            System.arraycopy(bytes, 0, der, 2 + lengthBytes.length, bytes.length);
            return der;
        }
    }

    /**
     * Encodes an RSA public key in DER format.
     * @param pubkey the RSA public key to encode.
     * @return the public key's data in DER format.
     */
    public static byte[] derEncodeRSAPublicKey(RSAPublicKey pubkey) {
        List<byte[]> sequence = new ArrayList<byte[]>();
        sequence.add(derEncodeBigInteger(pubkey.getModulus()));
        sequence.add(derEncodeBigInteger(pubkey.getPublicExponent()));
        return derEncodeSequence(sequence);
    }

    /**
     * Encodes a list of objects into a DER "sequence".
     * @param objects the DER encoded objects to sequence.
     * @return the bytes representing the DER sequence.
     */
    public static byte[] derEncodeSequence(List<byte[]> objects) {
        int totalSize = 0;
        for(byte[] obj : objects) {
            totalSize += obj.length;
        }

        byte[] objectData = new byte[totalSize];
        int p = 0;
        for(byte[] obj : objects) {
            System.arraycopy(obj, 0, objectData, p, obj.length);
            p+=obj.length;
        }

        return derEncodeValue((byte)0x30, objectData);
    }

    /**
     * Constructs an RSA KeyPair from base-64 encoded key material.
     * @param publicKey The Base-64 encoded RSA public key in X.509 format.
     * @param privateKey The Base-64 encoded RSA private key in PKCS#8 format.
     * @return the KeyPair object containing both keys.
     * @throws GeneralSecurityException 
     */
    public static KeyPair rsaKeyPairFromBase64(String publicKey, String privateKey) throws GeneralSecurityException {
        try {
            byte[] pubKeyBytes = Base64.decodeBase64(publicKey.getBytes("US-ASCII"));
            byte[] privKeyBytes = Base64.decodeBase64(privateKey.getBytes("US-ASCII"));
            
            X509EncodedKeySpec pubKeySpec = new X509EncodedKeySpec(pubKeyBytes);
            PKCS8EncodedKeySpec privKeySpec = new PKCS8EncodedKeySpec(privKeyBytes);
            
            PublicKey pubKey;
            PrivateKey privKey;
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            pubKey = keyFactory.generatePublic(pubKeySpec);
            privKey = keyFactory.generatePrivate(privKeySpec);
            
            return new KeyPair(pubKey, privKey);
        } catch (UnsupportedEncodingException e) {
            // This should never happen for US-ASCII.
            throw new RuntimeException("Could not load key pair: " + e, e);
        }
    }
    
    public static SecretKey decryptKey(String encodedKey, String algorithm, Provider provider, PrivateKey privateKey) {
        try {
            Cipher cipher;
            if(provider != null) {
                cipher = Cipher.getInstance(EncryptionConstants.KEY_ENCRYPTION_CIPHER, provider);
            } else {
                cipher = Cipher.getInstance(EncryptionConstants.KEY_ENCRYPTION_CIPHER);
            }
            
            cipher.init(Cipher.DECRYPT_MODE, privateKey);
            
            byte[] keyData = urlSafeDecodeBase64(encodedKey);
            
            byte[] decryptedKey = cipher.doFinal(keyData);
            
            return new SecretKeySpec(decryptedKey, algorithm);
        } catch(GeneralSecurityException e) {
            throw new RuntimeException("error decrypting object key: " + e, e);
        }
    }

    public static String encryptKey(SecretKey key, Provider provider, PublicKey publicKey) {
        try {
            Cipher cipher;
            if (provider != null) {
                cipher = Cipher.getInstance(EncryptionConstants.KEY_ENCRYPTION_CIPHER, provider);
            } else {
                cipher = Cipher.getInstance(EncryptionConstants.KEY_ENCRYPTION_CIPHER);
            }

            cipher.init(Cipher.ENCRYPT_MODE, publicKey);

            byte[] encryptedKey = cipher.doFinal(key.getEncoded());

            return urlSafeEncodeBase64(encryptedKey);
        } catch (GeneralSecurityException e) {
            throw new RuntimeException("error encrypting object key: " + e, e);
        }
    }
    
    /**
     * Uses the 'base64url' encoding from RFC4648 to encode a byte array to a string.
     * @param data the byte array to encode
     * @return the Base-64 encoded string.
     */
    public static String urlSafeEncodeBase64(byte[] data) {
        String b64Data;
        try {
            b64Data = new String(Base64.encodeBase64(data), "US-ASCII");
        } catch (UnsupportedEncodingException e) {
            // Should never happen...
            throw new RuntimeException("US-ASCII encoding not supported", e);
        }
        
        // Replacements
        b64Data = b64Data.replace('+', '-');
        b64Data = b64Data.replace('/', '_');
        
        return b64Data;
    }
    
    /**
     * Uses the 'base64url' encoding from RFC4648 to decode a string to a byte array.  It
     * is assumed that the characters are encoded as 7-bit US-ASCII.
     * @param b64Data the Base-64 encoded string to decode
     * @return the decoded bytes.
     */
    public static byte[] urlSafeDecodeBase64(String b64Data) {
        // Replacements
        b64Data = b64Data.replace('-', '+');
        b64Data = b64Data.replace('_', '/');
        
        byte[] data;
        try {
            data = Base64.decodeBase64(b64Data.getBytes("US-ASCII"));
        } catch (UnsupportedEncodingException e) {
            // Should never happen...
            throw new RuntimeException("US-ASCII encoding not supported", e);
        }
        return data;
    }

    public static String signMetadata(Map<String, String> metadata, RSAPrivateKey privateKey, Provider provider) {
        // Get the set of keys to sign and sort them.
        List<String> keys = new ArrayList<String>();

        for (String key : metadata.keySet()) {
            if (key.startsWith(EncryptionConstants.META_ENCRYPTION_PREFIX)) {
                keys.add(key);
            }
        }

        Collections.sort(keys, new Comparator<String>() {
            @Override
            public int compare(String s1, String s2) {
                if (s1 == null && s2 == null) {
                    return 0;
                }
                if (s1 == null) {
                    return -s2.toLowerCase().compareTo(s1);
                }

                return s1.toLowerCase().compareTo(s2.toLowerCase());
            }
        });

        StringBuffer canonicalString = new StringBuffer();
        for (String key : keys) {
            canonicalString.append(key.toLowerCase()).append(":").append(metadata.get(key)).append("\n");
        }

        log.debug("Canonical string: ''{}''", canonicalString);
        byte[] bytes;
        try {
            bytes = canonicalString.toString().getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            // Should never happen since UTF-8 is required.
            throw new RuntimeException("Could not render string to bytes");
        }

        Signature sig;
        try {
            if (provider != null) {
                sig = Signature.getInstance(EncryptionConstants.META_SIGNATURE_ALGORITHM, provider);
            } else {
                sig = Signature.getInstance(EncryptionConstants.META_SIGNATURE_ALGORITHM);
            }
            sig.initSign(privateKey);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Could not initialize signature algorithm: " + e, e);
        } catch (InvalidKeyException e) {
            throw new RuntimeException("Could not initialize signature algorithm: " + e, e);
        }
        
        // Sign it!
        try {
            sig.update(bytes);
            byte[] signature = sig.sign();
            
            return urlSafeEncodeBase64(signature);
        } catch (SignatureException e) {
            throw new RuntimeException("Could not compute metadata signature: " + e);
        }
    }

    public static byte[] extractSubjectKeyIdentifier(byte[] derSki) {
        // The first four bytes are DER encoding for the object type and length:
        // 04 16 04 14: octet-stream(22 bytes) { octet-stream(20 bytes) }
        // So, we just want the last 20 bytes to get the SHA1 fingerprint of the pubkey
        byte[] dst = new byte[20];
        if(derSki.length != 24) {
            throw new RuntimeException("DER-encoded SKI should be 24 bytes");
        }
        
        System.arraycopy(derSki, 4, dst, 0, 20);
        return dst;
    }

    public static String getCipherSpec(String encodeSpec) {
        return CodecUtil.getEncodeAlgorithm(encodeSpec);
    }

    public static String getBaseAlgorithm(String cipherSpec) {
        return cipherSpec.split("/")[0];
    }
}
