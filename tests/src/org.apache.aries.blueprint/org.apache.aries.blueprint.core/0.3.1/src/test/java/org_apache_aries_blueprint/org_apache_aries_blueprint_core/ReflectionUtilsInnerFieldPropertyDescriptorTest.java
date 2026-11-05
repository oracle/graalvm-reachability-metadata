/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_aries_blueprint.org_apache_aries_blueprint_core;

import org.apache.aries.blueprint.container.BeanRecipe;
import org.apache.aries.blueprint.container.BlueprintContainerImpl;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ReflectionUtilsInnerFieldPropertyDescriptorTest {
    @Test
    void readsNestedFieldsAndInjectsConvertedValues() {
        BlueprintContainerImpl container = RecipeSupport.container();
        BeanRecipe recipe = new BeanRecipe("fieldSettings", container, Settings.class, true);
        recipe.setProperty("limits.maximum", "42");
        Settings settings = (Settings) RecipeSupport.create(container, recipe);
        assertThat(settings.limits().maximum()).isEqualTo(42);
    }

    public static class Settings {
        private Limits limits = new Limits();

        public Limits limits() {
            return limits;
        }
    }

    public static class Limits {
        private int maximum = 1;

        public int maximum() {
            return maximum;
        }
    }
}
