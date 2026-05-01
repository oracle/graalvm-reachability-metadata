/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_lucene.lucene_core;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.lucene.util.VirtualMethod;
import org.junit.jupiter.api.Test;

public class VirtualMethodTest {
    @Test
    public void computesImplementationDistanceForOverriddenMethod() {
        VirtualMethod<BaseBehavior> renderMethod = new VirtualMethod<>(
                BaseBehavior.class,
                "render",
                CharSequence.class);

        assertThat(renderMethod.getImplementationDistance(BaseBehavior.class)).isZero();
        assertThat(renderMethod.isOverriddenAsOf(FirstLevelBehavior.class)).isTrue();
        assertThat(renderMethod.getImplementationDistance(SecondLevelBehavior.class)).isEqualTo(1);
        assertThat(renderMethod.getImplementationDistance(ThirdLevelBehavior.class)).isEqualTo(3);
    }

    private static class BaseBehavior {
        public String render(CharSequence value) {
            return value.toString();
        }
    }

    private static class FirstLevelBehavior extends BaseBehavior {
        @Override
        public String render(CharSequence value) {
            return "first:" + value;
        }
    }

    private static class SecondLevelBehavior extends FirstLevelBehavior {
    }

    private static class ThirdLevelBehavior extends SecondLevelBehavior {
        @Override
        public String render(CharSequence value) {
            return "third:" + value;
        }
    }
}
