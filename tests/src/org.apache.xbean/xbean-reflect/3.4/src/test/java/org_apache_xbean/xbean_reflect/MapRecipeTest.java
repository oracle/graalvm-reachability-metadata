/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_xbean.xbean_reflect;

import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.xbean.recipe.MapRecipe;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class MapRecipeTest {
    @Test
    void createsRequestedMapImplementationFromRecipeEntries() {
        MapRecipe recipe = new MapRecipe(LinkedHashMap.class);
        recipe.put("first", "alpha");
        recipe.put("second", "beta");

        Object value = recipe.create(LinkedHashMap.class, false);

        assertThat(value).isInstanceOf(LinkedHashMap.class);
        Map<?, ?> map = (Map<?, ?>) value;
        assertThat(map.get("first")).isEqualTo("alpha");
        assertThat(map.get("second")).isEqualTo("beta");
    }
}
