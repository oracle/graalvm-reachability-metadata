/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ch_qos_logback.logback_core;

import ch.qos.logback.core.ContextBase;
import ch.qos.logback.core.util.EnvUtil;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

import java.net.URL;

import static org.assertj.core.api.Assertions.assertThat;

public class EnvUtilTest {
    private static final String JANINO_SCRIPT_EVALUATOR_CLASS = "org.codehaus.janino.ScriptEvaluator";
    private static final String JANINO_SCRIPT_EVALUATOR_RESOURCE = "org/codehaus/janino/ScriptEvaluator.class";
    private static final String MISSING_CLASS = "ch_qos_logback.logback_core.MissingEnvUtilTarget";

    @Test
    void isClassAvailableLoadsClassByNameThroughCallerClassLoader() {
        try {
            boolean available = EnvUtil.isClassAvailable(EnvUtilTest.class, ContextBase.class.getName());

            assertThat(available).isTrue();
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    @Test
    void isClassAvailableReturnsFalseWhenCallerClassLoaderCannotFindClass() {
        try {
            boolean available = EnvUtil.isClassAvailable(EnvUtilTest.class, MISSING_CLASS);

            assertThat(available).isFalse();
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    @Test
    void isJaninoAvailableMatchesRuntimeClasspath() {
        try {
            URL janinoClassResource = EnvUtilTest.class.getClassLoader().getResource(JANINO_SCRIPT_EVALUATOR_RESOURCE);
            boolean expectedAvailable = janinoClassResource != null;

            assertThat(EnvUtil.isJaninoAvailable()).isEqualTo(expectedAvailable);
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    @Test
    void isClassAvailableUsesTheSameJaninoClassNameAsJaninoProbe() {
        try {
            boolean janinoAvailable = EnvUtil.isJaninoAvailable();
            boolean classAvailable = EnvUtil.isClassAvailable(EnvUtil.class, JANINO_SCRIPT_EVALUATOR_CLASS);

            assertThat(classAvailable).isEqualTo(janinoAvailable);
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }
}
