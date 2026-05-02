/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_xbean.xbean_reflect;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import org.apache.xbean.recipe.AsmParameterNameLoader;
import org.apache.xbean.recipe.Option;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AsmParameterNameLoaderTest {
    @Test
    void loadsNamesForPublicAndDeclaredConstructors() throws Exception {
        AsmParameterNameLoader loader = new AsmParameterNameLoader();
        Constructor<ParameterNamedTarget> nameConstructor = ParameterNamedTarget.class.getConstructor(String.class);
        Constructor<ParameterNamedTarget> countConstructor = ParameterNamedTarget.class.getDeclaredConstructor(Integer.class);

        Map<Constructor, List<String>> parameterNames = loader.getAllConstructorParameters(ParameterNamedTarget.class);

        assertThat(parameterNames).containsKeys(nameConstructor, countConstructor);
        assertThat(parameterNames.get(nameConstructor)).containsExactly("name");
        assertThat(parameterNames.get(countConstructor)).containsExactly("count");
    }

    @Test
    void loadsNamesForPublicAndDeclaredMethods() throws Exception {
        AsmParameterNameLoader loader = new AsmParameterNameLoader();
        Method allowMethod = ParameterNamedTarget.class.getMethod("allow", Option.class);
        Method disallowMethod = ParameterNamedTarget.class.getDeclaredMethod("disallow", Option.class);

        Map<Method, List<String>> allowParameterNames = loader.getAllMethodParameters(ParameterNamedTarget.class, "allow");
        Map<Method, List<String>> disallowParameterNames = loader.getAllMethodParameters(
                ParameterNamedTarget.class,
                "disallow");

        assertThat(allowParameterNames).containsKey(allowMethod);
        assertThat(disallowParameterNames).containsKey(disallowMethod);
        assertThat(allowParameterNames.get(allowMethod)).containsExactly("option");
        assertThat(disallowParameterNames.get(disallowMethod)).containsExactly("option");
    }

    public static final class ParameterNamedTarget {
        public ParameterNamedTarget(String name) {
        }

        private ParameterNamedTarget(Integer count) {
        }

        public void allow(Option option) {
        }

        private void disallow(Option option) {
        }
    }
}
