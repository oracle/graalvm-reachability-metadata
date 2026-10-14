/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_auto_value.auto_value;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class TypeSpecInnerBuilderTest {
    @Test
    void emitsNestedBuilderTypesWithoutNameClashes() throws Exception {
        ProcessorCompilation.Result result = ProcessorCompilation.result();

        assertThat(result.successful()).as(result.diagnostics()).isTrue();
        assertThat(result.generatedSource("AutoValue_Person"))
                .content()
                .contains("extends Person", "class Builder extends Person.Builder");
    }
}
