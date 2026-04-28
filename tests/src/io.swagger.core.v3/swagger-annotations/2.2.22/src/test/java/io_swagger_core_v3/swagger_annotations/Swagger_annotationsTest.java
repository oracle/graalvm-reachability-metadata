/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_swagger_core_v3.swagger_annotations;

import static org.assertj.core.api.Assertions.assertThat;

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
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.OAuthFlow;
import io.swagger.v3.oas.annotations.security.OAuthFlows;
import io.swagger.v3.oas.annotations.security.OAuthScope;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.security.SecuritySchemes;
import io.swagger.v3.oas.annotations.servers.Server;
import io.swagger.v3.oas.annotations.servers.Servers;
import io.swagger.v3.oas.annotations.servers.ServerVariable;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.tags.Tags;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.Test;

public class Swagger_annotationsTest {
    @Test
    void readsOpenApiDefinitionWithInfoServersSecurityTagsAndExtensions() {
        OpenAPIDefinition definition = annotation(AnnotatedApi.class, OpenAPIDefinition.class);

        assertThat(definition.info().title()).isEqualTo("Pet Store");
        assertThat(definition.info().summary()).isEqualTo("Pet Store API summary");
        assertThat(definition.info().description()).isEqualTo("API described entirely with swagger annotations");
        assertThat(definition.info().termsOfService()).isEqualTo("https://example.test/terms");
        assertThat(definition.info().version()).isEqualTo("2024.06");
        assertThat(definition.info().contact().name()).isEqualTo("API support");
        assertThat(definition.info().contact().url()).isEqualTo("https://example.test/support");
        assertThat(definition.info().contact().email()).isEqualTo("support@example.test");
        assertThat(definition.info().license().name()).isEqualTo("Apache-2.0");
        assertThat(definition.info().license().identifier()).isEqualTo("Apache-2.0");
        assertThat(definition.info().extensions()[0].properties()[0].parseValue()).isTrue();

        assertThat(definition.tags()).hasSize(2);
        assertThat(definition.tags()[0].name()).isEqualTo("pets");
        assertThat(definition.tags()[0].externalDocs().url()).isEqualTo("https://example.test/docs/pets");
        assertThat(definition.servers()[0].url()).isEqualTo("https://{region}.api.example.test/{basePath}");
        assertThat(definition.servers()[0].variables())
                .extracting(ServerVariable::name)
                .containsExactly("region", "basePath");
        assertThat(definition.security()[0].name()).isEqualTo("oauth");
        assertThat(definition.security()[0].scopes()).containsExactly("pet:read", "pet:write");
        assertThat(definition.externalDocs().description()).isEqualTo("Complete API guide");
        assertThat(definition.extensions()[0].name()).isEqualTo("x-definition");
    }

    @Test
    void readsRepeatableClassLevelContainersAndOpenApi31Webhooks() {
        SecuritySchemes securitySchemes = annotation(AnnotatedApi.class, SecuritySchemes.class);
        SecurityScheme oauth = securitySchemes.value()[0];
        SecurityScheme apiKey = securitySchemes.value()[1];
        Tags tags = annotation(AnnotatedApi.class, Tags.class);
        Webhooks webhooks = annotation(AnnotatedApi.class, Webhooks.class);

        assertThat(oauth.name()).isEqualTo("oauth");
        assertThat(oauth.type()).isEqualTo(SecuritySchemeType.OAUTH2);
        assertThat(oauth.flows().authorizationCode().authorizationUrl())
                .isEqualTo("https://auth.example.test/authorize");
        assertThat(oauth.flows().authorizationCode().tokenUrl()).isEqualTo("https://auth.example.test/token");
        assertThat(oauth.flows().authorizationCode().refreshUrl()).isEqualTo("https://auth.example.test/refresh");
        assertThat(oauth.flows().authorizationCode().scopes())
                .extracting(OAuthScope::name)
                .containsExactly("pet:read", "pet:write");
        assertThat(apiKey.type()).isEqualTo(SecuritySchemeType.APIKEY);
        assertThat(apiKey.in()).isEqualTo(SecuritySchemeIn.HEADER);
        assertThat(apiKey.paramName()).isEqualTo("X-API-Key");

        assertThat(tags.value()).extracting(Tag::name).containsExactly("inventory", "orders");
        assertThat(webhooks.value()).hasSize(1);
        assertThat(webhooks.value()[0].name()).isEqualTo("petStatusChanged");
        assertThat(webhooks.value()[0].operation().method()).isEqualTo("POST");
        assertThat(webhooks.value()[0].operation().responses()[0].responseCode()).isEqualTo("204");
    }

    @Test
    void readsStandaloneRepeatableAnnotationsAndOpenApi31DependentMaps() {
        Servers servers = annotation(RepeatableApiMetadata.class, Servers.class);
        SecurityRequirements requirements = annotation(RepeatableApiMetadata.class, SecurityRequirements.class);
        Extensions extensions = annotation(RepeatableApiMetadata.class, Extensions.class);
        DependentRequiredMap dependentRequiredMap = annotation(RepeatableApiMetadata.class, DependentRequiredMap.class);
        DependentSchemas dependentSchemas = annotation(RepeatableApiMetadata.class, DependentSchemas.class);

        assertThat(servers.value()).extracting(Server::url)
                .containsExactly("https://primary.example.test", "https://{tenant}.example.test");
        assertThat(servers.value()[1].variables()[0].name()).isEqualTo("tenant");
        assertThat(servers.value()[1].variables()[0].defaultValue()).isEqualTo("demo");

        assertThat(requirements.value()).extracting(SecurityRequirement::name).containsExactly("oauth", "api-key");
        assertThat(requirements.value()[0].scopes()).containsExactly("catalog:read", "catalog:write");
        assertThat(requirements.value()[1].scopes()).isEmpty();

        assertThat(extensions.value()).extracting(Extension::name).containsExactly("x-catalog", "x-owner");
        assertThat(extensions.value()[0].properties()[0].name()).isEqualTo("enabled");
        assertThat(extensions.value()[0].properties()[0].parseValue()).isTrue();
        assertThat(extensions.value()[1].properties()[0].value()).isEqualTo("metadata-team");

        assertThat(dependentRequiredMap.value()).extracting(DependentRequired::name)
                .containsExactly("shipping", "billing");
        assertThat(dependentRequiredMap.value()[0].value()).containsExactly("street", "postalCode");
        assertThat(dependentRequiredMap.value()[1].value()).containsExactly("accountId");

        assertThat(dependentSchemas.value()).extracting(DependentSchema::name).containsExactly("shipping", "billing");
        assertThat(dependentSchemas.value()[0].schema().implementation()).isEqualTo(Address.class);
        assertThat(dependentSchemas.value()[1].schema().implementation()).isEqualTo(BillingProfile.class);
    }

    @Test
    void readsOperationRequestResponseParameterCallbackAndMethodAnnotations() throws NoSuchMethodException {
        Method method = AnnotatedApi.class.getDeclaredMethod("replacePet", String.class, Pet.class);

        assertThat(annotation(method, OpenAPI31.class)).isNotNull();
        Operation operation = annotation(method, Operation.class);
        assertThat(operation.method()).isEqualTo("PUT");
        assertThat(operation.tags()).containsExactly("pets", "write");
        assertThat(operation.summary()).isEqualTo("Replace a pet");
        assertThat(operation.description()).contains("request body");
        assertThat(operation.operationId()).isEqualTo("replacePet");
        assertThat(operation.deprecated()).isFalse();
        assertThat(operation.hidden()).isFalse();
        assertThat(operation.ignoreJsonView()).isTrue();
        assertThat(operation.externalDocs().url()).isEqualTo("https://example.test/docs/replace-pet");

        Parameter pathParameter = operation.parameters()[0];
        assertThat(pathParameter.name()).isEqualTo("petId");
        assertThat(pathParameter.in()).isEqualTo(ParameterIn.PATH);
        assertThat(pathParameter.required()).isTrue();
        assertThat(pathParameter.style()).isEqualTo(ParameterStyle.SIMPLE);
        assertThat(pathParameter.explode()).isEqualTo(Explode.FALSE);
        assertThat(pathParameter.schema().implementation()).isEqualTo(String.class);
        assertThat(pathParameter.schema().allowableValues()).containsExactly("alpha", "beta");

        RequestBody body = operation.requestBody();
        assertThat(body.description()).isEqualTo("Replacement pet payload");
        assertThat(body.required()).isTrue();
        assertThat(body.useParameterTypeSchema()).isTrue();
        assertThat(body.content()[0].mediaType()).isEqualTo("application/json");
        assertThat(body.content()[0].schema().implementation()).isEqualTo(Pet.class);
        assertThat(body.content()[0].examples()[0].name()).isEqualTo("dog");
        assertThat(body.content()[0].encoding()[0].headers()[0].name()).isEqualTo("X-Encoding");

        ApiResponse ok = operation.responses()[0];
        assertThat(ok.responseCode()).isEqualTo("200");
        assertThat(ok.useReturnTypeSchema()).isTrue();
        assertThat(ok.content()[0].schemaProperties()[0].name()).isEqualTo("pet");
        assertThat(ok.content()[0].dependentSchemas()[0].name()).isEqualTo("collar");
        assertThat(ok.headers()[0].schema().type()).isEqualTo("string");
        assertThat(ok.links()[0].parameters()[0].expression()).isEqualTo("$response.body#/id");

        assertThat(operation.security()[0].name()).isEqualTo("oauth");
        assertThat(operation.servers()[0].variables()[0].defaultValue()).isEqualTo("v2");
        assertThat(operation.extensions()[0].properties()[0].value()).isEqualTo("true");

        assertThat(annotation(method, Parameters.class).value()[0].name()).isEqualTo("traceId");
        assertThat(annotation(method, ApiResponses.class).value()[0].ref())
                .isEqualTo("#/components/responses/Unauthorized");
        assertThat(annotation(method, Callbacks.class).value()[0].callbackUrlExpression())
                .isEqualTo("{$request.body#/callbackUrl}");
    }

    @Test
    void readsParameterAnnotationsAppliedDirectlyToMethodParameters() throws NoSuchMethodException {
        Method method = AnnotatedApi.class.getDeclaredMethod("replacePet", String.class, Pet.class);
        Annotation[][] annotations = method.getParameterAnnotations();

        Parameter idParameter = firstAnnotation(annotations[0], Parameter.class);
        RequestBody requestBody = firstAnnotation(annotations[1], RequestBody.class);

        assertThat(idParameter.name()).isEqualTo("id");
        assertThat(idParameter.in()).isEqualTo(ParameterIn.PATH);
        assertThat(idParameter.example()).isEqualTo("alpha");
        assertThat(idParameter.extensions()[0].properties()[0].name()).isEqualTo("x-parameter-source");
        assertThat(requestBody.description()).isEqualTo("Method parameter request body");
        assertThat(requestBody.content()[0].array().items().implementation()).isEqualTo(Pet.class);
    }

    @Test
    void readsComprehensiveSchemaAndArraySchemaAnnotations() throws NoSuchFieldException {
        Schema schema = annotation(Pet.class, Schema.class);
        Field statusesField = Pet.class.getDeclaredField("statuses");
        Field hiddenField = Pet.class.getDeclaredField("internalCode");
        ArraySchema arraySchema = annotation(statusesField, ArraySchema.class);

        assertThat(schema.name()).isEqualTo("Pet");
        assertThat(schema.title()).isEqualTo("Pet resource");
        assertThat(schema.implementation()).isEqualTo(Pet.class);
        assertThat(schema.not()).isEqualTo(ArchivedPet.class);
        assertThat(schema.oneOf()).containsExactly(Dog.class, Cat.class);
        assertThat(schema.anyOf()).containsExactly(ServiceAnimal.class);
        assertThat(schema.allOf()).containsExactly(BasePet.class);
        assertThat(schema.multipleOf()).isEqualTo(1.0d);
        assertThat(schema.maximum()).isEqualTo("100");
        assertThat(schema.exclusiveMaximum()).isTrue();
        assertThat(schema.minimum()).isEqualTo("1");
        assertThat(schema.exclusiveMinimum()).isTrue();
        assertThat(schema.maxLength()).isEqualTo(64);
        assertThat(schema.minLength()).isEqualTo(2);
        assertThat(schema.pattern()).isEqualTo("[A-Za-z0-9-]+");
        assertThat(schema.maxProperties()).isEqualTo(12);
        assertThat(schema.minProperties()).isEqualTo(2);
        assertThat(schema.requiredProperties()).containsExactly("id", "name");
        assertThat(schema.requiredMode()).isEqualTo(Schema.RequiredMode.REQUIRED);
        assertThat(schema.format()).isEqualTo("pet-format");
        assertThat(schema.ref()).isEqualTo("#/components/schemas/Pet");
        assertThat(schema.nullable()).isFalse();
        assertThat(schema.accessMode()).isEqualTo(Schema.AccessMode.READ_WRITE);
        assertThat(schema.example()).contains("Fido");
        assertThat(schema.type()).isEqualTo("object");
        assertThat(schema.allowableValues()).containsExactly("dog", "cat");
        assertThat(schema.defaultValue()).isEqualTo("dog");
        assertThat(schema.discriminatorProperty()).isEqualTo("kind");
        assertThat(schema.discriminatorMapping()[0].schema()).isEqualTo(Dog.class);
        assertThat(schema.enumAsRef()).isTrue();
        assertThat(schema.subTypes()).containsExactly(Dog.class, Cat.class);
        assertThat(schema.prefixItems()).containsExactly(String.class, Integer.class);
        assertThat(schema.types()).containsExactly("object", "null");
        assertThat(schema.exclusiveMaximumValue()).isEqualTo(101);
        assertThat(schema.exclusiveMinimumValue()).isEqualTo(0);
        assertThat(schema.contains()).isEqualTo(TagMarker.class);
        assertThat(schema.$id()).isEqualTo("https://example.test/schemas/pet");
        assertThat(schema.$schema()).isEqualTo("https://json-schema.org/draft/2020-12/schema");
        assertThat(schema.$anchor()).isEqualTo("PetAnchor");
        assertThat(schema.$vocabulary()).isEqualTo("https://json-schema.org/draft/2020-12/vocab/core");
        assertThat(schema.$dynamicAnchor()).isEqualTo("PetDynamicAnchor");
        assertThat(schema.contentEncoding()).isEqualTo("base64");
        assertThat(schema.contentMediaType()).isEqualTo("application/json");
        assertThat(schema.contentSchema()).isEqualTo(PetContent.class);
        assertThat(schema.propertyNames()).isEqualTo(PetPropertyName.class);
        assertThat(schema.maxContains()).isEqualTo(5);
        assertThat(schema.minContains()).isEqualTo(1);
        assertThat(schema.additionalItems()).isEqualTo(AdditionalItem.class);
        assertThat(schema.unevaluatedItems()).isEqualTo(UnevaluatedItem.class);
        assertThat(schema._if()).isEqualTo(ConditionalPet.class);
        assertThat(schema._else()).isEqualTo(OtherwisePet.class);
        assertThat(schema.then()).isEqualTo(ThenPet.class);
        assertThat(schema.$comment()).isEqualTo("Schema comment");
        assertThat(schema.exampleClasses()).containsExactly(Dog.class, Cat.class);
        assertThat(schema.additionalProperties())
                .isEqualTo(Schema.AdditionalPropertiesValue.USE_ADDITIONAL_PROPERTIES_ANNOTATION);
        assertThat(schema.dependentRequiredMap()[0].value()).containsExactly("name", "status");
        assertThat(schema.dependentSchemas()[0].value()).isEqualTo(Metadata.class);
        assertThat(schema.patternProperties()[0].key()).isEqualTo("^x-");
        assertThat(schema.properties()[0].key()).isEqualTo("metadata");
        assertThat(schema.unevaluatedProperties()).isEqualTo(UnevaluatedProperty.class);
        assertThat(schema.additionalPropertiesSchema()).isEqualTo(AdditionalProperty.class);
        assertThat(schema.examples()).containsExactly("dog-example", "cat-example");
        assertThat(schema._const()).isEqualTo("pet");

        assertThat(arraySchema.schema().description()).isEqualTo("Known pet status values");
        assertThat(arraySchema.items().implementation()).isEqualTo(String.class);
        assertThat(arraySchema.arraySchema().type()).isEqualTo("array");
        assertThat(arraySchema.minItems()).isEqualTo(1);
        assertThat(arraySchema.maxItems()).isEqualTo(5);
        assertThat(arraySchema.uniqueItems()).isTrue();
        assertThat(arraySchema.contains().implementation()).isEqualTo(String.class);
        assertThat(arraySchema.maxContains()).isEqualTo(3);
        assertThat(arraySchema.minContains()).isEqualTo(1);
        assertThat(arraySchema.unevaluatedItems().implementation()).isEqualTo(String.class);
        assertThat(arraySchema.prefixItems())
                .extracting(Schema::implementation)
                .containsExactly(String.class, Integer.class);
        assertThat(annotation(hiddenField, Hidden.class)).isNotNull();
    }

    @Test
    void readsRepeatableSchemaAndPatternPropertyAnnotations() throws NoSuchFieldException {
        Field attributesField = DynamicMetadata.class.getDeclaredField("attributes");
        SchemaProperties schemaProperties = annotation(attributesField, SchemaProperties.class);
        PatternProperties patternProperties = annotation(attributesField, PatternProperties.class);

        assertThat(schemaProperties.value()).hasSize(2);
        assertThat(schemaProperties.value()).extracting(SchemaProperty::name).containsExactly("owner", "labels");
        assertThat(schemaProperties.value()[0].schema().implementation()).isEqualTo(Owner.class);
        assertThat(schemaProperties.value()[0].schema().requiredMode()).isEqualTo(Schema.RequiredMode.REQUIRED);
        assertThat(schemaProperties.value()[1].array().items().implementation()).isEqualTo(Label.class);
        assertThat(schemaProperties.value()[1].array().minItems()).isEqualTo(1);
        assertThat(schemaProperties.value()[1].array().uniqueItems()).isTrue();

        assertThat(patternProperties.value()).hasSize(2);
        assertThat(patternProperties.value()).extracting(PatternProperty::regex)
                .containsExactly("^x-[a-z]+$", "^metric-[a-z]+$");
        assertThat(patternProperties.value()[0].schema().implementation()).isEqualTo(String.class);
        assertThat(patternProperties.value()[0].schema().description()).isEqualTo("Extension metadata value");
        assertThat(patternProperties.value()[1].array().items().implementation()).isEqualTo(Integer.class);
        assertThat(patternProperties.value()[1].array().maxItems()).isEqualTo(4);
    }

    @Test
    void verifiesEnumNamesAndOpenApiWireValues() {
        assertThat(ParameterIn.PATH.toString()).isEqualTo("path");
        assertThat(ParameterIn.valueOf("COOKIE")).isEqualTo(ParameterIn.COOKIE);
        assertThat(ParameterStyle.SPACEDELIMITED.toString()).isEqualTo("spaceDelimited");
        assertThat(ParameterStyle.DEEPOBJECT.toString()).isEqualTo("deepObject");
        assertThat(SecuritySchemeType.APIKEY.toString()).isEqualTo("apiKey");
        assertThat(SecuritySchemeType.OPENIDCONNECT.toString()).isEqualTo("openIdConnect");
        assertThat(SecuritySchemeType.MUTUALTLS.toString()).isEqualTo("mutualTLS");
        assertThat(SecuritySchemeIn.QUERY.toString()).isEqualTo("query");
        assertThat(Explode.TRUE.name()).isEqualTo("TRUE");
        assertThat(Schema.AccessMode.READ_ONLY.name()).isEqualTo("READ_ONLY");
        assertThat(Schema.RequiredMode.NOT_REQUIRED.name()).isEqualTo("NOT_REQUIRED");
        assertThat(Schema.AdditionalPropertiesValue.FALSE.name()).isEqualTo("FALSE");
    }

    private static <A extends Annotation> A annotation(AnnotatedElement element, Class<A> annotationType) {
        A[] annotations = element.getAnnotationsByType(annotationType);
        assertThat(annotations).as(annotationType.getSimpleName()).hasSize(1);
        return annotations[0];
    }

    private static <A extends Annotation> A firstAnnotation(Annotation[] annotations, Class<A> annotationType) {
        for (Annotation annotation : annotations) {
            if (annotationType.isInstance(annotation)) {
                return annotationType.cast(annotation);
            }
        }
        throw new AssertionError("Missing annotation " + annotationType.getName());
    }

    @Server(url = "https://primary.example.test", description = "Primary API endpoint")
    @Server(
            url = "https://{tenant}.example.test",
            description = "Tenant API endpoint",
            variables = @ServerVariable(
                    name = "tenant",
                    allowableValues = {"demo", "production"},
                    defaultValue = "demo"))
    @SecurityRequirement(name = "oauth", scopes = {"catalog:read", "catalog:write"})
    @SecurityRequirement(name = "api-key")
    @Extension(
            name = "x-catalog",
            properties = @ExtensionProperty(name = "enabled", value = "true", parseValue = true))
    @Extension(
            name = "x-owner",
            properties = @ExtensionProperty(name = "team", value = "metadata-team"))
    @DependentRequired(name = "shipping", value = {"street", "postalCode"})
    @DependentRequired(name = "billing", value = "accountId")
    @DependentSchema(name = "shipping", schema = @Schema(implementation = Address.class))
    @DependentSchema(name = "billing", schema = @Schema(implementation = BillingProfile.class))
    private static final class RepeatableApiMetadata {
    }

    @OpenAPIDefinition(
            info = @Info(
                    title = "Pet Store",
                    summary = "Pet Store API summary",
                    description = "API described entirely with swagger annotations",
                    termsOfService = "https://example.test/terms",
                    contact = @Contact(
                            name = "API support",
                            url = "https://example.test/support",
                            email = "support@example.test",
                            extensions = @Extension(
                                    name = "x-contact",
                                    properties = @ExtensionProperty(name = "tier", value = "gold"))),
                    license = @License(
                            name = "Apache-2.0",
                            url = "https://www.apache.org/licenses/LICENSE-2.0",
                            identifier = "Apache-2.0"),
                    version = "2024.06",
                    extensions = @Extension(
                            name = "x-info",
                            properties = @ExtensionProperty(name = "stable", value = "true", parseValue = true))),
            tags = {
                    @Tag(
                            name = "pets",
                            description = "Pet operations",
                            externalDocs = @ExternalDocumentation(
                                    description = "Pet docs",
                                    url = "https://example.test/docs/pets")),
                    @Tag(name = "store", description = "Store operations")
            },
            servers = @Server(
                    url = "https://{region}.api.example.test/{basePath}",
                    description = "Regional endpoint",
                    variables = {
                            @ServerVariable(
                                    name = "region",
                                    allowableValues = {"eu", "us"},
                                    defaultValue = "eu",
                                    description = "Deployment region"),
                            @ServerVariable(name = "basePath", defaultValue = "v1")
                    }),
            security = @SecurityRequirement(name = "oauth", scopes = {"pet:read", "pet:write"}),
            externalDocs = @ExternalDocumentation(
                    description = "Complete API guide",
                    url = "https://example.test/docs"),
            extensions = @Extension(
                    name = "x-definition",
                    properties = @ExtensionProperty(name = "audience", value = "internal")))
    @SecuritySchemes({
            @SecurityScheme(
                    name = "oauth",
                    type = SecuritySchemeType.OAUTH2,
                    description = "OAuth authorization code flow",
                    flows = @OAuthFlows(
                            authorizationCode = @OAuthFlow(
                                    authorizationUrl = "https://auth.example.test/authorize",
                                    tokenUrl = "https://auth.example.test/token",
                                    refreshUrl = "https://auth.example.test/refresh",
                                    scopes = {
                                            @OAuthScope(name = "pet:read", description = "Read pets"),
                                            @OAuthScope(name = "pet:write", description = "Write pets")
                                    }))),
            @SecurityScheme(
                    name = "api-key",
                    type = SecuritySchemeType.APIKEY,
                    paramName = "X-API-Key",
                    in = SecuritySchemeIn.HEADER,
                    description = "Header API key")
    })
    @Tags({
            @Tag(name = "inventory", description = "Inventory views"),
            @Tag(name = "orders", description = "Order operations")
    })
    @Webhooks({
            @Webhook(
                    name = "petStatusChanged",
                    operation = @Operation(
                            method = "POST",
                            summary = "Pet status callback",
                            responses = @ApiResponse(responseCode = "204", description = "Callback accepted")))
    })
    private static final class AnnotatedApi {
        @OpenAPI31
        @Operation(
                method = "PUT",
                tags = {"pets", "write"},
                summary = "Replace a pet",
                description = "Replaces a pet with values from the request body",
                operationId = "replacePet",
                parameters = @Parameter(
                        name = "petId",
                        in = ParameterIn.PATH,
                        description = "Pet identifier",
                        required = true,
                        style = ParameterStyle.SIMPLE,
                        explode = Explode.FALSE,
                        allowReserved = true,
                        schema = @Schema(
                                implementation = String.class,
                                allowableValues = {"alpha", "beta"}),
                        examples = @ExampleObject(name = "alpha-id", value = "alpha"),
                        extensions = @Extension(
                                name = "x-path-parameter",
                                properties = @ExtensionProperty(name = "source", value = "operation"))),
                requestBody = @RequestBody(
                        description = "Replacement pet payload",
                        required = true,
                        useParameterTypeSchema = true,
                        content = @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = Pet.class),
                                examples = @ExampleObject(
                                        name = "dog",
                                        summary = "A dog",
                                        value = "{\"name\":\"Fido\"}",
                                        description = "Dog request example"),
                                encoding = @Encoding(
                                        name = "metadata",
                                        contentType = "application/json",
                                        style = "form",
                                        explode = true,
                                        allowReserved = true,
                                        headers = @Header(
                                                name = "X-Encoding",
                                                description = "Encoding header",
                                                schema = @Schema(type = "string"))))),
                responses = {
                        @ApiResponse(
                                responseCode = "200",
                                description = "Pet replaced",
                                useReturnTypeSchema = true,
                                headers = @Header(
                                        name = "ETag",
                                        description = "Entity tag",
                                        schema = @Schema(type = "string"),
                                        example = "W/\"pet\""),
                                links = @Link(
                                        name = "self",
                                        operationId = "getPet",
                                        parameters = @LinkParameter(name = "petId", expression = "$response.body#/id"),
                                        description = "Fetch this pet",
                                        requestBody = "$request.body",
                                        server = @Server(url = "https://api.example.test")),
                                content = @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = Pet.class),
                                        schemaProperties = @SchemaProperty(
                                                name = "pet",
                                                schema = @Schema(implementation = Pet.class)),
                                        additionalPropertiesSchema = @Schema(implementation = Metadata.class),
                                        additionalPropertiesArraySchema = @ArraySchema(
                                                items = @Schema(implementation = String.class)),
                                        propertyNames = @Schema(pattern = "^[a-z]+$"),
                                        dependentSchemas = @DependentSchema(
                                                name = "collar",
                                                schema = @Schema(implementation = Metadata.class)),
                                        _if = @Schema(implementation = Dog.class),
                                        _then = @Schema(implementation = ServiceAnimal.class),
                                        _else = @Schema(implementation = Cat.class),
                                        not = @Schema(implementation = ArchivedPet.class),
                                        oneOf = {@Schema(implementation = Dog.class)},
                                        anyOf = {@Schema(implementation = Cat.class)},
                                        allOf = {@Schema(implementation = BasePet.class)})),
                        @ApiResponse(
                                responseCode = "404",
                                description = "Pet not found",
                                ref = "#/components/responses/NotFound")
                },
                security = @SecurityRequirement(name = "oauth", scopes = "pet:write"),
                servers = @Server(
                        url = "https://write.example.test/{version}",
                        variables = @ServerVariable(name = "version", defaultValue = "v2")),
                externalDocs = @ExternalDocumentation(url = "https://example.test/docs/replace-pet"),
                extensions = @Extension(
                        name = "x-operation",
                        properties = @ExtensionProperty(name = "idempotent", value = "true", parseValue = true)),
                ignoreJsonView = true)
        @Parameters(@Parameter(name = "traceId", in = ParameterIn.HEADER, description = "Trace correlation id"))
        @ApiResponses(
                value = @ApiResponse(
                        responseCode = "401",
                        description = "Unauthorized",
                        ref = "#/components/responses/Unauthorized"),
                extensions = @Extension(
                        name = "x-responses",
                        properties = @ExtensionProperty(name = "group", value = "errors")))
        @Callbacks(@Callback(
                name = "onPetReplaced",
                callbackUrlExpression = "{$request.body#/callbackUrl}",
                operation = @Operation(
                        method = "POST",
                        summary = "Replacement notification",
                        responses = @ApiResponse(responseCode = "200", description = "Callback received"))))
        Pet replacePet(
                @Parameter(
                        name = "id",
                        in = ParameterIn.PATH,
                        required = true,
                        example = "alpha",
                        extensions = @Extension(
                                name = "x-parameter",
                                properties = @ExtensionProperty(name = "x-parameter-source", value = "method")))
                        String id,
                @RequestBody(
                        description = "Method parameter request body",
                        required = true,
                        content = @Content(
                                mediaType = "application/json",
                                array = @ArraySchema(items = @Schema(implementation = Pet.class))))
                        Pet pet) {
            return pet;
        }
    }

    @Schema(
            implementation = Pet.class,
            not = ArchivedPet.class,
            oneOf = {Dog.class, Cat.class},
            anyOf = ServiceAnimal.class,
            allOf = BasePet.class,
            name = "Pet",
            title = "Pet resource",
            multipleOf = 1.0,
            maximum = "100",
            exclusiveMaximum = true,
            minimum = "1",
            exclusiveMinimum = true,
            maxLength = 64,
            minLength = 2,
            pattern = "[A-Za-z0-9-]+",
            maxProperties = 12,
            minProperties = 2,
            requiredProperties = {"id", "name"},
            requiredMode = Schema.RequiredMode.REQUIRED,
            description = "Schema for a pet resource",
            format = "pet-format",
            ref = "#/components/schemas/Pet",
            nullable = false,
            accessMode = Schema.AccessMode.READ_WRITE,
            example = "{\"name\":\"Fido\"}",
            type = "object",
            allowableValues = {"dog", "cat"},
            defaultValue = "dog",
            discriminatorProperty = "kind",
            discriminatorMapping = @DiscriminatorMapping(value = "dog", schema = Dog.class),
            enumAsRef = true,
            subTypes = {Dog.class, Cat.class},
            extensions = @Extension(
                    name = "x-schema",
                    properties = @ExtensionProperty(name = "category", value = "animal")),
            prefixItems = {String.class, Integer.class},
            types = {"object", "null"},
            exclusiveMaximumValue = 101,
            exclusiveMinimumValue = 0,
            contains = TagMarker.class,
            $id = "https://example.test/schemas/pet",
            $schema = "https://json-schema.org/draft/2020-12/schema",
            $anchor = "PetAnchor",
            $vocabulary = "https://json-schema.org/draft/2020-12/vocab/core",
            $dynamicAnchor = "PetDynamicAnchor",
            contentEncoding = "base64",
            contentMediaType = "application/json",
            contentSchema = PetContent.class,
            propertyNames = PetPropertyName.class,
            maxContains = 5,
            minContains = 1,
            additionalItems = AdditionalItem.class,
            unevaluatedItems = UnevaluatedItem.class,
            _if = ConditionalPet.class,
            _else = OtherwisePet.class,
            then = ThenPet.class,
            $comment = "Schema comment",
            exampleClasses = {Dog.class, Cat.class},
            additionalProperties = Schema.AdditionalPropertiesValue.USE_ADDITIONAL_PROPERTIES_ANNOTATION,
            dependentRequiredMap = @DependentRequired(name = "tag", value = {"name", "status"}),
            dependentSchemas = @StringToClassMapItem(key = "metadata", value = Metadata.class),
            patternProperties = @StringToClassMapItem(key = "^x-", value = Metadata.class),
            properties = @StringToClassMapItem(key = "metadata", value = Metadata.class),
            unevaluatedProperties = UnevaluatedProperty.class,
            additionalPropertiesSchema = AdditionalProperty.class,
            examples = {"dog-example", "cat-example"},
            _const = "pet")
    private static class Pet extends BasePet {
        @ArraySchema(
                schema = @Schema(description = "Known pet status values"),
                items = @Schema(implementation = String.class),
                arraySchema = @Schema(type = "array"),
                maxItems = 5,
                minItems = 1,
                uniqueItems = true,
                extensions = @Extension(
                        name = "x-array",
                        properties = @ExtensionProperty(name = "ordered", value = "false")),
                contains = @Schema(implementation = String.class),
                maxContains = 3,
                minContains = 1,
                unevaluatedItems = @Schema(implementation = String.class),
                prefixItems = {@Schema(implementation = String.class), @Schema(implementation = Integer.class)})
        private List<String> statuses;

        @Hidden
        private String internalCode;
    }

    private static class BasePet {
    }

    private static final class DynamicMetadata {
        @SchemaProperty(
                name = "owner",
                schema = @Schema(
                        implementation = Owner.class,
                        description = "Metadata owner",
                        requiredMode = Schema.RequiredMode.REQUIRED))
        @SchemaProperty(
                name = "labels",
                array = @ArraySchema(
                        items = @Schema(implementation = Label.class),
                        minItems = 1,
                        uniqueItems = true))
        @PatternProperty(
                regex = "^x-[a-z]+$",
                schema = @Schema(implementation = String.class, description = "Extension metadata value"))
        @PatternProperty(
                regex = "^metric-[a-z]+$",
                array = @ArraySchema(items = @Schema(implementation = Integer.class), maxItems = 4))
        private Metadata attributes;
    }

    private static final class Owner {
    }

    private static final class Address {
    }

    private static final class BillingProfile {
    }

    private static final class Label {
    }

    private static final class Dog extends Pet {
    }

    private static final class Cat extends Pet {
    }

    private static final class ServiceAnimal extends Pet {
    }

    private static final class ArchivedPet extends Pet {
    }

    private static final class Metadata {
    }

    private static final class TagMarker {
    }

    private static final class PetContent {
    }

    private static final class PetPropertyName {
    }

    private static final class AdditionalItem {
    }

    private static final class UnevaluatedItem {
    }

    private static final class ConditionalPet {
    }

    private static final class OtherwisePet {
    }

    private static final class ThenPet {
    }

    private static final class UnevaluatedProperty {
    }

    private static final class AdditionalProperty {
    }
}
