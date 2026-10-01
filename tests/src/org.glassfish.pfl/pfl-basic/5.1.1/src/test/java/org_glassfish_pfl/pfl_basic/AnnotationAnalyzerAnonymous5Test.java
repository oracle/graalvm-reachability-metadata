/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_pfl.pfl_basic;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;

import org.glassfish.pfl.basic.algorithm.AnnotationAnalyzer;
import org.junit.jupiter.api.Test;

public class AnnotationAnalyzerAnonymous5Test {
    @Test
    public void resolvesMethodAnnotationsThroughAnalyzer() throws Exception {
        Method method = Annotated.class.getDeclaredMethod("label", String.class);

        assertThat(new AnnotationAnalyzer().getAnnotations(method)).containsKey(Deprecated.class);
    }

    public static class Annotated {
        @Deprecated
        public String label(String value) {
            return value;
        }
    }
}
