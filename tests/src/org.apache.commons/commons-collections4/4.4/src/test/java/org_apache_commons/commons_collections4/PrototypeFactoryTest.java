/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_collections4;

import java.lang.reflect.Field;

import org.apache.commons.collections4.Factory;
import org.apache.commons.collections4.functors.PrototypeFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class PrototypeFactoryTest {

    @Test
    void createsCopiesThroughPublicCloneMethodWhenAvailable() {
        PublicCloneableValue prototype = new PublicCloneableValue("metadata", 4);

        Factory<PublicCloneableValue> factory = PrototypeFactory.prototypeFactory(prototype);
        PublicCloneableValue created = factory.create();

        assertThat(created).isNotSameAs(prototype);
        assertThat(created.describe()).isEqualTo(prototype.describe());
    }

    @Test
    void recreatesTransientCloneMethodBeforeCreatingCopies() throws ReflectiveOperationException {
        PublicCloneableValue prototype = new PublicCloneableValue("metadata", 4);

        Factory<PublicCloneableValue> factory = PrototypeFactory.prototypeFactory(prototype);
        clearCachedCloneMethod(factory);
        PublicCloneableValue created = factory.create();

        assertThat(created).isNotSameAs(prototype);
        assertThat(created.describe()).isEqualTo(prototype.describe());
    }

    @Test
    void createsCopiesThroughPublicCopyConstructorWhenCloneIsUnavailable() {
        CopyConstructedValue prototype = new CopyConstructedValue("metadata", 4);

        Factory<CopyConstructedValue> factory = PrototypeFactory.prototypeFactory(prototype);
        CopyConstructedValue created = factory.create();

        assertThat(created).isNotSameAs(prototype);
        assertThat(created.describe()).isEqualTo(prototype.describe());
    }

    private static void clearCachedCloneMethod(Factory<?> factory) throws ReflectiveOperationException {
        Field cloneMethodField = factory.getClass().getDeclaredField("iCloneMethod");
        cloneMethodField.setAccessible(true);
        cloneMethodField.set(factory, null);
    }

    public static final class PublicCloneableValue {

        private final String text;
        private final int count;

        public PublicCloneableValue(String text, int count) {
            this.text = text;
            this.count = count;
        }

        public PublicCloneableValue clone() {
            return new PublicCloneableValue(text, count);
        }

        public String describe() {
            return text + "-" + count;
        }
    }

    public static final class CopyConstructedValue {

        private final String text;
        private final int count;

        public CopyConstructedValue(String text, int count) {
            this.text = text;
            this.count = count;
        }

        public CopyConstructedValue(CopyConstructedValue source) {
            this(source.text, source.count);
        }

        public String describe() {
            return text + "-" + count;
        }
    }
}
