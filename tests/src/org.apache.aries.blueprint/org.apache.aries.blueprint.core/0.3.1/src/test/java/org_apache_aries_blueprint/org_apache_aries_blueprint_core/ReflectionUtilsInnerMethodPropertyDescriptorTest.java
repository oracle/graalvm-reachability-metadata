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

public class ReflectionUtilsInnerMethodPropertyDescriptorTest {
    @Test
    void followsBeanGettersAndConvertsSetterArguments() {
        BlueprintContainerImpl container = RecipeSupport.container();
        BeanRecipe recipe = new BeanRecipe("methodSettings", container, Settings.class, false);
        recipe.setProperty("limits.maximum", "64");
        Settings settings = (Settings) RecipeSupport.create(container, recipe);
        assertThat(settings.getLimits().getMaximum()).isEqualTo(64);
        assertThat(settings.getLimits().updates).isEqualTo(1);
    }

    public static class Settings {
        private final Limits limits = new Limits();

        public Limits getLimits() {
            return limits;
        }
    }

    public static class Limits {
        private int maximum;
        private int updates;

        public int getMaximum() {
            return maximum;
        }

        public void setMaximum(int maximum) {
            this.maximum = maximum;
            updates++;
        }
    }
}
