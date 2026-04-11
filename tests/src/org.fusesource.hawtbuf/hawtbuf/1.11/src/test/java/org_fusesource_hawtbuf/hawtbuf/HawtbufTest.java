/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_fusesource_hawtbuf.hawtbuf;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import org.fusesource.hawtbuf.AsciiBuffer;
import org.fusesource.hawtbuf.Buffer;
import org.fusesource.hawtbuf.BufferEditor;
import org.fusesource.hawtbuf.BufferInputStream;
import org.fusesource.hawtbuf.BufferMarshaller;
import org.fusesource.hawtbuf.BufferOutputStream;
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
    void bufferShouldSupportSlicingSearchAndCopyOperations() {
        Buffer buffer = new AsciiBuffer("hello world").buffer();

        assertThat(buffer.length()).isEqualTo(11);
        assertThat(buffer.indexOf((byte) 'o', 0)).isEqualTo(4);
        assertThat(buffer.indexOf(new AsciiBuffer("world").buffer(), 0)).isEqualTo(6);
        assertThat(buffer.contains((byte) 'w')).isTrue();
        assertThat(buffer.contains(new AsciiBuffer("hello").buffer())).isTrue();

        Buffer slice = buffer.slice(6, 5);
        assertThat(slice.ascii().toString()).isEqualTo("world");

        Buffer deepCopy = buffer.deepCopy();
        assertThat(deepCopy).isEqualTo(buffer);
        assertThat(deepCopy).isNotSameAs(buffer);

        Buffer joined = new AsciiBuffer("hello").buffer().join((byte) ' ', new AsciiBuffer("world").buffer());
        assertThat(joined.ascii().toString()).isEqualTo("hello world");
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
    void bufferStreamsShouldWriteAndReadByteSequences() throws Exception {
        BufferOutputStream out = new BufferOutputStream(2);
        out.write('A');
        out.write("BC".getBytes(StandardCharsets.US_ASCII));
        out.write("DEF".getBytes(StandardCharsets.US_ASCII), 0, 3);

        Buffer buffer = out.toBuffer();
        assertThat(buffer.ascii().toString()).isEqualTo("ABCDEF");

        BufferInputStream in = new BufferInputStream(buffer);
        byte[] read = new byte[6];
        int count = in.read(read);

        assertThat(count).isEqualTo(6);
        assertThat(read).containsExactly("ABCDEF".getBytes(StandardCharsets.US_ASCII));
        assertThat(in.read()).isEqualTo(-1);
    }

    @Test
    void bufferEditorShouldWritePrimitiveValuesIntoBuffer() {
        Buffer buffer = new Buffer(32);
        BufferEditor editor = buffer.bigEndianEditor();

        editor.writeInt(0x01020304);
        editor.writeLong(0x0102030405060708L);
        editor.writeBoolean(true);
        editor.writeByte((byte) 0x55);

        BufferEditor reader = buffer.bigEndianEditor();
        assertThat(reader.readInt()).isEqualTo(0x01020304);
        assertThat(reader.readLong()).isEqualTo(0x0102030405060708L);
        assertThat(reader.readBoolean()).isTrue();
        assertThat(reader.readByte()).isEqualTo((byte) 0x55);
    }

    @Test
    void bufferMarshallerShouldMarshalAndUnmarshalBuffers() throws Exception {
        Buffer source = new UTF8Buffer("marshal-data").buffer();
        BufferMarshaller marshaller = new BufferMarshaller();

        ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
        marshaller.writePayload(source, byteOut);

        Buffer restored = marshaller.readPayload(new ByteArrayInputStream(byteOut.toByteArray()));
        assertThat(restored.utf8().toString()).isEqualTo("marshal-data");
        assertThat(restored).isEqualTo(source);
    }
}
