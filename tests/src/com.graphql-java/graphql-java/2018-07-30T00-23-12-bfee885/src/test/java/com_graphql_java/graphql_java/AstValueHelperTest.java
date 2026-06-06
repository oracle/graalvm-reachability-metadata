/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_graphql_java.graphql_java;

import graphql.language.AstValueHelper;
import graphql.language.IntValue;
import graphql.language.ObjectField;
import graphql.language.ObjectValue;
import graphql.language.StringValue;
import graphql.language.Value;
import graphql.schema.GraphQLInputObjectType;
import java.math.BigInteger;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

import static graphql.Scalars.GraphQLInt;
import static graphql.Scalars.GraphQLString;
import static graphql.schema.GraphQLInputObjectField.newInputObjectField;
import static graphql.schema.GraphQLInputObjectType.newInputObject;
import static org.assertj.core.api.Assertions.assertThat;

public class AstValueHelperTest {

  @Test
  void convertsJavaBeanInputObjectIntoAstObjectValue() {
    GraphQLInputObjectType inputType = newInputObject()
        .name("BookInput")
        .field(newInputObjectField().name("title").type(GraphQLString))
        .field(newInputObjectField().name("pages").type(GraphQLInt))
        .build();
    BookInputValue inputValue = new BookInputValue("Native GraphQL", 321);

    Value value = AstValueHelper.astFromValue(inputValue, inputType);

    assertThat(value).isInstanceOf(ObjectValue.class);
    Map<String, ObjectField> fieldsByName = ((ObjectValue) value).getObjectFields().stream()
        .collect(Collectors.toMap(ObjectField::getName, Function.identity()));
    assertThat(fieldsByName).containsOnlyKeys("title", "pages");
    StringValue titleValue = (StringValue) fieldsByName.get("title").getValue();
    IntValue pagesValue = (IntValue) fieldsByName.get("pages").getValue();
    assertThat(titleValue.getValue()).isEqualTo("Native GraphQL");
    assertThat(pagesValue.getValue()).isEqualTo(BigInteger.valueOf(321));
  }

  public static class BookInputValue {

    private final String title;
    private final int pages;

    public BookInputValue(String title, int pages) {
      this.title = title;
      this.pages = pages;
    }

    public String getTitle() {
      return title;
    }

    public int getPages() {
      return pages;
    }
  }
}
