/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_swagger_core_v3.swagger_annotations_jakarta;

import io.swagger.v3.oas.annotations.ExternalDocumentation;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.OpenAPI31;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.StringToClassMapItem;
import io.swagger.v3.oas.annotations.Webhook;
import io.swagger.v3.oas.annotations.Webhooks;
import io.swagger.v3.oas.annotations.callbacks.Callback;
import io.swagger.v3.oas.annotations.callbacks.Callbacks;
import io.swagger.v3.oas.annotations.enums.Explode;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.enums.ParameterStyle;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.extensions.Extension;
import io.swagger.v3.oas.annotations.extensions.ExtensionProperty;
import io.swagger.v3.oas.annotations.extensions.Extensions;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import io.swagger.v3.oas.annotations.links.Link;
import io.swagger.v3.oas.annotations.links.LinkParameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.DependentRequired;
import io.swagger.v3.oas.annotations.media.DependentRequiredMap;
import io.swagger.v3.oas.annotations.media.DependentSchema;
import io.swagger.v3.oas.annotations.media.DependentSchemas;
import io.swagger.v3.oas.annotations.media.DiscriminatorMapping;
import io.swagger.v3.oas.annotations.media.Encoding;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.PatternProperties;
import io.swagger.v3.oas.annotations.media.PatternProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.SchemaProperties;
import io.swagger.v3.oas.annotations.media.SchemaProperty;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.parameters.ValidatedParameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.responses.FailedApiResponse;
import io.swagger.v3.oas.annotations.security.OAuthFlow;
import io.swagger.v3.oas.annotations.security.OAuthFlows;
import io.swagger.v3.oas.annotations.security.OAuthScope;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirementEntry;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.security.SecuritySchemes;
import io.swagger.v3.oas.annotations.servers.Server;
import io.swagger.v3.oas.annotations.servers.ServerVariable;
import io.swagger.v3.oas.annotations.servers.Servers;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.tags.Tags;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class Swagger_annotations_jakartaTest {
    @Test
    void readsOpenApiDefinitionAndClassLevelContainers() {
        OpenAPIDefinition definition = annotation(AnnotatedInventoryApi.class, OpenAPIDefinition.class);

        assertThat(definition.info().title()).isEqualTo("Inventory API");
        assertThat(definition.info().summary()).isEqualTo("Inventory operations");
        assertThat(definition.info().contact().email()).isEqualTo("support@example.test");
        assertThat(definition.info().license().identifier()).isEqualTo("Apache-2.0");
        assertThat(definition.tags()).extracting(Tag::name).containsExactly("inventory", "admin");
        assertThat(definition.servers()).hasSize(1);
        assertThat(definition.servers()[0].variables()[0].allowableValues()).containsExactly("dev", "prod");
        assertThat(definition.security()[0].combine()[0].scopes()).containsExactly("items:read", "items:write");
        assertThat(definition.externalDocs().url()).isEqualTo("https://docs.example.test/inventory");
        assertThat(definition.extensions()[0].properties()[0].parseValue()).isTrue();

        SecuritySchemes securitySchemes = annotation(AnnotatedInventoryApi.class, SecuritySchemes.class);
        assertThat(securitySchemes.value())
                .extracting(SecurityScheme::name)
                .containsExactly("bearerAuth", "apiKeyAuth");
        assertThat(securitySchemes.value()[0].type()).isEqualTo(SecuritySchemeType.HTTP);
        assertThat(securitySchemes.value()[0].flows().authorizationCode().scopes()[0].name()).isEqualTo("items:read");
        assertThat(securitySchemes.value()[1].in()).isEqualTo(SecuritySchemeIn.HEADER);

        Webhooks webhooks = annotation(AnnotatedInventoryApi.class, Webhooks.class);
        assertThat(webhooks.value()).hasSize(1);
        assertThat(webhooks.value()[0].operation().requestBody().content()[0].schema().implementation())
                .isEqualTo(WebhookEvent.class);

        assertThat(annotation(AnnotatedInventoryApi.class, Hidden.class)).isNotNull();
    }

    @Test
    void readsOperationParametersResponsesCallbacksAndLinks() throws NoSuchMethodException {
        Method method = AnnotatedInventoryApi.class.getDeclaredMethod("readItem", String.class, ItemUpdate.class);
        Operation operation = annotation(method, Operation.class);

        assertThat(annotation(method, OpenAPI31.class)).isNotNull();
        assertThat(operation.method()).isEqualTo("GET");
        assertThat(operation.tags()).containsExactly("inventory");
        assertThat(operation.operationId()).isEqualTo("readItem");
        assertThat(operation.ignoreJsonView()).isTrue();
        assertThat(operation.parameters()).hasSize(2);
        assertThat(operation.parameters()[0].in()).isEqualTo(ParameterIn.PATH);
        assertThat(operation.parameters()[0].schema().pattern()).isEqualTo("[A-Z0-9-]+");
        assertThat(operation.parameters()[1].style()).isEqualTo(ParameterStyle.FORM);
        assertThat(operation.parameters()[1].explode()).isEqualTo(Explode.TRUE);
        assertThat(operation.parameters()[1].array().schema().allowableValues()).containsExactly("owner", "audit");
        assertThat(operation.requestBody().required()).isTrue();
        assertThat(operation.requestBody().content()[0].schema().implementation()).isEqualTo(ItemUpdate.class);
        assertThat(operation.responses()).extracting(ApiResponse::responseCode).containsExactly("200", "404");
        assertThat(operation.responses()[0].headers()[0].schema().type()).isEqualTo("string");
        assertThat(operation.responses()[0].links()[0].parameters()[0].expression())
                .isEqualTo("$response.body#/ownerId");
        assertThat(operation.security()[0].scopes()).containsExactly("items:read");
        assertThat(operation.servers()[0].url()).isEqualTo("https://inventory.example.test");

        Callbacks callbacks = annotation(method, Callbacks.class);
        assertThat(callbacks.value()[0].callbackUrlExpression()).isEqualTo("{$request.body#/callbackUrl}");
        assertThat(callbacks.value()[0].operation()[0].responses()[0].responseCode()).isEqualTo("202");

        Parameters parameters = annotation(method, Parameters.class);
        assertThat(parameters.value()).extracting(Parameter::name).containsExactly("traceId");

        ApiResponses apiResponses = annotation(method, ApiResponses.class);
        assertThat(apiResponses.value()).extracting(ApiResponse::responseCode).containsExactly("400");
        assertThat(apiResponses.extensions()[0].name()).isEqualTo("x-response-group");

        FailedApiResponse failedResponse = annotation(method, FailedApiResponse.class);
        assertThat(failedResponse.ref()).isEqualTo("#/components/responses/ServerError");
    }

    @Test
    void readsSchemaRichJsonSchemaAndMediaAnnotations() throws NoSuchFieldException, NoSuchMethodException {
        Schema itemSchema = annotation(InventoryItem.class, Schema.class);

        assertThat(itemSchema.name()).isEqualTo("InventoryItem");
        assertThat(itemSchema.requiredProperties()).containsExactly("id", "name");
        assertThat(itemSchema.discriminatorMapping()[0].schema()).isEqualTo(BookItem.class);
        assertThat(itemSchema.additionalProperties()).isEqualTo(Schema.AdditionalPropertiesValue.FALSE);
        assertThat(itemSchema.schemaResolution()).isEqualTo(Schema.SchemaResolution.ALL_OF);
        assertThat(itemSchema.properties()[0].value()).isEqualTo(ItemMetadata.class);
        assertThat(itemSchema.dependentRequiredMap()[0].value()).containsExactly("billingAddress");
        assertThat(itemSchema.patternProperties()[0].key()).isEqualTo("^x-");
        assertThat(itemSchema.examples()).containsExactly("{\"id\":\"A-1\"}");
        assertThat(itemSchema.accessMode()).isEqualTo(Schema.AccessMode.READ_ONLY);
        assertThat(itemSchema._const()).isEqualTo("inventory");

        Field tags = InventoryItem.class.getDeclaredField("tags");
        ArraySchema arraySchema = annotation(tags, ArraySchema.class);
        assertThat(arraySchema.minItems()).isEqualTo(1);
        assertThat(arraySchema.maxItems()).isEqualTo(5);
        assertThat(arraySchema.uniqueItems()).isTrue();
        assertThat(arraySchema.prefixItems()[0].type()).isEqualTo("string");
        assertThat(arraySchema.contains().pattern()).isEqualTo("[a-z-]+");
        assertThat(arraySchema.unevaluatedItems().hidden()).isTrue();

        Method method = AnnotatedInventoryApi.class.getDeclaredMethod("readItem", String.class, ItemUpdate.class);
        Content content = annotation(method, Operation.class).responses()[0].content()[0];
        assertThat(content.mediaType()).isEqualTo("application/vnd.inventory+json");
        assertThat(content.schemaProperties()[0].name()).isEqualTo("metadata");
        assertThat(content.additionalPropertiesArraySchema().schema().type()).isEqualTo("string");
        assertThat(content.dependentSchemas()[0].schema().implementation()).isEqualTo(BillingAddress.class);
        assertThat(content._then().description()).isEqualTo("Payload for stocked items");
        assertThat(content.not().implementation()).isEqualTo(DeprecatedPayload.class);

        assertThat(annotation(InventoryItem.class, SchemaProperties.class).value()[0].name()).isEqualTo("stock");
        assertThat(annotation(InventoryItem.class, PatternProperties.class).value()[0].regex()).isEqualTo("^flag-");
        assertThat(annotation(InventoryItem.class, DependentSchemas.class).value()[0].name()).isEqualTo("address");
        assertThat(annotation(InventoryItem.class, DependentRequiredMap.class).value()[0].name()).isEqualTo("payment");
    }

    @Test
    void readsRequestBodyAndContentParameterAnnotationsAppliedToMethodParameters() throws NoSuchMethodException {
        Method method = AnnotatedInventoryApi.class.getDeclaredMethod(
                "submitMultipartItem", MultipartItemUpload.class, String.class);
        AnnotatedElement uploadParameter = method.getParameters()[0];
        AnnotatedElement filterParameter = method.getParameters()[1];

        RequestBody requestBody = annotation(uploadParameter, RequestBody.class);
        assertThat(requestBody.description()).isEqualTo("Multipart upload request");
        assertThat(requestBody.required()).isTrue();
        assertThat(requestBody.useParameterTypeSchema()).isTrue();
        assertThat(requestBody.content()[0].mediaType()).isEqualTo("multipart/form-data");
        assertThat(requestBody.content()[0].schema().implementation()).isEqualTo(MultipartItemUpload.class);
        assertThat(requestBody.content()[0].encoding()[0].name()).isEqualTo("metadata");
        assertThat(requestBody.extensions()[0].properties()[0].parseValue()).isTrue();

        Parameter filter = annotation(filterParameter, Parameter.class);
        assertThat(filter.name()).isEqualTo("filter");
        assertThat(filter.in()).isEqualTo(ParameterIn.QUERY);
        assertThat(filter.deprecated()).isTrue();
        assertThat(filter.allowEmptyValue()).isTrue();
        assertThat(filter.content()[0].mediaType()).isEqualTo("application/json");
        assertThat(filter.content()[0].schema().implementation()).isEqualTo(SearchFilter.class);
        assertThat(filter.content()[0].examples()[0].name()).isEqualTo("active-filter");
    }

    @Test
    void readsRepeatableAnnotationsDeclaredWithoutContainerAnnotations() throws NoSuchMethodException {
        Tag[] tags = RepeatableAnnotatedApi.class.getAnnotationsByType(Tag.class);
        assertThat(tags).extracting(Tag::name).containsExactly("catalog", "pricing");
        assertThat(tags[0].description()).isEqualTo("Catalog operations");
        assertThat(tags[1].externalDocs().description()).isEqualTo("Pricing guide");

        SecurityScheme[] securitySchemes = RepeatableAnnotatedApi.class.getAnnotationsByType(SecurityScheme.class);
        assertThat(securitySchemes).extracting(SecurityScheme::name).containsExactly("openId", "clientCertificate");
        assertThat(securitySchemes[0].type()).isEqualTo(SecuritySchemeType.OPENIDCONNECT);
        assertThat(securitySchemes[0].openIdConnectUrl())
                .isEqualTo("https://auth.example.test/.well-known/openid-configuration");
        assertThat(securitySchemes[1].type()).isEqualTo(SecuritySchemeType.MUTUALTLS);
        assertThat(securitySchemes[1].ref()).isEqualTo("#/components/securitySchemes/clientCertificate");

        Method method = RepeatableAnnotatedApi.class.getDeclaredMethod("searchCatalog");
        Server[] servers = method.getAnnotationsByType(Server.class);
        assertThat(servers).extracting(Server::url)
                .containsExactly("https://catalog.example.test", "https://catalog-backup.example.test");

        SecurityRequirement[] requirements = method.getAnnotationsByType(SecurityRequirement.class);
        assertThat(requirements).extracting(SecurityRequirement::name).containsExactly("openId", "clientCertificate");
        assertThat(requirements[0].scopes()).containsExactly("catalog:read");

        Extension[] extensions = method.getAnnotationsByType(Extension.class);
        assertThat(extensions).extracting(Extension::name).containsExactly("x-method", "x-owner");
        assertThat(extensions[0].properties()[0].value()).isEqualTo("beta");
        assertThat(extensions[1].properties()[0].value()).isEqualTo("catalog-team");
    }

    @Test
    void exposesEnumConstantsAndParameterValidationGroups() throws NoSuchMethodException {
        assertThat(ParameterIn.values()).containsExactly(
                ParameterIn.DEFAULT, ParameterIn.HEADER, ParameterIn.QUERY, ParameterIn.PATH, ParameterIn.COOKIE);
        assertThat(ParameterStyle.values()).contains(
                ParameterStyle.DEFAULT,
                ParameterStyle.MATRIX,
                ParameterStyle.LABEL,
                ParameterStyle.FORM,
                ParameterStyle.SPACEDELIMITED,
                ParameterStyle.PIPEDELIMITED,
                ParameterStyle.DEEPOBJECT,
                ParameterStyle.SIMPLE);
        assertThat(SecuritySchemeType.valueOf("OAUTH2")).isEqualTo(SecuritySchemeType.OAUTH2);
        assertThat(Schema.RequiredMode.valueOf("REQUIRED")).isEqualTo(Schema.RequiredMode.REQUIRED);
        assertThat(Schema.AccessMode.valueOf("READ_WRITE")).isEqualTo(Schema.AccessMode.READ_WRITE);
        assertThat(Schema.AdditionalPropertiesValue.valueOf("USE_ADDITIONAL_PROPERTIES_ANNOTATION"))
                .isEqualTo(Schema.AdditionalPropertiesValue.USE_ADDITIONAL_PROPERTIES_ANNOTATION);
        assertThat(Schema.SchemaResolution.valueOf("ALL_OF_REF"))
                .isEqualTo(Schema.SchemaResolution.ALL_OF_REF);

        Method method = AnnotatedInventoryApi.class.getDeclaredMethod("readItem", String.class, ItemUpdate.class);
        AnnotatedElement idParameter = method.getParameters()[0];
        ValidatedParameter validatedParameter = annotation(idParameter, ValidatedParameter.class);
        Parameter swaggerParameter = annotation(idParameter, Parameter.class);

        assertThat(validatedParameter.value()).containsExactly(ValidationGroup.class);
        assertThat(swaggerParameter.validationGroups()).containsExactly(ValidationGroup.class);
        assertThat(swaggerParameter.example()).isEqualTo("SKU-1");
    }

    private static <A extends Annotation> A annotation(AnnotatedElement elementAnnotationAccess, Class<A> annotationType) {
        A annotation = elementAnnotationAccess.getAnnotation(annotationType);
        assertThat(annotation).isNotNull();
        return annotation;
    }

    @Hidden
    @OpenAPIDefinition(
            info = @Info(
                    title = "Inventory API",
                    summary = "Inventory operations",
                    description = "Operations for inventory items",
                    termsOfService = "https://example.test/terms",
                    contact = @Contact(
                            name = "Support",
                            url = "https://example.test/support",
                            email = "support@example.test"),
                    license = @License(
                            name = "Apache License",
                            url = "https://www.apache.org/licenses/LICENSE-2.0",
                            identifier = "Apache-2.0"),
                    version = "v1",
                    extensions = @Extension(
                            name = "x-info",
                            properties = @ExtensionProperty(name = "audience", value = "internal"))),
            tags = {
                    @Tag(
                            name = "inventory",
                            description = "Item operations",
                            externalDocs = @ExternalDocumentation(url = "https://docs.example.test/tags/inventory")),
                    @Tag(name = "admin", description = "Administrative operations")
            },
            servers = @Server(
                    url = "https://{environment}.inventory.example.test",
                    description = "Environment server",
                    variables = @ServerVariable(
                            name = "environment",
                            allowableValues = {"dev", "prod"},
                            defaultValue = "dev",
                            description = "Deployment environment")),
            security = @SecurityRequirement(
                    name = "oauth",
                    scopes = "items:read",
                    combine = @SecurityRequirementEntry(name = "apiKeyAuth", scopes = {"items:read", "items:write"})),
            externalDocs = @ExternalDocumentation(
                    description = "Inventory API guide",
                    url = "https://docs.example.test/inventory"),
            extensions = @Extension(
                    name = "x-openapi",
                    properties = @ExtensionProperty(name = "stable", value = "true", parseValue = true)))
    @SecuritySchemes({
            @SecurityScheme(
                    name = "bearerAuth",
                    type = SecuritySchemeType.HTTP,
                    scheme = "bearer",
                    bearerFormat = "JWT",
                    flows = @OAuthFlows(authorizationCode = @OAuthFlow(
                            authorizationUrl = "https://auth.example.test/authorize",
                            tokenUrl = "https://auth.example.test/token",
                            refreshUrl = "https://auth.example.test/refresh",
                            scopes = @OAuthScope(name = "items:read", description = "Read items"))),
                    extensions = @Extension(
                            name = "x-security",
                            properties = @ExtensionProperty(name = "issuer", value = "auth"))),
            @SecurityScheme(
                    name = "apiKeyAuth",
                    type = SecuritySchemeType.APIKEY,
                    paramName = "X-API-Key",
                    in = SecuritySchemeIn.HEADER,
                    description = "API key authentication")
    })
    @SecurityRequirements(@SecurityRequirement(name = "bearerAuth", scopes = "items:read"))
    @Servers(@Server(url = "https://fallback.example.test"))
    @Tags(@Tag(name = "class-tag", description = "Container tag"))
    @Extensions(@Extension(name = "x-class", properties = @ExtensionProperty(name = "scope", value = "test")))
    @Webhooks(@Webhook(
            name = "inventory.created",
            operation = @Operation(
                    summary = "Inventory created webhook",
                    requestBody = @RequestBody(
                            content = @Content(schema = @Schema(implementation = WebhookEvent.class))),
                    responses = @ApiResponse(responseCode = "200", description = "Webhook accepted"))))
    private static final class AnnotatedInventoryApi {
        @OpenAPI31
        @Operation(
                method = "GET",
                tags = "inventory",
                summary = "Read an item",
                description = "Reads an inventory item by id",
                operationId = "readItem",
                parameters = {
                        @Parameter(
                                name = "itemId",
                                in = ParameterIn.PATH,
                                description = "Inventory item id",
                                required = true,
                                schema = @Schema(
                                        type = "string",
                                        minLength = 3,
                                        maxLength = 32,
                                        pattern = "[A-Z0-9-]+"),
                                examples = @ExampleObject(name = "sample", value = "SKU-1")),
                        @Parameter(
                                name = "expand",
                                in = ParameterIn.QUERY,
                                description = "Additional projections",
                                style = ParameterStyle.FORM,
                                explode = Explode.TRUE,
                                allowReserved = true,
                                array = @ArraySchema(
                                        schema = @Schema(type = "string", allowableValues = {"owner", "audit"})))
                },
                requestBody = @RequestBody(
                        description = "Update to apply before reading",
                        required = true,
                        useParameterTypeSchema = true,
                        content = @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ItemUpdate.class),
                                examples = @ExampleObject(name = "update", value = "{\"name\":\"new\"}"))),
                responses = {
                        @ApiResponse(
                                responseCode = "200",
                                description = "Item found",
                                useReturnTypeSchema = true,
                                headers = @Header(
                                        name = "ETag",
                                        description = "Entity tag",
                                        required = true,
                                        schema = @Schema(type = "string"),
                                        example = "abc123"),
                                links = @Link(
                                        name = "owner",
                                        operationId = "readOwner",
                                        parameters = @LinkParameter(
                                                name = "ownerId",
                                                expression = "$response.body#/ownerId"),
                                        requestBody = "$request.body",
                                        server = @Server(url = "https://owners.example.test")),
                                content = @Content(
                                        mediaType = "application/vnd.inventory+json",
                                        examples = @ExampleObject(
                                                name = "payload",
                                                summary = "Payload example",
                                                description = "A payload example",
                                                value = "{}",
                                                externalValue = "https://example.test/payload.json",
                                                ref = "#/components/examples/Payload"),
                                        schema = @Schema(implementation = InventoryItem.class),
                                        schemaProperties = @SchemaProperty(
                                                name = "metadata",
                                                schema = @Schema(implementation = ItemMetadata.class)),
                                        additionalPropertiesSchema = @Schema(implementation = ItemMetadata.class),
                                        additionalPropertiesArraySchema = @ArraySchema(
                                                schema = @Schema(type = "string")),
                                        array = @ArraySchema(schema = @Schema(implementation = ItemPayload.class)),
                                        encoding = @Encoding(
                                                name = "metadata",
                                                contentType = "application/json",
                                                style = "form",
                                                explode = true,
                                                allowReserved = true,
                                                headers = @Header(
                                                        name = "X-Encoding",
                                                        schema = @Schema(type = "string"))),
                                        extensions = @Extension(
                                                name = "x-content",
                                                properties = @ExtensionProperty(name = "media", value = "inventory")),
                                        dependentSchemas = @DependentSchema(
                                                name = "billing",
                                                schema = @Schema(implementation = BillingAddress.class)),
                                        contentSchema = @Schema(implementation = ItemPayload.class),
                                        propertyNames = @Schema(type = "string"),
                                        _if = @Schema(implementation = ItemCondition.class),
                                        _then = @Schema(description = "Payload for stocked items"),
                                        _else = @Schema(description = "Payload for unstocked items"),
                                        not = @Schema(implementation = DeprecatedPayload.class),
                                        oneOf = @Schema(implementation = ItemPayload.class),
                                        anyOf = @Schema(implementation = ItemUpdate.class),
                                        allOf = @Schema(implementation = InventoryItem.class))),
                        @ApiResponse(
                                responseCode = "404",
                                description = "Item not found",
                                ref = "#/components/responses/NotFound")
                },
                security = @SecurityRequirement(name = "bearerAuth", scopes = "items:read"),
                servers = @Server(url = "https://inventory.example.test"),
                extensions = @Extension(
                        name = "x-operation",
                        properties = @ExtensionProperty(name = "cached", value = "false", parseValue = true)),
                ignoreJsonView = true)
        @Callbacks(@Callback(
                name = "onInventoryRead",
                callbackUrlExpression = "{$request.body#/callbackUrl}",
                operation = @Operation(
                        summary = "Read callback",
                        responses = @ApiResponse(responseCode = "202", description = "Callback accepted")),
                extensions = @Extension(
                        name = "x-callback",
                        properties = @ExtensionProperty(name = "async", value = "true", parseValue = true))))
        @Parameters(@Parameter(name = "traceId", in = ParameterIn.HEADER, description = "Trace identifier"))
        @ApiResponses(
                value = @ApiResponse(responseCode = "400", description = "Bad request"),
                extensions = @Extension(
                        name = "x-response-group",
                        properties = @ExtensionProperty(name = "group", value = "client-errors")))
        @FailedApiResponse(
                ref = "#/components/responses/ServerError",
                extensions = @Extension(
                        name = "x-failed",
                        properties = @ExtensionProperty(name = "handled", value = "true")))
        ItemUpdate readItem(
                @ValidatedParameter(ValidationGroup.class)
                @Parameter(name = "itemId", example = "SKU-1", validationGroups = ValidationGroup.class) String itemId,
                ItemUpdate update) {
            return update;
        }

        void submitMultipartItem(
                @RequestBody(
                        description = "Multipart upload request",
                        required = true,
                        useParameterTypeSchema = true,
                        content = @Content(
                                mediaType = "multipart/form-data",
                                schema = @Schema(implementation = MultipartItemUpload.class),
                                encoding = @Encoding(name = "metadata", contentType = "application/json")),
                        extensions = @Extension(
                                name = "x-upload",
                                properties = @ExtensionProperty(name = "streamed", value = "true", parseValue = true)))
                        MultipartItemUpload upload,
                @Parameter(
                        name = "filter",
                        in = ParameterIn.QUERY,
                        description = "Structured upload filter",
                        deprecated = true,
                        allowEmptyValue = true,
                        content = @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = SearchFilter.class),
                                examples = @ExampleObject(name = "active-filter", value = "{\"active\":true}")))
                        String filter) {
        }
    }

    @Schema(
            name = "InventoryItem",
            title = "Inventory item",
            description = "Item stored in inventory",
            implementation = InventoryItem.class,
            oneOf = {BookItem.class},
            anyOf = {InventoryItem.class, BookItem.class},
            allOf = {InventoryItem.class},
            requiredProperties = {"id", "name"},
            multipleOf = 1.0,
            maximum = "1000",
            exclusiveMaximum = true,
            minimum = "1",
            exclusiveMinimum = true,
            maxProperties = 10,
            minProperties = 1,
            requiredMode = Schema.RequiredMode.REQUIRED,
            format = "inventory-id",
            nullable = false,
            accessMode = Schema.AccessMode.READ_ONLY,
            example = "{\"id\":\"A-1\"}",
            examples = "{\"id\":\"A-1\"}",
            externalDocs = @ExternalDocumentation(url = "https://docs.example.test/schemas/item"),
            type = "object",
            allowableValues = {"book", "tool"},
            defaultValue = "book",
            discriminatorProperty = "kind",
            discriminatorMapping = @DiscriminatorMapping(value = "book", schema = BookItem.class),
            enumAsRef = true,
            subTypes = BookItem.class,
            extensions = @Extension(
                    name = "x-schema",
                    properties = @ExtensionProperty(name = "owner", value = "inventory")),
            prefixItems = String.class,
            types = {"object"},
            exclusiveMaximumValue = 1000,
            exclusiveMinimumValue = 1,
            contains = String.class,
            $id = "https://example.test/schemas/inventory-item",
            $schema = "https://json-schema.org/draft/2020-12/schema",
            $anchor = "InventoryItem",
            $vocabulary = "https://json-schema.org/draft/2020-12/vocab/core",
            $dynamicAnchor = "InventoryDynamic",
            $dynamicRef = "#InventoryDynamic",
            contentEncoding = "base64",
            contentMediaType = "application/json",
            contentSchema = ItemPayload.class,
            propertyNames = String.class,
            maxContains = 3,
            minContains = 1,
            additionalItems = String.class,
            unevaluatedItems = String.class,
            _if = ItemCondition.class,
            _else = ItemPayload.class,
            then = ItemUpdate.class,
            $comment = "schema comment",
            exampleClasses = BookItem.class,
            additionalProperties = Schema.AdditionalPropertiesValue.FALSE,
            dependentRequiredMap = @DependentRequired(name = "creditCard", value = "billingAddress"),
            dependentSchemas = @StringToClassMapItem(key = "billingAddress", value = BillingAddress.class),
            patternProperties = @StringToClassMapItem(key = "^x-", value = String.class),
            properties = @StringToClassMapItem(key = "metadata", value = ItemMetadata.class),
            unevaluatedProperties = String.class,
            additionalPropertiesSchema = ItemMetadata.class,
            _const = "inventory",
            schemaResolution = Schema.SchemaResolution.ALL_OF)
    @SchemaProperties(@SchemaProperty(name = "stock", schema = @Schema(type = "integer", minimum = "0")))
    @PatternProperties(@PatternProperty(regex = "^flag-", schema = @Schema(type = "boolean")))
    @DependentSchemas(@DependentSchema(name = "address", schema = @Schema(implementation = BillingAddress.class)))
    @DependentRequiredMap(@DependentRequired(name = "payment", value = {"billingAddress", "cardHolder"}))
    private static final class InventoryItem {
        @ArraySchema(
                schema = @Schema(type = "string", description = "Tag value"),
                arraySchema = @Schema(type = "array"),
                minItems = 1,
                maxItems = 5,
                uniqueItems = true,
                extensions = @Extension(
                        name = "x-tags",
                        properties = @ExtensionProperty(name = "normalized", value = "true")),
                contains = @Schema(type = "string", pattern = "[a-z-]+"),
                maxContains = 2,
                minContains = 1,
                unevaluatedItems = @Schema(hidden = true),
                prefixItems = @Schema(type = "string"))
        private List<String> tags;
    }

    @Tag(name = "catalog", description = "Catalog operations")
    @Tag(
            name = "pricing",
            description = "Pricing operations",
            externalDocs = @ExternalDocumentation(description = "Pricing guide", url = "https://docs.example.test/pricing"))
    @SecurityScheme(
            name = "openId",
            type = SecuritySchemeType.OPENIDCONNECT,
            openIdConnectUrl = "https://auth.example.test/.well-known/openid-configuration")
    @SecurityScheme(
            name = "clientCertificate",
            type = SecuritySchemeType.MUTUALTLS,
            ref = "#/components/securitySchemes/clientCertificate")
    private static final class RepeatableAnnotatedApi {
        @Server(url = "https://catalog.example.test", description = "Primary catalog server")
        @Server(url = "https://catalog-backup.example.test", description = "Backup catalog server")
        @SecurityRequirement(name = "openId", scopes = "catalog:read")
        @SecurityRequirement(name = "clientCertificate")
        @Extension(name = "x-method", properties = @ExtensionProperty(name = "release", value = "beta"))
        @Extension(name = "x-owner", properties = @ExtensionProperty(name = "team", value = "catalog-team"))
        void searchCatalog() {
        }
    }

    private interface ValidationGroup {
    }

    private static final class ItemUpdate {
    }

    private static final class MultipartItemUpload {
    }

    private static final class SearchFilter {
    }

    private static final class WebhookEvent {
    }

    private static final class BookItem {
    }

    private static final class ItemMetadata {
    }

    private static final class BillingAddress {
    }

    private static final class ItemPayload {
    }

    private static final class ItemCondition {
    }

    private static final class DeprecatedPayload {
    }
}
