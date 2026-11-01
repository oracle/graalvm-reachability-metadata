/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_aries_blueprint.org_apache_aries_blueprint_core;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import org.apache.aries.blueprint.container.BlueprintContainerImpl;
import org.apache.aries.blueprint.di.MapRecipe;
import org.apache.aries.blueprint.di.PassThroughRecipe;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class MapRecipeTest {
    @Test
    void instantiatesMapAndPreservesEntriesIncludingNullValues() {
        BlueprintContainerImpl container = RecipeSupport.container();
        MapRecipe recipe = new MapRecipe("settings", LinkedHashMap.class);
        recipe.put(new PassThroughRecipe("key", "region"), new PassThroughRecipe("value", "north"));
        recipe.put(new PassThroughRecipe("empty", "optional"), null);
        Map<?, ?> values = (Map<?, ?>) RecipeSupport.create(container, recipe);
        assertThat(values).isInstanceOf(LinkedHashMap.class);
        assertThat(values.get("region")).isEqualTo("north");
        assertThat(values.containsKey("optional")).isTrue();
        assertThat(values.get("optional")).isNull();
        assertThat(values.keySet().toArray()).containsExactly("region", "optional");
    }

    @Test
    void resolvesSortedMapInterface() {
        BlueprintContainerImpl container = RecipeSupport.container();
        MapRecipe recipe = new MapRecipe("sorted", SortedMap.class);
        recipe.put(new PassThroughRecipe("zKey", "z"), new PassThroughRecipe("zValue", 2));
        recipe.put(new PassThroughRecipe("aKey", "a"), new PassThroughRecipe("aValue", 1));
        Map<?, ?> values = (Map<?, ?>) RecipeSupport.create(container, recipe);
        assertThat(values).isInstanceOf(TreeMap.class);
        assertThat(values.keySet().toArray()).containsExactly("a", "z");
        assertThat(values.get("a")).isEqualTo(1);
        assertThat(values.get("z")).isEqualTo(2);
    }
}
