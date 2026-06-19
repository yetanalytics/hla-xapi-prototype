package com.yetanalytics.hlaxapi;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

import org.portico.impl.hla1516e.types.encoding.HLA1516eEncoderFactory;

import hla.rti1516e.encoding.DataElement;
import hla.rti1516e.encoding.EncoderFactory;
import hla.rti1516e.encoding.EncoderException;

public class HLAEncodingTestSupport {

    private HLAEncodingTestSupport() {
    }

    static EncoderFactory testEncoderFactory() {
        return new HLA1516eEncoderFactory();
    }

    static byte[] int16(short value, ByteOrder byteOrder) {
        return ByteBuffer.allocate(Short.BYTES).order(byteOrder).putShort(value).array();
    }

    static byte[] int32(int value, ByteOrder byteOrder) {
        return ByteBuffer.allocate(Integer.BYTES).order(byteOrder).putInt(value).array();
    }

    static byte[] int64(long value, ByteOrder byteOrder) {
        return ByteBuffer.allocate(Long.BYTES).order(byteOrder).putLong(value).array();
    }

    static byte[] float32(float value, ByteOrder byteOrder) {
        return ByteBuffer.allocate(Float.BYTES).order(byteOrder).putFloat(value).array();
    }

    static byte[] float64(double value, ByteOrder byteOrder) {
        return ByteBuffer.allocate(Double.BYTES).order(byteOrder).putDouble(value).array();
    }

    static byte[] asciiString(String value) {
        return variableBytes(value.getBytes(StandardCharsets.US_ASCII));
    }

    static byte[] unicodeString(String value) {
        return encoded(testEncoderFactory().createHLAunicodeString(value));
    }

    static byte[] variableBytes(byte[] value) {
        return ByteBuffer.allocate(Integer.BYTES + value.length)
                .order(ByteOrder.BIG_ENDIAN)
                .putInt(value.length)
                .put(value)
                .array();
    }

    static byte[] fixedArray(byte[]... encodedElements) {
        int size = Integer.BYTES;
        for (byte[] encodedElement : encodedElements) {
            size += encodedElement.length;
        }
        ByteBuffer buffer = ByteBuffer.allocate(size)
                .order(ByteOrder.BIG_ENDIAN)
                .putInt(encodedElements.length);
        for (byte[] encodedElement : encodedElements) {
            buffer.put(encodedElement);
        }
        return buffer.array();
    }

    static byte[] variableArray(byte[]... encodedElements) {
        int size = Integer.BYTES;
        for (byte[] encodedElement : encodedElements) {
            size += encodedElement.length;
        }
        ByteBuffer buffer = ByteBuffer.allocate(size)
                .order(ByteOrder.BIG_ENDIAN)
                .putInt(encodedElements.length);
        for (byte[] encodedElement : encodedElements) {
            buffer.put(encodedElement);
        }
        return buffer.array();
    }

    private static byte[] encoded(DataElement element) {
        try {
            return element.toByteArray();
        } catch (EncoderException e) {
            throw new IllegalStateException("Failed to encode test value", e);
        }
    }
}
