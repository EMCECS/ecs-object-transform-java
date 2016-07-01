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

import java.lang.reflect.Method;
import java.security.Provider;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tests using the RSA BSAFE Crypto-J JCE encryption provider in FIPS 140-2 mode.
 */
public class Fips140BasicEncryptionTest extends EncryptionCodecTest {

    private static final Logger logger = LoggerFactory.getLogger(Fips140BasicEncryptionTest.class);

    @Before
    public void setUp() throws Exception {
        // Check to make sure the provider is available.
        boolean providerLoaded = false;
        
        try {
            Class<?> bsafeProvider = Class.forName("com.rsa.jsafe.provider.JsafeJCE");
            Provider p = (Provider) bsafeProvider.newInstance();
            provider = p;
            providerLoaded = true;
        } catch(ClassNotFoundException e) {
            logger.info("RSA Crypto-J JCE Provider not found: " + e);
        } catch(NoClassDefFoundError e) {
            logger.info("RSA Crypto-J JCE Provider not found: " + e);
        }
        
        Assume.assumeTrue("Crypto-J JCE provider not loaded", providerLoaded);
        super.setUp();
    }
    
    @Test
    public void testFips140CompliantMode() throws Exception {
        // Verify FIPS-140 mode.
        // Do this through reflection so tests don't fail to run/compile if the 
        // crypto-J module is not available.
        Class<?> cryptoJClass = Class.forName("com.rsa.jsafe.crypto.CryptoJ");
        Method fipsCheck = cryptoJClass.getMethod("isFIPS140Compliant", (Class<?>[])null);
        Object result = fipsCheck.invoke(null, (Object[])null);
        Assert.assertTrue("isFips140Compliant() didn't return a boolean", 
                result instanceof Boolean);
        Boolean b = (Boolean)result;
        Assert.assertTrue("Crypto-J is not FIPS-140 compliant", b);
        
        Method fipsCheck2 = cryptoJClass.getMethod("isInFIPS140Mode", (Class<?>[])null);
        Object result2 = fipsCheck2.invoke(null, (Object[])null);
        Assert.assertTrue("isFips140Compliant() didn't return a boolean", 
                result2 instanceof Boolean);
        Boolean b2 = (Boolean)result;
        Assert.assertTrue("Crypto-J is not in FIPS-140 mode", b2);

    }

}
