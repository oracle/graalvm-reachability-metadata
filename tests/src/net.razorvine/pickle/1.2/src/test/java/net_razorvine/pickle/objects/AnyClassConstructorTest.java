/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_razorvine.pickle.objects;

import net.razorvine.pickle.objects.AnyClassConstructor;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AnyClassConstructorTest {
    @Test
    void constructsObjectUsingRuntimeArgumentTypes() {
        AnyClassConstructor constructor = new AnyClassConstructor(ConstructorTarget.class);

        Object value = constructor.construct(new Object[]{"sample", Integer.valueOf(7)});

        assertThat(value).isInstanceOfSatisfying(ConstructorTarget.class, target -> {
            assertThat(target.name()).isEqualTo("sample");
            assertThat(target.count()).isEqualTo(7);
        });
    }

    public static final class ConstructorTarget {
        private final String name;
        private final Integer count;

        public ConstructorTarget(String name, Integer count) {
            this.name = name;
            this.count = count;
        }

        public String name() {
            return name;
        }

        public Integer count() {
            return count;
        }
    }
}
