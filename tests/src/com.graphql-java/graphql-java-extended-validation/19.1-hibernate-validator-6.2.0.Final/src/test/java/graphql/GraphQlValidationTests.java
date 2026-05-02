/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package graphql;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.function.Consumer;

import graphql.schema.GraphQLSchema;
import graphql.schema.StaticDataFetcher;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import graphql.validation.rules.OnValidationErrorStrategy;
import graphql.validation.rules.ValidationRules;
import graphql.validation.schemawiring.ValidationSchemaWiring;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Brian Clozel
 */
public class GraphQlValidationTests {


  @Test
  void validationErrorQuery() throws Exception {
    GraphQLSchema graphQLSchema = parseSchema("validation", runtimeWiringBuilder ->
        runtimeWiringBuilder.type("Query", builder ->
            builder.dataFetcher("hired", new StaticDataFetcher(true))));
    GraphQL graphQl = GraphQL.newGraphQL(graphQLSchema).build();
    ExecutionResult result = graphQl.execute("{ hired(application: {name: \"p\"}) }");
    assertThat(result.getErrors()).isNotEmpty();
    assertThat(result.getErrors().get(0).getMessage()).isEqualTo("/hired/application/name size must be between 3 and 100");
  }

  @Test
  void validationErrorQueryInFrench() throws Exception {
    GraphQLSchema graphQLSchema = parseSchema("validation", runtimeWiringBuilder ->
        runtimeWiringBuilder.type("Query", builder ->
            builder.dataFetcher("hired", new StaticDataFetcher(true))));
    GraphQL graphQl = GraphQL.newGraphQL(graphQLSchema).build();
    ExecutionResult result = graphQl.execute(ExecutionInput.newExecutionInput("{ hired(application: {name: \"p\"}) }").locale(Locale.FRENCH).build());
    assertThat(result.getErrors()).isNotEmpty();
    assertThat(result.getErrors().get(0).getMessage()).isEqualTo("/hired/application/name la taille doit etre comprise entre 3 et 100");
  }

  private GraphQLSchema parseSchema(String schemaFileName, Consumer<RuntimeWiring.Builder> consumer) throws IOException {
    try (InputStream inputStream = GraphQlValidationTests.class.getResourceAsStream(schemaFileName + ".graphqls")) {
      TypeDefinitionRegistry registry = new SchemaParser().parse(inputStream);
    ValidationRules validationRules = ValidationRules.newValidationRules()
        .onValidationErrorStrategy(OnValidationErrorStrategy.RETURN_NULL)
        .build();
    ValidationSchemaWiring schemaWiring = new ValidationSchemaWiring(validationRules);
      RuntimeWiring.Builder runtimeWiringBuilder = RuntimeWiring.newRuntimeWiring();
    runtimeWiringBuilder.directiveWiring(schemaWiring);
      consumer.accept(runtimeWiringBuilder);
      return new SchemaGenerator().makeExecutableSchema(registry, runtimeWiringBuilder.build());
    }
  }

}
