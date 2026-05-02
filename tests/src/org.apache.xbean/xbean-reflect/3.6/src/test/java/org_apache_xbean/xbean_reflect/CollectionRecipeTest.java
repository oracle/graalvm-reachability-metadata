/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_xbean.xbean_reflect;

import java.util.ArrayList;
import java.util.List;

import org.apache.xbean.recipe.CollectionRecipe;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class CollectionRecipeTest {
    @Test
    void createsDefaultListImplementationFromRecipeValues() {
        CollectionRecipe recipe = new CollectionRecipe();
        recipe.add("alpha");
        recipe.add("beta");

        Object value = recipe.create(List.class, false);

        List<?> values = (List<?>) value;
        assertThat(value).isInstanceOf(ArrayList.class);
        assertThat(values).hasSize(2);
        assertThat(values.get(0)).isEqualTo("alpha");
        assertThat(values.get(1)).isEqualTo("beta");
    }

    @Test
    void createsRequestedCollectionImplementationFromDefaultConstructor() {
        CollectionRecipe recipe = new CollectionRecipe(RecordingCollection.class);
        recipe.add("first");
        recipe.add("second");

        Object value = recipe.create(RecordingCollection.class, false);

        assertThat(value).isInstanceOf(RecordingCollection.class);
        assertThat((RecordingCollection) value).containsExactly("first", "second");
    }

    public static class RecordingCollection extends ArrayList<String> {
        public RecordingCollection() {
            super();
        }
    }
}
