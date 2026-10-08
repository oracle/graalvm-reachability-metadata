/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_graphql.spring_graphql;

import java.lang.reflect.Method;
import java.util.Map;

import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.Scalars;
import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchema;
import org.junit.jupiter.api.Test;

import org.springframework.graphql.data.GraphQlArgumentBinder;
import org.springframework.graphql.data.method.HandlerMethod;
import org.springframework.graphql.data.method.HandlerMethodArgumentResolverComposite;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.support.ArgumentMethodArgumentResolver;
import org.springframework.graphql.data.method.annotation.support.DataFetcherHandlerMethod;

import static org.assertj.core.api.Assertions.assertThat;

public class HandlerMethodTest {

    @Test
    void resolvesArgumentsDeclaredOnHandlerInterface() throws Exception {
        InterfaceHandler handler = new InterfaceHandler();
        Method method = InterfaceHandler.class.getDeclaredMethod("greet", String.class);
        HandlerMethodArgumentResolverComposite resolvers = new HandlerMethodArgumentResolverComposite();
        resolvers.addResolver(new ArgumentMethodArgumentResolver(new GraphQlArgumentBinder()));
        DataFetcherHandlerMethod dataFetcher = new DataFetcherHandlerMethod(
                new HandlerMethod(handler, method), resolvers, null, null, false, false);

        GraphQLFieldDefinition greeting = GraphQLFieldDefinition.newFieldDefinition()
                .name("greet")
                .type(Scalars.GraphQLString)
                .argument(GraphQLArgument.newArgument()
                        .name("name")
                        .type(Scalars.GraphQLString)
                        .build())
                .dataFetcher(environment -> dataFetcher.invoke(environment))
                .build();
        GraphQLObjectType query = GraphQLObjectType.newObject()
                .name("Query")
                .field(greeting)
                .build();
        GraphQLSchema schema = GraphQLSchema.newSchema().query(query).build();

        ExecutionResult result = GraphQL.newGraphQL(schema).build()
                .execute("""
                        { greet(name: "Ada") }
                        """);

        Map<String, Object> data = result.getData();
        assertThat(result.getErrors()).isEmpty();
        assertThat(data).isEqualTo(Map.of("greet", "Hello, Ada"));
    }

}

interface InterfaceGreeting {

    String greet(@Argument(name = "name") String name);

}

class InterfaceHandler implements InterfaceGreeting {

    @Override
    public String greet(String name) {
        return "Hello, " + name;
    }

}
