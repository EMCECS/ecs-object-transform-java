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

package com.emc.codec;

import SevenZip.Compression.LZMA.Encoder;
import com.emc.codec.compression.CompressionConstants;
import com.emc.codec.compression.deflate.DeflateCodec;
import com.emc.codec.compression.lzma.LzmaCodec;
import com.emc.codec.compression.lzma.LzmaProfile;
import com.emc.codec.encryption.EncryptionCodec;
import com.emc.codec.encryption.EncryptionConstants;
import com.emc.codec.encryption.KeyProvider;
import com.emc.codec.encryption.KeystoreKeyProvider;
import org.apache.commons.codec.digest.DigestUtils;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.security.KeyStore;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

import static org.junit.Assert.*;

public class ChainTest {
    @Test
    public void testDefaultLzma() throws Exception {
        CodecChain chain = new CodecChain(new LzmaCodec());

        Assert.assertFalse(chain.isSizePredictable());

        Map<String, String> metadata = new HashMap<String, String>();
        metadata.put("foo", "bar");
        metadata.put("bar", "baz");

        testStreams(chain, metadata, new EncodeVerifier() {
            @Override
            public void verify(byte[] encodedData, Map<String, String> encodedMetadata) {
                byte[] manualData;
                try {
                    // manually compress
                    LzmaProfile profile = LzmaProfile.fromCompressionLevel(CompressionConstants.DEFAULT_COMPRESSION_LEVEL);
                    Encoder encoder = new Encoder();
                    encoder.SetDictionarySize(profile.getDictionarySize());
                    encoder.SetNumFastBytes(profile.getFastBytes());
                    encoder.SetMatchFinder(profile.getMatchFinder());
                    encoder.SetLcLpPb(profile.getLc(), profile.getLp(), profile.getPb());
                    encoder.SetEndMarkerMode(true);

                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    encoder.WriteCoderProperties(baos);
                    encoder.Code(TestUtil.getOriginalStream(), baos, -1, -1, null);
                    baos.close();
                    manualData = baos.toByteArray();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }

                assertArrayEquals(manualData, encodedData);

                assertEquals("Uncompressed digest incorrect", "027e997e6b1dfc97b93eb28dc9a6804096d85873",
                        encodedMetadata.get(CompressionConstants.META_COMPRESSION_UNCOMP_SHA1));
                assertEquals("Uncompressed size incorrect", 2516125,
                        Long.parseLong(encodedMetadata.get(CompressionConstants.META_COMPRESSION_UNCOMP_SIZE)));
                assertEquals("Compression ratio incorrect", "95.2%",
                        encodedMetadata.get(CompressionConstants.META_COMPRESSION_COMP_RATIO));
                assertEquals("Compressed size incorrect", 120338,
                        Long.parseLong(encodedMetadata.get(CompressionConstants.META_COMPRESSION_COMP_SIZE)));
                assertEquals("name1 incorrect", "bar", encodedMetadata.get("foo"));
                assertEquals("name2 incorrect", "baz", encodedMetadata.get("bar"));
                assertEquals("Transform config string incorrect", "COMP:LZMA/5",
                        encodedMetadata.get(CodecChain.META_TRANSFORM_MODE));
            }
        });
    }

    @Test
    public void testDefaultDeflate() throws Exception {
        CodecChain chain = new CodecChain(new DeflateCodec());

        Assert.assertFalse(chain.isSizePredictable());

        Map<String, String> metadata = new HashMap<String, String>();
        metadata.put("foo", "bar");
        metadata.put("bar", "baz");

        testStreams(chain, metadata, new EncodeVerifier() {
            @Override
            public void verify(byte[] encodedData, Map<String, String> encodedMetadata) {
                byte[] manualData;
                try {
                    // manually compress
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    DeflaterOutputStream dos = new DeflaterOutputStream(baos, new Deflater(CompressionConstants.DEFAULT_COMPRESSION_LEVEL));
                    dos.write(TestUtil.getOriginalData());
                    dos.close();
                    manualData = baos.toByteArray();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }

                assertArrayEquals(manualData, encodedData);

                assertEquals("Uncompressed digest incorrect", "027e997e6b1dfc97b93eb28dc9a6804096d85873",
                        encodedMetadata.get(CompressionConstants.META_COMPRESSION_UNCOMP_SHA1));
                assertEquals("Uncompressed size incorrect", 2516125,
                        Long.parseLong(encodedMetadata.get(CompressionConstants.META_COMPRESSION_UNCOMP_SIZE)));
                assertEquals("Compression ratio incorrect", "91.1%",
                        encodedMetadata.get(CompressionConstants.META_COMPRESSION_COMP_RATIO));
                assertEquals("Compressed size incorrect", 223548,
                        Long.parseLong(encodedMetadata.get(CompressionConstants.META_COMPRESSION_COMP_SIZE)));
                assertEquals("name1 incorrect", "bar", encodedMetadata.get("foo"));
                assertEquals("name2 incorrect", "baz", encodedMetadata.get("bar"));
                assertEquals("Transform config string incorrect", "COMP:Deflate/5",
                        encodedMetadata.get(CodecChain.META_TRANSFORM_MODE));
            }
        });
    }

    @Test
    public void testDefaultEncryption() throws Exception {
        KeyProvider keyProvider = new KeystoreKeyProvider(getKeystore(), "viprviprvipr".toCharArray(), "masterkey");

        CodecChain chain = new CodecChain(new EncryptionCodec()).withProperty(EncryptionCodec.PROP_KEY_PROVIDER, keyProvider);

        Assert.assertTrue(chain.isSizePredictable());
        Assert.assertEquals(2516128, chain.getEncodedSize(2516125));

        Map<String, String> metadata = new HashMap<String, String>();
        metadata.put("foo", "bar");
        metadata.put("bar", "baz");

        testStreams(chain, metadata, new EncodeVerifier() {
            @Override
            public void verify(byte[] encodedData, Map<String, String> encodedMetadata) {
                assertEquals(2516128, encodedData.length);
                assertEquals("Unencrypted digest incorrect", "027e997e6b1dfc97b93eb28dc9a6804096d85873",
                        encodedMetadata.get(EncryptionConstants.META_ENCRYPTION_UNENC_SHA1));
                assertEquals("Unencrypted size incorrect", 2516125,
                        Long.parseLong(encodedMetadata.get(EncryptionConstants.META_ENCRYPTION_UNENC_SIZE)));
                assertNotNull("Missing IV", encodedMetadata.get(EncryptionConstants.META_ENCRYPTION_IV));
                assertEquals("Incorrect master encryption key ID", "e3a69d422e6d9008e3cdfcbea674ccd9ab4758c3",
                        encodedMetadata.get(EncryptionConstants.META_ENCRYPTION_KEY_ID));
                assertNotNull("Missing object key", encodedMetadata.get(EncryptionConstants.META_ENCRYPTION_OBJECT_KEY));
                assertNotNull("Missing metadata signature", encodedMetadata.get(EncryptionConstants.META_ENCRYPTION_META_SIG));
                assertEquals("name1 incorrect", "bar", encodedMetadata.get("foo"));
                assertEquals("name2 incorrect", "baz", encodedMetadata.get("bar"));
                assertEquals("Transform config string incorrect", "ENC:AES/CBC/PKCS5Padding",
                        encodedMetadata.get(CodecChain.META_TRANSFORM_MODE));
            }
        });
    }

    @Test
    public void testDefaultEncryptionWithLzma() throws Exception {
        KeyProvider keyProvider = new KeystoreKeyProvider(getKeystore(), "viprviprvipr".toCharArray(), "masterkey");

        CodecChain chain = new CodecChain(new EncryptionCodec(), new LzmaCodec()).withProperty(EncryptionCodec.PROP_KEY_PROVIDER, keyProvider);

        Assert.assertFalse(chain.isSizePredictable());

        Map<String, String> metadata = new HashMap<String, String>();
        metadata.put("foo", "bar");
        metadata.put("bar", "baz");

        testStreams(chain, metadata, new EncodeVerifier() {
            @Override
            public void verify(byte[] encodedData, Map<String, String> encodedMetadata) {
                assertEquals(120352, encodedData.length);

                assertEquals("original digest incorrect", "027e997e6b1dfc97b93eb28dc9a6804096d85873",
                        encodedMetadata.get(CompressionConstants.META_COMPRESSION_UNCOMP_SHA1));
                assertEquals("Uncompressed size incorrect", 2516125,
                        Long.parseLong(encodedMetadata.get(CompressionConstants.META_COMPRESSION_UNCOMP_SIZE)));
                assertEquals("Compression ratio incorrect", "95.2%",
                        encodedMetadata.get(CompressionConstants.META_COMPRESSION_COMP_RATIO));
                assertEquals("Compressed size incorrect", 120338,
                        Long.parseLong(encodedMetadata.get(CompressionConstants.META_COMPRESSION_COMP_SIZE)));

                assertEquals("Unencrypted digest incorrect", "90c87618bb34c238ee7785fe9aec848a09eb02a0",
                        encodedMetadata.get(EncryptionConstants.META_ENCRYPTION_UNENC_SHA1));
                assertEquals("Unencrypted size incorrect", 120338,
                        Long.parseLong(encodedMetadata.get(EncryptionConstants.META_ENCRYPTION_UNENC_SIZE)));
                assertNotNull("Missing IV", encodedMetadata.get(EncryptionConstants.META_ENCRYPTION_IV));
                assertEquals("Incorrect master encryption key ID", "e3a69d422e6d9008e3cdfcbea674ccd9ab4758c3",
                        encodedMetadata.get(EncryptionConstants.META_ENCRYPTION_KEY_ID));
                assertNotNull("Missing object key", encodedMetadata.get(EncryptionConstants.META_ENCRYPTION_OBJECT_KEY));
                assertNotNull("Missing metadata signature", encodedMetadata.get(EncryptionConstants.META_ENCRYPTION_META_SIG));

                assertEquals("name1 incorrect", "bar", encodedMetadata.get("foo"));
                assertEquals("name2 incorrect", "baz", encodedMetadata.get("bar"));
                assertEquals("Transform config string incorrect", "COMP:LZMA/5,ENC:AES/CBC/PKCS5Padding",
                        encodedMetadata.get(CodecChain.META_TRANSFORM_MODE));
            }
        });
    }

    @Test
    public void testDefaultEncryptionWithDeflate() throws Exception {
        KeyProvider keyProvider = new KeystoreKeyProvider(getKeystore(), "viprviprvipr".toCharArray(), "masterkey");

        CodecChain chain = new CodecChain(new EncryptionCodec(), new DeflateCodec()).withProperty(EncryptionCodec.PROP_KEY_PROVIDER, keyProvider);

        Map<String, String> metadata = new HashMap<String, String>();
        metadata.put("foo", "bar");
        metadata.put("bar", "baz");

        testStreams(chain, metadata, new EncodeVerifier() {
            @Override
            public void verify(byte[] encodedData, Map<String, String> encodedMetadata) {
                byte[] deflatedData;
                try {
                    // manually deflate
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    DeflaterOutputStream dos = new DeflaterOutputStream(baos, new Deflater(CompressionConstants.DEFAULT_COMPRESSION_LEVEL));
                    dos.write(TestUtil.getOriginalData());
                    dos.close();
                    deflatedData = baos.toByteArray();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }

                assertEquals(223552, encodedData.length);

                assertEquals("original digest incorrect", "027e997e6b1dfc97b93eb28dc9a6804096d85873",
                        encodedMetadata.get(CompressionConstants.META_COMPRESSION_UNCOMP_SHA1));
                assertEquals("Uncompressed size incorrect", 2516125,
                        Long.parseLong(encodedMetadata.get(CompressionConstants.META_COMPRESSION_UNCOMP_SIZE)));
                assertEquals("Compression ratio incorrect", "91.1%",
                        encodedMetadata.get(CompressionConstants.META_COMPRESSION_COMP_RATIO));
                assertEquals("Compressed size incorrect", 223548,
                        Long.parseLong(encodedMetadata.get(CompressionConstants.META_COMPRESSION_COMP_SIZE)));

                assertEquals("Unencrypted digest incorrect", DigestUtils.sha1Hex(deflatedData),
                        encodedMetadata.get(EncryptionConstants.META_ENCRYPTION_UNENC_SHA1));
                assertEquals("Unencrypted size incorrect", 223548,
                        Long.parseLong(encodedMetadata.get(EncryptionConstants.META_ENCRYPTION_UNENC_SIZE)));
                assertNotNull("Missing IV", encodedMetadata.get(EncryptionConstants.META_ENCRYPTION_IV));
                assertEquals("Incorrect master encryption key ID", "e3a69d422e6d9008e3cdfcbea674ccd9ab4758c3",
                        encodedMetadata.get(EncryptionConstants.META_ENCRYPTION_KEY_ID));
                assertNotNull("Missing object key", encodedMetadata.get(EncryptionConstants.META_ENCRYPTION_OBJECT_KEY));
                assertNotNull("Missing metadata signature", encodedMetadata.get(EncryptionConstants.META_ENCRYPTION_META_SIG));

                assertEquals("name1 incorrect", "bar", encodedMetadata.get("foo"));
                assertEquals("name2 incorrect", "baz", encodedMetadata.get("bar"));
                assertEquals("Transform config string incorrect", "COMP:Deflate/5,ENC:AES/CBC/PKCS5Padding",
                        encodedMetadata.get(CodecChain.META_TRANSFORM_MODE));
            }
        });
    }

    protected void testStreams(CodecChain chain, Map<String, String> metadata, EncodeVerifier verifier) throws Exception {
        byte[] originalData = TestUtil.getOriginalData();
        Map<String, String> originalMeta = new HashMap<String, String>();
        originalMeta.putAll(metadata);

        // TEST PULL

        // ENCODE
        InputStream encodeIn = chain.getEncodeStream(TestUtil.getOriginalStream(), metadata);
        ByteArrayOutputStream bufferStream = new ByteArrayOutputStream();
        TestUtil.copyStream(encodeIn, bufferStream, true);

        byte[] encodedData = bufferStream.toByteArray();

        // VERIFY ENCODE
        verifier.verify(encodedData, metadata);

        // DECODE
        InputStream decodeIn = chain.getDecodeStream(new ByteArrayInputStream(encodedData), metadata);
        bufferStream = new ByteArrayOutputStream();
        TestUtil.copyStream(decodeIn, bufferStream, true);

        byte[] decodedData = bufferStream.toByteArray();

        // VERIFY INTEGRITY
        Assert.assertEquals(originalMeta, metadata);
        Assert.assertArrayEquals(originalData, decodedData);

        // TEST PUSH

        // ENCODE
        InputStream originalIn = TestUtil.getOriginalStream();
        bufferStream = new ByteArrayOutputStream();
        TestUtil.copyStream(originalIn, chain.getEncodeStream(bufferStream, metadata), true);

        encodedData = bufferStream.toByteArray();

        // VERIFY ENCODE
        verifier.verify(encodedData, metadata);

        // DECODE
        InputStream encodedIn = new ByteArrayInputStream(encodedData);
        bufferStream = new ByteArrayOutputStream();
        TestUtil.copyStream(encodedIn, chain.getDecodeStream(bufferStream, metadata), true);

        decodedData = bufferStream.toByteArray();

        // VERIFY INTEGRITY
        Assert.assertEquals(originalMeta, metadata);
        Assert.assertArrayEquals(originalData, decodedData);
    }

    protected KeyStore getKeystore() throws Exception {
        KeyStore keystore = KeyStore.getInstance("jks");
        InputStream in = this.getClass().getClassLoader().getResourceAsStream("keystore.jks");
        Assume.assumeNotNull(in);
        keystore.load(in, "viprviprvipr".toCharArray());
        return keystore;
    }

    private interface EncodeVerifier {
        void verify(byte[] encodedData, Map<String, String> encodedMetadata);
    }
}
