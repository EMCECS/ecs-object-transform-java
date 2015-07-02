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

import java.io.*;
import java.util.*;

public class CodecChain {
    public static final String META_TRANSFORM_MODE = "x-emc-transform-mode";

    // apparently ServiceLoader instances are not thread-safe and we don't want to synchronize on a static property or
    // load an instance each time a codec is constructed (potentially in every read request from the encryption client)
    private static ThreadLocal<ServiceLoader<AbstractCodec>> codecLoader = new ThreadLocal<ServiceLoader<AbstractCodec>>();

    private static ServiceLoader<AbstractCodec> getCodecLoader() {
        if (codecLoader.get() == null) {
            codecLoader.set(ServiceLoader.load(AbstractCodec.class));
        }
        return codecLoader.get();
    }

    public static String[] getEncodeSpecs(Map<String, String> metaMap) {
        if (metaMap == null) return null;
        String specString = metaMap.get(META_TRANSFORM_MODE);
        if (specString == null) return null;
        return specString.split(",");
    }

    public static void addEncodeSpec(Map<String, String> metaMap, String encodeSpec) {
        String specString = metaMap.get(META_TRANSFORM_MODE);
        if (specString != null && specString.length() > 0) encodeSpec = specString + "," + encodeSpec;
        metaMap.put(META_TRANSFORM_MODE, encodeSpec);
    }

    public static void removeEncodeSpec(Map<String, String> metaMap, String encodeSpec) {
        String specString = metaMap.get(META_TRANSFORM_MODE);
        if (specString.equals(encodeSpec)) {
            metaMap.remove(META_TRANSFORM_MODE);
        } else {
            if (!specString.endsWith("," + encodeSpec))
                throw new UnsupportedOperationException("the encode chain does not include " + encodeSpec);

            metaMap.put(META_TRANSFORM_MODE,
                    specString.substring(0, specString.length() - (encodeSpec.length() + 1)));
        }
    }

    private List<AbstractCodec> codecs;
    private Map<AbstractCodec, String> specMap = new HashMap<AbstractCodec, String>();
    private Map<String, Object> properties = new HashMap<String, Object>();

    public CodecChain(String... encodeSpecs) {
        codecs = new ArrayList<AbstractCodec>();

        for (String encodeSpec : encodeSpecs) {
            boolean codecFound = false;
            for (AbstractCodec codec : getCodecLoader()) {
                if (codec.canDecode(encodeSpec)) {
                    codecFound = true;
                    codecs.add(codec);
                    specMap.put(codec, encodeSpec);
                }
            }
            if (!codecFound) throw new IllegalArgumentException("Unsupported encoder: " + encodeSpec);
        }
    }

    public CodecChain(AbstractCodec... codecs) {
        this(Arrays.asList(codecs), null);
    }

    public CodecChain(List<AbstractCodec> codecs, Map<String, Object> properties) {
        this.codecs = codecs;
        if (properties != null) this.properties = properties;
        Collections.sort(codecs); // make sure codecs are sorted by priority
    }

    public boolean isSizePredictable() {
        for (AbstractCodec codec : codecs) {
            if (!codec.isSizePredictable()) return false;
        }
        return true;
    }

    public long getEncodedSize(long originalSize) {
        long encodedSize = originalSize;
        for (AbstractCodec codec : codecs) {
            String encodeSpec = specMap.get(codec);
            encodedSize = encodeSpec == null ? codec.getEncodedSize(originalSize, properties)
                    : codec.getEncodedSize(originalSize, encodeSpec, properties);
        }
        return encodedSize;
    }

    public OutputStream getEncodeStream(OutputStream targetStream, Map<String, String> completeMetaMap) {
        for (int i = codecs.size() - 1; i >= 0; i--) { // wrap encode output streams in reverse order
            AbstractCodec codec = codecs.get(i);
            String encodeSpec = specMap.get(codec);
            if (encodeSpec == null) targetStream = codec.getEncodingStream(targetStream, properties);
            else targetStream = codec.getEncodingStream(targetStream, encodeSpec, properties);
        }

        return new MetaAddingOutputStream((EncodeOutputStream) targetStream, completeMetaMap);
    }

    public InputStream getEncodeStream(InputStream sourceStream, Map<String, String> completeMetaMap) {
        for (AbstractCodec codec : codecs) { // wrap encode input streams in natural order
            String encodeSpec = specMap.get(codec);
            if (encodeSpec == null) sourceStream = codec.getEncodingStream(sourceStream, properties);
            else sourceStream = codec.getEncodingStream(sourceStream, encodeSpec, properties);
        }

        return new MetaAddingInputStream((EncodeInputStream) sourceStream, completeMetaMap);
    }

    @SuppressWarnings("unchecked")
    public OutputStream getDecodeStream(OutputStream targetStream, Map<String, String> completeMetaMap) {
        List<EncodeMetadata> metadataList = getEncodeMetadataList(completeMetaMap);

        // wrap decode output streams in natural order
        for (int i = 0; i < codecs.size(); i++) {
            AbstractCodec codec = codecs.get(i);
            EncodeMetadata metadata = metadataList.get(i);
            targetStream = codec.getDecodingStream(targetStream, metadata, properties);
        }

        // remove encode metadata from map (we don't need it anymore)
        removeEncodeMetadata(completeMetaMap, metadataList);

        return targetStream;
    }

    @SuppressWarnings("unchecked")
    public InputStream getDecodeStream(InputStream sourceStream, Map<String, String> completeMetaMap) {
        List<EncodeMetadata> metadataList = getEncodeMetadataList(completeMetaMap);

        // wrap decode input streams in reverse order
        for (int i = codecs.size() - 1; i >= 0; i--) {
            AbstractCodec codec = codecs.get(i);
            EncodeMetadata metadata = metadataList.get(i);
            sourceStream = codec.getDecodingStream(sourceStream, metadata, properties);
        }

        // remove encode metadata from map (we don't need it anymore)
        removeEncodeMetadata(completeMetaMap, metadataList);

        return sourceStream;
    }

    public List<EncodeMetadata> getEncodeMetadataList(Map<String, String> completeMetaMap) {
        String[] encodeSpecs = getEncodeSpecs(completeMetaMap);
        List<EncodeMetadata> metadataList = new ArrayList<EncodeMetadata>();

        // if we have X codecs, we can only decode the last X encode specs.
        // this may leave the object still encoded somehow, but perhaps that is a valid use-case
        encodeSpecs = Arrays.copyOfRange(encodeSpecs, encodeSpecs.length - codecs.size(), encodeSpecs.length);

        for (int i = 0; i < codecs.size(); i++) {
            AbstractCodec codec = codecs.get(i);
            String encodeSpec = encodeSpecs[i];
            if (!codec.canDecode(encodeSpec))
                throw new RuntimeException("this codec chain cannot decode the following encode list:\n" + Arrays.toString(encodeSpecs));

            EncodeMetadata metadata = codec.createEncodeMetadata(encodeSpec, completeMetaMap);
            metadataList.add(metadata);
        }

        return metadataList;
    }

    public class MetaAddingOutputStream extends FilterOutputStream {
        private EncodeOutputStream firstOutputStream;
        private Map<String, String> metaMap;

        /**
         * wrap head of chain for output streams
         */
        public MetaAddingOutputStream(EncodeOutputStream firstOutputStream, Map<String, String> metaMap) {
            super(firstOutputStream);
            this.firstOutputStream = firstOutputStream;
            this.metaMap = metaMap;
        }

        // Override because FilterOutputStream does not do array writes.
        @Override
        public void write(byte[] b) throws IOException {
            out.write(b);
        }

        // Override because FilterOutputStream does not do array writes.
        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            out.write(b, off, len);
        }

        @Override
        public void close() throws IOException {
            super.close();
            addEncodeMetadata(metaMap, firstOutputStream);
        }
    }

    public class MetaAddingInputStream extends FilterInputStream {
        private EncodeInputStream lastInputStream;
        private Map<String, String> metaMap;

        /**
         * wrap tail of chain for input streams
         */
        public MetaAddingInputStream(EncodeInputStream lastInputStream, Map<String, String> metaMap) {
            super(lastInputStream);
            this.lastInputStream = lastInputStream;
            this.metaMap = metaMap;
        }

        @Override
        public void close() throws IOException {
            super.close();
            addEncodeMetadata(metaMap, lastInputStream);
        }
    }

    protected void addEncodeMetadata(Map<String, String> metaMap, EncodeStream encodeStream) {

        // add all encode metadata to the meta map
        encodeStream = encodeStream.getChainHead(); // make sure we start at the head of the chain
        do {
            EncodeMetadata metadata = encodeStream.getEncodeMetadata();
            metaMap.putAll(metadata.toMap());
            addEncodeSpec(metaMap, metadata.getEncodeSpec());
            encodeStream = encodeStream.getNext();
        } while (encodeStream != null);
    }

    public void removeEncodeMetadata(Map<String, String> metaMap, List<EncodeMetadata> encodeMetaList) {
        // remove decoded specs from the chain spec and remove related metadata
        // must remove in reverse order (tail to head)
        for (int i = encodeMetaList.size() - 1; i >= 0; i--) {
            EncodeMetadata metadata = encodeMetaList.get(i);
            removeEncodeSpec(metaMap, metadata.getEncodeSpec()); // remove spec from end of chain
            metaMap.keySet().removeAll(metadata.toMap().keySet()); // remove encode metadata
        }
    }

    public Map<String, Object> getProperties() {
        return properties;
    }

    public void setProperties(Map<String, Object> properties) {
        this.properties = properties;
    }

    public CodecChain withProperties(Map<String, Object> properties) {
        setProperties(properties);
        return this;
    }

    public void addProperty(String name, Object value) {
        properties.put(name, value);
    }

    public CodecChain withProperty(String name, Object value) {
        addProperty(name, value);
        return this;
    }
}
