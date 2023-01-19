/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package graphql;

import java.io.IOException;
import java.io.InputStream;
import java.util.function.Consumer;

import graphql.introspection.IntrospectionQuery;
import graphql.schema.GraphQLSchema;
import graphql.schema.StaticDataFetcher;
import graphql.schema.TypeResolver;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import graphql.starwars.Droid;
import graphql.starwars.Human;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Brian Clozel
 */
public class GraphQlTests {


  @Test
  void greetingQuery() throws Exception {
    assertThat(result.getErrors()).isEmpty();
    assertThat(result.getData().toString()).isEqualTo("{greeting=Hello GraphQL}");
  }

  @Test
  void introspectionQuery() throws Exception {
    GraphQLSchema graphQLSchema = parseSchema("starwars", runtimeWiringBuilder -> {
      runtimeWiringBuilder.type("Query", builder -> builder.typeName("Character").typeResolver(characterTypeResolver()));
    });
    GraphQL graphQl = GraphQL.newGraphQL(graphQLSchema).build();
    ExecutionResult result = graphQl.execute(IntrospectionQuery.INTROSPECTION_QUERY);
    assertThat(result.getErrors()).isEmpty();
    assertThat(result.getData().toString()).contains("{__schema={queryType={name=QueryType}");
  }

  private GraphQLSchema parseSchema(String schemaFileName, Consumer<RuntimeWiring.Builder> consumer) throws IOException {
    try (InputStream inputStream = GraphQlTests.class.getResourceAsStream(schemaFileName + ".graphqls")) {
      TypeDefinitionRegistry registry = new SchemaParser().parse(inputStream);
      RuntimeWiring.Builder runtimeWiringBuilder = RuntimeWiring.newRuntimeWiring();
      consumer.accept(runtimeWiringBuilder);
      return new SchemaGenerator().makeExecutableSchema(registry, runtimeWiringBuilder.build());
    }
  }

  private TypeResolver characterTypeResolver() {
    return env -> {
      // test
      Object javaObject = env.getObject();
      if (javaObject instanceof Droid) {
        return env.getSchema().getObjectType("Droid");
      } else if (javaObject instanceof Human) {
        return env.getSchema().getObjectType("Human");
      } else {
        throw new IllegalStateException("Unknown Character type");
      }
    };
  }

}
