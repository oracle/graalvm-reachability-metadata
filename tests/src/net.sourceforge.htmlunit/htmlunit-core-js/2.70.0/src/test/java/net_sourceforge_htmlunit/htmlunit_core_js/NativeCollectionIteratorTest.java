/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sourceforge_htmlunit.htmlunit_core_js;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import net.sourceforge.htmlunit.corejs.javascript.NativeCollectionIterator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class NativeCollectionIteratorTest {
    @Test
    void serializesClassNameAndIteratorType() throws Exception {
        NativeCollectionIterator iterator = new NativeCollectionIterator("Map Iterator");

        NativeCollectionIterator restored = roundTrip(iterator, NativeCollectionIterator.class);

        assertThat(restored.getClassName()).isEqualTo("Map Iterator");
    }

    private static <T> T roundTrip(T value, Class<T> type) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(value);
        }

        ByteArrayInputStream input = new ByteArrayInputStream(bytes.toByteArray());
        try (ObjectInputStream in = new ObjectInputStream(input)) {
            return type.cast(in.readObject());
        }
    }
}
