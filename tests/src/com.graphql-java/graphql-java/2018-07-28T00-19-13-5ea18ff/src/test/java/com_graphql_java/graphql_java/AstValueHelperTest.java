/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_graphql_java.graphql_java;

import graphql.schema.GraphQLInputObjectType;
import graphql.schema.idl.SchemaPrinter;
import org.junit.jupiter.api.Test;

import static graphql.Scalars.GraphQLString;
import static org.assertj.core.api.Assertions.assertThat;

public class AstValueHelperTest {

  @Test
  void printsInputObjectDefaultValueFromJavaBeanProperties() {
    GraphQLInputObjectType addressInput = GraphQLInputObjectType.newInputObject()
        .name("AddressInput")
        .field(field -> field.name("city").type(GraphQLString))
        .field(field -> field.name("street").type(GraphQLString))
        .build();
    GraphQLInputObjectType personInput = GraphQLInputObjectType.newInputObject()
        .name("PersonInput")
        .field(field -> field.name("home").type(addressInput).defaultValue(new AddressInput("London", "Baker Street")))
        .build();

    String schema = new SchemaPrinter().print(personInput);

    assertThat(schema).contains("home: AddressInput = {city : \"London\", street : \"Baker Street\"}");
  }

  public static class AddressInput {

    private final String city;
    private final String street;

    public AddressInput(String city, String street) {
      this.city = city;
      this.street = street;
    }

    public String getCity() {
      return city;
    }

    public String getStreet() {
      return street;
    }
  }
}
