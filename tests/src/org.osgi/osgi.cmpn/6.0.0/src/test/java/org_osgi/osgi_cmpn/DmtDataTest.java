/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_osgi.osgi_cmpn;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Date;
import org.junit.jupiter.api.Test;
import org.osgi.service.dmt.DmtData;
import org.osgi.service.dmt.DmtIllegalStateException;

public class DmtDataTest {
    @Test
    void scalarConstructorsExposeTypedValuesAndFormats() {
        DmtData integerData = new DmtData(42);
        DmtData floatData = new DmtData(3.5f);
        DmtData longData = new DmtData(123456789L);
        DmtData trueData = new DmtData(true);
        DmtData falseData = new DmtData(false);

        assertThat(integerData.getFormat()).isEqualTo(DmtData.FORMAT_INTEGER);
        assertThat(integerData.getFormatName()).isEqualTo("integer");
        assertThat(integerData.getInt()).isEqualTo(42);
        assertThat(integerData.getSize()).isEqualTo(4);
        assertThat(integerData.toString()).isEqualTo("42");

        assertThat(floatData.getFormat()).isEqualTo(DmtData.FORMAT_FLOAT);
        assertThat(floatData.getFormatName()).isEqualTo("float");
        assertThat(floatData.getFloat()).isEqualTo(3.5f);
        assertThat(floatData.getSize()).isEqualTo(4);

        assertThat(longData.getFormat()).isEqualTo(DmtData.FORMAT_LONG);
        assertThat(longData.getFormatName()).isEqualTo("long");
        assertThat(longData.getLong()).isEqualTo(123456789L);
        assertThat(longData.getSize()).isEqualTo(8);

        assertThat(trueData.getFormat()).isEqualTo(DmtData.FORMAT_BOOLEAN);
        assertThat(trueData.getFormatName()).isEqualTo("boolean");
        assertThat(trueData.getBoolean()).isTrue();
        assertThat(falseData.getBoolean()).isFalse();
        assertThat(trueData.getSize()).isEqualTo(1);
    }

    @Test
    void stringConstructorsValidateSpecificFormats() {
        DmtData stringData = new DmtData("plain text");
        DmtData xmlData = new DmtData("<root/>", DmtData.FORMAT_XML);
        DmtData dateData = new DmtData("20240229", DmtData.FORMAT_DATE);
        DmtData timeData = new DmtData("235959Z", DmtData.FORMAT_TIME);

        assertThat(stringData.getFormat()).isEqualTo(DmtData.FORMAT_STRING);
        assertThat(stringData.getFormatName()).isEqualTo("string");
        assertThat(stringData.getString()).isEqualTo("plain text");
        assertThat(stringData.getSize()).isEqualTo("plain text".length());

        assertThat(xmlData.getFormat()).isEqualTo(DmtData.FORMAT_XML);
        assertThat(xmlData.getXml()).isEqualTo("<root/>");

        assertThat(dateData.getFormat()).isEqualTo(DmtData.FORMAT_DATE);
        assertThat(dateData.getDate()).isEqualTo("20240229");

        assertThat(timeData.getFormat()).isEqualTo(DmtData.FORMAT_TIME);
        assertThat(timeData.getTime()).isEqualTo("235959Z");
    }

    @Test
    void binaryConstructorsReturnCopiesAndRenderHexValues() {
        byte[] bytes = new byte[] {0x00, 0x0A, (byte) 0xFF};
        DmtData binaryData = new DmtData(bytes);
        DmtData base64Data = new DmtData(bytes, true);
        DmtData rawBinaryData = new DmtData("application/octet-stream", bytes);

        byte[] binaryCopy = binaryData.getBinary();
        binaryCopy[0] = 0x7F;

        assertThat(binaryData.getFormat()).isEqualTo(DmtData.FORMAT_BINARY);
        assertThat(binaryData.getFormatName()).isEqualTo("binary");
        assertThat(binaryCopy).isNotSameAs(bytes);
        assertThat(binaryData.getBinary()).containsExactly((byte) 0x00, (byte) 0x0A, (byte) 0xFF);
        assertThat(binaryData.getSize()).isEqualTo(3);
        assertThat(binaryData.toString()).isEqualTo("00 0A FF");

        assertThat(base64Data.getFormat()).isEqualTo(DmtData.FORMAT_BASE64);
        assertThat(base64Data.getBase64()).containsExactly((byte) 0x00, (byte) 0x0A, (byte) 0xFF);
        assertThat(base64Data.getFormatName()).isEqualTo("base64");

        assertThat(rawBinaryData.getFormat()).isEqualTo(DmtData.FORMAT_RAW_BINARY);
        assertThat(rawBinaryData.getRawBinary())
                .containsExactly((byte) 0x00, (byte) 0x0A, (byte) 0xFF);
        assertThat(rawBinaryData.getFormatName()).isEqualTo("application/octet-stream");
    }

    @Test
    void nodeRawStringDateTimeAndNullValuesExposeTheirSpecializedState() {
        Object node = new Object();
        Date date = new Date(0L);
        DmtData nodeData = new DmtData(node);
        DmtData rawStringData = new DmtData("text/plain", "payload");
        DmtData dateTimeData = new DmtData(date);
        DmtData nullData = DmtData.NULL_VALUE;

        assertThat(nodeData.getFormat()).isEqualTo(DmtData.FORMAT_NODE);
        assertThat(nodeData.getFormatName()).isEqualTo("NODE");
        assertThat(nodeData.getNode()).isSameAs(node);
        assertThat(nodeData.getSize()).isEqualTo(-1);

        assertThat(rawStringData.getFormat()).isEqualTo(DmtData.FORMAT_RAW_STRING);
        assertThat(rawStringData.getFormatName()).isEqualTo("text/plain");
        assertThat(rawStringData.getRawString()).isEqualTo("payload");

        assertThat(dateTimeData.getFormat()).isEqualTo(DmtData.FORMAT_DATE_TIME);
        assertThat(dateTimeData.getFormatName()).isEqualTo("dateTime");
        assertThat(dateTimeData.getDateTime()).isSameAs(date);
        assertThat(dateTimeData.getSize()).isEqualTo(8);

        assertThat(nullData.getFormat()).isEqualTo(DmtData.FORMAT_NULL);
        assertThat(nullData.getFormatName()).isEqualTo("null");
        assertThat(nullData.toString()).isEqualTo("null");
        assertThat(nullData.getSize()).isZero();
    }

    @Test
    void equalityUsesValueFormatAndBinaryContents() {
        assertThat(new DmtData(42)).isEqualTo(new DmtData(42));
        assertThat(new DmtData(42)).isNotEqualTo(new DmtData(43));
        assertThat(new DmtData(42)).isNotEqualTo(new DmtData(42L));
        assertThat(new DmtData(new byte[] {1, 2, 3}))
                .isEqualTo(new DmtData(new byte[] {1, 2, 3}));
        assertThat(new DmtData(new byte[] {1, 2, 3}))
                .isNotEqualTo(new DmtData(new byte[] {1, 2, 4}));
        assertThat(new DmtData("text/plain", "payload"))
                .isEqualTo(new DmtData("text/plain", "payload"));
        assertThat(new DmtData("text/plain", "payload"))
                .isNotEqualTo(new DmtData("application/json", "payload"));
    }

    @Test
    void typedGettersRejectMismatchedFormats() {
        DmtData data = new DmtData("plain text");

        assertThatThrownBy(data::getInt)
                .isInstanceOf(DmtIllegalStateException.class)
                .hasMessage("DmtData value is not integer.");
        assertThatThrownBy(data::getBinary)
                .isInstanceOf(DmtIllegalStateException.class)
                .hasMessage("DmtData value is not a byte array.");
        assertThatThrownBy(data::getNode)
                .isInstanceOf(DmtIllegalStateException.class)
                .hasMessage("DmtData does not contain interior node data.");
    }

    @Test
    void invalidInputIsRejectedByConstructors() {
        assertThatThrownBy(() -> new DmtData("not-a-date", DmtData.FORMAT_DATE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not follow the format");
        assertThatThrownBy(() -> new DmtData("240001", DmtData.FORMAT_TIME))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Time string is out of range.");
        assertThatThrownBy(() -> new DmtData("value", DmtData.FORMAT_INTEGER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid type type for DmtData");
        assertThatThrownBy(() -> new DmtData(new byte[] {1}, DmtData.FORMAT_RAW_BINARY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("only FORMAT_BINARY and FORMAT_BASE64 are allowed");
        assertThatThrownBy(() -> new DmtData((byte[]) null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("The bytes argument is null.");
    }
}
