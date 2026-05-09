/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_directory_api.api_asn1_ber;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.apache.directory.api.asn1.DecoderException;
import org.apache.directory.api.asn1.EncoderException;
import org.apache.directory.api.asn1.actions.AbstractReadBitString;
import org.apache.directory.api.asn1.actions.AbstractReadInteger;
import org.apache.directory.api.asn1.actions.AbstractReadOctetString;
import org.apache.directory.api.asn1.actions.CheckNotNullLength;
import org.apache.directory.api.asn1.ber.AbstractContainer;
import org.apache.directory.api.asn1.ber.Asn1Decoder;
import org.apache.directory.api.asn1.ber.grammar.AbstractGrammar;
import org.apache.directory.api.asn1.ber.grammar.Action;
import org.apache.directory.api.asn1.ber.grammar.GrammarTransition;
import org.apache.directory.api.asn1.ber.grammar.States;
import org.apache.directory.api.asn1.ber.tlv.BerValue;
import org.apache.directory.api.asn1.ber.tlv.BooleanDecoder;
import org.apache.directory.api.asn1.ber.tlv.BooleanDecoderException;
import org.apache.directory.api.asn1.ber.tlv.IntegerDecoder;
import org.apache.directory.api.asn1.ber.tlv.IntegerDecoderException;
import org.apache.directory.api.asn1.ber.tlv.LongDecoder;
import org.apache.directory.api.asn1.ber.tlv.LongDecoderException;
import org.apache.directory.api.asn1.ber.tlv.TLV;
import org.apache.directory.api.asn1.ber.tlv.TLVStateEnum;
import org.apache.directory.api.asn1.ber.tlv.UniversalTag;
import org.junit.jupiter.api.Test;

public class Api_asn1_berTest {
    @Test
    void encodesPrimitiveBerValuesIntoByteBuffers() throws Exception {
        ByteBuffer buffer = ByteBuffer.allocate(128);

        BerValue.encode(buffer, true);
        BerValue.encode(buffer, false);
        BerValue.encode(buffer, 127);
        BerValue.encode(buffer, -128);
        BerValue.encodeEnumerated(buffer, 3);
        BerValue.encode(buffer, "h\u00E9llo");
        BerValue.encode(buffer, new byte[] {0x01, 0x02, 0x03});

        buffer.flip();
        byte[] encoded = new byte[buffer.remaining()];
        buffer.get(encoded);

        assertThat(encoded).containsExactly(bytes(
                0x01, 0x01, 0xFF,
                0x01, 0x01, 0x00,
                0x02, 0x01, 0x7F,
                0x02, 0x01, 0x80,
                0x0A, 0x01, 0x03,
                0x04, 0x06, 0x68, 0xC3, 0xA9, 0x6C, 0x6C, 0x6F,
                0x04, 0x03, 0x01, 0x02, 0x03));
    }

    @Test
    void rejectsNullOrTooSmallEncodingBuffers() {
        assertThatExceptionOfType(EncoderException.class)
                .isThrownBy(() -> BerValue.encode(null, 1));
        assertThatExceptionOfType(EncoderException.class)
                .isThrownBy(() -> BerValue.encode(ByteBuffer.allocate(2), true));
    }

    @Test
    void computesMinimalIntegerAndLongValueEncodings() {
        assertThat(BerValue.getNbBytes(127)).isEqualTo(1);
        assertThat(BerValue.getNbBytes(128)).isEqualTo(2);
        assertThat(BerValue.getNbBytes(-128)).isEqualTo(1);
        assertThat(BerValue.getNbBytes(-129)).isEqualTo(2);
        assertThat(BerValue.getBytes(128)).containsExactly(bytes(0x00, 0x80));
        assertThat(BerValue.getBytes(-129)).containsExactly(bytes(0xFF, 0x7F));
        assertThat(BerValue.getBytes(Integer.MIN_VALUE)).containsExactly(bytes(0x80, 0x00, 0x00, 0x00));

        assertThat(BerValue.getNbBytes(140_737_488_355_328L)).isEqualTo(7);
        assertThat(BerValue.getNbBytes(Long.MIN_VALUE)).isEqualTo(8);
        assertThat(BerValue.getBytes(140_737_488_355_328L))
                .containsExactly(bytes(0x00, 0x80, 0x00, 0x00, 0x00, 0x00, 0x00));
        assertThat(BerValue.getBytes(-140_737_488_355_329L))
                .containsExactly(bytes(0xFF, 0x7F, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF));
    }

    @Test
    void appendsAndResetsMutableBerValueData() {
        BerValue value = new BerValue();

        value.init(5);
        value.setData(ByteBuffer.wrap(new byte[] {1, 2}));
        value.addData(ByteBuffer.wrap(new byte[] {3, 4, 5}));

        assertThat(value.getCurrentLength()).isEqualTo(5);
        assertThat(value.getData()).containsExactly(bytes(1, 2, 3, 4, 5));
        assertThat(value.toString()).contains("DATA");

        value.reset();

        assertThat(value.getData()).isNull();
        assertThat(value.getCurrentLength()).isZero();
        assertThat(value.toString()).isEqualTo("[]");
    }

    @Test
    void decodesBooleansAndRejectsMalformedBooleanValues() throws Exception {
        assertThat(BooleanDecoder.parse(new BerValue(new byte[] {0}))).isFalse();
        assertThat(BooleanDecoder.parse(new BerValue(new byte[] {(byte) 0xFF}))).isTrue();
        assertThat(BooleanDecoder.parse(new BerValue(new byte[] {0x7F}))).isTrue();

        assertThatExceptionOfType(BooleanDecoderException.class)
                .isThrownBy(() -> BooleanDecoder.parse(new BerValue(new byte[0])));
        assertThatExceptionOfType(BooleanDecoderException.class)
                .isThrownBy(() -> BooleanDecoder.parse(new BerValue(new byte[] {0, 1})));
    }

    @Test
    void decodesIntegersWithBoundsAndRejectsMalformedIntegers() throws Exception {
        assertThat(IntegerDecoder.parse(new BerValue(new byte[] {0x00}))).isZero();
        assertThat(IntegerDecoder.parse(new BerValue(new byte[] {0x7F}))).isEqualTo(127);
        assertThat(IntegerDecoder.parse(new BerValue(new byte[] {0x00, (byte) 0x80}))).isEqualTo(128);
        assertThat(IntegerDecoder.parse(new BerValue(new byte[] {(byte) 0x80}))).isEqualTo(-128);
        assertThat(IntegerDecoder.parse(new BerValue(new byte[] {(byte) 0xFF, 0x7F}))).isEqualTo(-129);
        assertThat(IntegerDecoder.parse(new BerValue(new byte[] {0x01}), 0, 10)).isEqualTo(1);

        assertThatExceptionOfType(IntegerDecoderException.class)
                .isThrownBy(() -> IntegerDecoder.parse(new BerValue(new byte[0])));
        assertThatExceptionOfType(IntegerDecoderException.class)
                .isThrownBy(() -> IntegerDecoder.parse(new BerValue(new byte[] {0x01}), 2, 10));
        assertThatExceptionOfType(IntegerDecoderException.class)
                .isThrownBy(() -> IntegerDecoder.parse(new BerValue(new byte[] {1, 2, 3, 4, 5, 6})));
    }

    @Test
    void decodesLongsWithBoundsAndRejectsMalformedLongs() throws Exception {
        assertThat(LongDecoder.parse(new BerValue(new byte[] {0x7F}))).isEqualTo(127L);
        assertThat(LongDecoder.parse(new BerValue(new byte[] {0x00, (byte) 0x80}))).isEqualTo(128L);
        assertThat(LongDecoder.parse(new BerValue(new byte[] {(byte) 0x80}))).isEqualTo(-128L);
        assertThat(LongDecoder.parseLong(new BerValue(new byte[] {(byte) 0xFF, 0x7F}))).isEqualTo(-129L);
        assertThat(LongDecoder.parse(new BerValue(new byte[] {0x05}), 0L, 10L)).isEqualTo(5L);

        assertThatExceptionOfType(LongDecoderException.class)
                .isThrownBy(() -> LongDecoder.parse(new BerValue(new byte[0])));
        assertThatExceptionOfType(LongDecoderException.class)
                .isThrownBy(() -> LongDecoder.parse(new BerValue(new byte[] {0x05}), 6L, 10L));
        assertThatExceptionOfType(LongDecoderException.class)
                .isThrownBy(() -> LongDecoder.parse(new BerValue(new byte[] {1, 2, 3, 4, 5, 6, 7, 8, 9})));
    }

    @Test
    void encodesLengthBytesAcrossShortAndLongForms() {
        assertThat(TLV.getNbBytes(0)).isEqualTo(1);
        assertThat(TLV.getBytes(0)).containsExactly(bytes(0x00));
        assertThat(TLV.getBytes(127)).containsExactly(bytes(0x7F));
        assertThat(TLV.getBytes(128)).containsExactly(bytes(0x81, 0x80));
        assertThat(TLV.getBytes(256)).containsExactly(bytes(0x82, 0x01, 0x00));
        assertThat(TLV.getBytes(65_536)).containsExactly(bytes(0x83, 0x01, 0x00, 0x00));
        assertThat(TLV.getBytes(16_777_216)).containsExactly(bytes(0x84, 0x01, 0x00, 0x00, 0x00));
        assertThat(TLV.getNbBytes(-1)).isEqualTo(5);
        assertThat(TLV.getBytes(-1)).containsExactly(bytes(0x84, 0xFF, 0xFF, 0xFF, 0xFF));
    }

    @Test
    void exposesTlvStateAndTagHelpers() {
        TLV parent = new TLV(1);
        TLV child = new TLV(2);

        child.setTag(UniversalTag.SEQUENCE.getValue());
        child.setLength(3);
        child.setLengthNbBytes(1);
        child.setLengthBytesRead(1);
        child.setExpectedLength(3);
        child.setParent(parent);
        child.getValue().init(3);
        child.getValue().setData(new byte[] {1, 2, 3});

        assertThat(TLV.isConstructed(UniversalTag.SEQUENCE.getValue())).isTrue();
        assertThat(TLV.isPrimitive(UniversalTag.INTEGER.getValue())).isTrue();
        assertThat(TLV.isUniversal(UniversalTag.INTEGER.getValue())).isTrue();
        assertThat(TLV.isUniversal((byte) 0x80)).isFalse();
        assertThat(child.isConstructed()).isTrue();
        assertThat(child.getSize()).isEqualTo(5);
        assertThat(child.getLengthBytesRead()).isEqualTo(1);
        assertThat(child.getExpectedLength()).isEqualTo(3);
        assertThat(child.getParent()).isSameAs(parent);
        assertThat(child.toString()).contains("TLV", "0x30", "DATA");

        child.incLengthBytesRead();
        assertThat(child.getLengthBytesRead()).isEqualTo(2);

        child.reset();
        assertThat(child.getTag()).isEqualTo((byte) 0);
        assertThat(child.getLength()).isZero();
        assertThat(child.getLengthNbBytes()).isZero();
        assertThat(child.getExpectedLength()).isZero();
        assertThat(child.getValue().getData()).isNull();
    }

    @Test
    void decodesPrimitiveTlvWithCustomGrammar() throws Exception {
        RecordingContainer container = new RecordingContainer(new PrimitiveGrammar());
        Asn1Decoder decoder = new Asn1Decoder();

        decoder.decode(ByteBuffer.wrap(new byte[] {0x02, 0x01, 0x2A}), container);

        assertThat(container.getState()).isEqualTo(TLVStateEnum.PDU_DECODED);
        assertThat(container.getTransition()).isEqualTo(TestState.END);
        assertThat(container.events).containsExactly("integer=42");
        assertThat(container.getCurrentTLV().getTag()).isEqualTo(UniversalTag.INTEGER.getValue());
        assertThat(container.getDecodedBytes()).isEqualTo(3);
    }

    @Test
    void decodesConstructedSequenceAndPendingValueAcrossBuffers() throws Exception {
        RecordingContainer container = new RecordingContainer(new SequenceGrammar());
        Asn1Decoder decoder = new Asn1Decoder();

        decoder.decode(ByteBuffer.wrap(new byte[] {0x30, 0x08, 0x01, 0x01, (byte) 0xFF, 0x04, 0x03, 0x61}), container);

        assertThat(container.getState()).isEqualTo(TLVStateEnum.VALUE_STATE_PENDING);
        assertThat(container.events).containsExactly("sequence", "boolean=true");
        assertThat(container.getCurrentTLV().getValue().getCurrentLength()).isEqualTo(1);

        decoder.decode(ByteBuffer.wrap(new byte[] {0x62, 0x63}), container);

        assertThat(container.getState()).isEqualTo(TLVStateEnum.PDU_DECODED);
        assertThat(container.events).containsExactly("sequence", "boolean=true", "octets=abc");
        assertThat(container.getDecodedBytes()).isEqualTo(10);
    }

    @Test
    void gathersConstructedValueAsRawPayloadWhenRequested() throws Exception {
        RecordingContainer container = new RecordingContainer(new GatheredSequenceGrammar());
        container.setGathering(true);
        Asn1Decoder decoder = new Asn1Decoder();

        decoder.decode(ByteBuffer.wrap(new byte[] {0x30, 0x06, 0x02, 0x01, 0x2A, 0x01, 0x01, (byte) 0xFF}),
                container);

        assertThat(container.getState()).isEqualTo(TLVStateEnum.PDU_DECODED);
        assertThat(container.getTransition()).isEqualTo(TestState.END);
        assertThat(container.rawPayload).containsExactly(bytes(0x02, 0x01, 0x2A, 0x01, 0x01, 0xFF));
        assertThat(container.events).containsExactly("gathered=6");
        assertThat(container.getCurrentTLV().getValue().getData())
                .containsExactly(bytes(0x02, 0x01, 0x2A, 0x01, 0x01, 0xFF));
        assertThat(container.getDecodedBytes()).isEqualTo(8);
    }

    @Test
    void reusableGrammarActionsReadTypedValuesFromDecodedTlv() throws Exception {
        RecordingContainer container = new RecordingContainer(new ActionHelperGrammar());
        Asn1Decoder decoder = new Asn1Decoder();

        decoder.decode(ByteBuffer.wrap(new byte[] {
            0x30, 0x0F,
            0x02, 0x01, 0x05,
            0x04, 0x03, 0x61, 0x62, 0x63,
            0x03, 0x05, 0x00, (byte) 0xF0, 0x0F, 0x55, (byte) 0xAA}), container);

        assertThat(container.getState()).isEqualTo(TLVStateEnum.PDU_DECODED);
        assertThat(container.getTransition()).isEqualTo(TestState.END);
        assertThat(container.integerValue).isEqualTo(5);
        assertThat(container.octetString).containsExactly(bytes(0x61, 0x62, 0x63));
        assertThat(container.bitString).containsExactly(bytes(0x00, 0xF0, 0x0F, 0x55, 0xAA));
        assertThat(container.getDecodedBytes()).isEqualTo(17);
    }

    @Test
    void reusableGrammarActionsRejectUnexpectedNullLengths() {
        Asn1Decoder decoder = new Asn1Decoder();
        RecordingContainer nullSequence = new RecordingContainer(new NonNullSequenceGrammar());
        RecordingContainer nullInteger = new RecordingContainer(new RequiredIntegerGrammar());

        assertThatExceptionOfType(DecoderException.class)
                .isThrownBy(() -> decoder.decode(ByteBuffer.wrap(new byte[] {0x30, 0x00}), nullSequence));
        assertThatExceptionOfType(DecoderException.class)
                .isThrownBy(() -> decoder.decode(ByteBuffer.wrap(new byte[] {0x02, 0x00}), nullInteger));
    }

    @Test
    void rejectsMalformedTlvStreamsBeforeActionsRun() throws Exception {
        Asn1Decoder decoder = new Asn1Decoder();

        RecordingContainer oversized = new RecordingContainer(new PrimitiveGrammar());
        oversized.setMaxPDUSize(2);
        assertThatExceptionOfType(DecoderException.class)
                .isThrownBy(() -> decoder.decode(ByteBuffer.wrap(new byte[] {0x02, 0x01, 0x01}), oversized));

        RecordingContainer invalidLength = new RecordingContainer(new PrimitiveGrammar());
        assertThatExceptionOfType(DecoderException.class)
                .isThrownBy(() -> decoder.decode(ByteBuffer.wrap(new byte[] {0x02, (byte) 0x85, 0, 0, 0, 0, 1}),
                        invalidLength));

        RecordingContainer truncated = new RecordingContainer(new PrimitiveGrammar());
        assertThatCode(() -> decoder.decode(ByteBuffer.wrap(new byte[] {0x02, 0x02, 0x01}), truncated))
                .doesNotThrowAnyException();
        assertThat(truncated.getState()).isEqualTo(TLVStateEnum.VALUE_STATE_PENDING);
    }

    @Test
    void configuresDecoderMBeanSettingsAndValidatesDefiniteLengthLimit() throws Exception {
        Asn1Decoder decoder = new Asn1Decoder();

        assertThat(decoder.isIndefiniteLengthAllowed()).isFalse();
        assertThat(decoder.getMaxLengthLength()).isEqualTo(1);
        assertThat(decoder.getMaxTagLength()).isEqualTo(1);

        decoder.allowIndefiniteLength();
        decoder.setMaxLengthLength(126);
        decoder.setMaxTagLength(4);

        assertThat(decoder.isIndefiniteLengthAllowed()).isTrue();
        assertThat(decoder.getMaxLengthLength()).isEqualTo(126);
        assertThat(decoder.getMaxTagLength()).isEqualTo(4);

        assertThatExceptionOfType(DecoderException.class)
                .isThrownBy(() -> decoder.setMaxLengthLength(127));

        decoder.disallowIndefiniteLength();
        decoder.setMaxLengthLength(127);

        assertThat(decoder.isIndefiniteLengthAllowed()).isFalse();
        assertThat(decoder.getMaxLengthLength()).isEqualTo(127);
    }

    private static byte[] bytes(int... values) {
        byte[] bytes = new byte[values.length];

        for (int i = 0; i < values.length; i++) {
            bytes[i] = (byte) values[i];
        }

        return bytes;
    }

    private enum TestState implements States {
        START,
        SEQUENCE,
        BOOLEAN,
        INTEGER,
        OCTETS,
        END;

        @Override
        public boolean isEndState() {
            return this == END;
        }

        @Override
        public Enum<?> getStartState() {
            return START;
        }
    }

    private static class RecordingContainer extends AbstractContainer {
        private final List<String> events = new ArrayList<>();
        private Integer integerValue;
        private byte[] octetString;
        private byte[] bitString;
        private byte[] rawPayload;

        RecordingContainer(AbstractGrammar<RecordingContainer> grammar) {
            setGrammar(grammar);
            setTransition(TestState.START);
        }
    }

    private static class PrimitiveGrammar extends AbstractGrammar<RecordingContainer> {
        PrimitiveGrammar() {
            setName("primitive-test-grammar");
            transitions = transitions();
            transitions[TestState.START.ordinal()][UniversalTag.INTEGER.getValue()] = new GrammarTransition<>(
                    TestState.START,
                    TestState.END,
                    UniversalTag.INTEGER,
                    integerAction(true));
        }
    }

    private static class SequenceGrammar extends AbstractGrammar<RecordingContainer> {
        SequenceGrammar() {
            setName("sequence-test-grammar");
            transitions = transitions();
            transitions[TestState.START.ordinal()][UniversalTag.SEQUENCE.getValue()] = new GrammarTransition<>(
                    TestState.START,
                    TestState.SEQUENCE,
                    UniversalTag.SEQUENCE,
                    container -> container.events.add("sequence"));
            transitions[TestState.SEQUENCE.ordinal()][UniversalTag.BOOLEAN.getValue()] = new GrammarTransition<>(
                    TestState.SEQUENCE,
                    TestState.BOOLEAN,
                    UniversalTag.BOOLEAN,
                    booleanAction());
            transitions[TestState.BOOLEAN.ordinal()][UniversalTag.OCTET_STRING.getValue()] = new GrammarTransition<>(
                    TestState.BOOLEAN,
                    TestState.END,
                    UniversalTag.OCTET_STRING,
                    octetsAction(true));
        }
    }

    private static class GatheredSequenceGrammar extends AbstractGrammar<RecordingContainer> {
        GatheredSequenceGrammar() {
            setName("gathered-sequence-test-grammar");
            transitions = transitions();
            transitions[TestState.START.ordinal()][UniversalTag.SEQUENCE.getValue()] = new GrammarTransition<>(
                    TestState.START,
                    TestState.END,
                    UniversalTag.SEQUENCE,
                    container -> {
                        container.rawPayload = container.getCurrentTLV().getValue().getData();
                        container.events.add("gathered=" + container.rawPayload.length);
                        container.setGrammarEndAllowed(true);
                    });
        }
    }

    private static class ActionHelperGrammar extends AbstractGrammar<RecordingContainer> {
        ActionHelperGrammar() {
            setName("action-helper-test-grammar");
            transitions = transitions();
            transitions[TestState.START.ordinal()][UniversalTag.SEQUENCE.getValue()] = new GrammarTransition<>(
                    TestState.START,
                    TestState.SEQUENCE,
                    UniversalTag.SEQUENCE,
                    new CheckNotNullLength<>());
            transitions[TestState.SEQUENCE.ordinal()][UniversalTag.INTEGER.getValue()] = new GrammarTransition<>(
                    TestState.SEQUENCE,
                    TestState.INTEGER,
                    UniversalTag.INTEGER,
                    new RecordingIntegerAction(1, 10));
            transitions[TestState.INTEGER.ordinal()][UniversalTag.OCTET_STRING.getValue()] = new GrammarTransition<>(
                    TestState.INTEGER,
                    TestState.OCTETS,
                    UniversalTag.OCTET_STRING,
                    new RecordingOctetStringAction(false));
            transitions[TestState.OCTETS.ordinal()][UniversalTag.BIT_STRING.getValue()] = new GrammarTransition<>(
                    TestState.OCTETS,
                    TestState.END,
                    UniversalTag.BIT_STRING,
                    new RecordingBitStringAction());
        }
    }

    private static class NonNullSequenceGrammar extends AbstractGrammar<RecordingContainer> {
        NonNullSequenceGrammar() {
            setName("non-null-sequence-test-grammar");
            transitions = transitions();
            transitions[TestState.START.ordinal()][UniversalTag.SEQUENCE.getValue()] = new GrammarTransition<>(
                    TestState.START,
                    TestState.END,
                    UniversalTag.SEQUENCE,
                    new CheckNotNullLength<>());
        }
    }

    private static class RequiredIntegerGrammar extends AbstractGrammar<RecordingContainer> {
        RequiredIntegerGrammar() {
            setName("required-integer-test-grammar");
            transitions = transitions();
            transitions[TestState.START.ordinal()][UniversalTag.INTEGER.getValue()] = new GrammarTransition<>(
                    TestState.START,
                    TestState.END,
                    UniversalTag.INTEGER,
                    new RecordingIntegerAction(1, 10));
        }
    }

    private static class RecordingIntegerAction extends AbstractReadInteger<RecordingContainer> {
        RecordingIntegerAction(int minValue, int maxValue) {
            super("read test integer", minValue, maxValue);
        }

        @Override
        protected void setIntegerValue(int value, RecordingContainer container) {
            container.integerValue = value;
        }
    }

    private static class RecordingOctetStringAction extends AbstractReadOctetString<RecordingContainer> {
        RecordingOctetStringAction(boolean canBeNull) {
            super("read test octet string", canBeNull);
        }

        @Override
        protected void setOctetString(byte[] value, RecordingContainer container) {
            container.octetString = value;
        }
    }

    private static class RecordingBitStringAction extends AbstractReadBitString<RecordingContainer> {
        RecordingBitStringAction() {
            super("read test bit string");
        }

        @Override
        protected void setBitString(byte[] value, RecordingContainer container) {
            container.bitString = value;
            container.setGrammarEndAllowed(true);
        }
    }

    @SuppressWarnings("unchecked")
    private static GrammarTransition<RecordingContainer>[][] transitions() {
        return new GrammarTransition[TestState.values().length][256];
    }

    private static Action<RecordingContainer> integerAction(boolean grammarEnds) {
        return container -> {
            try {
                int decoded = IntegerDecoder.parse(container.getCurrentTLV().getValue());
                container.events.add("integer=" + decoded);
                container.setGrammarEndAllowed(grammarEnds);
            } catch (IntegerDecoderException exception) {
                throw new DecoderException("Unable to decode test integer", exception);
            }
        };
    }

    private static Action<RecordingContainer> booleanAction() {
        return container -> {
            try {
                boolean decoded = BooleanDecoder.parse(container.getCurrentTLV().getValue());
                container.events.add("boolean=" + decoded);
            } catch (BooleanDecoderException exception) {
                throw new DecoderException("Unable to decode test boolean", exception);
            }
        };
    }

    private static Action<RecordingContainer> octetsAction(boolean grammarEnds) {
        return container -> {
            String decoded = new String(container.getCurrentTLV().getValue().getData(), StandardCharsets.UTF_8);
            container.events.add("octets=" + decoded);
            container.setGrammarEndAllowed(grammarEnds);
        };
    }
}
