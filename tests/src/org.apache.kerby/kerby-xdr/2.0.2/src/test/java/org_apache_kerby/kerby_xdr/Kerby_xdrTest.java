/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kerby.kerby_xdr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIOException;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;

import org.apache.kerby.xdr.EnumType;
import org.apache.kerby.xdr.XdrDataType;
import org.apache.kerby.xdr.XdrFieldInfo;
import org.apache.kerby.xdr.type.AbstractXdrType;
import org.apache.kerby.xdr.type.XdrBoolean;
import org.apache.kerby.xdr.type.XdrBytes;
import org.apache.kerby.xdr.type.XdrEnumerated;
import org.apache.kerby.xdr.type.XdrInteger;
import org.apache.kerby.xdr.type.XdrLong;
import org.apache.kerby.xdr.type.XdrSimple;
import org.apache.kerby.xdr.type.XdrString;
import org.apache.kerby.xdr.type.XdrStructType;
import org.apache.kerby.xdr.type.XdrType;
import org.apache.kerby.xdr.type.XdrUnion;
import org.apache.kerby.xdr.type.XdrUnsignedInteger;
import org.apache.kerby.xdr.util.HexUtil;
import org.apache.kerby.xdr.util.IOUtil;
import org.apache.kerby.xdr.util.Utf8;
import org.junit.jupiter.api.Test;

public class Kerby_xdrTest {
    @Test
    void integerBooleanUnsignedAndLongValuesRoundTripAsBigEndianXdrWords() throws Exception {
        XdrInteger negativeInteger = new XdrInteger(-12_345_678);
        assertThat(negativeInteger.getDataType()).isEqualTo(XdrDataType.INTEGER);
        assertThat(negativeInteger.encodingLength()).isEqualTo(Integer.BYTES);
        assertThat(negativeInteger.encode()).isEqualTo(ByteBuffer.allocate(Integer.BYTES).putInt(-12_345_678).array());

        XdrInteger decodedInteger = new XdrInteger();
        decodedInteger.decode(negativeInteger.encode());
        assertThat(decodedInteger.getValue()).isEqualTo(-12_345_678);

        assertThat(XdrBoolean.TRUE.getDataType()).isEqualTo(XdrDataType.BOOLEAN);
        assertThat(XdrBoolean.TRUE.encode()).isEqualTo(new byte[] {0, 0, 0, 1});
        assertThat(XdrBoolean.FALSE.encode()).isEqualTo(new byte[] {0, 0, 0, 0});

        XdrBoolean decodedFalse = new XdrBoolean();
        decodedFalse.decode(new byte[] {0, 0, 0, 0});
        assertThat(decodedFalse.getValue()).isFalse();

        XdrUnsignedInteger largestUnsigned = new XdrUnsignedInteger(4_294_967_295L);
        assertThat(largestUnsigned.getDataType()).isEqualTo(XdrDataType.UNSIGNED_INTEGER);
        assertThat(largestUnsigned.encode()).isEqualTo(new byte[] {(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF});

        XdrUnsignedInteger decodedUnsigned = new XdrUnsignedInteger();
        decodedUnsigned.decode(new byte[] {(byte) 0x80, 0, 0, 0});
        assertThat(decodedUnsigned.getValue()).isEqualTo(2_147_483_648L);

        assertThatIOException().isThrownBy(() -> new XdrUnsignedInteger(-1L).encode())
                .withMessageContaining("Invalid unsigned integer");

        XdrLong signedLong = new XdrLong(-9_223_372_036_854_775_000L);
        assertThat(signedLong.getDataType()).isEqualTo(XdrDataType.LONG);
        assertThat(signedLong.encodingLength()).isEqualTo(Long.BYTES);
        assertThat(signedLong.encode()).isEqualTo(ByteBuffer.allocate(Long.BYTES).putLong(-9_223_372_036_854_775_000L).array());

        XdrLong decodedLong = new XdrLong();
        decodedLong.decode(signedLong.encode());
        assertThat(decodedLong.getValue()).isEqualTo(-9_223_372_036_854_775_000L);
    }

    @Test
    void valuesCanBeEncodedSequentiallyIntoCallerProvidedByteBuffer() throws Exception {
        ByteBuffer message = ByteBuffer.allocate(3 * Integer.BYTES);

        new XdrInteger(0x01020304).encode(message);
        XdrBoolean.TRUE.encode(message);
        new XdrUnsignedInteger("65535").encode(message);

        assertThat(message.position()).isEqualTo(3 * Integer.BYTES);
        assertThat(message.array()).isEqualTo(new byte[] {
                1, 2, 3, 4,
                0, 0, 0, 1,
                0, 0, (byte) 0xFF, (byte) 0xFF
        });

        XdrInteger decodedInteger = new XdrInteger();
        decodedInteger.decode(ByteBuffer.wrap(Arrays.copyOfRange(message.array(), 0, Integer.BYTES)));
        assertThat(decodedInteger.getValue()).isEqualTo(0x01020304);
    }

    @Test
    void booleanRejectsNonCanonicalEncodings() {
        XdrBoolean decoded = new XdrBoolean();

        assertThatIOException().isThrownBy(() -> decoded.decode(new byte[] {0, 0, 0, 2}))
                .withMessageContaining("Fail to decode boolean type");
    }

    @Test
    void stringsEncodeLengthPrefixPaddingAndDecodeBackToValue() throws Exception {
        XdrString value = new XdrString("kerby");

        assertThat(value.getDataType()).isEqualTo(XdrDataType.STRING);
        assertThat(value.encodingLength()).isEqualTo(12);
        assertThat(value.getPadding()).isEqualTo(3);
        assertThat(value.encode()).isEqualTo(new byte[] {0, 0, 0, 5, 'k', 'e', 'r', 'b', 'y', 0, 0, 0});

        XdrString decoded = new XdrString();
        decoded.decode(value.encode());
        assertThat(decoded.getValue()).isEqualTo("kerby");
        assertThat(decoded.getPadding()).isEqualTo(3);

        XdrString fourByteAligned = new XdrString("data");
        assertThat(fourByteAligned.encode()).isEqualTo(new byte[] {0, 0, 0, 4, 'd', 'a', 't', 'a'});
        assertThat(fourByteAligned.getPadding()).isZero();
    }

    @Test
    void utf8AndAsciiStringHelpersConvertCaseSplitAndByteArrays() throws Exception {
        String multilingual = "Kerby \u03C0 \uD83D\uDE80";
        assertThat(Utf8.toString(Utf8.toBytes(multilingual))).isEqualTo(multilingual);
        assertThat(XdrString.fromUTF8ByteArray(XdrString.toUTF8ByteArray(multilingual))).isEqualTo(multilingual);

        assertThat(XdrString.toUpperCase("Abc-xyz-123")).isEqualTo("ABC-XYZ-123");
        assertThat(XdrString.toLowerCase("AbC-XYZ-123")).isEqualTo("abc-xyz-123");
        assertThat(XdrString.split("alpha:beta:gamma", ':')).containsExactly("alpha", "beta", "gamma");

        byte[] latinBytes = XdrString.toByteArray("Kerby");
        assertThat(latinBytes).isEqualTo(new byte[] {'K', 'e', 'r', 'b', 'y'});
        assertThat(XdrString.asCharArray(latinBytes)).containsExactly('K', 'e', 'r', 'b', 'y');
        assertThat(XdrString.fromByteArray(latinBytes)).isEqualTo("Kerby");
    }

    @Test
    void enumeratedTypesEncodeDecodeUsingEnumTypeContract() throws Exception {
        XdrEnumerated<MessageKind> encoded = new MessageKindXdr(MessageKind.REPLY);
        assertThat(encoded.getDataType()).isEqualTo(XdrDataType.ENUM);
        assertThat(encoded.encodingLength()).isEqualTo(Integer.BYTES);
        assertThat(encoded.encode()).isEqualTo(new byte[] {0, 0, 0, 2});

        MessageKindXdr decoded = new MessageKindXdr();
        decoded.decode(new byte[] {0, 0, 0, 1});
        assertThat(decoded.getValue()).isEqualTo(MessageKind.REQUEST);
        assertThat(decoded.getValue().getName()).isEqualTo("request");
    }

    @Test
    void structEncodesCompositeFieldsAndDecodesThemInOrder() throws Exception {
        PersonRecord record = new PersonRecord(42, "alice", true);

        byte[] encoded = record.encode();
        assertThat(record.getDataType()).isEqualTo(XdrDataType.STRUCT);
        assertThat(record.encodingLength()).isEqualTo(20);
        assertThat(encoded).isEqualTo(new byte[] {
                0, 0, 0, 42, 0, 0, 0, 5, 'a', 'l', 'i', 'c', 'e', 0, 0, 0, 0, 0, 0, 1
        });

        PersonRecord decoded = new PersonRecord();
        decoded.decode(encoded);
        PersonRecord decodedValue = (PersonRecord) decoded.getValue();
        assertThat(decodedValue.id).isEqualTo(42);
        assertThat(decodedValue.name).isEqualTo("alice");
        assertThat(decodedValue.active).isTrue();

        XdrFieldInfo[] fieldInfos = record.getXdrFieldInfos();
        assertThat(fieldInfos).hasSize(3);
        assertThat(fieldInfos[1].getIndex()).isEqualTo(1);
        assertThat(fieldInfos[1].getDataType()).isEqualTo(XdrDataType.STRING);
        assertThat(fieldInfos[1].getValue()).isEqualTo("alice");
    }

    @Test
    void unionSubclassCanEncodeAndDecodeDiscriminatedTextArm() throws Exception {
        TextChoice choice = new TextChoice(7, "selected");

        assertThat(choice.getDataType()).isEqualTo(XdrDataType.UNION);
        assertThat(choice.encode()).isEqualTo(new byte[] {
                0, 0, 0, 7, 0, 0, 0, 8, 's', 'e', 'l', 'e', 'c', 't', 'e', 'd'
        });

        TextChoice decoded = new TextChoice();
        decoded.decode(choice.encode());
        TextChoice decodedValue = (TextChoice) decoded.getValue();
        assertThat(decodedValue.discriminant).isEqualTo(7);
        assertThat(decodedValue.text).isEqualTo("selected");
    }

    @Test
    void bytesTypeExposesStoredValueAndSimpleTypeClassification() {
        byte[] payload = {1, 2, 3, 4};
        XdrBytes bytes = new XdrBytes(payload);

        assertThat(bytes.getDataType()).isEqualTo(XdrDataType.BYTES);
        assertThat(bytes.getValue()).isEqualTo(payload);
        assertThat(XdrSimple.isSimple(XdrDataType.BOOLEAN)).isTrue();
        assertThat(XdrSimple.isSimple(XdrDataType.INTEGER)).isTrue();
        assertThat(XdrSimple.isSimple(XdrDataType.STRING)).isTrue();
        assertThat(XdrSimple.isSimple(XdrDataType.ENUM)).isTrue();
        assertThat(XdrSimple.isSimple(XdrDataType.UNSIGNED_INTEGER)).isTrue();
        assertThat(XdrSimple.isSimple(XdrDataType.LONG)).isTrue();
        assertThat(XdrSimple.isSimple(XdrDataType.STRUCT)).isFalse();
        assertThat(XdrSimple.isSimple(XdrDataType.UNION)).isFalse();
    }

    @Test
    void dataTypesExposeTheirNumericIdentifiersAndDeclaredOrder() {
        assertThat(XdrDataType.values()).containsExactly(
                XdrDataType.UNKNOWN,
                XdrDataType.BOOLEAN,
                XdrDataType.INTEGER,
                XdrDataType.BYTES,
                XdrDataType.STRING,
                XdrDataType.ENUM,
                XdrDataType.OPAQUE,
                XdrDataType.UNSIGNED_INTEGER,
                XdrDataType.STRUCT,
                XdrDataType.UNION,
                XdrDataType.LONG);

        assertThat(XdrDataType.UNKNOWN.getValue()).isEqualTo(-1);
        assertThat(XdrDataType.BOOLEAN.getValue()).isEqualTo(1);
        assertThat(XdrDataType.INTEGER.getValue()).isEqualTo(2);
        assertThat(XdrDataType.BYTES.getValue()).isEqualTo(3);
        assertThat(XdrDataType.STRING.getValue()).isEqualTo(4);
        assertThat(XdrDataType.ENUM.getValue()).isEqualTo(5);
        assertThat(XdrDataType.OPAQUE.getValue()).isEqualTo(6);
        assertThat(XdrDataType.UNSIGNED_INTEGER.getValue()).isEqualTo(7);
        assertThat(XdrDataType.STRUCT.getValue()).isEqualTo(8);
        assertThat(XdrDataType.UNION.getValue()).isEqualTo(9);
        assertThat(XdrDataType.LONG.getValue()).isEqualTo(10);
    }

    @Test
    void hexUtilitiesRoundTripCompactAndFriendlyRepresentations() {
        byte[] bytes = {0, 15, 16, 31, 127, -128, -1};

        String compact = HexUtil.bytesToHex(bytes);
        assertThat(compact).isEqualTo("000F101F7F80FF");
        assertThat(HexUtil.hex2bytes(compact)).isEqualTo(bytes);
        assertThat(HexUtil.hex2bytes("000f101f7f80ff")).isEqualTo(bytes);

        String friendly = HexUtil.bytesToHexFriendly(bytes);
        assertThat(friendly).isEqualTo("0x00 0F 10 1F 7F 80 FF ");
        assertThat(HexUtil.hex2bytesFriendly(friendly)).isEqualTo(bytes);
        assertThat(HexUtil.hex2bytesFriendly("00 0f 10 1f 7f 80 ff ")).isEqualTo(bytes);
    }

    @Test
    void ioUtilitiesReadStreamsAndFilesCompletely() throws Exception {
        byte[] source = "first line\nsecond line".getBytes(StandardCharsets.UTF_8);

        assertThat(IOUtil.readInputStream(new ByteArrayInputStream(source))).isEqualTo(source);
        byte[] destination = new byte[source.length];
        IOUtil.readInputStream(new ByteArrayInputStream(source), destination);
        assertThat(destination).isEqualTo(source);
        assertThat(IOUtil.readInput(new ByteArrayInputStream(source))).isEqualTo("first line\nsecond line");

        File file = Files.createTempFile("kerby-xdr", ".txt").toFile();
        file.deleteOnExit();
        IOUtil.writeFile("xdr file content", file);
        assertThat(IOUtil.readFile(file)).isEqualTo("xdr file content");
    }

    private enum MessageKind implements EnumType {
        REQUEST(1, "request"),
        REPLY(2, "reply");

        private final int value;
        private final String name;

        MessageKind(int value, String name) {
            this.value = value;
            this.name = name;
        }

        @Override
        public int getValue() {
            return value;
        }

        @Override
        public String getName() {
            return name;
        }
    }

    private static final class MessageKindXdr extends XdrEnumerated<MessageKind> {
        MessageKindXdr() {
            super();
        }

        MessageKindXdr(MessageKind value) {
            super(value);
        }

        @Override
        protected EnumType[] getAllEnumValues() {
            return MessageKind.values();
        }
    }

    private static final class PersonRecord extends XdrStructType {
        private int id;
        private String name;
        private boolean active;

        PersonRecord() {
            super(XdrDataType.STRUCT);
        }

        PersonRecord(int id, String name, boolean active) {
            super(XdrDataType.STRUCT, new XdrFieldInfo[] {
                    new XdrFieldInfo(0, XdrDataType.INTEGER, id),
                    new XdrFieldInfo(1, XdrDataType.STRING, name),
                    new XdrFieldInfo(2, XdrDataType.BOOLEAN, active)
            });
            this.id = id;
            this.name = name;
            this.active = active;
        }

        @Override
        protected void getStructTypeInstance(XdrType[] fields, XdrFieldInfo[] fieldInfos) {
            Arrays.stream(fieldInfos).forEach(fieldInfo -> fields[fieldInfo.getIndex()] = toXdrType(fieldInfo));
        }

        @Override
        protected PersonRecord fieldsToValues(AbstractXdrType[] fields) {
            return new PersonRecord(
                    (Integer) fields[0].getValue(),
                    (String) fields[1].getValue(),
                    (Boolean) fields[2].getValue());
        }

        @Override
        protected AbstractXdrType[] getAllFields() {
            return new AbstractXdrType[] {new XdrInteger(), new XdrString(), new XdrBoolean()};
        }
    }

    private static final class TextChoice extends XdrUnion {
        private int discriminant;
        private String text;

        TextChoice() {
            super(XdrDataType.UNION);
        }

        TextChoice(int discriminant, String text) {
            super(XdrDataType.UNION, new XdrFieldInfo[] {
                    new XdrFieldInfo(0, XdrDataType.INTEGER, discriminant),
                    new XdrFieldInfo(1, XdrDataType.STRING, text)
            });
            this.discriminant = discriminant;
            this.text = text;
        }

        @Override
        protected void getUnionInstance(XdrType[] fields, XdrFieldInfo[] fieldInfos) {
            Arrays.stream(fieldInfos).forEach(fieldInfo -> fields[fieldInfo.getIndex()] = toXdrType(fieldInfo));
        }

        @Override
        protected TextChoice fieldsToValues(AbstractXdrType[] fields) {
            return new TextChoice((Integer) fields[0].getValue(), (String) fields[1].getValue());
        }

        @Override
        protected AbstractXdrType[] getAllFields() {
            return new AbstractXdrType[] {new XdrInteger(), new XdrString()};
        }
    }

    private static XdrType toXdrType(XdrFieldInfo fieldInfo) {
        if (fieldInfo.getDataType() == XdrDataType.INTEGER) {
            return new XdrInteger((Integer) fieldInfo.getValue());
        }
        if (fieldInfo.getDataType() == XdrDataType.STRING) {
            return new XdrString((String) fieldInfo.getValue());
        }
        if (fieldInfo.getDataType() == XdrDataType.BOOLEAN) {
            return new XdrBoolean((Boolean) fieldInfo.getValue());
        }
        throw new IllegalArgumentException("Unsupported field type: " + fieldInfo.getDataType());
    }
}
