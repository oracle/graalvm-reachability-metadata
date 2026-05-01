/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sourceforge_htmlunit.htmlunit_core_js;

import java.util.Arrays;

import net.sourceforge.htmlunit.corejs.javascript.Context;
import net.sourceforge.htmlunit.corejs.javascript.NativeJavaClass;
import net.sourceforge.htmlunit.corejs.javascript.Scriptable;
import net.sourceforge.htmlunit.corejs.javascript.Wrapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class NativeJavaClassTest {
    @Test
    void constructsJavaObjectWithExpandedVarargs() {
        Context cx = Context.enter();
        try {
            Scriptable scope = cx.initStandardObjects();
            NativeJavaClass javaClass = new NativeJavaClass(scope, VarargsCollector.class);

            Scriptable result =
                    javaClass.construct(
                            cx, scope, new Object[] {"letters", "alpha", "beta", "gamma"});
            VarargsCollector collector = (VarargsCollector) ((Wrapper) result).unwrap();

            assertThat(collector.label()).isEqualTo("letters");
            assertThat(collector.values()).containsExactly("alpha", "beta", "gamma");
        } finally {
            Context.exit();
        }
    }

    public static class VarargsCollector {
        private final String label;
        private final String[] values;

        public VarargsCollector(String label, String... values) {
            this.label = label;
            this.values = values.clone();
        }

        public String label() {
            return label;
        }

        public String[] values() {
            return Arrays.copyOf(values, values.length);
        }
    }
}
