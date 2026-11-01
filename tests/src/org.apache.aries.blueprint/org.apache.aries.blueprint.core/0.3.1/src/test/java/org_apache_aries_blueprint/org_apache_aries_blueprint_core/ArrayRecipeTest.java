/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_aries_blueprint.org_apache_aries_blueprint_core;

import org.apache.aries.blueprint.container.BlueprintContainerImpl;
import org.apache.aries.blueprint.di.ArrayRecipe;
import org.apache.aries.blueprint.di.PassThroughRecipe;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ArrayRecipeTest {
    @Test
    void buildsTypedArrayAndConvertsElements() {
        BlueprintContainerImpl container = RecipeSupport.container();
        ArrayRecipe recipe = new ArrayRecipe("numbers", Integer.class);
        recipe.add(new PassThroughRecipe("first", "17"));
        recipe.add(null);
        recipe.add(new PassThroughRecipe("last", 23));
        Integer[] values = (Integer[]) RecipeSupport.create(container, recipe);
        assertThat(values).containsExactly(17, null, 23);
    }

    @Test
    void buildsPrimitiveArray() {
        BlueprintContainerImpl container = RecipeSupport.container();
        ArrayRecipe recipe = new ArrayRecipe("primitives", int.class);
        recipe.add(new PassThroughRecipe("number", "9"));
        int[] values = (int[]) RecipeSupport.create(container, recipe);
        assertThat(values).containsExactly(9);
    }
}
