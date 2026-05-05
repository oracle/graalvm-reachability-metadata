/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_collections.eclipse_collections;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Constructor;

import org.eclipse.collections.impl.utility.internal.ReflectionHelper;
import org.junit.jupiter.api.Test;

public class ReflectionHelperTest {
    @Test
    void getConstructorFindsExactPublicConstructor() {
        final Constructor<NamedThing> constructor = ReflectionHelper.getConstructor(NamedThing.class, String.class);

        assertThat(constructor).isNotNull();
        assertThat(ReflectionHelper.newInstance(constructor, "alpha").name()).isEqualTo("alpha");
    }

    @Test
    void getConstructorSearchesForAssignableConstructorWhenExactSignatureIsAbsent() {
        final Constructor<CharSequenceThing> constructor = ReflectionHelper.getConstructor(
                CharSequenceThing.class,
                String.class);

        assertThat(constructor).isNotNull();
        assertThat(ReflectionHelper.newInstance(constructor, "bravo").value()).isEqualTo("bravo");
    }

    @Test
    void getConstructorSearchesForPrimitiveConstructorWhenWrapperTypeIsRequested() {
        final Constructor<SizedThing> constructor = ReflectionHelper.getConstructor(SizedThing.class, Integer.class);

        assertThat(constructor).isNotNull();
        assertThat(ReflectionHelper.newInstance(constructor, 42).size()).isEqualTo(42);
    }

    @Test
    void newInstanceCreatesObjectWithPublicDefaultConstructor() {
        final DefaultConstructibleThing thing = ReflectionHelper.newInstance(DefaultConstructibleThing.class);

        assertThat(thing.description()).isEqualTo("created");
    }

    @Test
    void hasDefaultConstructorDetectsPublicNoArgumentConstructor() {
        assertThat(ReflectionHelper.hasDefaultConstructor(DefaultConstructibleThing.class)).isTrue();
        assertThat(ReflectionHelper.hasDefaultConstructor(NamedThing.class)).isFalse();
    }

    public static final class NamedThing {
        private final String name;

        public NamedThing(String name) {
            this.name = name;
        }

        String name() {
            return this.name;
        }
    }

    public static final class CharSequenceThing {
        private final CharSequence value;

        public CharSequenceThing(CharSequence value) {
            this.value = value;
        }

        CharSequence value() {
            return this.value;
        }
    }

    public static final class SizedThing {
        private final int size;

        public SizedThing(int size) {
            this.size = size;
        }

        int size() {
            return this.size;
        }
    }

    public static final class DefaultConstructibleThing {
        public DefaultConstructibleThing() {
        }

        String description() {
            return "created";
        }
    }
}
