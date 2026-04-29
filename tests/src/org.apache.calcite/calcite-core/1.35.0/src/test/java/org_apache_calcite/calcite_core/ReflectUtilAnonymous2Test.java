/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_calcite.calcite_core;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.calcite.util.ReflectUtil;
import org.apache.calcite.util.ReflectiveVisitor;
import org.junit.jupiter.api.Test;

public class ReflectUtilAnonymous2Test {
    @Test
    void methodDispatcherInvokesMostSpecificVisitorMethod() {
        DispatchingVisitor visitor = new DispatchingVisitor();
        ReflectUtil.MethodDispatcher<String> dispatcher = ReflectUtil.createMethodDispatcher(
                String.class,
                visitor,
                "describe",
                VisitTarget.class,
                String.class);

        assertThat(dispatcher.invoke(new SpecializedVisitTarget("alpha"), "!"))
                .isEqualTo("specialized:alpha!");
        assertThat(dispatcher.invoke(new VisitTarget("beta"), "."))
                .isEqualTo("base:beta.");
    }

    public static class DispatchingVisitor implements ReflectiveVisitor {
        public String describe(VisitTarget target, String suffix) {
            return "base:" + target.name + suffix;
        }

        public String describe(SpecializedVisitTarget target, String suffix) {
            return "specialized:" + target.name + suffix;
        }
    }

    public static class VisitTarget {
        protected final String name;

        VisitTarget(String name) {
            this.name = name;
        }
    }

    public static final class SpecializedVisitTarget extends VisitTarget {
        SpecializedVisitTarget(String name) {
            super(name);
        }
    }
}
