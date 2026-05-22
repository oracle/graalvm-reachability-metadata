/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_aesh.aesh;

import static org.assertj.core.api.Assertions.assertThat;

import org.aesh.util.ReflectionUtil;
import org.junit.jupiter.api.Test;

public class ReflectionUtilTest {
    @Test
    void newInstanceBuildsSingleConstructorParameterFromItsPublicNoArgumentConstructor() {
        DependencyConstructedInstance instance = ReflectionUtil.newInstance(DependencyConstructedInstance.class);

        assertThat(instance.message()).isEqualTo("created by dependency");
    }

    public static class DependencyConstructedInstance {
        private final ConstructorDependency dependency;

        public DependencyConstructedInstance(ConstructorDependency dependency) {
            this.dependency = dependency;
        }

        String message() {
            return dependency.message();
        }
    }

    public static class ConstructorDependency {
        public ConstructorDependency() {
        }

        String message() {
            return "created by dependency";
        }
    }
}
