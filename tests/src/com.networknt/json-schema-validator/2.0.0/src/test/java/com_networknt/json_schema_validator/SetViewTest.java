/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_networknt.json_schema_validator;

import static org.assertj.core.api.Assertions.assertThat;

import com.networknt.schema.utils.SetView;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

public class SetViewTest {
    @Test
    void typedToArrayAllocatesArrayWithRequestedComponentTypeWhenInputIsTooSmall() {
        SetView<String> view = new SetView<String>()
                .union(linkedSet("alpha", "beta"))
                .union(linkedSet("gamma"));
        CharSequence[] target = new CharSequence[1];

        CharSequence[] result = view.toArray(target);

        assertThat(result).isNotSameAs(target);
        assertThat(result.getClass().getComponentType()).isEqualTo(CharSequence.class);
        assertThat(result).containsExactly("alpha", "beta", "gamma");
    }

    private static Set<String> linkedSet(String... values) {
        return new LinkedHashSet<>(Arrays.asList(values));
    }
}
