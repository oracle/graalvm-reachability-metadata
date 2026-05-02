/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_parquet.parquet_format_structures;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.parquet.format.KeyValue;
import org.apache.parquet.format.event.Consumers;
import org.apache.parquet.format.event.EventBasedThriftReader;
import org.junit.jupiter.api.Test;

import shaded.parquet.org.apache.thrift.protocol.TCompactProtocol;
import shaded.parquet.org.apache.thrift.protocol.TProtocol;
import shaded.parquet.org.apache.thrift.transport.TIOStreamTransport;

public class TBaseStructConsumerTest {
    @Test
    void structConsumerInstantiatesAndReadsGeneratedThriftStruct() throws Exception {
        KeyValue expected = new KeyValue("parquet.key").setValue("parquet.value");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        expected.write(new TCompactProtocol(new TIOStreamTransport(out)));
        TProtocol protocol = new TCompactProtocol(new TIOStreamTransport(new ByteArrayInputStream(out.toByteArray())));
        List<KeyValue> consumed = new ArrayList<>();

        Consumers.struct(KeyValue.class, consumed::add)
                .consumeStruct(protocol, new EventBasedThriftReader(protocol));

        assertThat(consumed).containsExactly(expected);
    }
}
