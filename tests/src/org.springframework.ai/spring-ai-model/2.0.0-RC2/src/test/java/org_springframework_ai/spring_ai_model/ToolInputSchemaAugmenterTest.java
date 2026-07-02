/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_ai.spring_ai_model;

import java.lang.reflect.Type;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.tool.augment.ToolInputSchemaAugmenter;
import org.springframework.ai.tool.augment.ToolInputSchemaAugmenter.AugmentedArgumentType;

import static org.assertj.core.api.Assertions.assertThat;

public class ToolInputSchemaAugmenterTest {

    @Test
    void extractsRecordComponentTypesAndFieldToolParamMetadata() {
        List<AugmentedArgumentType> arguments = ToolInputSchemaAugmenter.toAugmentedArgumentTypes(
                SearchArguments.class);

        assertThat(arguments)
                .extracting(AugmentedArgumentType::name)
                .containsExactly("query", "limit", "includeArchived");
        assertArgument(arguments.get(0), String.class, "Search text", true);
        assertArgument(arguments.get(1), Integer.class, "Maximum result count", false);
        assertArgument(arguments.get(2), boolean.class, "no description", false);
    }

    private static void assertArgument(AugmentedArgumentType argument, Type type,
            String description, boolean required) {
        assertThat(argument.type()).isEqualTo(type);
        assertThat(argument.description()).isEqualTo(description);
        assertThat(argument.required()).isEqualTo(required);
    }

    private record SearchArguments(
            @ToolParam(description = "Search text") String query,
            @ToolParam(description = "Maximum result count", required = false) Integer limit,
            boolean includeArchived) {
    }

}
