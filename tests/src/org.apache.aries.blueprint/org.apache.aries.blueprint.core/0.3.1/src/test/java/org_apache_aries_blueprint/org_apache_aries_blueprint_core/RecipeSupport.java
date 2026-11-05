/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_aries_blueprint.org_apache_aries_blueprint_core;

import java.util.List;

import org.apache.aries.blueprint.container.BlueprintContainerImpl;
import org.apache.aries.blueprint.di.Recipe;

final class RecipeSupport {
    private RecipeSupport() {
    }

    static BlueprintContainerImpl container() {
        return new BlueprintContainerImpl(null, null, null, null, null, List.of());
    }

    static Object create(BlueprintContainerImpl container, Recipe recipe) {
        container.getRepository().putRecipe(recipe.getName(), recipe);
        return container.getRepository().create(recipe.getName());
    }
}
