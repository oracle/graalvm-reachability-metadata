/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_calcite.calcite_core;

import org.apache.calcite.util.ReflectUtil;
import org.apache.calcite.util.ReflectiveVisitor;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ReflectUtilTest {
    @Test
    void dispatchesToMostSpecificVisitorMethod() {
        AnimalVisitor visitor = new AnimalVisitor();

        boolean invoked = ReflectUtil.invokeVisitor(visitor, new Cat(), Animal.class, "visit");

        assertThat(invoked).isTrue();
        assertThat(visitor.visited).isEqualTo("cat");
        assertThat(ReflectUtil.getBoxingClass(int.class)).isEqualTo(Integer.class);
    }

    public static class Animal {
    }

    public static class Cat extends Animal {
    }

    public static class AnimalVisitor implements ReflectiveVisitor {
        private String visited;

        public void visit(Animal animal) {
            visited = "animal";
        }

        public void visit(Cat cat) {
            visited = "cat";
        }
    }
}
