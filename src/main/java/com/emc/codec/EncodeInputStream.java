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

import java.io.FilterInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public abstract class EncodeInputStream<M extends EncodeMetadata> extends FilterInputStream
        implements EncodeStream<M> {
    List<EncodeListener<M>> listeners = new ArrayList<EncodeListener<M>>();
    EncodeStream prevEncodeStream;
    EncodeStream nextEncodeStream;

    public EncodeInputStream(InputStream in) {
        super(in);
        if (in instanceof EncodeInputStream) {
            prevEncodeStream = (EncodeStream) in;
            ((EncodeInputStream) in).nextEncodeStream = this;
        }
    }

    protected void notifyListeners() {
        for (EncodeListener<M> listener : listeners) {
            listener.encodeComplete(this);
        }
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
