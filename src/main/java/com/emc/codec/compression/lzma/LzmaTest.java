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

package com.emc.codec.compression.lzma;

import SevenZip.Compression.LZMA.Decoder;

import java.io.*;

public class LzmaTest {

    public static void main(String[] args) {
        File fin = new File(args[0]);
        File fout = new File(args[1]);
        
        try {
            OutputStream fos = new BufferedOutputStream(new FileOutputStream(fout));
            int compressionLevel = 4, bufferSize = 16 * 1024;
            LzmaProfile lzmaProfile = LzmaProfile.fromCompressionLevel(compressionLevel);
            LzmaEncodeOutputStream compOut = new LzmaEncodeOutputStream(fos, "COMP:LZMA/4", lzmaProfile, bufferSize);

            FileInputStream fis = new FileInputStream(fin);
            byte[] buffer = new byte[4096];
            int c = 0;
            while((c = fis.read(buffer)) != -1) {
                compOut.write(buffer, 0, c);
            }
            
            fis.close();
            compOut.close();
            compOut = null;
            Runtime.getRuntime().gc();
            
            System.out.printf("Done.  Input size %d compressed size %d\n", fin.length(), fout.length());
            
            System.out.printf("Free: %d max: %d total: %d\n", Runtime.getRuntime().freeMemory(), Runtime.getRuntime().maxMemory(), Runtime.getRuntime().totalMemory());
            
            // Decompress
            InputStream compIn = new FileInputStream(fout);
            ByteArrayOutputStream decompOut = new ByteArrayOutputStream((int) fin.length());
            Decoder d = new Decoder();
            // Read props
            byte[] props = new byte[5];
            compIn.read(props);
            d.SetDecoderProperties(props);
            d.Code(compIn, decompOut, -1);
            
            System.out.printf("Done. Input size %d uncompressed size %d\n", fout.length(), decompOut.size());
            
            
        } catch(Exception e) {
            e.printStackTrace();
        }

    }

}
