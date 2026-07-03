/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_ai.spring_ai_model;

import java.util.function.Function;

import org.junit.jupiter.api.Test;

import org.springframework.ai.tool.resolution.TypeResolverHelper;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.ResolvableType;

import static org.assertj.core.api.Assertions.assertThat;

public class TypeResolverHelperTest {

    @Test
    void resolvesPublicFactoryMethodTypeWhenNonPublicAccessIsDisabled() {
        try (GenericApplicationContext applicationContext = new GenericApplicationContext()) {
            applicationContext.registerBeanDefinition("toolFunctionFactory",
                    new RootBeanDefinition(ToolFunctionFactory.class));
            RootBeanDefinition beanDefinition = new RootBeanDefinition();
            beanDefinition.setFactoryBeanName("toolFunctionFactory");
            beanDefinition.setFactoryMethodName("searchFunction");
            beanDefinition.setNonPublicAccessAllowed(false);
            applicationContext.registerBeanDefinition("searchFunction", beanDefinition);

            ResolvableType resolvedType = TypeResolverHelper.resolveBeanType(applicationContext, "searchFunction");

            assertThat(resolvedType.resolve()).isEqualTo(Function.class);
            assertThat(resolvedType.getGeneric(0).resolve()).isEqualTo(SearchRequest.class);
            assertThat(resolvedType.getGeneric(1).resolve()).isEqualTo(SearchResponse.class);
        }
    }

    public static final class ToolFunctionFactory {

        public Function<SearchRequest, SearchResponse> searchFunction() {
            return request -> new SearchResponse("Result for " + request.query());
        }

    }

    public record SearchRequest(String query) {
    }

    public record SearchResponse(String answer) {
    }

}
