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

public class ObjectRecipeInnerFieldMemberTest {
    @Test
    void setsPublicFieldValueThroughFieldMember() {
        ObjectRecipe recipe = new ObjectRecipe(FieldInjectedTarget.class);
        recipe.setFieldProperty("name", "configured through field injection");

        FieldInjectedTarget target = (FieldInjectedTarget) recipe.create();

        assertThat(target.name).isEqualTo("configured through field injection");
    }

    public static class FieldInjectedTarget {
        public String name;

        public FieldInjectedTarget() {
        }
    }
}
