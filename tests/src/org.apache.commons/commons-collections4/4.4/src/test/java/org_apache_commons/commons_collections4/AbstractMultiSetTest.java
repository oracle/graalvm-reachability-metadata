/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_collections4;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import org.apache.commons.collections4.MultiSet;
import org.apache.commons.collections4.multiset.AbstractMultiSet;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AbstractMultiSetTest {

    @Test
    void serializesAndDeserializesEntriesThroughAbstractMultiSetHooks() throws Exception {
        SerializableTestMultiSet original = new SerializableTestMultiSet();
        SerializableElement alpha = new SerializableElement("alpha", 1);
        SerializableElement beta = new SerializableElement("beta", 2);
        SerializableElement gamma = new SerializableElement("gamma", 3);

        original.add(alpha, 2);
        original.add(beta, 1);
        original.add(gamma, 3);

        byte[] serialized = serialize(original);
        SerializableTestMultiSet restored = deserialize(serialized);

        assertThat(restored).hasSize(6);
        assertThat(restored.getCount(alpha)).isEqualTo(2);
        assertThat(restored.getCount(beta)).isEqualTo(1);
        assertThat(restored.getCount(gamma)).isEqualTo(3);
        assertThat(restored.uniqueSet()).containsExactly(alpha, beta, gamma);

        restored.remove(gamma, 1);
        restored.add(beta, 2);

        assertThat(restored).hasSize(7);
        assertThat(restored.getCount(alpha)).isEqualTo(2);
        assertThat(restored.getCount(beta)).isEqualTo(3);
        assertThat(restored.getCount(gamma)).isEqualTo(2);
    }

    private static byte[] serialize(SerializableTestMultiSet multiSet) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(outputStream)) {
            objectOutputStream.writeObject(multiSet);
        }
        return outputStream.toByteArray();
    }

    private static SerializableTestMultiSet deserialize(byte[] serialized)
            throws IOException, ClassNotFoundException {
        try (ObjectInputStream objectInputStream = new ObjectInputStream(new ByteArrayInputStream(serialized))) {
            Object restored = objectInputStream.readObject();
            assertThat(restored).isInstanceOf(SerializableTestMultiSet.class);
            return (SerializableTestMultiSet) restored;
        }
    }

    private static final class SerializableTestMultiSet extends AbstractMultiSet<SerializableElement>
            implements Serializable {

        private static final long serialVersionUID = 1L;

        private transient Map<SerializableElement, Integer> elements = new LinkedHashMap<>();

        @Override
        public int add(SerializableElement object, int occurrences) {
            if (occurrences <= 0) {
                return getCount(object);
            }
            int previousCount = getCount(object);
            elements.put(object, previousCount + occurrences);
            return previousCount;
        }

        @Override
        public int remove(Object object, int occurrences) {
            int previousCount = getCount(object);
            if (previousCount == 0 || occurrences <= 0) {
                return previousCount;
            }
            if (occurrences >= previousCount) {
                elements.remove(object);
            } else {
                elements.put((SerializableElement) object, previousCount - occurrences);
            }
            return previousCount;
        }

        @Override
        protected int uniqueElements() {
            return elements.size();
        }

        @Override
        protected Iterator<MultiSet.Entry<SerializableElement>> createEntrySetIterator() {
            Iterator<Map.Entry<SerializableElement, Integer>> iterator = elements.entrySet().iterator();
            return new Iterator<>() {
                @Override
                public boolean hasNext() {
                    return iterator.hasNext();
                }

                @Override
                public MultiSet.Entry<SerializableElement> next() {
                    Map.Entry<SerializableElement, Integer> entry = iterator.next();
                    return new AbstractEntry<>() {
                        @Override
                        public SerializableElement getElement() {
                            return entry.getKey();
                        }

                        @Override
                        public int getCount() {
                            return entry.getValue();
                        }
                    };
                }

                @Override
                public void remove() {
                    iterator.remove();
                }
            };
        }

        private void writeObject(ObjectOutputStream out) throws IOException {
            out.defaultWriteObject();
            doWriteObject(out);
        }

        private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
            in.defaultReadObject();
            elements = new LinkedHashMap<>();
            doReadObject(in);
        }
    }

    private static final class SerializableElement implements Serializable {

        private static final long serialVersionUID = 1L;

        private final String name;
        private final int id;

        private SerializableElement(String name, int id) {
            this.name = name;
            this.id = id;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof SerializableElement that)) {
                return false;
            }
            return id == that.id && Objects.equals(name, that.name);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, id);
        }
    }
}
