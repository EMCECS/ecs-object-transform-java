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

import org.apache.commons.codec.binary.Base64;

import javax.crypto.*;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;

public class AESTest {
    public static final String TEST_CIPHER = EncryptionUtil.getCipherSpec(EncryptionCodec.AES_CBC_PKCS5_CIPHER);

    private enum Mode {ENCRYPT, DECRYPT}
    
    public static void main(String[] args) {
        try {
            Mode m = Mode.valueOf(args[0]);
            
            if(m == Mode.ENCRYPT) {
                int bits = Integer.valueOf(args[1]);
                String infile = args[2];
                String outfile = args[3];
                
                // Generate key
                KeyGenerator kg = KeyGenerator.getInstance("AES");
                kg.init(bits);
                SecretKey sk = kg.generateKey();

                Cipher cipher = Cipher.getInstance(TEST_CIPHER);
                cipher.init(Cipher.ENCRYPT_MODE, sk);
                
                System.out.println("Key: " + new String(Base64.encodeBase64(sk.getEncoded()), "US-ASCII"));
                System.out.println("IV: " + new String(Base64.encodeBase64(cipher.getIV()), "US-ASCII"));
                
                CipherOutputStream out = new CipherOutputStream(new FileOutputStream(new File(outfile)), cipher);
                InputStream in = new FileInputStream(new File(infile));
                
                doStream(in, out);
                
                out.close();
                in.close();
            } else if(m == Mode.DECRYPT) {
                String key = args[1];
                String iv = args[2];
                String infile = args[3];
                String outfile = args[4];
                
                SecretKeySpec sk = new SecretKeySpec(Base64.decodeBase64(key.getBytes("US-ASCII")), "AES");
                IvParameterSpec ivspec = new IvParameterSpec(Base64.decodeBase64(iv.getBytes("US-ASCII")));
                Cipher cipher = Cipher.getInstance(TEST_CIPHER);
                cipher.init(Cipher.DECRYPT_MODE, sk, ivspec);

                CipherInputStream in = new CipherInputStream(new FileInputStream(new File(infile)), cipher);
                OutputStream out = new FileOutputStream(new File(outfile));
                
                doStream(in, out);
                
                out.close();
                in.close();
            }
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    private static void doStream(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[128*1024];
        int c = 0;
        while((c = in.read(buffer)) != -1) {
            out.write(buffer, 0, c);
        }
    }

}
