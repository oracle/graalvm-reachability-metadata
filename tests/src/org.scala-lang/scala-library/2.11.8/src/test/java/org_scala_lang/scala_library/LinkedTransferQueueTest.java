/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_scala_lang.scala_library;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import org.junit.jupiter.api.Test;

import scala.concurrent.forkjoin.LinkedTransferQueue;

import static org.assertj.core.api.Assertions.assertThat;

/** Exercises supported {@link LinkedTransferQueue} serialization. §FS-repository-functional-spec.5.2 */
public class LinkedTransferQueueTest {

    @Test
    void serializesAndDeserializesQueuedElementsInOrder() throws Exception {
        LinkedTransferQueue<String> original = new LinkedTransferQueue<>();
        original.add("first");
        original.add("second");

        LinkedTransferQueue<String> restored = roundTrip(original);

        assertThat(restored.poll()).isEqualTo("first");
        assertThat(restored.poll()).isEqualTo("second");
        assertThat(restored.poll()).isNull();
        assertThat(restored.remainingCapacity()).isEqualTo(Integer.MAX_VALUE);
    }

    @SuppressWarnings("unchecked")
    private static <E> LinkedTransferQueue<E> roundTrip(LinkedTransferQueue<E> queue) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(queue);
        }

        try (ObjectInputStream input = new ObjectInputStream(
                new ByteArrayInputStream(bytes.toByteArray()))) {
            return (LinkedTransferQueue<E>) input.readObject();
        }
    }
}
