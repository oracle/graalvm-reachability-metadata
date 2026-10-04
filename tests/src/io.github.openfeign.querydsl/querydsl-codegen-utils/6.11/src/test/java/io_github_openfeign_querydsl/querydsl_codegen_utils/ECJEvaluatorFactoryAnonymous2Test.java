/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_github_openfeign_querydsl.querydsl_codegen_utils;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import com.querydsl.codegen.utils.ECJEvaluatorFactory;
import com.querydsl.codegen.utils.Evaluator;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

public class ECJEvaluatorFactoryAnonymous2Test {

    @Test
    void compilesAndEvaluatesAnExpressionWithTheEclipseCompiler() throws Exception {
        NativeImageSupport.runToleratingUnsupportedFeature(() -> {
            Evaluator<Integer> evaluator = new ECJEvaluatorFactory(getClass().getClassLoader())
                    .createEvaluator(
                            "return input + adjustment;",
                            Integer.class,
                            new String[] {"input"},
                            new Class<?>[] {Integer.class},
                            Map.<String, Object>of("adjustment", 2));

            assertThat(evaluator.getType()).isEqualTo(Integer.class);
            assertThat(evaluator.evaluate(3)).isEqualTo(5);
        });
    }
}
