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

public class ClassAnalyzerAnonymous2Test {
    @Test
    public void analyzesDeclaredMethods() {
        assertThat(ClassAnalyzer.getClassAnalyzer(Analyzed.class).findMethods(method -> true))
                .isNotEmpty();
    }

    public static class Analyzed {
        public void visit() {
        }
    }
}
