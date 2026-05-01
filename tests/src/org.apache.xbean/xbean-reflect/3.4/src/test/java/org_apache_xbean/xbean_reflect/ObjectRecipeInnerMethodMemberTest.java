/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_xbean.xbean_reflect;

import org.apache.xbean.recipe.ObjectRecipe;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ObjectRecipeInnerMethodMemberTest {
    @Test
    void setsPropertyValueThroughSetterMethodMember() {
        ObjectRecipe recipe = new ObjectRecipe(SetterInjectedTarget.class);
        recipe.setMethodProperty("name", "configured through setter injection");

        SetterInjectedTarget target = (SetterInjectedTarget) recipe.create();

        assertThat(target.getName()).isEqualTo("configured through setter injection");
    }

    public static class SetterInjectedTarget {
        private String name;

        public SetterInjectedTarget() {
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }
}
