/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate.hibernate_core;

import org.hibernate.engine.jdbc.SerializableBlobProxy;
import org.junit.jupiter.api.Test;

import javax.sql.rowset.serial.SerialBlob;
import java.nio.charset.StandardCharsets;
import java.sql.Blob;

import static org.assertj.core.api.Assertions.assertThat;

public class SerializableBlobProxyTest {

    @Test
    public void delegatesBlobOperationsToTheWrappedBlob() throws Exception {
        byte[] content = "hibernate".getBytes(StandardCharsets.UTF_8);
        Blob blob = SerializableBlobProxy.generateProxy(new SerialBlob(content));

        assertThat(blob.length()).isEqualTo(content.length);
        assertThat(blob.getBytes(1, content.length)).isEqualTo(content);
    }
}
