/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.jboss.jandex;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StrongInternPoolTest {

    @Test
    void serializesAndDeserializesUsingStrongInternPoolCustomSerialization() throws Exception {
        StrongInternPool<String> originalPool = new StrongInternPool<>();
        String canonicalAlpha = new String("alpha");
        String duplicateAlpha = new String("alpha");

        assertThat(originalPool.intern(canonicalAlpha)).isSameAs(canonicalAlpha);
        assertThat(originalPool.intern(duplicateAlpha)).isSameAs(canonicalAlpha);
        assertThat(originalPool.intern("beta")).isEqualTo("beta");
        assertThat(originalPool.intern(null)).isNull();
        assertThat(originalPool.size()).isEqualTo(3);

        StrongInternPool<String> restoredPool = roundTrip(originalPool);

        assertThat(restoredPool.size()).isEqualTo(3);
        assertThat(restoredPool.contains("alpha")).isTrue();
        assertThat(restoredPool.contains("beta")).isTrue();
        assertThat(restoredPool.contains(null)).isTrue();

        String restoredCanonicalAlpha = restoredPool.intern(new String("alpha"));
        String restoredCanonicalAlphaDuplicate = restoredPool.intern(new String("alpha"));

        assertThat(restoredPool.size()).isEqualTo(3);
        assertThat(restoredCanonicalAlpha).isEqualTo("alpha");
        assertThat(restoredCanonicalAlphaDuplicate).isSameAs(restoredCanonicalAlpha);
    }

    @SuppressWarnings("unchecked")
    private StrongInternPool<String> roundTrip(StrongInternPool<String> originalPool) throws Exception {
        byte[] serializedForm;
        try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteArrayOutputStream)) {
            objectOutputStream.writeObject(originalPool);
            objectOutputStream.flush();
            serializedForm = byteArrayOutputStream.toByteArray();
        }

        try (ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(serializedForm);
                ObjectInputStream objectInputStream = new ObjectInputStream(byteArrayInputStream)) {
            return (StrongInternPool<String>) objectInputStream.readObject();
        }
    }
}
