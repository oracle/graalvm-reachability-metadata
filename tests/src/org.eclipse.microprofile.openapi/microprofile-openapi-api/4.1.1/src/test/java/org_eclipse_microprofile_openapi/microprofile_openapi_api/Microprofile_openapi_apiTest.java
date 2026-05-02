/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_microprofile_openapi.microprofile_openapi_api;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.microprofile.openapi.OASConfig;
import org.eclipse.microprofile.openapi.OASFactory;
import org.eclipse.microprofile.openapi.OASFilter;
import org.eclipse.microprofile.openapi.OASModelReader;
import org.eclipse.microprofile.openapi.annotations.enums.ParameterIn;
import org.eclipse.microprofile.openapi.models.Components;
import org.eclipse.microprofile.openapi.models.ExternalDocumentation;
import org.eclipse.microprofile.openapi.models.OpenAPI;
import org.eclipse.microprofile.openapi.models.Operation;
import org.eclipse.microprofile.openapi.models.PathItem;
import org.eclipse.microprofile.openapi.models.Paths;
import org.eclipse.microprofile.openapi.models.callbacks.Callback;
import org.eclipse.microprofile.openapi.models.examples.Example;
import org.eclipse.microprofile.openapi.models.headers.Header;
import org.eclipse.microprofile.openapi.models.info.Contact;
import org.eclipse.microprofile.openapi.models.info.Info;
import org.eclipse.microprofile.openapi.models.info.License;
import org.eclipse.microprofile.openapi.models.links.Link;
import org.eclipse.microprofile.openapi.models.media.Content;
import org.eclipse.microprofile.openapi.models.media.Discriminator;
import org.eclipse.microprofile.openapi.models.media.Encoding;
import org.eclipse.microprofile.openapi.models.media.MediaType;
import org.eclipse.microprofile.openapi.models.media.Schema;
import org.eclipse.microprofile.openapi.models.media.XML;
import org.eclipse.microprofile.openapi.models.parameters.Parameter;
import org.eclipse.microprofile.openapi.models.parameters.RequestBody;
import org.eclipse.microprofile.openapi.models.responses.APIResponse;
import org.eclipse.microprofile.openapi.models.responses.APIResponses;
import org.eclipse.microprofile.openapi.models.security.OAuthFlow;
import org.eclipse.microprofile.openapi.models.security.OAuthFlows;
import org.eclipse.microprofile.openapi.models.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.models.security.SecurityScheme;
import org.eclipse.microprofile.openapi.models.servers.Server;
import org.eclipse.microprofile.openapi.models.tags.Tag;
import org.eclipse.microprofile.openapi.spi.OASFactoryResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class Microprofile_openapi_apiTest {
    @BeforeEach
    void installResolver() {
        OASFactoryResolver.setInstance(new TestOASFactoryResolver());
    }

    @Test
    void createsAndLinksCompleteOpenAPIModel() {
        Contact contact = OASFactory.createContact()
                .name("OpenAPI Team")
                .email("team@example.test")
                .url("https://example.test/contact")
                .addExtension("x-contact-kind", "support");
        License license = OASFactory.createLicense()
                .name("Apache-2.0")
                .identifier("Apache-2.0")
                .url("https://www.apache.org/licenses/LICENSE-2.0");
        Info info = OASFactory.createInfo()
                .title("Inventory")
                .summary("Inventory summary")
                .description("Inventory API")
                .termsOfService("https://example.test/terms")
                .version("1.0.0")
                .contact(contact)
                .license(license)
                .addExtension("x-info", 7);
        Server server = OASFactory.createServer()
                .url("https://{tenant}.example.test/{basePath}")
                .description("tenant server")
                .addVariable("tenant", OASFactory.createServerVariable()
                        .defaultValue("acme")
                        .description("tenant subdomain")
                        .addEnumeration("acme")
                        .addEnumeration("contoso"))
                .addVariable("basePath", OASFactory.createServerVariable().defaultValue("v1"));
        SecurityRequirement requirement = OASFactory.createSecurityRequirement()
                .addScheme("oauth2", Arrays.asList("inventory:read", "inventory:write"))
                .addScheme("apiKey");
        OpenAPI openAPI = OASFactory.createOpenAPI()
                .openapi("3.1.1")
                .jsonSchemaDialect("https://json-schema.org/draft/2020-12/schema")
                .info(info)
                .addServer(server)
                .addSecurityRequirement(requirement)
                .components(OASFactory.createComponents())
                .paths(OASFactory.createPaths())
                .addTag(OASFactory.createTag().name("inventory").description("Inventory operations"))
                .addWebhook("stock-changed", OASFactory.createPathItem().summary("stock webhook"));

        assertThat(openAPI.getOpenapi()).isEqualTo("3.1.1");
        assertThat(openAPI.getJsonSchemaDialect()).contains("2020-12");
        assertThat(openAPI.getInfo().getContact().getExtension("x-contact-kind")).isEqualTo("support");
        assertThat(openAPI.getInfo().getLicense().getIdentifier()).isEqualTo("Apache-2.0");
        assertThat(openAPI.getServers()).containsExactly(server);
        assertThat(openAPI.getServers().get(0).getVariables()).containsKeys("tenant", "basePath");
        assertThat(openAPI.getServers().get(0).getVariables().get("tenant").getEnumeration())
                .containsExactly("acme", "contoso");
        assertThat(openAPI.getSecurity().get(0).getScheme("oauth2"))
                .containsExactly("inventory:read", "inventory:write");
        assertThat(openAPI.getSecurity().get(0).getScheme("apiKey")).isEmpty();
        assertThat(openAPI.getTags().get(0).getName()).isEqualTo("inventory");
        assertThat(openAPI.getWebhooks()).containsKey("stock-changed");
    }

    @Test
    void managesComponentsSchemasResponsesAndSecuritySchemes() {
        Schema skuSchema = OASFactory.createSchema()
                .title("Sku")
                .type(Arrays.asList(Schema.SchemaType.STRING))
                .minLength(3)
                .maxLength(20)
                .pattern("[A-Z0-9-]+")
                .addExample("ABC-123")
                .addEnumeration("ABC-123")
                .addEnumeration("XYZ-987")
                .addExtension("x-normalized", Boolean.TRUE);
        Schema inventorySchema = OASFactory.createSchema()
                .title("InventoryItem")
                .description("An item available for sale")
                .addType(Schema.SchemaType.OBJECT)
                .addRequired("sku")
                .addRequired("quantity")
                .addProperty("sku", skuSchema)
                .addProperty("quantity", OASFactory.createSchema()
                        .addType(Schema.SchemaType.INTEGER)
                        .minimum(BigDecimal.ZERO)
                        .defaultValue(0))
                .additionalPropertiesSchema(OASFactory.createSchema().booleanSchema(false));
        APIResponse okResponse = OASFactory.createAPIResponse()
                .description("item found")
                .content(OASFactory.createContent().addMediaType("application/json",
                        OASFactory.createMediaType().schema(inventorySchema)))
                .addHeader("ETag", OASFactory.createHeader()
                        .description("entity tag")
                        .schema(OASFactory.createSchema().addType(Schema.SchemaType.STRING)));
        SecurityScheme securityScheme = OASFactory.createSecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .description("JWT bearer token");
        Components components = OASFactory.createComponents()
                .addSchema("Sku", skuSchema)
                .addSchema("InventoryItem", inventorySchema)
                .addResponse("ItemResponse", okResponse)
                .addParameter("SkuParameter", OASFactory.createParameter()
                        .name("sku")
                        .in(Parameter.In.PATH)
                        .required(true)
                        .schema(skuSchema))
                .addExample("SampleItem", OASFactory.createExample().summary("sample").value("ABC-123"))
                .addRequestBody("ItemBody", OASFactory.createRequestBody().required(true)
                        .content(OASFactory.createContent().addMediaType("application/json",
                                OASFactory.createMediaType().schema(inventorySchema))))
                .addHeader("ETag", OASFactory.createHeader().description("entity tag"))
                .addSecurityScheme("bearerAuth", securityScheme)
                .addLink("findItem", OASFactory.createLink().operationId("getItem"))
                .addCallback("stockCallback", OASFactory.createCallback().addPathItem("{$request.body#/callbackUrl}",
                        OASFactory.createPathItem().POST(OASFactory.createOperation().summary("callback"))))
                .addPathItem("ReusableItemPath", OASFactory.createPathItem().summary("reusable path"));

        assertThat(components.getSchemas()).containsEntry("Sku", skuSchema).containsEntry("InventoryItem", inventorySchema);
        assertThat(components.getSchemas().get("InventoryItem").getRequired()).containsExactly("sku", "quantity");
        assertThat(components.getSchemas().get("InventoryItem").getProperties()).containsEntry("sku", skuSchema);
        assertThat(components.getResponses().get("ItemResponse").getContent().hasMediaType("application/json")).isTrue();
        assertThat(components.getResponses().get("ItemResponse").getHeaders()).containsKey("ETag");
        assertThat(components.getParameters().get("SkuParameter").getIn()).isEqualTo(Parameter.In.PATH);
        assertThat(components.getExamples().get("SampleItem").getValue()).isEqualTo("ABC-123");
        assertThat(components.getRequestBodies().get("ItemBody").getContent().getMediaType("application/json").getSchema())
                .isSameAs(inventorySchema);
        assertThat(components.getSecuritySchemes().get("bearerAuth").getBearerFormat()).isEqualTo("JWT");
        assertThat(components.getLinks().get("findItem").getOperationId()).isEqualTo("getItem");
        assertThat(components.getCallbacks().get("stockCallback").hasPathItem("{$request.body#/callbackUrl}")).isTrue();
        assertThat(components.getPathItems()).containsKey("ReusableItemPath");

        components.removeSchema("Sku");
        components.removeResponse("ItemResponse");
        assertThat(components.getSchemas()).doesNotContainKey("Sku");
        assertThat(components.getResponses()).doesNotContainKey("ItemResponse");
    }

    @Test
    void managesPathsOperationsParametersRequestsAndResponses() {
        Parameter skuParameter = OASFactory.createParameter()
                .name("sku")
                .in(Parameter.In.PATH)
                .style(Parameter.Style.SIMPLE)
                .required(true)
                .description("stock keeping unit")
                .schema(OASFactory.createSchema().addType(Schema.SchemaType.STRING));
        RequestBody requestBody = OASFactory.createRequestBody()
                .description("item update")
                .required(true)
                .content(OASFactory.createContent().addMediaType("application/json",
                        OASFactory.createMediaType().schema(OASFactory.createSchema().addType(Schema.SchemaType.OBJECT))));
        APIResponse created = OASFactory.createAPIResponse().description("created");
        APIResponse notFound = OASFactory.createAPIResponse().description("not found");
        APIResponses responses = OASFactory.createAPIResponses()
                .addAPIResponse("201", created)
                .addAPIResponse("404", notFound)
                .defaultValue(OASFactory.createAPIResponse().description("unexpected error"))
                .addExtension("x-rate-limit", 100);
        Operation getItem = OASFactory.createOperation()
                .operationId("getItem")
                .summary("Fetch item")
                .description("Fetches one inventory item")
                .addTag("inventory")
                .addParameter(skuParameter)
                .requestBody(requestBody)
                .responses(responses)
                .deprecated(false)
                .addSecurityRequirement(OASFactory.createSecurityRequirement().addScheme("bearerAuth", "inventory:read"))
                .addServer(OASFactory.createServer().url("https://api.example.test"));
        PathItem pathItem = OASFactory.createPathItem()
                .summary("Inventory item")
                .description("Single item resource")
                .GET(getItem)
                .addParameter(skuParameter)
                .addServer(OASFactory.createServer().url("https://path.example.test"));
        pathItem.setOperation(PathItem.HttpMethod.PATCH, OASFactory.createOperation().operationId("patchItem"));
        Paths paths = OASFactory.createPaths().addPathItem("/items/{sku}", pathItem);

        assertThat(paths.hasPathItem("/items/{sku}")).isTrue();
        assertThat(paths.getPathItem("/items/{sku}").getGET().getOperationId()).isEqualTo("getItem");
        assertThat(paths.getPathItem("/items/{sku}").getOperations())
                .containsEntry(PathItem.HttpMethod.GET, getItem)
                .containsKey(PathItem.HttpMethod.PATCH);
        assertThat(getItem.getTags()).containsExactly("inventory");
        assertThat(getItem.getParameters()).containsExactly(skuParameter);
        assertThat(getItem.getRequestBody().getContent().getMediaType("application/json").getSchema().getType())
                .containsExactly(Schema.SchemaType.OBJECT);
        assertThat(getItem.getResponses().hasAPIResponse("201")).isTrue();
        assertThat(getItem.getResponses().getAPIResponse("404").getDescription()).isEqualTo("not found");
        assertThat(getItem.getResponses().getDefaultValue().getDescription()).contains("unexpected");
        assertThat(getItem.getSecurity().get(0).getScheme("bearerAuth")).containsExactly("inventory:read");
        assertThat(pathItem.getServers().get(0).getUrl()).isEqualTo("https://path.example.test");

        responses.removeAPIResponse("404");
        paths.removePathItem("/items/{sku}");
        assertThat(responses.hasAPIResponse("404")).isFalse();
        assertThat(paths.hasPathItem("/items/{sku}")).isFalse();
    }

    @Test
    void supportsJsonSchema202012ModelFeaturesAndArbitrarySchemaProperties() {
        Schema positiveInteger = OASFactory.createSchema()
                .schemaDialect("https://json-schema.org/draft/2020-12/schema")
                .addType(Schema.SchemaType.INTEGER)
                .minimum(BigDecimal.ONE)
                .exclusiveMaximum(new BigDecimal("1000"))
                .multipleOf(BigDecimal.ONE)
                .constValue(42)
                .comment("business identifier")
                .contentEncoding("base64")
                .contentMediaType("text/plain")
                .booleanSchema(true)
                .addExample(1)
                .addExample(42)
                .set("x-ui-order", 10);
        ExternalDocumentation docs = OASFactory.createExternalDocumentation()
                .description("schema docs")
                .url("https://example.test/schema-docs");
        Discriminator discriminator = OASFactory.createDiscriminator()
                .propertyName("kind")
                .addMapping("physical", "#/components/schemas/PhysicalItem")
                .addMapping("digital", "#/components/schemas/DigitalItem");
        XML xml = OASFactory.createXML()
                .name("inventoryItem")
                .namespace("https://example.test/inventory")
                .prefix("inv")
                .attribute(false)
                .wrapped(true);
        Schema composed = OASFactory.createSchema()
                .discriminator(discriminator)
                .externalDocs(docs)
                .xml(xml)
                .format("int32")
                .readOnly(true)
                .writeOnly(false)
                .deprecated(false)
                .items(positiveInteger)
                .not(OASFactory.createSchema().booleanSchema(false))
                .addAllOf(OASFactory.createSchema().ref("#/components/schemas/BaseItem"))
                .addAnyOf(OASFactory.createSchema().ref("#/components/schemas/PhysicalItem"))
                .addOneOf(OASFactory.createSchema().ref("#/components/schemas/DigitalItem"));
        Schema conditional = OASFactory.createSchema()
                .ifSchema(OASFactory.createSchema().addProperty("kind", OASFactory.createSchema().constValue("physical")))
                .thenSchema(OASFactory.createSchema().addRequired("weight"))
                .elseSchema(OASFactory.createSchema().addRequired("downloadUrl"))
                .addDependentSchema("billingAddress", OASFactory.createSchema().addRequired("paymentMethod"))
                .addDependentRequired("creditCard", Arrays.asList("billingAddress", "cardholder"))
                .addPrefixItem(OASFactory.createSchema().addType(Schema.SchemaType.STRING))
                .contains(positiveInteger)
                .minContains(1)
                .maxContains(3)
                .addPatternProperty("^x-", OASFactory.createSchema().addType(Schema.SchemaType.STRING))
                .propertyNames(OASFactory.createSchema().pattern("^[a-zA-Z0-9-]+$"))
                .unevaluatedItems(OASFactory.createSchema().booleanSchema(false))
                .unevaluatedProperties(OASFactory.createSchema().booleanSchema(false))
                .contentSchema(positiveInteger)
                .additionalPropertiesSchema(OASFactory.createSchema().ref("#/components/schemas/Extra"));

        assertThat(positiveInteger.getType()).containsExactly(Schema.SchemaType.INTEGER);
        assertThat(positiveInteger.getExclusiveMaximum()).isEqualByComparingTo("1000");
        assertThat(positiveInteger.getExamples()).containsExactly(1, 42);
        assertThat(positiveInteger.get("x-ui-order")).isEqualTo(10);
        assertThat(positiveInteger.getAll()).containsKey("x-ui-order");
        assertThat(positiveInteger.getAll().get("x-ui-order")).isEqualTo(10);
        assertThat(conditional.getIfSchema().getProperties()).containsKey("kind");
        assertThat(conditional.getThenSchema().getRequired()).containsExactly("weight");
        assertThat(conditional.getDependentSchemas()).containsKey("billingAddress");
        assertThat(conditional.getDependentRequired()).containsEntry("creditCard", Arrays.asList("billingAddress", "cardholder"));
        assertThat(conditional.getPrefixItems().get(0).getType()).containsExactly(Schema.SchemaType.STRING);
        assertThat(conditional.getContains()).isSameAs(positiveInteger);
        assertThat(conditional.getPatternProperties()).containsKey("^x-");
        assertThat(conditional.getPropertyNames().getPattern()).contains("a-zA-Z");
        assertThat(conditional.getAdditionalPropertiesSchema().getRef()).isEqualTo("#/components/schemas/Extra");
        assertThat(composed.getDiscriminator().getMapping()).containsKeys("physical", "digital");
        assertThat(composed.getExternalDocs().getUrl()).contains("schema-docs");
        assertThat(composed.getXml().getPrefix()).isEqualTo("inv");
        assertThat(composed.getItems()).isSameAs(positiveInteger);
        assertThat(composed.getAllOf().get(0).getRef()).contains("BaseItem");
        assertThat(composed.getAnyOf().get(0).getRef()).contains("PhysicalItem");
        assertThat(composed.getOneOf().get(0).getRef()).contains("DigitalItem");

        conditional.removeDependentSchema("billingAddress");
        conditional.removePatternProperty("^x-");
        positiveInteger.removeExample(1);
        assertThat(conditional.getDependentSchemas()).doesNotContainKey("billingAddress");
        assertThat(conditional.getPatternProperties()).doesNotContainKey("^x-");
        assertThat(positiveInteger.getExamples()).containsExactly(42);
    }

    @Test
    void supportsMediaExamplesHeadersLinksCallbacksAndOAuthFlows() {
        Example successExample = OASFactory.createExample()
                .summary("success")
                .description("successful response")
                .value(Map.of("status", "ok"))
                .externalValue("https://example.test/examples/success.json")
                .ref("#/components/examples/Success");
        Encoding encoding = OASFactory.createEncoding()
                .contentType("application/json")
                .style(Encoding.Style.FORM)
                .explode(true)
                .allowReserved(false)
                .addHeader("X-Trace", OASFactory.createHeader().description("trace header"));
        MediaType mediaType = OASFactory.createMediaType()
                .schema(OASFactory.createSchema().addType(Schema.SchemaType.OBJECT))
                .example(Map.of("status", "ok"))
                .addExample("success", successExample)
                .addEncoding("payload", encoding);
        Header header = OASFactory.createHeader()
                .description("pagination cursor")
                .required(false)
                .deprecated(false)
                .allowEmptyValue(false)
                .style(Header.Style.SIMPLE)
                .explode(false)
                .schema(OASFactory.createSchema().addType(Schema.SchemaType.STRING))
                .addExample("cursor", OASFactory.createExample().value("abc"));
        Link link = OASFactory.createLink()
                .operationRef("#/paths/~1items/get")
                .operationId("listItems")
                .requestBody("$request.body#/id")
                .addParameter("sku", "$response.body#/sku")
                .description("next item")
                .server(OASFactory.createServer().url("https://links.example.test"));
        Callback callback = OASFactory.createCallback()
                .addPathItem("{$request.body#/callbackUrl}", OASFactory.createPathItem().POST(OASFactory.createOperation().summary("notify")))
                .ref("#/components/callbacks/InventoryChanged");
        OAuthFlow authorizationCode = OASFactory.createOAuthFlow()
                .authorizationUrl("https://auth.example.test/authorize")
                .tokenUrl("https://auth.example.test/token")
                .refreshUrl("https://auth.example.test/refresh")
                .addScope("inventory:read", "read inventory")
                .addScope("inventory:write", "write inventory");
        OAuthFlows flows = OASFactory.createOAuthFlows()
                .authorizationCode(authorizationCode)
                .clientCredentials(OASFactory.createOAuthFlow().tokenUrl("https://auth.example.test/client-token"));
        SecurityScheme oauth = OASFactory.createSecurityScheme()
                .type(SecurityScheme.Type.OAUTH2)
                .flows(flows)
                .openIdConnectUrl("https://auth.example.test/.well-known/openid-configuration");

        assertThat(mediaType.getExamples()).containsEntry("success", successExample);
        assertThat(mediaType.getEncoding().get("payload").getHeaders()).containsKey("X-Trace");
        assertThat(header.getExamples().get("cursor").getValue()).isEqualTo("abc");
        assertThat(link.getParameters()).containsEntry("sku", "$response.body#/sku");
        assertThat(link.getServer().getUrl()).contains("links");
        assertThat(callback.hasPathItem("{$request.body#/callbackUrl}")).isTrue();
        assertThat(callback.getRef()).isEqualTo("#/components/callbacks/InventoryChanged");
        assertThat(oauth.getFlows().getAuthorizationCode().getScopes())
                .containsEntry("inventory:read", "read inventory")
                .containsEntry("inventory:write", "write inventory");
        assertThat(oauth.getFlows().getClientCredentials().getTokenUrl()).contains("client-token");
    }

    @Test
    void supportsContentBasedParametersAndHeaders() {
        Schema searchCriteria = OASFactory.createSchema()
                .addType(Schema.SchemaType.OBJECT)
                .addProperty("query", OASFactory.createSchema().addType(Schema.SchemaType.STRING))
                .addProperty("limit", OASFactory.createSchema().addType(Schema.SchemaType.INTEGER));
        MediaType jsonCriteria = OASFactory.createMediaType()
                .schema(searchCriteria)
                .example(Map.of("query", "boots", "limit", 10));
        MediaType vendorCriteria = OASFactory.createMediaType()
                .schema(searchCriteria)
                .addExample("compact", OASFactory.createExample().value(Map.of("query", "boots")));
        Content parameterContent = OASFactory.createContent()
                .addMediaType("application/json", jsonCriteria)
                .addMediaType("application/vnd.search+json", vendorCriteria);
        Parameter filterParameter = OASFactory.createParameter()
                .name("filter")
                .in(Parameter.In.QUERY)
                .description("structured search criteria")
                .allowReserved(true)
                .explode(false)
                .content(parameterContent);
        MediaType plainSignature = OASFactory.createMediaType()
                .schema(OASFactory.createSchema().addType(Schema.SchemaType.STRING))
                .example("sha256=abc123");
        Content headerContent = OASFactory.createContent()
                .addMediaType("text/plain", plainSignature);
        Header signatureHeader = OASFactory.createHeader()
                .description("request signature")
                .required(true)
                .content(headerContent);

        assertThat(filterParameter.getContent()).isSameAs(parameterContent);
        assertThat(filterParameter.getContent().hasMediaType("application/json")).isTrue();
        assertThat(filterParameter.getContent().getMediaType("application/json").getSchema().getProperties())
                .containsKeys("query", "limit");
        assertThat(filterParameter.getContent().getMediaTypes())
                .containsEntry("application/vnd.search+json", vendorCriteria);
        assertThat(filterParameter.getAllowReserved()).isTrue();
        assertThat(filterParameter.getExplode()).isFalse();
        assertThat(signatureHeader.getContent().getMediaType("text/plain").getExample()).isEqualTo("sha256=abc123");
        assertThat(signatureHeader.getRequired()).isTrue();

        parameterContent.removeMediaType("application/vnd.search+json");
        assertThat(parameterContent.hasMediaType("application/vnd.search+json")).isFalse();
    }

    @Test
    void supportsOperationCallbacksExternalDocsAndExtensionMaps() {
        ExternalDocumentation operationDocs = OASFactory.createExternalDocumentation()
                .description("callback contract")
                .url("https://example.test/docs/callbacks")
                .extensions(new LinkedHashMap<>(Map.of("x-document-kind", "runbook")))
                .addExtension("x-reviewed", Boolean.TRUE);
        Operation notification = OASFactory.createOperation()
                .operationId("receiveInventoryEvent")
                .summary("Receive inventory event")
                .externalDocs(operationDocs);
        Callback initialCallback = OASFactory.createCallback()
                .addPathItem("{$request.body#/subscriptions/initialUrl}",
                        OASFactory.createPathItem().POST(OASFactory.createOperation().operationId("receiveInitialEvent")));
        Callback updatedCallback = OASFactory.createCallback()
                .addPathItem("{$request.body#/subscriptions/updateUrl}", OASFactory.createPathItem().POST(notification))
                .addExtension("x-event-type", "inventory.updated");
        Operation subscription = OASFactory.createOperation()
                .operationId("subscribeToInventory")
                .externalDocs(operationDocs)
                .callbacks(new LinkedHashMap<>(Map.of("initial", initialCallback)))
                .addCallback("updated", updatedCallback)
                .addExtension("x-audience", "partners");
        Tag subscriptionTag = OASFactory.createTag()
                .name("subscriptions")
                .externalDocs(operationDocs);

        assertThat(subscription.getExternalDocs().getUrl()).contains("callbacks");
        assertThat(subscription.getCallbacks())
                .containsEntry("initial", initialCallback)
                .containsEntry("updated", updatedCallback);
        assertThat(subscription.getCallbacks().get("updated").getPathItem("{$request.body#/subscriptions/updateUrl}")
                .getPOST().getOperationId()).isEqualTo("receiveInventoryEvent");
        assertThat(operationDocs.hasExtension("x-document-kind")).isTrue();
        assertThat(operationDocs.getExtensions()).containsEntry("x-reviewed", Boolean.TRUE);
        assertThat(subscription.hasExtension("x-audience")).isTrue();
        assertThat(updatedCallback.getExtension("x-event-type")).isEqualTo("inventory.updated");
        assertThat(subscriptionTag.getExternalDocs()).isSameAs(operationDocs);

        subscription.removeCallback("initial");
        subscription.removeExtension("x-audience");
        operationDocs.removeExtension("x-reviewed");
        assertThat(subscription.getCallbacks()).doesNotContainKey("initial");
        assertThat(subscription.hasExtension("x-audience")).isFalse();
        assertThat(operationDocs.getExtensions()).doesNotContainKey("x-reviewed");
    }

    @Test
    void exposesConfigurationConstantsEnumsAndDefaultFilterBehavior() {
        assertThat(OASConfig.MODEL_READER).isEqualTo("mp.openapi.model.reader");
        assertThat(OASConfig.FILTER).isEqualTo("mp.openapi.filter");
        assertThat(OASConfig.SCAN_DISABLE).isEqualTo("mp.openapi.scan.disable");
        assertThat(OASConfig.SERVERS_PATH_PREFIX).isEqualTo("mp.openapi.servers.path.");
        assertThat(OASConfig.SERVERS_OPERATION_PREFIX).isEqualTo("mp.openapi.servers.operation.");
        assertThat(OASConfig.SCHEMA_PREFIX).isEqualTo("mp.openapi.schema.");
        assertThat(OASConfig.EXTENSIONS_PREFIX).isEqualTo("mp.openapi.extensions.");
        assertThat(ParameterIn.PATH.toString()).isEqualTo("path");
        assertThat(Parameter.In.COOKIE.toString()).isEqualTo("cookie");
        assertThat(Parameter.Style.DEEPOBJECT.toString()).isEqualTo("deepObject");
        assertThat(Schema.SchemaType.NULL.toString()).isEqualTo("null");
        assertThat(SecurityScheme.Type.MUTUALTLS.toString()).isEqualTo("mutualTLS");

        OASFilter filter = new OASFilter() {
        };
        OpenAPI openAPI = OASFactory.createOpenAPI();
        OASModelReader reader = () -> openAPI;
        Operation operation = OASFactory.createOperation();
        Parameter parameter = OASFactory.createParameter();
        Header header = OASFactory.createHeader();
        RequestBody requestBody = OASFactory.createRequestBody();
        APIResponse response = OASFactory.createAPIResponse();
        Schema schema = OASFactory.createSchema();
        SecurityScheme securityScheme = OASFactory.createSecurityScheme();
        Server server = OASFactory.createServer();
        Tag tag = OASFactory.createTag();
        Link link = OASFactory.createLink();
        Callback callback = OASFactory.createCallback();

        assertThat(filter.filterOperation(operation)).isSameAs(operation);
        assertThat(filter.filterParameter(parameter)).isSameAs(parameter);
        assertThat(filter.filterHeader(header)).isSameAs(header);
        assertThat(filter.filterRequestBody(requestBody)).isSameAs(requestBody);
        assertThat(filter.filterAPIResponse(response)).isSameAs(response);
        assertThat(filter.filterSchema(schema)).isSameAs(schema);
        assertThat(filter.filterSecurityScheme(securityScheme)).isSameAs(securityScheme);
        assertThat(filter.filterServer(server)).isSameAs(server);
        assertThat(filter.filterTag(tag)).isSameAs(tag);
        assertThat(filter.filterLink(link)).isSameAs(link);
        assertThat(filter.filterCallback(callback)).isSameAs(callback);
        filter.filterOpenAPI(openAPI);
        assertThat(reader.buildModel()).isSameAs(openAPI);
        assertThat(OASFactory.createObject(Schema.class)).isInstanceOf(Schema.class);
    }

    private static final class TestOASFactoryResolver extends OASFactoryResolver {
        @Override
        public <T extends org.eclipse.microprofile.openapi.models.Constructible> T createObject(Class<T> type) {
            Object proxy = Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, new ModelInvocationHandler(type));
            return type.cast(proxy);
        }
    }

    private static final class ModelInvocationHandler implements InvocationHandler {
        private final Class<?> type;
        private final Map<String, Object> values = new HashMap<>();
        private final Map<String, Object> arbitraryValues = new LinkedHashMap<>();

        private ModelInvocationHandler(Class<?> type) {
            this.type = type;
        }

        @Override
        public Object invoke(Object currentProxy, Method method, Object[] args) {
            String name = method.getName();
            if ("toString".equals(name)) {
                return type.getSimpleName() + values;
            }
            if ("hashCode".equals(name)) {
                return System.identityHashCode(currentProxy);
            }
            if ("equals".equals(name)) {
                return currentProxy == args[0];
            }
            if ("setOperation".equals(name)) {
                map("operations").put(args[0], args[1]);
                setOperationAccessor(args[0], args[1]);
                return null;
            }
            if ("getOperations".equals(name)) {
                return map("operations");
            }
            if (isHttpAccessor(name) && args != null && args.length == 1) {
                values.put(name, args[0]);
                map("operations").put(PathItem.HttpMethod.valueOf(name), args[0]);
                return currentProxy;
            }
            if ("set".equals(name)) {
                arbitraryValues.put((String) args[0], args[1]);
                return currentProxy;
            }
            if ("get".equals(name)) {
                return arbitraryValues.get(args[0]);
            }
            if ("getAll".equals(name)) {
                return arbitraryValues;
            }
            if ("setAll".equals(name)) {
                arbitraryValues.clear();
                arbitraryValues.putAll((Map<String, ?>) args[0]);
                return null;
            }
            if (name.startsWith("get") && args == null) {
                return values.get(propertyName(name.substring(3)));
            }
            if (name.startsWith("get") && args.length == 1) {
                return map(collectionProperty(name.substring(3))).get(args[0]);
            }
            if (name.startsWith("set") && args.length == 1) {
                values.put(propertyName(name.substring(3)), args[0]);
                return null;
            }
            if (name.startsWith("has") && args.length == 1) {
                return map(collectionProperty(name.substring(3))).containsKey(args[0]);
            }
            if ("additionalPropertiesSchema".equals(name)) {
                values.put("additionalPropertiesSchema", args[0]);
                return currentProxy;
            }
            if (name.startsWith("add") && args.length > 0) {
                add(name.substring(3), args);
                return currentProxy;
            }
            if (name.startsWith("remove") && args.length == 1) {
                remove(name.substring(6), args[0]);
                return null;
            }
            if (args != null && args.length == 1 && method.getReturnType().isInstance(currentProxy)) {
                values.put(propertyName(name), args[0]);
                return currentProxy;
            }
            return defaultValue(method.getReturnType());
        }

        private void add(String suffix, Object[] args) {
            if ("Scheme".equals(suffix)) {
                Object value = args.length == 1 ? List.of() : args[1] instanceof List ? args[1] : List.of(args[1]);
                map("schemes").put(args[0], value);
                return;
            }
            if (args.length == 2 && args[0] instanceof String) {
                map(collectionProperty(suffix)).put(args[0], args[1]);
                return;
            }
            list(collectionProperty(suffix)).add(args[0]);
        }

        private void remove(String suffix, Object keyOrValue) {
            String property = collectionProperty(suffix);
            Object current = values.get(property);
            if (current instanceof Map<?, ?>) {
                ((Map<?, ?>) current).remove(keyOrValue);
                return;
            }
            if (current instanceof List<?>) {
                ((List<?>) current).remove(keyOrValue);
            }
        }

        private Map<Object, Object> map(String property) {
            return (Map<Object, Object>) values.computeIfAbsent(property, ignored -> new LinkedHashMap<>());
        }

        private List<Object> list(String property) {
            return (List<Object>) values.computeIfAbsent(property, ignored -> new ArrayList<>());
        }

        private boolean isHttpAccessor(String name) {
            return "GET".equals(name) || "PUT".equals(name) || "POST".equals(name) || "DELETE".equals(name)
                    || "OPTIONS".equals(name) || "HEAD".equals(name) || "PATCH".equals(name) || "TRACE".equals(name);
        }

        private void setOperationAccessor(Object method, Object operation) {
            if (method == PathItem.HttpMethod.GET) {
                values.put("GET", operation);
            } else if (method == PathItem.HttpMethod.PUT) {
                values.put("PUT", operation);
            } else if (method == PathItem.HttpMethod.POST) {
                values.put("POST", operation);
            } else if (method == PathItem.HttpMethod.DELETE) {
                values.put("DELETE", operation);
            } else if (method == PathItem.HttpMethod.OPTIONS) {
                values.put("OPTIONS", operation);
            } else if (method == PathItem.HttpMethod.HEAD) {
                values.put("HEAD", operation);
            } else if (method == PathItem.HttpMethod.PATCH) {
                values.put("PATCH", operation);
            } else if (method == PathItem.HttpMethod.TRACE) {
                values.put("TRACE", operation);
            }
        }

        private String collectionProperty(String singular) {
            if ("APIResponse".equals(singular)) {
                return "APIResponses";
            }
            if ("PathItem".equals(singular)) {
                return "pathItems";
            }
            if ("MediaType".equals(singular)) {
                return "mediaTypes";
            }
            if ("RequestBody".equals(singular)) {
                return "requestBodies";
            }
            if ("SecurityRequirement".equals(singular)) {
                return "security";
            }
            if ("SecurityScheme".equals(singular)) {
                return "securitySchemes";
            }
            if ("PrefixItem".equals(singular)) {
                return "prefixItems";
            }
            if ("DependentSchema".equals(singular)) {
                return "dependentSchemas";
            }
            if ("PatternProperty".equals(singular)) {
                return "patternProperties";
            }
            if ("DependentRequired".equals(singular)) {
                return "dependentRequired";
            }
            if ("Property".equals(singular)) {
                return "properties";
            }
            if ("AllOf".equals(singular) || "AnyOf".equals(singular) || "OneOf".equals(singular)) {
                return propertyName(singular);
            }
            if ("Type".equals(singular)) {
                return "type";
            }
            if ("Enumeration".equals(singular)) {
                return "enumeration";
            }
            if ("Required".equals(singular)) {
                return "required";
            }
            if ("Encoding".equals(singular) || "Mapping".equals(singular)) {
                return propertyName(singular);
            }
            return propertyName(singular) + "s";
        }

        private String propertyName(String suffix) {
            if (suffix.length() > 1 && Character.isUpperCase(suffix.charAt(0)) && Character.isUpperCase(suffix.charAt(1))) {
                return suffix;
            }
            return Character.toLowerCase(suffix.charAt(0)) + suffix.substring(1);
        }

        private Object defaultValue(Class<?> returnType) {
            if (returnType == boolean.class || returnType == Boolean.class) {
                return false;
            }
            if (returnType == int.class || returnType == Integer.class) {
                return 0;
            }
            return null;
        }
    }
}
