/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_graphql_java.graphql_java;

import com_graphql_java.graphql_java.starwars.Human;
import graphql.schema.GraphQLInputObjectType;
import graphql.schema.idl.SchemaPrinter;
import org.junit.jupiter.api.Test;

import static graphql.Scalars.GraphQLLong;
import static graphql.Scalars.GraphQLString;
import static org.assertj.core.api.Assertions.assertThat;

public class AstValueHelperTest {

  @Test
  void printsJavaBeanAsInputObjectDefaultValue() {
    GraphQLInputObjectType humanInput = GraphQLInputObjectType.newInputObject()
        .name("HumanInput")
        .field(field -> field.name("id").type(GraphQLLong))
        .field(field -> field.name("name").type(GraphQLString))
        .build();
    GraphQLInputObjectType searchInput = GraphQLInputObjectType.newInputObject()
        .name("SearchInput")
        .field(field -> field.name("human").type(humanInput).defaultValue(new Human()))
        .build();

    String schema = new SchemaPrinter().print(searchInput);

    assertThat(schema).contains("human: HumanInput = {id : 42, name : \"GraalVM\"}");
  }
}
