/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package graphql;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.function.Consumer;

import graphql.introspection.IntrospectionQuery;
import graphql.relay.Connection;
import graphql.relay.DefaultConnection;
import graphql.relay.DefaultConnectionCursor;
import graphql.relay.DefaultEdge;
import graphql.relay.DefaultPageInfo;
import graphql.relay.Edge;
import graphql.relay.PageInfo;
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
    GraphQLSchema graphQLSchema = parseSchema("greeting", runtimeWiringBuilder ->
        runtimeWiringBuilder.type("Query", builder ->
            builder.dataFetcher("greeting", new StaticDataFetcher("Hello GraphQL"))));
    GraphQL graphQl = GraphQL.newGraphQL(graphQLSchema).build();
    ExecutionResult result = graphQl.execute("{ greeting }");
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

  @Test
  void paginatedQuery() throws Exception {
    PageInfo pageInfo = new DefaultPageInfo(new DefaultConnectionCursor("start"),
            new DefaultConnectionCursor("end"), true, true);
    Edge<Human> edge = new DefaultEdge<>(new Human(), new DefaultConnectionCursor("current"));
    Connection<Human> connection = new DefaultConnection<>(List.of(edge), pageInfo);
    GraphQLSchema graphQLSchema = parseSchema("starwars", runtimeWiringBuilder ->
            runtimeWiringBuilder.type("QueryType", builder ->
                    builder.dataFetcher("humans", new StaticDataFetcher(connection)))
                    .type("Character", typeBuilder -> typeBuilder.typeResolver(characterTypeResolver())));
    GraphQL graphQl = GraphQL.newGraphQL(graphQLSchema).build();
    ExecutionResult result = graphQl.execute("{ humans { edges { node { id name } } pageInfo { startCursor } } }");
    assertThat(result.getErrors()).isEmpty();
    assertThat(result.getData().toString()).isEqualTo("{humans={edges=[{node={id=42, name=GraalVM}}], pageInfo={startCursor=start}}}");
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
