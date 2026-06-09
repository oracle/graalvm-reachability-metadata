/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_camel.camel_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Date;
import java.util.List;

import org.apache.camel.util.component.ArgumentSubstitutionParser;
import org.apache.camel.util.component.ApiMethodParser.ApiMethodModel;
import org.apache.camel.util.component.ApiMethodParser.Argument;
import org.junit.jupiter.api.Test;

public class ApiMethodParserTest {
    @Test
    void parsesSignaturesAndResolvesJavaLangArrayAndFullyQualifiedTypes() {
        ArgumentSubstitutionParser<ExampleApi> parser = new ArgumentSubstitutionParser<ExampleApi>(
                ExampleApi.class, new ArgumentSubstitutionParser.Substitution[0]);
        parser.setSignatures(Arrays.asList(
                "public String join(String[] values, java.util.Date date)",
                "java.util.Date createdAt()"));

        List<ApiMethodModel> models = parser.parse();

        assertThat(models).extracting(ApiMethodModel::getName).containsExactly("createdAt", "join");

        ApiMethodModel createdAt = models.get(0);
        assertThat(createdAt.getUniqueName()).isEqualTo("CREATEDAT");
        assertThat(createdAt.getResultType()).isEqualTo(Date.class);
        assertThat(createdAt.getMethod().getReturnType()).isEqualTo(Date.class);
        assertThat(createdAt.getArguments()).isEmpty();

        ApiMethodModel join = models.get(1);
        assertThat(join.getUniqueName()).isEqualTo("JOIN");
        assertThat(join.getResultType()).isEqualTo(String.class);
        assertThat(join.getMethod().getParameterTypes()).containsExactly(String[].class, Date.class);
        assertThat(join.getArguments()).extracting(Argument::getName).containsExactly("values", "date");
        assertThat(join.getArguments()).extracting(Argument::getType).containsExactly(String[].class, Date.class);
    }

    public interface ExampleApi {
        String join(String[] values, Date date);

        Date createdAt();
    }
}
