package com.yetanalytics.hlaxapi;

import hla.rti1516e.encoding.DecoderException;

@FunctionalInterface
public interface HLAValueDecoder<T> {
    T decode(byte[] bytes) throws DecoderException;
}
