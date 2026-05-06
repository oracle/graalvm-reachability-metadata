/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package berkeleydb.je;

import com.sleepycat.je.config.BooleanConfigParam;
import com.sleepycat.je.config.EnvironmentParams;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class BooleanConfigParamTest {

    @Test
    void resolvesClassNameThroughCompilerGeneratedClassLookup() throws Exception {
        Method classLookup = BooleanConfigParam.class.getDeclaredMethod("class$", String.class);
        classLookup.setAccessible(true);

        Object resolvedClass = classLookup.invoke(null, BooleanConfigParam.class.getName());

        assertThat(resolvedClass).isSameAs(BooleanConfigParam.class);
    }

    @Test
    void validatesBooleanEnvironmentParameterValues() {
        BooleanConfigParam param = EnvironmentParams.ENV_RECOVERY;

        assertThat(param.getDefault()).isEqualTo("true");
        param.validateValue(" true ");
        param.validateValue("FALSE");

        assertThatThrownBy(() -> param.validateValue("not-a-boolean"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("com.sleepycat.je.config.BooleanConfigParam")
            .hasMessageContaining("not-a-boolean not valid boolean")
            .hasMessageContaining(param.getName());
    }
}
