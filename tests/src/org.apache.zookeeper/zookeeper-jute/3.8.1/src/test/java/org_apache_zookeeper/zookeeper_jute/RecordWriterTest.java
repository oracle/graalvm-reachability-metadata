/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_zookeeper.zookeeper_jute;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import org.apache.jute.RecordWriter;
import org.apache.zookeeper.proto.ReplyHeader;
import org.junit.jupiter.api.Test;

public class RecordWriterTest {

    @Test
    void writesBinaryReplyHeader() throws Exception {
        ReplyHeader expected = new ReplyHeader(7, 13L, -1);
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        RecordWriter writer = new RecordWriter(buffer, "binary");
        writer.write(expected);

        ReplyHeader actual = new ReplyHeader();
        actual.readFields(new DataInputStream(new ByteArrayInputStream(buffer.toByteArray())));

        assertThat(buffer.toByteArray()).isNotEmpty();
        assertThat(actual).isEqualTo(expected);
        assertThat(actual.getZxid()).isEqualTo(13L);
    }
}
