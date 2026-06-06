/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_vertx.vertx_uri_template;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.uritemplate.ExpandOptions;
import io.vertx.uritemplate.UriTemplate;
import io.vertx.uritemplate.Variables;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.Test;

public class Vertx_uri_templateTest {
    @Test
    void expandsStringVariablesWithSimpleReservedAndFragmentOperators() {
        Variables variables = Variables.variables()
                .set("scheme", "https")
                .set("host", "example.com")
                .set("path", "/a/b c")
                .set("query", "q=vert.x uri")
                .set("fragment", "section/one")
                .set("term", "coffee ☕ and rocket 🚀")
                .set("prefix", "abcdef");

        assertThat(UriTemplate.of("{scheme}://{host}{+path}{?query}{#fragment}").expandToString(variables))
                .isEqualTo("https://example.com/a/b%20c?query=q%3Dvert.x%20uri#section/one");
        assertThat(UriTemplate.of("/search/{term}").expandToString(variables))
                .isEqualTo("/search/coffee%20%E2%98%95%20and%20rocket%20%F0%9F%9A%80");
        assertThat(UriTemplate.of("/prefix/{prefix:3}").expandToString(variables))
                .isEqualTo("/prefix/abc");
    }

    @Test
    void expandsListsAndMapsWithExplodeModifiers() {
        Map<String, String> keys = new LinkedHashMap<>();
        keys.put("semi", ";");
        keys.put("dot", ".");
        keys.put("comma", ",");
        Variables variables = Variables.variables()
                .set("segments", List.of("red", "green", "blue"))
                .set("keys", keys)
                .set("x", "1024")
                .set("y", "768")
                .set("empty", "");

        assertThat(UriTemplate.of("{/segments}").expandToString(variables))
                .isEqualTo("/red,green,blue");
        assertThat(UriTemplate.of("{/segments*}").expandToString(variables))
                .isEqualTo("/red/green/blue");
        assertThat(UriTemplate.of("{?keys}").expandToString(variables))
                .isEqualTo("?keys=semi,%3B,dot,.,comma,%2C");
        assertThat(UriTemplate.of("{?keys*}").expandToString(variables))
                .isEqualTo("?semi=%3B&dot=.&comma=%2C");
        assertThat(UriTemplate.of("{;x,y,empty}").expandToString(variables))
                .isEqualTo(";x=1024;y=768;empty");
    }

    @Test
    void expandsLabelPathAndQueryContinuationOperators() {
        Variables variables = Variables.variables()
                .set("subdomain", "api")
                .set("resource", "orders")
                .set("id", "A/B")
                .set("page", "2")
                .set("filter", "new items");

        String expanded = UriTemplate.of("https://example.com{.subdomain}{/resource,id}?fixed=true{&page,filter}")
                .expandToString(variables);

        assertThat(expanded)
                .isEqualTo("https://example.com.api/orders/A%2FB?fixed=true&page=2&filter=new%20items");
    }

    @Test
    void expandsTemplatesWithPercentEncodedLiteralsAndVariableNames() {
        Variables variables = Variables.variables()
                .set("caf%C3%A9", "au lait")
                .set("path", "already%2Fencoded")
                .set("fragment", "section%201");

        assertThat(UriTemplate.of("/caf%C3%A9/{caf%C3%A9}{?caf%C3%A9}").expandToString(variables))
                .isEqualTo("/caf%C3%A9/au%20lait?caf%C3%A9=au%20lait");
        assertThat(UriTemplate.of("{+path}{#fragment}").expandToString(variables))
                .isEqualTo("already%2Fencoded#section%201");
        assertThat(UriTemplate.of("/{path}").expandToString(variables))
                .isEqualTo("/already%252Fencoded");
    }

    @Test
    void createsAndMutatesVariablesFromJsonObjects() {
        JsonObject json = new JsonObject()
                .put("user", new JsonObject()
                        .put("name", "Ada")
                        .put("address", new JsonObject().put("city", "Paris")))
                .put("colors", new JsonArray().add("red").add("green"))
                .put("count", 3)
                .put("missing", null);
        Variables variables = Variables.variables(json);

        assertThat(variables.names()).containsExactlyInAnyOrder("user", "colors", "count", "missing");
        assertThat(variables.getMap("user"))
                .containsEntry("name", "Ada")
                .containsEntry("address", "{\"city\":\"Paris\"}");
        assertThat(variables.getList("colors")).containsExactly("red", "green");
        assertThat(variables.getSingle("count")).isEqualTo("3");
        assertThat(variables.get("missing")).isNull();

        variables.setAll(new JsonObject().put("path", new JsonArray().add("v1").add("users")));

        assertThat(variables.names()).containsExactly("path");
        assertThat(UriTemplate.of("{/path*}").expandToString(variables)).isEqualTo("/v1/users");
    }

    @Test
    void mergesJsonVariablesIntoExistingCollectionsAndClearsForReuse() {
        Variables variables = Variables.variables()
                .set("tenant", "stale")
                .set("resource", "projects");
        JsonObject update = new JsonObject()
                .put("tenant", "acme")
                .put("id", "42");

        assertThat(variables.addAll(update)).isSameAs(variables);
        assertThat(variables.names()).containsExactlyInAnyOrder("tenant", "resource", "id");
        assertThat(variables.getSingle("tenant")).isEqualTo("acme");
        assertThat(UriTemplate.of("/{tenant}{/resource,id}").expandToString(variables))
                .isEqualTo("/acme/projects/42");

        assertThat(variables.clear()).isSameAs(variables);
        assertThat(variables.names()).isEmpty();
        assertThat(variables.get("tenant")).isNull();
    }

    @Test
    void controlsMissingVariablesWithExpandOptionsAndJsonConversion() {
        UriTemplate template = UriTemplate.of("/users/{user}/orders/{order}");
        Variables variables = Variables.variables().set("user", "alice");

        assertThat(template.expandToString(variables)).isEqualTo("/users/alice/orders/");

        ExpandOptions strictOptions = new ExpandOptions(new JsonObject().put("allowVariableMiss", false));
        assertThat(strictOptions.getAllowVariableMiss()).isFalse();
        assertThat(strictOptions.toJson()).isEqualTo(new JsonObject().put("allowVariableMiss", false));
        assertThat(new ExpandOptions(strictOptions).getAllowVariableMiss()).isFalse();
        ExpandOptions mutableOptions = new ExpandOptions();
        assertThat(mutableOptions.setAllowVariableMiss(false)).isSameAs(mutableOptions);

        assertThatThrownBy(() -> template.expandToString(variables, strictOptions))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("Variable order is missing");
    }

    @Test
    void rejectsInvalidTemplates() {
        assertThatThrownBy(() -> UriTemplate.of("/items/{id"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> UriTemplate.of("/items/{name:0}"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> UriTemplate.of("{=reserved}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid reserved operator");
    }
}
