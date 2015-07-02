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

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

public abstract class EncodeOutputStream<M extends EncodeMetadata> extends FilterOutputStream
        implements EncodeStream<M> {
    List<EncodeListener<M>> listeners = new ArrayList<EncodeListener<M>>();
    EncodeStream prevEncodeStream;
    EncodeStream nextEncodeStream;

    public EncodeOutputStream(OutputStream out) {
        super(out);
        if (out instanceof EncodeOutputStream) {
            ((EncodeOutputStream) out).prevEncodeStream = this;
            this.nextEncodeStream = (EncodeStream) out;
        }
    }

    protected void notifyListeners() {
        for (EncodeListener<M> listener : listeners) {
            listener.encodeComplete(this);
        }
    }

    // Override because the default FilterOutputStream method does not delegate array writes.
    @Override
    public void write(byte[] b) throws IOException {
        out.write(b);
    }

    // Override because the default FilterOutputStream method does not delegate array writes.
    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        out.write(b, off, len);
    }

    @Override
    public void addListener(EncodeListener<M> listener) {
        listeners.add(listener);
    }

    @Override
    public void removeListener(EncodeListener<M> listener) {
        listeners.remove(listener);
    }

    @Override
    public EncodeStream getChainHead() {
        return prevEncodeStream == null ? this : prevEncodeStream.getChainHead();
    }

    @Override
    public EncodeStream getNext() {
        return nextEncodeStream;
    }
}
