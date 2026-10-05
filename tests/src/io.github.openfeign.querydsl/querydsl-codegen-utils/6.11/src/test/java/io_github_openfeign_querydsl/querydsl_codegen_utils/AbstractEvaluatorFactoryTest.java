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

public class AbstractEvaluatorFactoryTest {

    @Test
    void createsAndCachesAnEvaluatorForGeneratedCode() throws Exception {
        NativeImageSupport.runToleratingUnsupportedFeature(() -> {
            ECJEvaluatorFactory factory = new ECJEvaluatorFactory(getClass().getClassLoader());
            Evaluator<Integer> evaluator = factory.createEvaluator(
                    "return input + adjustment;",
                    Integer.class,
                    new String[] {"input"},
                    new Class<?>[] {Integer.class},
                    Map.<String, Object>of("adjustment", 2));

            assertThat(evaluator.evaluate(4)).isEqualTo(6);
            assertThat(factory.createEvaluator(
                    "return input + adjustment;",
                    Integer.class,
                    new String[] {"input"},
                    new Class<?>[] {Integer.class},
                    Map.<String, Object>of("adjustment", 2)).evaluate(5)).isEqualTo(7);
        });
    }

    @Test
    void compilesASeparateExpressionThroughTheFactory() throws Exception {
        NativeImageSupport.runToleratingUnsupportedFeature(() -> {
            Evaluator<Integer> evaluator = new ECJEvaluatorFactory(getClass().getClassLoader())
                    .createEvaluator(
                            "return input * multiplier;",
                            Integer.class,
                            new String[] {"input"},
                            new Class<?>[] {Integer.class},
                            Map.<String, Object>of("multiplier", 3));

            assertThat(evaluator.evaluate(4)).isEqualTo(12);
        });
    }

    @Test
    void loadsAnEvaluatorAlreadyAvailableToTheFactoryLoader() throws Exception {
        ECJEvaluatorFactory factory = new ECJEvaluatorFactory(
                new ExistingEvaluatorClassLoader(getClass().getClassLoader()));

        Evaluator<Integer> evaluator = factory.createEvaluator(
                "return input + adjustment;",
                Integer.class,
                new String[] {"input"},
                new Class<?>[] {Integer.class},
                Map.<String, Object>of("adjustment", 2));

        assertThat(evaluator.evaluate(4)).isEqualTo(6);
    }

    private static final class ExistingEvaluatorClassLoader extends ClassLoader {

        private ExistingEvaluatorClassLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (name.startsWith("Q_")) {
                return ExistingEvaluator.class;
            }
            return super.loadClass(name, resolve);
        }
    }

    public static final class ExistingEvaluator {

        public static Integer eval(Integer input, Integer adjustment) {
            return input + adjustment;
        }
    }
}
