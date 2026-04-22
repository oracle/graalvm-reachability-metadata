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
import java.io.DataOutputStream;
import org.apache.jute.RecordReader;
import org.apache.zookeeper.proto.RequestHeader;
import org.junit.jupiter.api.Test;

public class RecordReaderTest {

    @Test
    void readsBinaryRequestHeader() throws Exception {
        RequestHeader expected = new RequestHeader(42, -11);
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        expected.write(new DataOutputStream(buffer));

        RequestHeader actual = new RequestHeader();
        RecordReader reader = new RecordReader(new ByteArrayInputStream(buffer.toByteArray()), "binary");
        reader.read(actual);

        assertThat(actual).isEqualTo(expected);
        assertThat(actual.getXid()).isEqualTo(42);
        assertThat(actual.getType()).isEqualTo(-11);
    }
}
