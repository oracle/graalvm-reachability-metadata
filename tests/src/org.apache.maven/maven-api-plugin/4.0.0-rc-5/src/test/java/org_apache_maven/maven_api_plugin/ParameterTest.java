/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven.maven_api_plugin;

import org.apache.maven.api.plugin.descriptor.Parameter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ParameterTest {
    @Test
    void describesConfigurableParameterAndDerivesReadOnlyCopy() {
        Parameter parameter = Parameter.newBuilder()
                .name("outputDirectory")
                .alias("output")
                .type("java.io.File")
                .required(true)
                .description("Directory for generated sources")
                .expression("${sample.output}")
                .defaultValue("${project.build.directory}/generated-sources")
                .build();

        assertThat(parameter.getName()).isEqualTo("outputDirectory");
        assertThat(parameter.getAlias()).isEqualTo("output");
        assertThat(parameter.getType()).isEqualTo("java.io.File");
        assertThat(parameter.isRequired()).isTrue();
        assertThat(parameter.isEditable()).isTrue();
        assertThat(parameter.getDescription()).isEqualTo("Directory for generated sources");
        assertThat(parameter.getExpression()).isEqualTo("${sample.output}");
        assertThat(parameter.getDefaultValue()).isEqualTo("${project.build.directory}/generated-sources");

        Parameter readOnly = parameter.withEditable(false);
        Parameter optional = parameter.with().required(false).alias("destination").build();
        assertThat(readOnly.isEditable()).isFalse();
        assertThat(readOnly.getName()).isEqualTo(parameter.getName());
        assertThat(readOnly.getExpression()).isEqualTo(parameter.getExpression());
        assertThat(readOnly.getDefaultValue()).isEqualTo(parameter.getDefaultValue());
        assertThat(optional.isRequired()).isFalse();
        assertThat(optional.getAlias()).isEqualTo("destination");
        assertThat(optional.getType()).isEqualTo(parameter.getType());
        assertThat(parameter.isEditable()).isTrue();
        assertThat(parameter.isRequired()).isTrue();
        assertThat(parameter.getAlias()).isEqualTo("output");
    }
}
