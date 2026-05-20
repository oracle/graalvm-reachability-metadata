/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_swagger_core_v3.swagger_models_jakarta;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.SpecVersion;
import io.swagger.v3.oas.models.callbacks.Callback;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.links.Link;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.BooleanSchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.DateSchema;
import io.swagger.v3.oas.models.media.Discriminator;
import io.swagger.v3.oas.models.media.EmailSchema;
import io.swagger.v3.oas.models.media.Encoding;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MapSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.NumberSchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.media.UUIDSchema;
import io.swagger.v3.oas.models.media.XML;
import io.swagger.v3.oas.models.parameters.CookieParameter;
import io.swagger.v3.oas.models.parameters.HeaderParameter;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.PathParameter;
import io.swagger.v3.oas.models.parameters.QueryParameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.OAuthFlow;
import io.swagger.v3.oas.models.security.OAuthFlows;
import io.swagger.v3.oas.models.security.Scopes;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.servers.ServerVariable;
import io.swagger.v3.oas.models.servers.ServerVariables;
import io.swagger.v3.oas.models.tags.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class Swagger_models_jakartaTest {
    @Test
    void buildsOpenApiDocumentWithPathsOperationsAndReusableComponents() {
        Info info = new Info()
                .title("Store API")
                .summary("OpenAPI model exercise")
                .description("Operations for orders")
                .termsOfService("https://example.test/terms")
                .contact(new Contact()
                        .name("API Support")
                        .url("https://example.test/support")
                        .email("support@example.test"))
                .license(new License()
                        .name("Apache-2.0")
                        .identifier("Apache-2.0")
                        .url("https://www.apache.org/licenses/LICENSE-2.0"))
                .version("v1");
        info.addExtension("x-info", Map.of("audience", "partners"));
        info.addExtension("not-an-extension", "ignored");

        ServerVariable region = new ServerVariable()
                ._default("us")
                .description("deployment region")
                .addEnumItem("us")
                .addEnumItem("eu");
        Server server = new Server()
                .url("https://{region}.api.example.test")
                .description("regional endpoint")
                .variables(new ServerVariables().addServerVariable("region", region));

        SecurityScheme oauth = new SecurityScheme()
                .type(SecurityScheme.Type.OAUTH2)
                .description("OAuth2 client credentials")
                .flows(new OAuthFlows().clientCredentials(new OAuthFlow()
                        .tokenUrl("https://auth.example.test/token")
                        .refreshUrl("https://auth.example.test/refresh")
                        .scopes(new Scopes().addString("orders:read", "read orders"))));
        SecurityRequirement securityRequirement = new SecurityRequirement()
                .addList("oauth", List.of("orders:read"));

        Schema<?> orderSchema = orderSchema();
        Parameter limit = new QueryParameter()
                .name("limit")
                .description("maximum number of orders")
                .schema(new IntegerSchema().minimum(BigDecimal.ONE).maximum(BigDecimal.valueOf(100)))
                .style(Parameter.StyleEnum.FORM)
                .explode(false);
        RequestBody requestBody = new RequestBody()
                .description("order payload")
                .required(true)
                .content(jsonContent(orderSchema));
        ApiResponse created = new ApiResponse()
                .description("created")
                .content(jsonContent(orderSchema))
                .addHeaderObject("Location", new Header()
                        .description("created order URI")
                        .schema(new StringSchema()))
                .link("self", new Link()
                        .operationId("getOrder")
                        .addParameter("id", "$response.body#/id"));
        ApiResponses responses = new ApiResponses()
                .addApiResponse("201", created)
                ._default(new ApiResponse().description("unexpected error"));
        responses.addExtension("x-response-group", "write");

        Operation createOrder = new Operation()
                .summary("Create order")
                .description("Creates a new order")
                .operationId("createOrder")
                .addTagsItem("orders")
                .addParametersItem(limit)
                .requestBody(requestBody)
                .responses(responses)
                .addSecurityItem(securityRequirement)
                .addServersItem(server);
        PathItem pathItem = new PathItem()
                .summary("Orders")
                .description("Order collection")
                .post(createOrder)
                .addParametersItem(new HeaderParameter().name("X-Request-Id").schema(new UUIDSchema()))
                .addServersItem(server);
        Paths paths = new Paths().addPathItem("/orders", pathItem);
        paths.addExtension("x-paths-owner", "commerce");

        OpenAPI openAPI = new OpenAPI(SpecVersion.V31)
                .openapi("3.1.0")
                .info(info)
                .externalDocs(new ExternalDocumentation()
                        .description("API guide")
                        .url("https://example.test/docs"))
                .addServersItem(server)
                .addSecurityItem(securityRequirement)
                .addTagsItem(new Tag()
                        .name("orders")
                        .description("Order operations")
                        .externalDocs(new ExternalDocumentation().url("https://example.test/tags/orders")))
                .paths(paths)
                .schema("Order", orderSchema)
                .schemaRequirement("oauth", oauth)
                .jsonSchemaDialect("https://json-schema.org/draft/2020-12/schema")
                .addWebhooks("orderCreated", new PathItem().post(new Operation().operationId("onOrderCreated")));
        openAPI.addExtension("x-service", "store");
        openAPI.addExtension31("x-oas-internal", "ignored by OpenAPI 3.1");

        assertThat(openAPI.getInfo().getContact().getEmail()).isEqualTo("support@example.test");
        assertThat(openAPI.getInfo().getExtensions()).containsEntry("x-info", Map.of("audience", "partners"));
        assertThat(openAPI.getInfo().getExtensions()).doesNotContainKey("not-an-extension");
        assertThat(openAPI.getServers()).containsExactly(server);
        assertThat(openAPI.getServers().get(0).getVariables().get("region").getEnum()).containsExactly("us", "eu");
        assertThat(openAPI.getPaths().get("/orders").getPost().getParameters()).containsExactly(limit);
        assertThat(openAPI.getPaths().getExtensions()).containsEntry("x-paths-owner", "commerce");
        assertThat(openAPI.getComponents().getSchemas()).containsEntry("Order", orderSchema);
        assertThat(openAPI.getComponents().getSecuritySchemes()).containsEntry("oauth", oauth);
        assertThat(openAPI.getSecurity().get(0)).containsEntry("oauth", List.of("orders:read"));
        assertThat(openAPI.getWebhooks()).containsKey("orderCreated");
        assertThat(openAPI.getExtensions()).containsEntry("x-service", "store");
        assertThat(openAPI.getExtensions()).doesNotContainKey("x-oas-internal");
        assertThat(openAPI.toString())
                .contains("class OpenAPI")
                .contains("jsonSchemaDialect: https://json-schema.org/draft/2020-12/schema");
    }

    @Test
    void schemaModelsSupportCompositionValidationMetadataAndOpenApi31Keywords() {
        StringSchema statusSchema = new StringSchema()
                ._default("pending")
                .addEnumItem("pending")
                .addEnumItem("paid");
        statusSchema.description("order state").minLength(4).maxLength(20).pattern("^[a-z]+$");

        Schema<?> moneySchema = new NumberSchema()
                .multipleOf(new BigDecimal("0.01"))
                .minimum(BigDecimal.ZERO)
                .exclusiveMaximumValue(new BigDecimal("1000000"));
        moneySchema.title("Money").description("decimal currency amount");

        Schema<?> lineItem = new ObjectSchema()
                .addProperty("sku", new StringSchema())
                .addProperty("quantity", new IntegerSchema().minimum(BigDecimal.ONE))
                .addRequiredItem("sku")
                .addRequiredItem("quantity");
        ArraySchema items = new ArraySchema().items(lineItem);
        items.minItems(1).uniqueItems(true).contains(lineItem).minContains(1).maxContains(100);

        Schema<?> shippingAddress = new ObjectSchema()
                .addProperty("street", new StringSchema())
                .addProperty("country", new StringSchema().addEnumItem("US").addEnumItem("DE"))
                .addRequiredItem("country");
        Schema<?> cardPayment = new ObjectSchema()
                .name("CardPayment")
                .addProperty("kind", new StringSchema()._default("card"))
                .addProperty("last4", new StringSchema().minLength(4).maxLength(4));
        Schema<?> bankPayment = new ObjectSchema()
                .name("BankPayment")
                .addProperty("kind", new StringSchema()._default("bank"))
                .addProperty("iban", new StringSchema());

        Discriminator discriminator = new Discriminator()
                .propertyName("kind")
                .mapping("card", "#/components/schemas/CardPayment")
                .mapping("bank", "#/components/schemas/BankPayment");
        discriminator.addExtension("x-discriminator", "payment-kind");

        Schema<?> paymentSchema = new Schema<>()
                .addOneOfItem(cardPayment)
                .addOneOfItem(bankPayment)
                .discriminator(discriminator);

        Schema<?> order = new ObjectSchema()
                .specVersion(SpecVersion.V31)
                .$id("https://example.test/schemas/order")
                .$schema("https://json-schema.org/draft/2020-12/schema")
                .$anchor("order")
                .$dynamicAnchor("orderRoot")
                .types(Set.of("object"))
                .addProperty("id", new UUIDSchema())
                .addProperty("status", statusSchema)
                .addProperty("total", moneySchema)
                .addProperty("items", items)
                .addProperty("shipping", shippingAddress)
                .addProperty("payment", paymentSchema)
                .addPatternProperty("^x-", new StringSchema())
                .required(List.of("id", "items", "status"))
                .additionalProperties(false)
                .propertyNames(new StringSchema().pattern("^[A-Za-z][A-Za-z0-9-]*$"))
                .unevaluatedProperties(new BooleanSchema().booleanSchemaValue(false))
                .contentMediaType("application/json")
                .contentEncoding("utf-8")
                .contentSchema(new Schema<>().$dynamicRef("#orderRoot"))
                .examples(List.of(Map.of("status", "pending")))
                ._const(Map.of("resource", "order"))
                .xml(new XML().name("order").namespace("urn:example:store").wrapped(true));
        order.addExtension("x-schema-owner", "commerce");

        assertThat(statusSchema.getEnum()).containsExactly("pending", "paid");
        assertThat(statusSchema.getDefault()).isEqualTo("pending");
        assertThat(order.getSpecVersion()).isEqualTo(SpecVersion.V31);
        assertThat(order.getProperties()).containsKeys("id", "status", "total", "items", "shipping", "payment");
        assertThat(order.getRequired()).containsExactly("id", "items", "status");
        assertThat(order.getPatternProperties()).containsKey("^x-");
        assertThat(order.getPropertyNames().getPattern()).isEqualTo("^[A-Za-z][A-Za-z0-9-]*$");
        assertThat(order.getAdditionalProperties()).isEqualTo(false);
        assertThat(order.getXml().getNamespace()).isEqualTo("urn:example:store");
        assertThat(order.getTypes()).containsExactly("object");
        assertThat(order.getExamples()).isEqualTo(List.of(Map.of("status", "pending")));
        assertThat(order.getConst()).isEqualTo(Map.of("resource", "order"));
        assertThat(order.getExtensions()).containsEntry("x-schema-owner", "commerce");
        assertThat(order.toString()).contains("class Schema").contains("patternProperties");

        assertThatThrownBy(() -> new ObjectSchema().additionalProperties("not a boolean or schema"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("additionalProperties");
    }

    @Test
    void componentsCollectReusableParametersResponsesCallbacksExamplesAndLinks() {
        Example orderExample = new Example()
                .summary("sample order")
                .description("Example returned by list orders")
                .value(Map.of("id", "00000000-0000-0000-0000-000000000001"));
        orderExample.addExtension("x-example-source", "unit-test");

        Encoding jsonEncoding = new Encoding()
                .contentType("application/json")
                .style(Encoding.StyleEnum.FORM)
                .explode(true)
                .allowReserved(false)
                .addHeader("X-Encoding", new Header()
                        .description("encoding marker")
                        .schema(new StringSchema()));
        MediaType mediaType = new MediaType()
                .schema(orderSchema())
                .example(Map.of("status", "paid"))
                .addExamples("order", orderExample)
                .addEncoding("payload", jsonEncoding);
        mediaType.addExtension31("x-oai-reserved", "ignored");
        mediaType.addExtension31("x-media", "accepted");

        Header rateLimitHeader = new Header()
                .description("remaining requests")
                .required(false)
                .deprecated(false)
                .style(Header.StyleEnum.SIMPLE)
                .explode(false)
                .schema(new IntegerSchema())
                .addExample("remaining", new Example().value(99));
        Header headerReference = new Header().$ref("RateLimit");

        Link nextPageLink = new Link()
                .operationId("listOrders")
                .requestBody("$request.body#/nextPageToken")
                .addParameter("cursor", "$response.body#/next")
                .addHeaderObject("X-Trace", new Header().schema(new StringSchema()))
                .description("next page")
                .server(new Server().url("https://api.example.test"));
        nextPageLink.addExtension("x-link-kind", "pagination");

        ApiResponse okResponse = new ApiResponse()
                .description("ok")
                .content(new Content().addMediaType("application/json", mediaType))
                .addHeaderObject("X-RateLimit-Remaining", rateLimitHeader)
                .addLink("next", nextPageLink);
        ApiResponse responseReference = new ApiResponse().$ref("OrderResponse");

        Callback callback = new Callback()
                .addPathItem("{$request.body#/callbackUrl}", new PathItem()
                        .post(new Operation().operationId("notifyClient")
                                .responses(new ApiResponses().addApiResponse(
                                        "204",
                                        new ApiResponse().description("no content")))))
                .$ref("OrderCallback");

        Parameter orderId = new PathParameter().name("id").schema(new UUIDSchema());
        Parameter cookie = new CookieParameter()
                .name("session")
                .schema(new StringSchema())
                .style(Parameter.StyleEnum.FORM);
        RequestBody bodyReference = new RequestBody().$ref("OrderBody");

        Components components = new Components()
                .addSchemas("Order", orderSchema())
                .addResponses("OrderResponse", okResponse)
                .addResponses("OrderResponseRef", responseReference)
                .addParameters("OrderId", orderId)
                .addParameters("SessionCookie", cookie)
                .addExamples("OrderExample", orderExample)
                .addRequestBodies("OrderBody", bodyReference)
                .addHeaders("RateLimit", rateLimitHeader)
                .addHeaders("RateLimitRef", headerReference)
                .addSecuritySchemes("ApiKey", new SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY)
                        .in(SecurityScheme.In.HEADER)
                        .name("X-API-Key"))
                .addLinks("NextPage", nextPageLink)
                .addCallbacks("OrderCallback", callback)
                .addPathItem("ReusableOrderPath", new PathItem().get(new Operation().operationId("getOrder")));
        components.addExtension("x-components-owner", "platform");

        assertThat(components.getSchemas()).containsKey("Order");
        assertThat(components.getResponses().get("OrderResponse").getContent().get("application/json"))
                .isSameAs(mediaType);
        assertThat(components.getResponses().get("OrderResponseRef").get$ref())
                .isEqualTo("#/components/responses/OrderResponse");
        assertThat(components.getParameters().get("OrderId").getIn()).isEqualTo("path");
        assertThat(components.getParameters().get("OrderId").getRequired()).isTrue();
        assertThat(components.getParameters().get("SessionCookie").getIn()).isEqualTo("cookie");
        assertThat(components.getExamples().get("OrderExample").getExtensions())
                .containsEntry("x-example-source", "unit-test");
        assertThat(components.getRequestBodies().get("OrderBody").get$ref())
                .isEqualTo("#/components/requestBodies/OrderBody");
        assertThat(components.getHeaders().get("RateLimitRef").get$ref())
                .isEqualTo("#/components/headers/RateLimit");
        assertThat(components.getSecuritySchemes().get("ApiKey").getIn()).isEqualTo(SecurityScheme.In.HEADER);
        assertThat(components.getLinks().get("NextPage").getParameters())
                .containsEntry("cursor", "$response.body#/next");
        assertThat(components.getCallbacks().get("OrderCallback").get$ref())
                .isEqualTo("#/components/callbacks/OrderCallback");
        assertThat(components.getPathItems()).containsKey("ReusableOrderPath");
        assertThat(mediaType.getEncoding().get("payload").getHeaders()).containsKey("X-Encoding");
        assertThat(mediaType.getExtensions()).containsEntry("x-media", "accepted");
        assertThat(mediaType.getExtensions()).doesNotContainKey("x-oai-reserved");
        assertThat(components.getExtensions()).containsEntry("x-components-owner", "platform");
        assertThat(components.toString()).contains("class Components").contains("OrderResponse");
    }

    @Test
    void typedSchemaConvenienceClassesExposeExpectedTypesFormatsAndValues() {
        Date epoch = new Date(0L);
        Schema<?> date = new DateSchema()._default(epoch);
        Schema<?> email = new EmailSchema().addEnumItem("orders@example.test");
        Schema<?> uuid = new UUIDSchema()._default(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        Schema<?> map = new MapSchema().additionalProperties(new StringSchema());
        Schema<?> array = new ArraySchema().items(new IntegerSchema());
        Schema<?> booleanSchema = new BooleanSchema()._default(true).addEnumItem(false);

        assertThat(date.getType()).isEqualTo("string");
        assertThat(date.getFormat()).isEqualTo("date");
        assertThat(date.getDefault()).isEqualTo(epoch);
        assertThat(email.getType()).isEqualTo("string");
        assertThat(email.getFormat()).isEqualTo("email");
        assertThat(email.getEnum()).isEqualTo(List.of("orders@example.test"));
        assertThat(uuid.getType()).isEqualTo("string");
        assertThat(uuid.getFormat()).isEqualTo("uuid");
        assertThat(map.getAdditionalProperties()).isInstanceOf(StringSchema.class);
        assertThat(array.getType()).isEqualTo("array");
        assertThat(array.getItems()).isInstanceOf(IntegerSchema.class);
        assertThat(booleanSchema.getType()).isEqualTo("boolean");
        assertThat(booleanSchema.getDefault()).isEqualTo(true);
        assertThat(booleanSchema.getEnum()).isEqualTo(List.of(false));
    }

    private static Content jsonContent(Schema<?> schema) {
        return new Content().addMediaType("application/json", new MediaType().schema(schema));
    }

    private static Schema<?> orderSchema() {
        Schema<?> orderSchema = new ObjectSchema()
                .name("Order")
                .description("An order resource")
                .addProperty("id", new UUIDSchema())
                .addProperty("status", new StringSchema().addEnumItem("pending").addEnumItem("paid"))
                .addProperty("created", new DateSchema())
                .addProperty("tags", new ArraySchema().items(new StringSchema()))
                .addRequiredItem("id")
                .addRequiredItem("status")
                .$ref(Components.COMPONENTS_SCHEMAS_REF + "Order");
        orderSchema.addExtension("x-model", "order");
        return orderSchema;
    }
}
