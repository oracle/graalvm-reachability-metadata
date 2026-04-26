/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_atomikos.atomikos_util;

import com.atomikos.util.SerializableObjectFactory;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import javax.naming.BinaryRefAddr;
import javax.naming.Reference;

import static org.assertj.core.api.Assertions.assertThat;

public class SerializableObjectFactoryTest {

    @Test
    void createReferenceAndGetObjectInstanceRoundTripSerializableObjects() throws Exception {
        ArrayList<String> original = new ArrayList<>(List.of("alpha", "beta"));

        Reference reference = SerializableObjectFactory.createReference(original);
        Object restored = new SerializableObjectFactory().getObjectInstance(reference, null, null, null);

        assertThat(reference.getClassName()).isEqualTo(ArrayList.class.getName());
        assertThat(reference.getFactoryClassName()).isEqualTo(SerializableObjectFactory.class.getName());
        assertThat(reference.get("com.atomikos.serializable")).isInstanceOf(BinaryRefAddr.class);
        assertThat((byte[]) reference.get("com.atomikos.serializable").getContent()).isNotEmpty();
        assertThat(restored).isInstanceOf(ArrayList.class);
        assertThat((ArrayList<?>) restored).containsExactly("alpha", "beta");
    }

    @Test
    void getObjectInstanceReturnsNullWhenTheReferenceDoesNotContainSerializedContent() throws Exception {
        Reference reference = new Reference(ArrayList.class.getName());

        Object restored = new SerializableObjectFactory().getObjectInstance(reference, null, null, null);

        assertThat(restored).isNull();
    }
}
