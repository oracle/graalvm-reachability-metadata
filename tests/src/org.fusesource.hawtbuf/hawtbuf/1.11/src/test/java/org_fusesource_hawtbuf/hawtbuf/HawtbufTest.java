/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_fusesource.hawtbuf.hawtbuf;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.fusesource.hawtbuf.AsciiBuffer;
import org.fusesource.hawtbuf.Buffer;
import org.fusesource.hawtbuf.DataByteArrayInputStream;
import org.fusesource.hawtbuf.DataByteArrayOutputStream;
import org.fusesource.hawtbuf.UTF8Buffer;
import org.junit.jupiter.api.Test;

class HawtbufTest {

    @Test
    void asciiBufferShouldExposeAsciiContentAndOrdering() {
        AsciiBuffer alpha = new AsciiBuffer("alpha");
        AsciiBuffer alphaCopy = new AsciiBuffer("alpha");
        AsciiBuffer beta = new AsciiBuffer("beta");

        assertThat(alpha.toString()).isEqualTo("alpha");
        assertThat(alpha.length()).isEqualTo(5);
        assertThat(alpha).isEqualTo(alphaCopy);
        assertThat(alpha.hashCode()).isEqualTo(alphaCopy.hashCode());
        assertThat(alpha.compareTo(beta)).isLessThan(0);
        assertThat(beta.compareTo(alpha)).isGreaterThan(0);
        assertThat(alpha.getData()).containsExactly("alpha".getBytes(StandardCharsets.US_ASCII));
    }

    @Test
    void utf8BufferShouldPreserveUnicodeContent() {
        String text = "héllo 世界";
        UTF8Buffer utf8 = new UTF8Buffer(text);

        assertThat(utf8.toString()).isEqualTo(text);
        assertThat(utf8.length()).isEqualTo(text.getBytes(StandardCharsets.UTF_8).length);
        assertThat(utf8.getData()).containsExactly(text.getBytes(StandardCharsets.UTF_8));
        assertThat(new UTF8Buffer(utf8.getData())).isEqualTo(utf8);
    }

    @Test
    void bufferShouldSupportSearchCopyAndJoinOperations() {
        Buffer buffer = new AsciiBuffer("hello world").buffer();

        assertThat(buffer.length()).isEqualTo(11);
        assertThat(buffer.indexOf((byte) 'o', 0)).isEqualTo(4);
        assertThat(buffer.indexOf(new AsciiBuffer("world").buffer(), 0)).isEqualTo(6);
        assertThat(buffer.contains((byte) 'w')).isTrue();

        Buffer deepCopy = buffer.deepCopy();
        assertThat(deepCopy).isEqualTo(buffer);
        assertThat(deepCopy).isNotSameAs(buffer);
        assertThat(deepCopy.ascii().toString()).isEqualTo("hello world");

        Buffer joined = Buffer.join(
                Arrays.asList(
                        new AsciiBuffer("hello").buffer(),
                        new AsciiBuffer("world").buffer()),
                new AsciiBuffer(" ").buffer());
        assertThat(joined.ascii().toString()).isEqualTo("hello world");
    }

    @Test
    void bufferShouldExposePrefixCompactAndByteAccess() {
        Buffer original = new AsciiBuffer("prefix-body").buffer();

        assertThat(original.startsWith(new AsciiBuffer("prefix").buffer())).isTrue();
        assertThat(original.utf8().toString()).isEqualTo("prefix-body");
        assertThat(original.get(0)).isEqualTo((byte) 'p');
        assertThat(original.get(6)).isEqualTo((byte) '-');
        assertThat(original.get(7)).isEqualTo((byte) 'b');

        Buffer compact = original.compact();
        assertThat(compact).isEqualTo(original);
        assertThat(compact.length()).isEqualTo(original.length());
        assertThat(compact.ascii().toString()).isEqualTo("prefix-body");
    }

    @Test
    void bufferShouldRoundTripFromByteArrayConstruction() {
        byte[] data = "sample-data".getBytes(StandardCharsets.US_ASCII);
        Buffer buffer = new Buffer(data);

        assertThat(buffer.length()).isEqualTo(data.length);
        assertThat(buffer.getData()).containsExactly(data);
        assertThat(buffer.ascii().toString()).isEqualTo("sample-data");
        assertThat(buffer.deepCopy()).isEqualTo(buffer);
    }

    @Test
    void bufferShouldCreateSlicesWithIndependentContentBoundaries() {
        Buffer original = new AsciiBuffer("header:payload:footer").buffer();

        Buffer payload = original.slice(7, 7);
        Buffer footer = original.slice(15, 6);

        assertThat(payload.ascii().toString()).isEqualTo("payload");
        assertThat(payload.length()).isEqualTo(7);
        assertThat(payload.startsWith(new AsciiBuffer("pay").buffer())).isTrue();
        assertThat(payload.indexOf((byte) 'o', 0)).isEqualTo(4);
        assertThat(payload.indexOf(new AsciiBuffer("load").buffer(), 0)).isEqualTo(3);

        assertThat(footer.ascii().toString()).isEqualTo("footer");
        assertThat(footer.length()).isEqualTo(6);
        assertThat(footer.get(0)).isEqualTo((byte) 'f');
        assertThat(footer.get(5)).isEqualTo((byte) 'r');
    }

    @Test
    void dataByteArrayStreamsShouldRoundTripPrimitiveValues() throws Exception {
        DataByteArrayOutputStream out = new DataByteArrayOutputStream();

        out.writeBoolean(true);
        out.writeByte(0x7f);
        out.writeShort(0x1234);
        out.writeChar('Z');
        out.writeInt(0x12345678);
        out.writeLong(0x0123456789ABCDEFL);
        out.writeFloat(123.5f);
        out.writeDouble(9876.25d);
        out.writeUTF("hawtbuf");

        byte[] bytes = out.toBuffer().getData();
        DataByteArrayInputStream in = new DataByteArrayInputStream(bytes);

        assertThat(in.readBoolean()).isTrue();
        assertThat(in.readByte()).isEqualTo((byte) 0x7f);
        assertThat(in.readShort()).isEqualTo((short) 0x1234);
        assertThat(in.readChar()).isEqualTo('Z');
        assertThat(in.readInt()).isEqualTo(0x12345678);
        assertThat(in.readLong()).isEqualTo(0x0123456789ABCDEFL);
        assertThat(in.readFloat()).isEqualTo(123.5f);
        assertThat(in.readDouble()).isEqualTo(9876.25d);
        assertThat(in.readUTF()).isEqualTo("hawtbuf");
    }

    @Test
    void dataByteArrayStreamsShouldRoundTripVariableLengthNumbers() throws Exception {
        DataByteArrayOutputStream out = new DataByteArrayOutputStream();

        out.writeVarInt(0);
        out.writeVarInt(127);
        out.writeVarInt(128);
        out.writeVarInt(16384);
        out.writeVarLong(0L);
        out.writeVarLong(127L);
        out.writeVarLong(128L);
        out.writeVarLong(9_876_543_210L);

        DataByteArrayInputStream in = new DataByteArrayInputStream(out.toBuffer());

        assertThat(in.readVarInt()).isEqualTo(0);
        assertThat(in.readVarInt()).isEqualTo(127);
        assertThat(in.readVarInt()).isEqualTo(128);
        assertThat(in.readVarInt()).isEqualTo(16384);
        assertThat(in.readVarLong()).isEqualTo(0L);
        assertThat(in.readVarLong()).isEqualTo(127L);
        assertThat(in.readVarLong()).isEqualTo(128L);
        assertThat(in.readVarLong()).isEqualTo(9_876_543_210L);
    }

    @Test
    void dataByteArrayOutputStreamShouldAccumulateBytesSequentially() throws Exception {
        DataByteArrayOutputStream out = new DataByteArrayOutputStream();

        out.write('A');
        out.write("BC".getBytes(StandardCharsets.US_ASCII));
        out.write("DEF".getBytes(StandardCharsets.US_ASCII), 0, 3);

        Buffer buffer = out.toBuffer();
        assertThat(buffer.ascii().toString()).isEqualTo("ABCDEF");

        DataByteArrayInputStream in = new DataByteArrayInputStream(buffer);
        byte[] read = new byte[6];
        in.readFully(read);

        assertThat(read).containsExactly("ABCDEF".getBytes(StandardCharsets.US_ASCII));
    }

    @Test
    void joinedBuffersShouldPreserveOrderAcrossMultipleSegments() {
        Buffer joined = Buffer.join(
                Arrays.asList(
                        new AsciiBuffer("aa").buffer(),
                        new AsciiBuffer("bb").buffer(),
                        new AsciiBuffer("cc").buffer()),
                new AsciiBuffer("-").buffer());

        assertThat(joined.ascii().toString()).isEqualTo("aa-bb-cc");
        assertThat(joined.indexOf((byte) '-', 0)).isEqualTo(2);
        assertThat(joined.indexOf((byte) '-', 3)).isEqualTo(5);
    }
}
