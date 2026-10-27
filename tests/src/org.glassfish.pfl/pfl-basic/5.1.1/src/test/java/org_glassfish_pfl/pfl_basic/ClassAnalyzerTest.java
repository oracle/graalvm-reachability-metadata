/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_pfl.pfl_basic;

import static org.assertj.core.api.Assertions.assertThat;

import org.glassfish.pfl.basic.algorithm.ClassAnalyzer;
import org.junit.jupiter.api.Test;

public class ClassAnalyzerTest {
    @Test
    public void findsMethodsAcrossClassHierarchy() {
        ClassAnalyzer analyzer = ClassAnalyzer.getClassAnalyzer(Child.class);

        assertThat(analyzer.findMethods(method -> method.getName().equals("name"))).isNotEmpty();
        assertThat(analyzer.toString()).contains("Child");
    }

    public static class Parent {
        public String name() {
            return "parent";
        }
    }

    public static class Child extends Parent {
        public int count() {
            return 1;
        }
    }
}
