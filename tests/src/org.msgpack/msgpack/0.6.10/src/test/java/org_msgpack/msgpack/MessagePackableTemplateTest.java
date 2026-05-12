/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_msgpack.msgpack;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.msgpack.MessagePack;
import org.msgpack.MessagePackable;
import org.msgpack.packer.Packer;
import org.msgpack.unpacker.Unpacker;

public class MessagePackableTemplateTest {
    @Test
    void readCreatesMessagePackableTargetWithNoArgumentConstructor() throws Exception {
        SampleMessage.resetConstructorCount();
        final MessagePack messagePack = new MessagePack();
        final SampleMessage source = new SampleMessage(73, "created by template");
        final byte[] packed = messagePack.write(source);
        final int constructorCountBeforeRead = SampleMessage.constructorCount();

        final SampleMessage unpacked = messagePack.read(packed, SampleMessage.class);

        assertThat(unpacked).isNotSameAs(source);
        assertThat(unpacked.getNumber()).isEqualTo(73);
        assertThat(unpacked.getText()).isEqualTo("created by template");
        assertThat(SampleMessage.constructorCount()).isEqualTo(constructorCountBeforeRead + 1);
    }

    public static class SampleMessage implements MessagePackable {
        private static int constructorCount;

        private int number;
        private String text;

        public SampleMessage() {
            constructorCount++;
        }

        SampleMessage(int number, String text) {
            this();
            this.number = number;
            this.text = text;
        }

        static void resetConstructorCount() {
            constructorCount = 0;
        }

        static int constructorCount() {
            return constructorCount;
        }

        int getNumber() {
            return number;
        }

        String getText() {
            return text;
        }

        @Override
        public void writeTo(Packer pk) throws IOException {
            pk.writeArrayBegin(2);
            pk.write(number);
            pk.write(text);
            pk.writeArrayEnd();
        }

        @Override
        public void readFrom(Unpacker u) throws IOException {
            u.readArrayBegin();
            number = u.readInt();
            text = u.readString();
            u.readArrayEnd();
        }
    }
}
