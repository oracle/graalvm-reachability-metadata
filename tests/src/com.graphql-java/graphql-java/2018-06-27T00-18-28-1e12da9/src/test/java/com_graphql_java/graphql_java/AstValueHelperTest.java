/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_graphql_java.graphql_java;

import java.math.BigInteger;
import java.util.List;

import graphql.language.AstValueHelper;
import graphql.language.IntValue;
import graphql.language.ObjectField;
import graphql.language.ObjectValue;
import graphql.language.StringValue;
import graphql.language.Value;
import graphql.schema.GraphQLInputObjectType;
import graphql.starwars.Human;
import org.junit.jupiter.api.Test;

import static graphql.Scalars.GraphQLLong;
import static graphql.Scalars.GraphQLString;
import static org.assertj.core.api.Assertions.assertThat;

public class AstValueHelperTest {

  @Test
  void convertsJavaBeanInputObjectToAstObjectValue() {
    GraphQLInputObjectType inputType = GraphQLInputObjectType.newInputObject()
        .name("HumanInput")
        .field(field -> field.name("id").type(GraphQLLong))
        .field(field -> field.name("name").type(GraphQLString))
        .build();

    Value value = AstValueHelper.astFromValue(new Human(), inputType);

    assertThat(value).isInstanceOf(ObjectValue.class);
    ObjectValue objectValue = (ObjectValue) value;
    List<ObjectField> objectFields = objectValue.getObjectFields();

    assertThat(objectFields).hasSize(2);
    assertThat(objectFields.get(0).getName()).isEqualTo("id");
    assertThat(((IntValue) objectFields.get(0).getValue()).getValue()).isEqualTo(BigInteger.valueOf(42));
    assertThat(objectFields.get(1).getName()).isEqualTo("name");
    assertThat(((StringValue) objectFields.get(1).getValue()).getValue()).isEqualTo("GraalVM");
  }
}
