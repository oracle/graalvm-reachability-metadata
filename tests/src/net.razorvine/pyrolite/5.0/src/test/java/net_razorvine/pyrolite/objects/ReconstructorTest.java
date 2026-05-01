/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_razorvine.pyrolite.objects;

import java.util.SortedMap;
import java.util.TreeMap;

import net.razorvine.pyro.Message;
import net.razorvine.pyro.PyroException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ReconstructorTest {
    @Test
    void messageHeaderPreservesProtocolFieldsAndAnnotations() {
        SortedMap<String, byte[]> annotations = new TreeMap<>();
        annotations.put("TEST", new byte[] {1, 2, 3});
        Message message = new Message(
                Message.MSG_RESULT,
                new byte[] {4, 5, 6},
                Message.SERIALIZER_SERPENT,
                Message.FLAGS_EXCEPTION,
                42,
                annotations,
                null);

        Message parsed = Message.from_header(message.get_header_bytes());

        assertThat(parsed.type).isEqualTo(Message.MSG_RESULT);
        assertThat(parsed.serializer_id).isEqualTo(Message.SERIALIZER_SERPENT);
        assertThat(parsed.flags).isEqualTo(Message.FLAGS_EXCEPTION);
        assertThat(parsed.seq).isEqualTo(42);
        assertThat(parsed.data_size).isEqualTo(3);
        assertThat(parsed.annotations_size).isEqualTo(11);
        assertThat(message.get_annotations_bytes()).hasSize(11);
    }

    @Test
    void rejectsMessagesWithInvalidProtocolHeader() {
        byte[] header = new Message(
                Message.MSG_RESULT,
                new byte[0],
                Message.SERIALIZER_SERPENT,
                0,
                1,
                null,
                null).get_header_bytes();
        header[0] = 'X';

        assertThatThrownBy(() -> Message.from_header(header))
                .isInstanceOf(PyroException.class)
                .hasMessage("invalid message");
    }
}
