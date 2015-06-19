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

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.security.SecureRandom;
import java.util.Random;

public class AesPerformanceTest {
    protected SecureRandom secureRandom;

    protected int[] dataSizes = new int[]{
            32 * 1024 * 1024, // 32MB
            128 * 1024 * 1024 // 128MB
    };
    protected int[] bufferSizes = new int[]{
            16 * 1024, // 16k
            64 * 1024, // 64k
            128 * 1024 // 128k
    };

    @Before
    public void setSecureRandom() throws Exception {
        secureRandom = SecureRandom.getInstance("SHA1PRNG");
    }

    @Test
    public void testAES128() throws Exception {
        KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");
        keyGenerator.init(128, secureRandom);
        System.out.println("AES 128-bit:");
        testAll(keyGenerator.generateKey());
    }

    @Test
    public void testAES256() throws Exception {
        Assume.assumeTrue(Cipher.getMaxAllowedKeyLength("AES") >= 256);
        KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");
        keyGenerator.init(256, secureRandom);
        System.out.println("AES 256-bit:");
        testAll(keyGenerator.generateKey());
    }

    protected void testAll(SecretKey secretKey) throws Exception {
        for (int dataSize : dataSizes) {
            for (int bufferSize : bufferSizes) {
                testEncDec(secretKey, dataSize, bufferSize);
            }
        }
    }

    protected void testEncDec(SecretKey secretKey, int dataSize, int bufferSize) throws Exception {
        File inFile = writeRandomData(dataSize);
        File encFile = File.createTempFile("enc-" + dataSize, null);
        encFile.deleteOnExit();
        File decFile = File.createTempFile("dec-" + dataSize, null);
        decFile.deleteOnExit();

        Cipher cipher = Cipher.getInstance(EncryptionCodec.AES_CBC_PKCS5_CIPHER);

        // byte array
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, secureRandom);
        byte[] iv = cipher.getIV();
        long duration = timeByteArrayStream(cipher, inFile, encFile, bufferSize);
        System.out.println("    byte encrypt:  " + summarize(dataSize, bufferSize, duration));

        cipher.init(Cipher.DECRYPT_MODE, secretKey, new IvParameterSpec(iv));
        duration = timeByteArrayStream(cipher, encFile, decFile, bufferSize);
        System.out.println("    byte decrypt:  " + summarize(dataSize, bufferSize, duration));

        Assert.assertTrue(equal(inFile, decFile));

        // byte buffer (nio)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, secureRandom);
        iv = cipher.getIV();
        duration = timeDirectByteBuffer(cipher, inFile, encFile, bufferSize);
        System.out.println("     nio encrypt:  " + summarize(dataSize, bufferSize, duration));

        cipher.init(Cipher.DECRYPT_MODE, secretKey, new IvParameterSpec(iv));
        duration = timeDirectByteBuffer(cipher, encFile, decFile, bufferSize);
        System.out.println("     nio decrypt:  " + summarize(dataSize, bufferSize, duration));

        Assert.assertTrue(equal(inFile, decFile));
    }

    protected File writeRandomData(int size) throws Exception {
        File file = File.createTempFile("random-" + size, null);
        file.deleteOnExit();
        OutputStream out = new FileOutputStream(file);
        Random random = new Random();
        int bufferSize = 64 * 1024, written = 0, toWrite = bufferSize;
        byte[] buffer = new byte[bufferSize];
        while (written < size) {
            random.nextBytes(buffer);
            if (written + toWrite > size) toWrite = size - written;
            out.write(buffer, 0, toWrite);
            written += toWrite;
        }
        return file;
    }

    protected long timeByteArrayStream(Cipher cipher, File inFile, File outFile, int bufferSize) throws Exception {
        InputStream in = new FileInputStream(inFile);
        OutputStream out = new FileOutputStream(outFile);
        byte[] buffer = new byte[bufferSize];
        long start = System.nanoTime();
        while (true) {
            int count = in.read(buffer);
            if (count == -1) break;
            out.write(cipher.update(buffer, 0, count));
        }
        out.write(cipher.doFinal());
        long duration = System.nanoTime() - start;
        in.close();
        out.close();
        return duration;
    }

    protected long timeDirectByteBuffer(Cipher cipher, File inFile, File outFile, int bufferSize) throws Exception {
        FileChannel cin = new FileInputStream(inFile).getChannel();
        FileChannel cout = new FileOutputStream(outFile).getChannel();
        ByteBuffer bin = ByteBuffer.allocateDirect(bufferSize);
        ByteBuffer bout = ByteBuffer.allocateDirect(bufferSize * 2); // must allow for buffering in the cipher
        long start = System.nanoTime();
        while (true) {
            int count = cin.read(bin);
            if (count == -1) break;
            bin.flip();
            cipher.update(bin, bout);
            bout.flip();
            cout.write(bout);
            bin.clear();
            bout.clear();
        }
        bout.put(cipher.doFinal());
        bout.flip();
        cout.write(bout);
        long duration = System.nanoTime() - start;
        cin.close();
        cout.close();
        return duration;
    }

    protected boolean equal(File file1, File file2) throws Exception {
        InputStream in1 = new FileInputStream(file1), in2 = new FileInputStream(file2);
        int bufferSize = 64 * 1024;
        byte[] buffer1 = new byte[bufferSize], buffer2 = new byte[bufferSize];
        try {
            while (true) {
                int count1 = in1.read(buffer1);
                if (count1 == -1) return in2.read() == -1;
                int count2 = 0;
                while (count2 < count1) {
                    int c2 = in2.read(buffer2, count2, count1 - count2);
                    if (c2 == -1) return false;
                    count2 += c2;
                }
                for (int i = 0; i < count1; i++) {
                    if (buffer1[i] != buffer2[i]) return false;
                }
            }
        } finally {
            in1.close();
            in2.close();
        }
    }

    protected String summarize(int dataSize, int bufferSize, long duration) {
        return String.format("%4dMB, %3dk buffer: %dms (%dMB/s)",
                dataSize / 1024 / 1024, bufferSize / 1024, duration / 1000000,
                (long) dataSize * 1000000000L / duration / 1024 / 1024);
    }
}
