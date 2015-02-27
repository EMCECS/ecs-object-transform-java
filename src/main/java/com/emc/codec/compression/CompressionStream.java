/**
 * 
 */
package com.emc.codec.compression;

import java.util.Map;

/**
 * @author cwikj
 *
 */
public interface CompressionStream {

    public abstract Map<String,String> getStreamMetadata();

}
