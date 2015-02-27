/**
 * 
 */
package com.emc.codec.compression;

import java.io.InputStream;
import java.util.Map;

import com.emc.codec.InputTransform;

/**
 * @author cwikj
 *
 */
public abstract class CompressionInputTransform extends InputTransform {

    public CompressionInputTransform(InputStream streamToDecode,
            Map<String, String> metadataToDecode) {
        super(streamToDecode, metadataToDecode);
    }

}
