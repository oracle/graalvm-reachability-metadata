/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_graphql.spring_graphql;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.Scalars;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchema;
import org.junit.jupiter.api.Test;

import org.springframework.graphql.data.method.HandlerMethod;
import org.springframework.graphql.data.method.HandlerMethodArgumentResolverComposite;
import org.springframework.graphql.data.method.annotation.support.DataFetcherHandlerMethod;
import org.springframework.graphql.data.method.annotation.support.SourceMethodArgumentResolver;
import org.springframework.graphql.execution.GraphQlSource;

import static org.assertj.core.api.Assertions.assertThat;

public class InvocableHandlerMethodSupportTest {

    @Test
    void invokesHandlerMethodsSynchronouslyAndAsynchronously() throws Exception {
        GraphQlController controller = new GraphQlController();
        HandlerMethodArgumentResolverComposite resolvers = new HandlerMethodArgumentResolverComposite();
        resolvers.addResolver(new SourceMethodArgumentResolver());

        DataFetcherHandlerMethod synchronousHandler = handlerMethod(
                controller, "synchronousGreeting", resolvers, null, false);
        Executor executor = Runnable::run;
        DataFetcherHandlerMethod asynchronousHandler = handlerMethod(
                controller, "asynchronousGreeting", resolvers, executor, true);

        GraphQLObjectType queryType = GraphQLObjectType.newObject()
                .name("Query")
                .field(field("synchronousGreeting", synchronousHandler))
                .field(field("asynchronousGreeting", asynchronousHandler))
                .build();
        GraphQLSchema schema = GraphQLSchema.newSchema().query(queryType).build();
        GraphQlSource source = GraphQlSource.builder(schema).build();

        ExecutionInput input = ExecutionInput.newExecutionInput()
                .query("{ synchronousGreeting asynchronousGreeting }")
                .build();
        ExecutionResult result = source.graphQl().executeAsync(input).get(30, TimeUnit.SECONDS);
        Map<String, Object> data = result.getData();

        assertThat(result.getErrors()).isEmpty();
        assertThat(data).isEqualTo(Map.of(
                "synchronousGreeting", "hello",
                "asynchronousGreeting", "hello asynchronously"));
    }

    private DataFetcherHandlerMethod handlerMethod(
            GraphQlController controller, String methodName,
            HandlerMethodArgumentResolverComposite resolvers, Executor executor, boolean invokeAsync)
            throws NoSuchMethodException {

        Method method = GraphQlController.class.getDeclaredMethod(methodName);
        return new DataFetcherHandlerMethod(
                new HandlerMethod(controller, method), resolvers, null, executor, invokeAsync, false);
    }

    private GraphQLFieldDefinition field(String name, DataFetcherHandlerMethod handlerMethod) {
        return GraphQLFieldDefinition.newFieldDefinition()
                .name(name)
                .type(Scalars.GraphQLString)
                .dataFetcher(environment -> handlerMethod.invoke(environment))
                .build();
    }

}

class GraphQlController {

    public String synchronousGreeting() {
        return "hello";
    }

    public String asynchronousGreeting() {
        return "hello asynchronously";
    }

}
