/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_aries_blueprint.org_apache_aries_blueprint_core;

import java.util.Collection;
import java.util.LinkedList;
import java.util.SortedSet;
import java.util.TreeSet;

import org.apache.aries.blueprint.container.BlueprintContainerImpl;
import org.apache.aries.blueprint.di.CollectionRecipe;
import org.apache.aries.blueprint.di.PassThroughRecipe;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class CollectionRecipeTest {
    @Test
    void instantiatesRequestedConcreteCollection() {
        BlueprintContainerImpl container = RecipeSupport.container();
        CollectionRecipe recipe = new CollectionRecipe("tasks", LinkedList.class);
        recipe.add(new PassThroughRecipe("task", "process"));
        recipe.add(null);
        Collection<?> values = (Collection<?>) RecipeSupport.create(container, recipe);
        assertThat(values).isInstanceOf(LinkedList.class);
        assertThat(values.toArray()).containsExactly("process", null);
    }

    @Test
    void resolvesSortedSetInterfaceAndDeduplicatesEntries() {
        BlueprintContainerImpl container = RecipeSupport.container();
        CollectionRecipe recipe = new CollectionRecipe("sorted", SortedSet.class);
        recipe.add(new PassThroughRecipe("high", "z"));
        recipe.add(new PassThroughRecipe("low", "a"));
        recipe.add(new PassThroughRecipe("duplicate", "z"));
        Collection<?> values = (Collection<?>) RecipeSupport.create(container, recipe);
        assertThat(values).isInstanceOf(TreeSet.class);
        assertThat(values.toArray()).containsExactly("a", "z");
    }
}
