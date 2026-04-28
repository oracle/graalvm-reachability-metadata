/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_netty.netty;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Arrays;
import java.util.List;

import org.jboss.netty.util.internal.LinkedTransferQueue;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class LinkedTransferQueueTest {
    @Test
    void serializesAndDeserializesQueuedElements() throws Exception {
        LinkedTransferQueue<String> queue = new LinkedTransferQueue<String>();
        queue.offer("alpha");
        queue.offer("beta");

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ObjectOutputStream objectOutputStream = new ObjectOutputStream(outputStream);
        objectOutputStream.writeObject(queue);
        objectOutputStream.close();

        ObjectInputStream objectInputStream = new ObjectInputStream(new ByteArrayInputStream(outputStream.toByteArray()));
        @SuppressWarnings("unchecked")
        LinkedTransferQueue<String> restored = (LinkedTransferQueue<String>) objectInputStream.readObject();

        List<String> values = Arrays.asList(restored.poll(), restored.poll());
        assertThat(values).containsExactly("alpha", "beta");
        assertThat(restored.poll()).isNull();
    }
}
