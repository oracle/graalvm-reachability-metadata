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
import org.apache.xbean.recipe.CollectionRecipe;
import org.apache.xbean.recipe.Option;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AsmParameterNameLoaderTest {
    @Test
    void loadsNamesForPublicAndDeclaredConstructors() throws Exception {
        AsmParameterNameLoader loader = new AsmParameterNameLoader();
        Constructor<CollectionRecipe> typeNameConstructor = CollectionRecipe.class.getConstructor(String.class);
        Constructor<CollectionRecipe> typeClassConstructor = CollectionRecipe.class.getConstructor(Class.class);

        Map<Constructor, List<String>> parameterNames = loader.getAllConstructorParameters(CollectionRecipe.class);

        assertThat(parameterNames).containsKeys(typeNameConstructor, typeClassConstructor);
        assertThat(parameterNames.get(typeNameConstructor)).containsExactly("type");
        assertThat(parameterNames.get(typeClassConstructor)).containsExactly("type");
    }

    @Test
    void loadsNamesForPublicAndDeclaredMethods() throws Exception {
        AsmParameterNameLoader loader = new AsmParameterNameLoader();
        Method allowMethod = CollectionRecipe.class.getMethod("allow", Option.class);
        Method disallowMethod = CollectionRecipe.class.getMethod("disallow", Option.class);

        Map<Method, List<String>> allowParameterNames = loader.getAllMethodParameters(CollectionRecipe.class, "allow");
        Map<Method, List<String>> disallowParameterNames = loader.getAllMethodParameters(
                CollectionRecipe.class,
                "disallow");

        assertThat(allowParameterNames).containsKey(allowMethod);
        assertThat(disallowParameterNames).containsKey(disallowMethod);
        assertThat(allowParameterNames.get(allowMethod)).containsExactly("option");
        assertThat(disallowParameterNames.get(disallowMethod)).containsExactly("option");
    }
}
