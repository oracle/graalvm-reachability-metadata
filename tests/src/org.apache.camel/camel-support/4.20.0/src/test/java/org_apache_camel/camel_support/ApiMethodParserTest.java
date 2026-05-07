/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_camel.camel_support;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.support.component.ApiMethodArg;
import org.apache.camel.support.component.ApiMethodParser;
import org.apache.camel.support.component.ApiMethodParser.ApiMethodModel;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ApiMethodParserTest {
    @Test
    void parseResolvesJavaLangArraysInnerClassesAndProxyMethod() {
        String signature = "String combine(String[] names, " + InnerParameter.class.getCanonicalName() + " parameter)";
        TestApiMethodParser parser = new TestApiMethodParser();
        parser.setSignatures(List.of(signature));

        Map<String, String> arguments = new LinkedHashMap<>();
        arguments.put("names", "String[]");
        arguments.put("parameter", InnerParameter.class.getCanonicalName());
        parser.addSignatureArguments(signature, arguments);

        List<ApiMethodModel> models = parser.parse();

        assertThat(models).hasSize(1);
        ApiMethodModel model = models.get(0);
        assertThat(model.getName()).isEqualTo("combine");
        assertThat(model.getResultType()).isEqualTo(String.class);
        assertThat(model.getUniqueName()).isEqualTo("COMBINE");
        assertThat(model.getMethod().getName()).isEqualTo("combine");
        assertThat(model.getMethod().getParameterCount()).isEqualTo(2);
        assertThat(model.getSignature()).isEqualTo(signature);

        assertThat(model.getArguments())
                .extracting(ApiMethodArg::getName)
                .containsExactly("names", "parameter");
        assertThat(model.getArguments())
                .extracting(ApiMethodArg::getType)
                .containsExactly(String[].class, InnerParameter.class);
    }

    public interface SampleApi {
        String combine(String[] names, InnerParameter parameter);
    }

    public static final class InnerParameter {
    }

    private static final class TestApiMethodParser extends ApiMethodParser<SampleApi> {
        private TestApiMethodParser() {
            super(SampleApi.class);
        }
    }
}
