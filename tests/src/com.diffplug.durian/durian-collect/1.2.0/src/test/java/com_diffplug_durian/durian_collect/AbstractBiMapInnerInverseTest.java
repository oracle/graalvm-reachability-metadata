/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_diffplug_durian.durian_collect;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import org.junit.jupiter.api.Test;

import com.diffplug.common.collect.BiMap;
import com.diffplug.common.collect.EnumBiMap;

public class AbstractBiMapInnerInverseTest {
    @Test
    void serializesInverseViewAndRestoresBidirectionalRelationship() throws Exception {
        EnumBiMap<ForwardKey, BackwardValue> forward = EnumBiMap.create(ForwardKey.class, BackwardValue.class);
        forward.put(ForwardKey.FIRST, BackwardValue.ALPHA);
        forward.put(ForwardKey.SECOND, BackwardValue.BETA);

        Object copy = roundTrip(forward.inverse());

        assertThat(copy).isInstanceOf(BiMap.class);
        @SuppressWarnings("unchecked")
        BiMap<BackwardValue, ForwardKey> inverseCopy = (BiMap<BackwardValue, ForwardKey>) copy;
        assertThat(inverseCopy).containsEntry(BackwardValue.ALPHA, ForwardKey.FIRST)
                .containsEntry(BackwardValue.BETA, ForwardKey.SECOND);

        BiMap<ForwardKey, BackwardValue> forwardCopy = inverseCopy.inverse();
        assertThat(forwardCopy).isInstanceOf(EnumBiMap.class)
                .containsEntry(ForwardKey.FIRST, BackwardValue.ALPHA)
                .containsEntry(ForwardKey.SECOND, BackwardValue.BETA);
        assertThat(forwardCopy.inverse()).isSameAs(inverseCopy);

        inverseCopy.put(BackwardValue.GAMMA, ForwardKey.THIRD);

        assertThat(forwardCopy).containsEntry(ForwardKey.THIRD, BackwardValue.GAMMA);
        assertThat(inverseCopy.inverse().inverse()).isSameAs(inverseCopy);
    }

    private static Object roundTrip(Object original) throws IOException, ClassNotFoundException {
        byte[] serialized;
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(original);
            output.flush();
            serialized = bytes.toByteArray();
        }

        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(serialized))) {
            return input.readObject();
        }
    }

    private enum ForwardKey {
        FIRST,
        SECOND,
        THIRD
    }

    private enum BackwardValue {
        ALPHA,
        BETA,
        GAMMA
    }
}
