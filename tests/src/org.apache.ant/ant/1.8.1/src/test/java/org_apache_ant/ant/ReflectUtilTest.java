/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_ant.ant;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.tools.ant.util.ReflectUtil;
import org.junit.jupiter.api.Test;

public class ReflectUtilTest {
    @Test
    void constructsObjectsWithPublicConstructors() {
        assertThat(ReflectUtil.newInstance(Probe.class, new Class<?>[] {String.class},
                        new Object[] {"Ant"}))
                .isEqualTo(new Probe("Ant"));
    }

    @Test
    void invokesInstanceMethodsWithSupportedArgumentCounts() {
        Probe probe = new Probe("Ant");

        assertThat(ReflectUtil.invoke(probe, "name")).isEqualTo("Ant");
        assertThat(ReflectUtil.invoke(probe, "greet", String.class, "Apache"))
                .isEqualTo("Apache Ant");
        assertThat(ReflectUtil.invoke(probe, "join", String.class, "Apache", String.class, "Ant"))
                .isEqualTo("Apache Ant");
    }

    @Test
    void invokesStaticMethods() {
        assertThat(ReflectUtil.invokeStatic(Probe.class, "libraryName")).isEqualTo("Ant");
    }

    @Test
    void readsDeclaredFields() {
        assertThat(ReflectUtil.getField(new Probe("Ant"), "name")).isEqualTo("Ant");
    }

    @Test
    void identifiesSupportedMethods() {
        Probe probe = new Probe("Ant");

        assertThat(ReflectUtil.respondsTo(probe, "greet")).isTrue();
        assertThat(ReflectUtil.respondsTo(probe, "missing")).isFalse();
    }

    public static class Probe {
        private final String name;

        public Probe(String name) {
            this.name = name;
        }

        public String name() {
            return name;
        }

        public String greet(String prefix) {
            return prefix + " " + name;
        }

        public String join(String first, String second) {
            return first + " " + second;
        }

        public static String libraryName() {
            return "Ant";
        }

        @Override
        public boolean equals(Object object) {
            if (!(object instanceof Probe)) {
                return false;
            }
            return name.equals(((Probe) object).name);
        }

        @Override
        public int hashCode() {
            return name.hashCode();
        }
    }
}
