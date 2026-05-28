package com.yetanalytics.hlaxapi;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import hla.rti1516e.encoding.ByteWrapper;
import hla.rti1516e.encoding.DataElement;
import hla.rti1516e.encoding.DataElementFactory;
import hla.rti1516e.encoding.DecoderException;
import hla.rti1516e.encoding.EncoderFactory;
import hla.rti1516e.encoding.HLAASCIIchar;
import hla.rti1516e.encoding.HLAASCIIstring;
import hla.rti1516e.encoding.HLAboolean;
import hla.rti1516e.encoding.HLAbyte;
import hla.rti1516e.encoding.HLAfixedArray;
import hla.rti1516e.encoding.HLAfloat32BE;
import hla.rti1516e.encoding.HLAfloat32LE;
import hla.rti1516e.encoding.HLAfloat64BE;
import hla.rti1516e.encoding.HLAfloat64LE;
import hla.rti1516e.encoding.HLAinteger16BE;
import hla.rti1516e.encoding.HLAinteger16LE;
import hla.rti1516e.encoding.HLAinteger32BE;
import hla.rti1516e.encoding.HLAinteger32LE;
import hla.rti1516e.encoding.HLAinteger64BE;
import hla.rti1516e.encoding.HLAinteger64LE;
import hla.rti1516e.encoding.HLAoctet;
import hla.rti1516e.encoding.HLAoctetPairBE;
import hla.rti1516e.encoding.HLAoctetPairLE;
import hla.rti1516e.encoding.HLAopaqueData;
import hla.rti1516e.encoding.HLAvariableArray;
import hla.rti1516e.encoding.HLAunicodeChar;
import hla.rti1516e.encoding.HLAunicodeString;

class HLADecoderRegistryTest {

    private final HLADecoderRegistry registry = new HLADecoderRegistry(testEncoderFactory());

    @ParameterizedTest(name = "{0}")
    @MethodSource("builtInDecodings")
    void decodesBuiltInHlaTypes(String hlaType, byte[] encodedValue, Object expectedValue) throws DecoderException {
        Object actualValue = registry.decode(hlaType, encodedValue);

        if (expectedValue instanceof byte[] expectedBytes) {
            assertArrayEquals(expectedBytes, (byte[]) actualValue);
        } else {
            assertEquals(expectedValue, actualValue);
        }
    }

    @Test
    void returnsTypedDecoderForRegisteredType() throws DecoderException {
        HLAValueDecoder<Float> decoder = registry.decoderFor("HLAfloat32LE", Float.class);

        assertEquals(3.5f, decoder.decode(float32(3.5f, ByteOrder.LITTLE_ENDIAN)));
    }

    @Test
    void decodesPackageQualifiedTypeNames() throws DecoderException {
        assertEquals(42, registry.decode(
                "hla.rti1516e.encoding.HLAinteger32BE",
                int32(42, ByteOrder.BIG_ENDIAN),
                Integer.class));
    }

    @Test
    void supportsFomSpecificAliases() throws DecoderException {
        registry.registerAlias("ScaleFactorFloat32", "HLAfloat32BE");

        assertEquals(2.25f, registry.decode(
                "ScaleFactorFloat32",
                float32(2.25f, ByteOrder.BIG_ENDIAN),
                Float.class));
    }

    @Test
    void decodesRegisteredVariableArrays() throws DecoderException {
        registry.registerVariableArray("IntegerHistory", "HLAinteger32BE", Integer.class);

        assertEquals(List.of(7, -3, 42), registry.decode(
                "IntegerHistory",
                variableArray(
                        int32(7, ByteOrder.BIG_ENDIAN),
                        int32(-3, ByteOrder.BIG_ENDIAN),
                        int32(42, ByteOrder.BIG_ENDIAN)),
                List.class));
    }

    @Test
    void decodesArraysOfAliases() throws DecoderException {
        registry.registerAlias("FuelInt32", "HLAinteger32BE");
        registry.registerVariableArray("FuelHistory", "FuelInt32", Integer.class);

        assertEquals(List.of(100, 75, 50), registry.decode(
                "FuelHistory",
                variableArray(
                        int32(100, ByteOrder.BIG_ENDIAN),
                        int32(75, ByteOrder.BIG_ENDIAN),
                        int32(50, ByteOrder.BIG_ENDIAN)),
                List.class));
    }

    @Test
    void decodesRegisteredFixedArrays() throws DecoderException {
        registry.registerFixedArray("EulerAngles", "HLAfloat64LE", 3, Double.class);

        assertEquals(List.of(10.5d, -20.25d, 30.125d), registry.decode(
                "EulerAngles",
                fixedArray(
                        float64(10.5d, ByteOrder.LITTLE_ENDIAN),
                        float64(-20.25d, ByteOrder.LITTLE_ENDIAN),
                        float64(30.125d, ByteOrder.LITTLE_ENDIAN)),
                List.class));
    }

    @Test
    void decodesNestedRegisteredArrays() throws DecoderException {
        registry.registerFixedArray("CoordinatePair", "HLAinteger16BE", 2, Short.class);
        registry.registerVariableArray("CoordinateHistory", "CoordinatePair", List.class);

        assertEquals(
                List.of(
                        List.of((short) 1, (short) 2),
                        List.of((short) -3, (short) 4)),
                registry.decode(
                        "CoordinateHistory",
                        variableArray(
                                fixedArray(
                                        int16((short) 1, ByteOrder.BIG_ENDIAN),
                                        int16((short) 2, ByteOrder.BIG_ENDIAN)),
                                fixedArray(
                                        int16((short) -3, ByteOrder.BIG_ENDIAN),
                                        int16((short) 4, ByteOrder.BIG_ENDIAN))),
                        List.class));
    }

    @Test
    void rejectsUnknownTypes() {
        assertThrows(IllegalArgumentException.class, () -> registry.decoderFor("MissingHlaType"));
    }

    @Test
    void rejectsMismatchedJavaTypes() {
        assertThrows(IllegalArgumentException.class, () -> registry.decoderFor("HLAfloat32BE", Integer.class));
    }

    @Test
    void rejectsValueOnlyDecodersAsArrayElements() {
        registry.register("CustomValue", Object.class, bytes -> new Object());

        assertThrows(IllegalArgumentException.class,
                () -> registry.registerVariableArray("CustomArray", "CustomValue", Object.class));
    }

    static Stream<Arguments> builtInDecodings() {
        return Stream.of(
                Arguments.of("HLAboolean", new byte[] { 1 }, Boolean.TRUE),
                Arguments.of("HLAinteger16BE", int16((short) -12345, ByteOrder.BIG_ENDIAN), (short) -12345),
                Arguments.of("HLAinteger16LE", int16((short) -12345, ByteOrder.LITTLE_ENDIAN), (short) -12345),
                Arguments.of("HLAinteger16", int16((short) 12345, ByteOrder.BIG_ENDIAN), (short) 12345),
                Arguments.of("HLAinteger32BE", int32(-1234567, ByteOrder.BIG_ENDIAN), -1234567),
                Arguments.of("HLAinteger32LE", int32(-1234567, ByteOrder.LITTLE_ENDIAN), -1234567),
                Arguments.of("HLAinteger32", int32(7654321, ByteOrder.BIG_ENDIAN), 7654321),
                Arguments.of("HLAinteger64BE", int64(-123456789012345L, ByteOrder.BIG_ENDIAN), -123456789012345L),
                Arguments.of("HLAinteger64LE", int64(-123456789012345L, ByteOrder.LITTLE_ENDIAN), -123456789012345L),
                Arguments.of("HLAinteger64", int64(123456789012345L, ByteOrder.BIG_ENDIAN), 123456789012345L),
                Arguments.of("HLAfloat32BE", float32(12.5f, ByteOrder.BIG_ENDIAN), 12.5f),
                Arguments.of("HLAfloat32LE", float32(12.5f, ByteOrder.LITTLE_ENDIAN), 12.5f),
                Arguments.of("HLAfloat32", float32(-6.75f, ByteOrder.BIG_ENDIAN), -6.75f),
                Arguments.of("HLAfloat64BE", float64(-42.25d, ByteOrder.BIG_ENDIAN), -42.25d),
                Arguments.of("HLAfloat64LE", float64(-42.25d, ByteOrder.LITTLE_ENDIAN), -42.25d),
                Arguments.of("HLAfloat64", float64(128.5d, ByteOrder.BIG_ENDIAN), 128.5d),
                Arguments.of("HLAoctet", new byte[] { (byte) 0xA5 }, (byte) 0xA5),
                Arguments.of("HLAbyte", new byte[] { (byte) 0x80 }, (byte) 0x80),
                Arguments.of("HLAASCIIchar", new byte[] { 'Z' }, 'Z'),
                Arguments.of("HLAunicodeChar", int16((short) '\u03a9', ByteOrder.BIG_ENDIAN), '\u03a9'),
                Arguments.of("HLAcharacter", new byte[] { 'A' }, 'A'),
                Arguments.of("HLAoctetPairBE", int16((short) 0x1234, ByteOrder.BIG_ENDIAN), (short) 0x1234),
                Arguments.of("HLAoctetPairLE", int16((short) 0x1234, ByteOrder.LITTLE_ENDIAN), (short) 0x1234),
                Arguments.of("HLAoctetPair", int16((short) 0x4567, ByteOrder.BIG_ENDIAN), (short) 0x4567),
                Arguments.of("HLAopaqueData", variableBytes(new byte[] { 1, 2, (byte) 0xFF }),
                        new byte[] { 1, 2, (byte) 0xFF }),
                Arguments.of("HLAASCIIstring", asciiString("xAPI Ready"), "xAPI Ready"),
                Arguments.of("HLAunicodeString", unicodeString("R\u00e9sum\u00e9"), "R\u00e9sum\u00e9"));
    }

    private static EncoderFactory testEncoderFactory() {
        return (EncoderFactory) Proxy.newProxyInstance(
                HLADecoderRegistryTest.class.getClassLoader(),
                new Class<?>[] { EncoderFactory.class },
                (proxy, method, args) -> {
                    Object initialValue = args != null && args.length == 1 ? args[0] : null;
                    return switch (method.getName()) {
                        case "createHLAboolean" -> element(HLAboolean.class,
                                fixedLengthDecoder(1, HLADecoderRegistryTest::decodeBoolean), initialValue);
                        case "createHLAinteger16BE" -> element(HLAinteger16BE.class,
                                fixedLengthDecoder(Short.BYTES, bytes -> decodeShort(bytes, ByteOrder.BIG_ENDIAN)),
                                initialValue);
                        case "createHLAinteger16LE" -> element(HLAinteger16LE.class,
                                fixedLengthDecoder(Short.BYTES, bytes -> decodeShort(bytes, ByteOrder.LITTLE_ENDIAN)),
                                initialValue);
                        case "createHLAinteger32BE" -> element(HLAinteger32BE.class,
                                fixedLengthDecoder(Integer.BYTES, bytes -> decodeInt(bytes, ByteOrder.BIG_ENDIAN)),
                                initialValue);
                        case "createHLAinteger32LE" -> element(HLAinteger32LE.class,
                                fixedLengthDecoder(Integer.BYTES, bytes -> decodeInt(bytes, ByteOrder.LITTLE_ENDIAN)),
                                initialValue);
                        case "createHLAinteger64BE" -> element(HLAinteger64BE.class,
                                fixedLengthDecoder(Long.BYTES, bytes -> decodeLong(bytes, ByteOrder.BIG_ENDIAN)),
                                initialValue);
                        case "createHLAinteger64LE" -> element(HLAinteger64LE.class,
                                fixedLengthDecoder(Long.BYTES, bytes -> decodeLong(bytes, ByteOrder.LITTLE_ENDIAN)),
                                initialValue);
                        case "createHLAfloat32BE" -> element(HLAfloat32BE.class,
                                fixedLengthDecoder(Float.BYTES, bytes -> decodeFloat(bytes, ByteOrder.BIG_ENDIAN)),
                                initialValue);
                        case "createHLAfloat32LE" -> element(HLAfloat32LE.class,
                                fixedLengthDecoder(Float.BYTES, bytes -> decodeFloat(bytes, ByteOrder.LITTLE_ENDIAN)),
                                initialValue);
                        case "createHLAfloat64BE" -> element(HLAfloat64BE.class,
                                fixedLengthDecoder(Double.BYTES, bytes -> decodeDouble(bytes, ByteOrder.BIG_ENDIAN)),
                                initialValue);
                        case "createHLAfloat64LE" -> element(HLAfloat64LE.class,
                                fixedLengthDecoder(Double.BYTES, bytes -> decodeDouble(bytes, ByteOrder.LITTLE_ENDIAN)),
                                initialValue);
                        case "createHLAoctet" -> element(HLAoctet.class,
                                fixedLengthDecoder(1, HLADecoderRegistryTest::decodeByte), initialValue);
                        case "createHLAbyte" -> element(HLAbyte.class,
                                fixedLengthDecoder(1, HLADecoderRegistryTest::decodeByte), initialValue);
                        case "createHLAASCIIchar" -> element(HLAASCIIchar.class,
                                fixedLengthDecoder(1, HLADecoderRegistryTest::decodeByte), initialValue);
                        case "createHLAunicodeChar" -> element(HLAunicodeChar.class,
                                fixedLengthDecoder(Short.BYTES, bytes -> decodeShort(bytes, ByteOrder.BIG_ENDIAN)),
                                initialValue);
                        case "createHLAoctetPairBE" -> element(HLAoctetPairBE.class,
                                fixedLengthDecoder(Short.BYTES, bytes -> decodeShort(bytes, ByteOrder.BIG_ENDIAN)),
                                initialValue);
                        case "createHLAoctetPairLE" -> element(HLAoctetPairLE.class,
                                fixedLengthDecoder(Short.BYTES, bytes -> decodeShort(bytes, ByteOrder.LITTLE_ENDIAN)),
                                initialValue);
                        case "createHLAopaqueData" -> element(HLAopaqueData.class,
                                variableByteLengthDecoder(HLADecoderRegistryTest::decodeOpaqueData), initialValue);
                        case "createHLAASCIIstring" -> element(HLAASCIIstring.class,
                                variableByteLengthDecoder(HLADecoderRegistryTest::decodeAsciiString), initialValue);
                        case "createHLAunicodeString" -> element(HLAunicodeString.class,
                                unicodeStringDecoder(), initialValue);
                        case "createHLAfixedArray" -> fixedArray(
                                dataElementFactory(args[0]),
                                (Integer) args[1]);
                        case "createHLAvariableArray" -> variableArray(dataElementFactory(args[0]));
                        default -> throw new UnsupportedOperationException(method.getName());
                    };
                });
    }

    private static <T> T element(Class<T> elementInterface, ByteDecoder decoder, Object initialValue) {
        AtomicReference<Object> value = new AtomicReference<Object>(initialValue);
        Object element = Proxy.newProxyInstance(
                HLADecoderRegistryTest.class.getClassLoader(),
                new Class<?>[] { elementInterface },
                (proxy, method, args) -> {
                    return switch (method.getName()) {
                        case "decode" -> {
                            if (args != null && args.length == 1 && args[0] instanceof byte[] bytes) {
                                value.set(decoder.decode(bytes));
                                yield null;
                            }
                            if (args != null && args.length == 1 && args[0] instanceof ByteWrapper byteWrapper) {
                                value.set(decoder.decode(byteWrapper));
                                yield null;
                            }
                            throw new UnsupportedOperationException("decode");
                        }
                        case "getValue" -> value.get();
                        case "setValue" -> {
                            value.set(args[0]);
                            yield null;
                        }
                        case "size" -> ((byte[]) value.get()).length;
                        case "get" -> ((byte[]) value.get())[(Integer) args[0]];
                        case "iterator" -> opaqueDataIterator((byte[]) value.get());
                        case "getOctetBoundary" -> 1;
                        case "getEncodedLength" -> decoder.encodedLength();
                        case "encode", "toByteArray" -> throw new UnsupportedOperationException(method.getName());
                        case "toString" -> elementInterface.getSimpleName() + "[" + value.get() + "]";
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "equals" -> proxy == args[0];
                        default -> throw new UnsupportedOperationException(method.getName());
                    };
                });
        return elementInterface.cast(element);
    }

    private static HLAfixedArray<DataElement> fixedArray(DataElementFactory<DataElement> factory, int size) {
        List<DataElement> elements = new ArrayList<DataElement>(size);
        for (int index = 0; index < size; index++) {
            elements.add(factory.createElement(index));
        }
        Object fixedArray = Proxy.newProxyInstance(
                HLADecoderRegistryTest.class.getClassLoader(),
                new Class<?>[] { HLAfixedArray.class },
                (proxy, method, args) -> switch (method.getName()) {
                    case "decode" -> {
                        if (args != null && args.length == 1 && args[0] instanceof byte[] bytes) {
                            decodeComplete(bytes, byteWrapper -> decodeElements(byteWrapper, elements));
                            yield null;
                        }
                        if (args != null && args.length == 1 && args[0] instanceof ByteWrapper byteWrapper) {
                            decodeElements(byteWrapper, elements);
                            yield null;
                        }
                        throw new UnsupportedOperationException("decode");
                    }
                    case "size" -> elements.size();
                    case "get" -> elements.get((Integer) args[0]);
                    case "iterator" -> elements.iterator();
                    case "getOctetBoundary" -> 1;
                    case "getEncodedLength" -> encodedLength(elements);
                    case "encode", "toByteArray" -> throw new UnsupportedOperationException(method.getName());
                    case "toString" -> "HLAfixedArray" + elements;
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        @SuppressWarnings("unchecked")
        HLAfixedArray<DataElement> typedArray = (HLAfixedArray<DataElement>) fixedArray;
        return typedArray;
    }

    private static HLAvariableArray<DataElement> variableArray(DataElementFactory<DataElement> factory) {
        List<DataElement> elements = new ArrayList<DataElement>();
        Object variableArray = Proxy.newProxyInstance(
                HLADecoderRegistryTest.class.getClassLoader(),
                new Class<?>[] { HLAvariableArray.class },
                (proxy, method, args) -> switch (method.getName()) {
                    case "decode" -> {
                        if (args != null && args.length == 1 && args[0] instanceof byte[] bytes) {
                            decodeComplete(bytes, byteWrapper -> decodeVariableElements(byteWrapper, factory, elements));
                            yield null;
                        }
                        if (args != null && args.length == 1 && args[0] instanceof ByteWrapper byteWrapper) {
                            decodeVariableElements(byteWrapper, factory, elements);
                            yield null;
                        }
                        throw new UnsupportedOperationException("decode");
                    }
                    case "addElement" -> {
                        elements.add((DataElement) args[0]);
                        yield null;
                    }
                    case "resize" -> {
                        resizeElements(factory, elements, (Integer) args[0]);
                        yield null;
                    }
                    case "size" -> elements.size();
                    case "get" -> elements.get((Integer) args[0]);
                    case "iterator" -> elements.iterator();
                    case "getOctetBoundary" -> 4;
                    case "getEncodedLength" -> Integer.BYTES + encodedLength(elements);
                    case "encode", "toByteArray" -> throw new UnsupportedOperationException(method.getName());
                    case "toString" -> "HLAvariableArray" + elements;
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        @SuppressWarnings("unchecked")
        HLAvariableArray<DataElement> typedArray = (HLAvariableArray<DataElement>) variableArray;
        return typedArray;
    }

    @SuppressWarnings("unchecked")
    private static DataElementFactory<DataElement> dataElementFactory(Object value) {
        return (DataElementFactory<DataElement>) value;
    }

    private static void decodeElements(ByteWrapper byteWrapper, List<DataElement> elements) throws DecoderException {
        for (DataElement element : elements) {
            element.decode(byteWrapper);
        }
    }

    private static void decodeVariableElements(
            ByteWrapper byteWrapper,
            DataElementFactory<DataElement> factory,
            List<DataElement> elements) throws DecoderException {
        int size = byteWrapper.getInt();
        if (size < 0) {
            throw new DecoderException("Negative array size " + size);
        }
        elements.clear();
        for (int index = 0; index < size; index++) {
            DataElement element = factory.createElement(index);
            element.decode(byteWrapper);
            elements.add(element);
        }
    }

    private static void resizeElements(
            DataElementFactory<DataElement> factory,
            List<DataElement> elements,
            int newSize) {
        while (elements.size() > newSize) {
            elements.remove(elements.size() - 1);
        }
        while (elements.size() < newSize) {
            elements.add(factory.createElement(elements.size()));
        }
    }

    private static void decodeComplete(byte[] bytes, ByteWrapperDecoder decoder) throws DecoderException {
        ByteWrapper byteWrapper = new ByteWrapper(bytes);
        decoder.decode(byteWrapper);
        if (byteWrapper.remaining() != 0) {
            throw new DecoderException("Unexpected trailing bytes: " + byteWrapper.remaining());
        }
    }

    private static int encodedLength(List<DataElement> elements) {
        int length = 0;
        for (DataElement element : elements) {
            length += element.getEncodedLength();
        }
        return length;
    }

    private static Iterator<Byte> opaqueDataIterator(byte[] bytes) {
        Byte[] boxed = new Byte[bytes.length];
        for (int index = 0; index < bytes.length; index++) {
            boxed[index] = bytes[index];
        }
        return Arrays.asList(boxed).iterator();
    }

    private static Boolean decodeBoolean(byte[] bytes) throws DecoderException {
        requireLength(bytes, 1);
        return bytes[0] != 0;
    }

    private static Byte decodeByte(byte[] bytes) throws DecoderException {
        requireLength(bytes, 1);
        return bytes[0];
    }

    private static Short decodeShort(byte[] bytes, ByteOrder byteOrder) throws DecoderException {
        requireLength(bytes, Short.BYTES);
        return ByteBuffer.wrap(bytes).order(byteOrder).getShort();
    }

    private static Integer decodeInt(byte[] bytes, ByteOrder byteOrder) throws DecoderException {
        requireLength(bytes, Integer.BYTES);
        return ByteBuffer.wrap(bytes).order(byteOrder).getInt();
    }

    private static Long decodeLong(byte[] bytes, ByteOrder byteOrder) throws DecoderException {
        requireLength(bytes, Long.BYTES);
        return ByteBuffer.wrap(bytes).order(byteOrder).getLong();
    }

    private static Float decodeFloat(byte[] bytes, ByteOrder byteOrder) throws DecoderException {
        requireLength(bytes, Float.BYTES);
        return ByteBuffer.wrap(bytes).order(byteOrder).getFloat();
    }

    private static Double decodeDouble(byte[] bytes, ByteOrder byteOrder) throws DecoderException {
        requireLength(bytes, Double.BYTES);
        return ByteBuffer.wrap(bytes).order(byteOrder).getDouble();
    }

    private static byte[] decodeOpaqueData(byte[] bytes) throws DecoderException {
        int length = decodeLength(bytes);
        requireLength(bytes, Integer.BYTES + length);
        return Arrays.copyOfRange(bytes, Integer.BYTES, bytes.length);
    }

    private static String decodeAsciiString(byte[] bytes) throws DecoderException {
        return new String(decodeOpaqueData(bytes), StandardCharsets.US_ASCII);
    }

    private static String decodeUnicodeString(byte[] bytes) throws DecoderException {
        int length = decodeLength(bytes);
        requireLength(bytes, Integer.BYTES + (length * Character.BYTES));
        ByteBuffer buffer = ByteBuffer.wrap(bytes, Integer.BYTES, bytes.length - Integer.BYTES)
                .order(ByteOrder.BIG_ENDIAN);
        StringBuilder builder = new StringBuilder(length);
        for (int index = 0; index < length; index++) {
            builder.append(buffer.getChar());
        }
        return builder.toString();
    }

    private static int decodeLength(byte[] bytes) throws DecoderException {
        if (bytes.length < Integer.BYTES) {
            throw new DecoderException("Expected at least 4 bytes but got " + bytes.length);
        }
        int length = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN).getInt();
        if (length < 0) {
            throw new DecoderException("Negative length " + length);
        }
        return length;
    }

    private static void requireLength(byte[] bytes, int expectedLength) throws DecoderException {
        if (bytes.length != expectedLength) {
            throw new DecoderException("Expected " + expectedLength + " bytes but got " + bytes.length);
        }
    }

    private static byte[] int16(short value, ByteOrder byteOrder) {
        return ByteBuffer.allocate(Short.BYTES).order(byteOrder).putShort(value).array();
    }

    private static byte[] int32(int value, ByteOrder byteOrder) {
        return ByteBuffer.allocate(Integer.BYTES).order(byteOrder).putInt(value).array();
    }

    private static byte[] int64(long value, ByteOrder byteOrder) {
        return ByteBuffer.allocate(Long.BYTES).order(byteOrder).putLong(value).array();
    }

    private static byte[] float32(float value, ByteOrder byteOrder) {
        return ByteBuffer.allocate(Float.BYTES).order(byteOrder).putFloat(value).array();
    }

    private static byte[] float64(double value, ByteOrder byteOrder) {
        return ByteBuffer.allocate(Double.BYTES).order(byteOrder).putDouble(value).array();
    }

    private static byte[] asciiString(String value) {
        return variableBytes(value.getBytes(StandardCharsets.US_ASCII));
    }

    private static byte[] unicodeString(String value) {
        ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES + (value.length() * Character.BYTES))
                .order(ByteOrder.BIG_ENDIAN);
        buffer.putInt(value.length());
        for (int index = 0; index < value.length(); index++) {
            buffer.putChar(value.charAt(index));
        }
        return buffer.array();
    }

    private static byte[] variableBytes(byte[] value) {
        return ByteBuffer.allocate(Integer.BYTES + value.length)
                .order(ByteOrder.BIG_ENDIAN)
                .putInt(value.length)
                .put(value)
                .array();
    }

    private static byte[] fixedArray(byte[]... encodedElements) {
        int size = 0;
        for (byte[] encodedElement : encodedElements) {
            size += encodedElement.length;
        }
        ByteBuffer buffer = ByteBuffer.allocate(size);
        for (byte[] encodedElement : encodedElements) {
            buffer.put(encodedElement);
        }
        return buffer.array();
    }

    private static byte[] variableArray(byte[]... encodedElements) {
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

    private static ByteDecoder fixedLengthDecoder(int encodedLength, ByteArrayDecoder decoder) {
        return new ByteDecoder() {
            @Override
            public Object decode(byte[] bytes) throws DecoderException {
                return decoder.decode(bytes);
            }

            @Override
            public Object decode(ByteWrapper byteWrapper) throws DecoderException {
                byte[] bytes = new byte[encodedLength];
                byteWrapper.get(bytes);
                return decoder.decode(bytes);
            }

            @Override
            public int encodedLength() {
                return encodedLength;
            }
        };
    }

    private static ByteDecoder variableByteLengthDecoder(ByteArrayDecoder decoder) {
        return new ByteDecoder() {
            @Override
            public Object decode(byte[] bytes) throws DecoderException {
                return decoder.decode(bytes);
            }

            @Override
            public Object decode(ByteWrapper byteWrapper) throws DecoderException {
                int length = byteWrapper.getInt();
                byte[] payload = new byte[length];
                byteWrapper.get(payload);
                byte[] bytes = ByteBuffer.allocate(Integer.BYTES + length)
                        .order(ByteOrder.BIG_ENDIAN)
                        .putInt(length)
                        .put(payload)
                        .array();
                return decoder.decode(bytes);
            }

            @Override
            public int encodedLength() {
                return 0;
            }
        };
    }

    private static ByteDecoder unicodeStringDecoder() {
        return new ByteDecoder() {
            @Override
            public Object decode(byte[] bytes) throws DecoderException {
                return decodeUnicodeString(bytes);
            }

            @Override
            public Object decode(ByteWrapper byteWrapper) throws DecoderException {
                int length = byteWrapper.getInt();
                byte[] payload = new byte[length * Character.BYTES];
                byteWrapper.get(payload);
                byte[] bytes = ByteBuffer.allocate(Integer.BYTES + payload.length)
                        .order(ByteOrder.BIG_ENDIAN)
                        .putInt(length)
                        .put(payload)
                        .array();
                return decodeUnicodeString(bytes);
            }

            @Override
            public int encodedLength() {
                return 0;
            }
        };
    }

    @FunctionalInterface
    private interface ByteArrayDecoder {
        Object decode(byte[] bytes) throws DecoderException;
    }

    private interface ByteDecoder {
        Object decode(byte[] bytes) throws DecoderException;

        Object decode(ByteWrapper byteWrapper) throws DecoderException;

        int encodedLength();
    }

    @FunctionalInterface
    private interface ByteWrapperDecoder {
        void decode(ByteWrapper byteWrapper) throws DecoderException;
    }
}
