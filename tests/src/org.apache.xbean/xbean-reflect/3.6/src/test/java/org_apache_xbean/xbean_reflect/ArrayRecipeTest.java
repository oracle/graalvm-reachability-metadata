/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_xbean.xbean_reflect;

import org.apache.xbean.recipe.ArrayRecipe;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ArrayRecipeTest {
    @Test
    void createsArrayInstanceForConfiguredComponentType() {
        ArrayRecipe recipe = new ArrayRecipe(String.class);
        recipe.add("alpha");
        recipe.add("beta");

        Object value = recipe.create(String[].class, false);

        assertThat(value).isInstanceOf(String[].class);
        assertThat((String[]) value).containsExactly("alpha", "beta");
    }
}
