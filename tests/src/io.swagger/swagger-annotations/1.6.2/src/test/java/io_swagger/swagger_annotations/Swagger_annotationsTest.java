/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_swagger.swagger_annotations;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiKeyAuthDefinition;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.annotations.Authorization;
import io.swagger.annotations.AuthorizationScope;
import io.swagger.annotations.BasicAuthDefinition;
import io.swagger.annotations.Contact;
import io.swagger.annotations.Example;
import io.swagger.annotations.ExampleProperty;
import io.swagger.annotations.Extension;
import io.swagger.annotations.ExtensionProperty;
import io.swagger.annotations.ExternalDocs;
import io.swagger.annotations.Info;
import io.swagger.annotations.License;
import io.swagger.annotations.OAuth2Definition;
import io.swagger.annotations.ResponseHeader;
import io.swagger.annotations.Scope;
import io.swagger.annotations.SecurityDefinition;
import io.swagger.annotations.SwaggerDefinition;
import io.swagger.annotations.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class Swagger_annotationsTest {
    @Test
    void swaggerDefinitionCarriesNestedDocumentationAndSecurityMetadata() {
        SwaggerDefinition definition = AnnotatedPetApi.class.getAnnotationsByType(SwaggerDefinition.class)[0];

        assertThat(definition.host()).isEqualTo("api.example.test");
        assertThat(definition.basePath()).isEqualTo("/v1");
        assertThat(definition.consumes()).containsExactly("application/json");
        assertThat(definition.produces()).containsExactly("application/json", "application/problem+json");
        assertThat(definition.schemes()).containsExactly(SwaggerDefinition.Scheme.HTTP, SwaggerDefinition.Scheme.HTTPS);

        Info info = definition.info();
        assertThat(info.title()).isEqualTo("Pet API");
        assertThat(info.version()).isEqualTo("1.0");
        assertThat(info.description()).isEqualTo("Endpoints for looking up pets");
        assertThat(info.termsOfService()).isEqualTo("https://example.test/terms");
        assertThat(info.contact().name()).isEqualTo("API Support");
        assertThat(info.contact().url()).isEqualTo("https://example.test/support");
        assertThat(info.contact().email()).isEqualTo("support@example.test");
        assertThat(info.license().name()).isEqualTo("Apache-2.0");
        assertThat(info.license().url()).isEqualTo("https://www.apache.org/licenses/LICENSE-2.0");
        assertThat(info.extensions()).hasSize(1);
        assertThat(info.extensions()[0].name()).isEqualTo("x-info");
        assertThat(info.extensions()[0].properties()[0].name()).isEqualTo("audience");
        assertThat(info.extensions()[0].properties()[0].value()).isEqualTo("integration-tests");
        assertThat(info.extensions()[0].properties()[0].parseValue()).isTrue();

        Tag tag = definition.tags()[0];
        assertThat(tag.name()).isEqualTo("pets");
        assertThat(tag.description()).isEqualTo("Pet operations");
        assertThat(tag.externalDocs().value()).isEqualTo("Pet guide");
        assertThat(tag.externalDocs().url()).isEqualTo("https://example.test/docs/pets");
        assertThat(tag.extensions()[0].properties()[0].value()).isEqualTo("owned-by-search-team");

        SecurityDefinition securityDefinition = definition.securityDefinition();
        assertThat(securityDefinition.oAuth2Definitions()).hasSize(1);
        OAuth2Definition oauth = securityDefinition.oAuth2Definitions()[0];
        assertThat(oauth.key()).isEqualTo("oauth2");
        assertThat(oauth.flow()).isEqualTo(OAuth2Definition.Flow.ACCESS_CODE);
        assertThat(oauth.authorizationUrl()).isEqualTo("https://auth.example.test/authorize");
        assertThat(oauth.tokenUrl()).isEqualTo("https://auth.example.test/token");
        assertThat(oauth.scopes()[0].name()).isEqualTo("pets:read");

        assertThat(securityDefinition.apiKeyAuthDefinitions()).hasSize(1);
        ApiKeyAuthDefinition apiKey = securityDefinition.apiKeyAuthDefinitions()[0];
        assertThat(apiKey.key()).isEqualTo("apiKey");
        assertThat(apiKey.in()).isEqualTo(ApiKeyAuthDefinition.ApiKeyLocation.HEADER);
        assertThat(apiKey.name()).isEqualTo("X-Api-Key");

        assertThat(securityDefinition.basicAuthDefinitions()).hasSize(1);
        BasicAuthDefinition basicAuth = securityDefinition.basicAuthDefinitions()[0];
        assertThat(basicAuth.key()).isEqualTo("basic");
        assertThat(basicAuth.description()).isEqualTo("Basic authentication for local tools");

        assertThat(definition.externalDocs().value()).isEqualTo("Complete API documentation");
        assertThat(definition.externalDocs().url()).isEqualTo("https://example.test/docs");
    }

    @Test
    void resourceAndOperationAnnotationsRetainCollectionsExamplesExtensions() throws NoSuchMethodException {
        Api api = PetResource.class.getAnnotationsByType(Api.class)[0];
        assertThat(api.value()).isEqualTo("/pets");
        assertThat(api.tags()).containsExactly("pets", "search");
        assertThat(api.produces()).isEqualTo("application/json");
        assertThat(api.consumes()).isEqualTo("application/json");
        assertThat(api.protocols()).isEqualTo("https");
        assertThat(api.hidden()).isFalse();
        assertThat(api.authorizations()[0].value()).isEqualTo("oauth2");
        assertThat(api.authorizations()[0].scopes()[0].scope()).isEqualTo("pets:read");

        Method findPets = PetResource.class.getDeclaredMethod("findPets", String.class, int.class);
        ApiOperation operation = findPets.getAnnotationsByType(ApiOperation.class)[0];
        assertThat(operation.value()).isEqualTo("Find pets");
        assertThat(operation.notes()).isEqualTo("Returns a bounded collection of pets matching the requested type");
        assertThat(operation.tags()).containsExactly("pets");
        assertThat(operation.response()).isEqualTo(Pet.class);
        assertThat(operation.responseContainer()).isEqualTo("List");
        assertThat(operation.responseReference()).isEqualTo("#/definitions/Pet");
        assertThat(operation.httpMethod()).isEqualTo("GET");
        assertThat(operation.nickname()).isEqualTo("findPetsByType");
        assertThat(operation.produces()).isEqualTo("application/json");
        assertThat(operation.consumes()).isEqualTo("application/json");
        assertThat(operation.protocols()).isEqualTo("https");
        assertThat(operation.hidden()).isFalse();
        assertThat(operation.code()).isEqualTo(200);
        assertThat(operation.ignoreJsonView()).isTrue();
        assertThat(operation.authorizations()[0].scopes()[0].description()).isEqualTo("Read pet records");
        assertThat(operation.responseHeaders()[0].name()).isEqualTo("X-Result-Count");
        assertThat(operation.responseHeaders()[0].response()).isEqualTo(Integer.class);
        assertThat(operation.responseHeaders()[0].responseContainer()).isEqualTo("List");
        assertThat(operation.extensions()[0].properties()[0].value()).isEqualTo("true");

        ApiResponses responses = findPets.getAnnotationsByType(ApiResponses.class)[0];
        assertThat(responses.value()).hasSize(2);
        assertThat(responses.value()[0].code()).isEqualTo(200);
        assertThat(responses.value()[0].message()).isEqualTo("Pets were returned");
        assertThat(responses.value()[0].response()).isEqualTo(Pet.class);
        assertThat(responses.value()[0].responseContainer()).isEqualTo("List");
        assertThat(responses.value()[0].responseHeaders()[0].name()).isEqualTo("X-Trace-Id");
        assertThat(responses.value()[0].examples().value()[0].mediaType()).isEqualTo("application/json");
        assertThat(responses.value()[0].examples().value()[0].value()).contains("Fido");
        assertThat(responses.value()[1].code()).isEqualTo(404);
        assertThat(responses.value()[1].reference()).isEqualTo("#/responses/NotFound");

        ApiImplicitParams implicitParams = findPets.getAnnotationsByType(ApiImplicitParams.class)[0];
        assertThat(implicitParams.value()).hasSize(2);
        ApiImplicitParam filter = implicitParams.value()[0];
        assertThat(filter.name()).isEqualTo("type");
        assertThat(filter.value()).isEqualTo("Pet type filter");
        assertThat(filter.defaultValue()).isEqualTo("dog");
        assertThat(filter.allowableValues()).isEqualTo("dog,cat,bird");
        assertThat(filter.required()).isTrue();
        assertThat(filter.access()).isEqualTo("public");
        assertThat(filter.allowMultiple()).isFalse();
        assertThat(filter.dataType()).isEqualTo("string");
        assertThat(filter.dataTypeClass()).isEqualTo(String.class);
        assertThat(filter.paramType()).isEqualTo("query");
        assertThat(filter.example()).isEqualTo("dog");
        assertThat(filter.examples().value()[0].value()).isEqualTo("cat");
        assertThat(filter.type()).isEqualTo("string");
        assertThat(filter.format()).isEqualTo("slug");
        assertThat(filter.allowEmptyValue()).isFalse();
        assertThat(filter.readOnly()).isFalse();
        assertThat(filter.collectionFormat()).isEqualTo("csv");
    }

    @Test
    void modelPropertyAndParameterAnnotationsRetainSchemaDetails() throws NoSuchFieldException, NoSuchMethodException {
        ApiModel model = Pet.class.getAnnotationsByType(ApiModel.class)[0];
        assertThat(model.value()).isEqualTo("Pet");
        assertThat(model.description()).isEqualTo("A pet visible through the API");
        assertThat(model.parent()).isEqualTo(BasePet.class);
        assertThat(model.discriminator()).isEqualTo("kind");
        assertThat(model.subTypes()).containsExactly(Dog.class, Cat.class);
        assertThat(model.reference()).isEqualTo("#/definitions/Pet");

        Field nameField = Pet.class.getDeclaredField("name");
        ApiModelProperty name = nameField.getAnnotationsByType(ApiModelProperty.class)[0];
        assertThat(name.value()).isEqualTo("Display name");
        assertThat(name.name()).isEqualTo("name");
        assertThat(name.allowableValues()).isEqualTo("Fido,Garfield");
        assertThat(name.access()).isEqualTo("public");
        assertThat(name.notes()).isEqualTo("Names are shown in search results");
        assertThat(name.dataType()).isEqualTo("string");
        assertThat(name.required()).isTrue();
        assertThat(name.position()).isEqualTo(1);
        assertThat(name.hidden()).isFalse();
        assertThat(name.example()).isEqualTo("Fido");
        assertThat(name.accessMode()).isEqualTo(ApiModelProperty.AccessMode.READ_WRITE);
        assertThat(name.reference()).isEqualTo("#/definitions/Pet/properties/name");
        assertThat(name.allowEmptyValue()).isFalse();
        assertThat(name.extensions()[0].properties()[0].name()).isEqualTo("sortable");

        Method getIdentifier = Pet.class.getDeclaredMethod("getIdentifier");
        ApiModelProperty identifier = getIdentifier.getAnnotationsByType(ApiModelProperty.class)[0];
        assertThat(identifier.name()).isEqualTo("id");
        assertThat(identifier.accessMode()).isEqualTo(ApiModelProperty.AccessMode.READ_ONLY);
        assertThat(identifier.hidden()).isFalse();

        Field sortField = PetResource.class.getDeclaredField("defaultSort");
        ApiParam sort = sortField.getAnnotationsByType(ApiParam.class)[0];
        assertThat(sort.name()).isEqualTo("sort");
        assertThat(sort.value()).isEqualTo("Default sort order");
        assertThat(sort.defaultValue()).isEqualTo("name");
        assertThat(sort.allowableValues()).isEqualTo("name,created");
        assertThat(sort.required()).isFalse();
        assertThat(sort.access()).isEqualTo("public");
        assertThat(sort.allowMultiple()).isFalse();
        assertThat(sort.hidden()).isFalse();
        assertThat(sort.example()).isEqualTo("name");
        assertThat(sort.examples().value()[0].mediaType()).isEqualTo("text/plain");
        assertThat(sort.type()).isEqualTo("string");
        assertThat(sort.format()).isEqualTo("field-name");
        assertThat(sort.allowEmptyValue()).isFalse();
        assertThat(sort.readOnly()).isFalse();
        assertThat(sort.collectionFormat()).isEqualTo("csv");
    }

    @Test
    void methodParameterAnnotationsRetainRequestParameterMetadata() throws NoSuchMethodException {
        Method findPets = PetResource.class.getDeclaredMethod("findPets", String.class, int.class);

        ApiParam limit = findPets.getParameters()[1].getAnnotationsByType(ApiParam.class)[0];

        assertThat(limit).isNotNull();
        assertThat(limit.name()).isEqualTo("limit");
        assertThat(limit.value()).isEqualTo("Maximum result count");
        assertThat(limit.defaultValue()).isEqualTo("10");
        assertThat(limit.allowableValues()).isEqualTo("range[1,100]");
        assertThat(limit.required()).isTrue();
        assertThat(limit.type()).isEqualTo("integer");
        assertThat(limit.format()).isEqualTo("int32");
    }

    @Test
    void visibilityAndParameterOptionAnnotationsRetainDocumentationControls()
            throws NoSuchFieldException, NoSuchMethodException {
        Api api = AdministrativeResource.class.getAnnotationsByType(Api.class)[0];
        assertThat(api.value()).isEqualTo("/admin");
        assertThat(api.hidden()).isTrue();

        Method purgeCache = AdministrativeResource.class.getDeclaredMethod("purgeCache", List.class);
        ApiOperation operation = purgeCache.getAnnotationsByType(ApiOperation.class)[0];
        assertThat(operation.value()).isEqualTo("Purge cache");
        assertThat(operation.hidden()).isTrue();

        ApiImplicitParam implicitParam = purgeCache.getAnnotationsByType(ApiImplicitParam.class)[0];
        assertThat(implicitParam.name()).isEqualTo("cacheName");
        assertThat(implicitParam.allowMultiple()).isTrue();
        assertThat(implicitParam.allowEmptyValue()).isTrue();
        assertThat(implicitParam.readOnly()).isTrue();

        ApiParam parameter = purgeCache.getParameters()[0].getAnnotationsByType(ApiParam.class)[0];
        assertThat(parameter.name()).isEqualTo("cacheNames");
        assertThat(parameter.allowMultiple()).isTrue();
        assertThat(parameter.hidden()).isTrue();
        assertThat(parameter.allowEmptyValue()).isTrue();
        assertThat(parameter.readOnly()).isTrue();

        Field auditNoteField = AdministrativeRequest.class.getDeclaredField("auditNote");
        ApiModelProperty auditNote = auditNoteField.getAnnotationsByType(ApiModelProperty.class)[0];
        assertThat(auditNote.value()).isEqualTo("Internal audit note");
        assertThat(auditNote.hidden()).isTrue();
        assertThat(auditNote.allowEmptyValue()).isTrue();
    }

    @Test
    void enumHelpersResolveWireValuesAndPreserveDeclarationOrder() {
        assertThat(ApiKeyAuthDefinition.ApiKeyLocation.HEADER.toValue()).isEqualTo("header");
        assertThat(ApiKeyAuthDefinition.ApiKeyLocation.QUERY.toValue()).isEqualTo("query");
        assertThat(ApiKeyAuthDefinition.ApiKeyLocation.forValue("HEADER"))
                .isEqualTo(ApiKeyAuthDefinition.ApiKeyLocation.HEADER);
        assertThat(ApiKeyAuthDefinition.ApiKeyLocation.forValue("query"))
                .isEqualTo(ApiKeyAuthDefinition.ApiKeyLocation.QUERY);

        assertThat(SwaggerDefinition.Scheme.values()).containsExactly(
                SwaggerDefinition.Scheme.DEFAULT,
                SwaggerDefinition.Scheme.HTTP,
                SwaggerDefinition.Scheme.HTTPS,
                SwaggerDefinition.Scheme.WS,
                SwaggerDefinition.Scheme.WSS);
        assertThat(OAuth2Definition.Flow.values()).containsExactly(
                OAuth2Definition.Flow.IMPLICIT,
                OAuth2Definition.Flow.ACCESS_CODE,
                OAuth2Definition.Flow.PASSWORD,
                OAuth2Definition.Flow.APPLICATION);
        assertThat(ApiModelProperty.AccessMode.values()).containsExactly(
                ApiModelProperty.AccessMode.AUTO,
                ApiModelProperty.AccessMode.READ_ONLY,
                ApiModelProperty.AccessMode.READ_WRITE);
    }

    @SwaggerDefinition(
            host = "api.example.test",
            basePath = "/v1",
            consumes = "application/json",
            produces = {"application/json", "application/problem+json"},
            schemes = {SwaggerDefinition.Scheme.HTTP, SwaggerDefinition.Scheme.HTTPS},
            tags = @Tag(
                    name = "pets",
                    description = "Pet operations",
                    externalDocs = @ExternalDocs(value = "Pet guide", url = "https://example.test/docs/pets"),
                    extensions = @Extension(
                            name = "x-tag",
                            properties = @ExtensionProperty(name = "owner", value = "owned-by-search-team"))),
            securityDefinition = @SecurityDefinition(
                    oAuth2Definitions = @OAuth2Definition(
                            key = "oauth2",
                            description = "OAuth2 access code flow",
                            flow = OAuth2Definition.Flow.ACCESS_CODE,
                            authorizationUrl = "https://auth.example.test/authorize",
                            tokenUrl = "https://auth.example.test/token",
                            scopes = @Scope(name = "pets:read", description = "Read pet records")),
                    apiKeyAuthDefinitions = @ApiKeyAuthDefinition(
                            key = "apiKey",
                            description = "Header API key",
                            in = ApiKeyAuthDefinition.ApiKeyLocation.HEADER,
                            name = "X-Api-Key"),
                    basicAuthDefinitions = @BasicAuthDefinition(
                            key = "basic",
                            description = "Basic authentication for local tools")),
            info = @Info(
                    title = "Pet API",
                    version = "1.0",
                    description = "Endpoints for looking up pets",
                    termsOfService = "https://example.test/terms",
                    contact = @Contact(
                            name = "API Support",
                            url = "https://example.test/support",
                            email = "support@example.test"),
                    license = @License(
                            name = "Apache-2.0",
                            url = "https://www.apache.org/licenses/LICENSE-2.0"),
                    extensions = @Extension(
                            name = "x-info",
                            properties = @ExtensionProperty(
                                    name = "audience",
                                    value = "integration-tests",
                                    parseValue = true))),
            externalDocs = @ExternalDocs(value = "Complete API documentation", url = "https://example.test/docs"))
    private static final class AnnotatedPetApi {
    }

    @Api(
            value = "/pets",
            tags = {"pets", "search"},
            produces = "application/json",
            consumes = "application/json",
            protocols = "https",
            authorizations = @Authorization(
                    value = "oauth2",
                    scopes = @AuthorizationScope(scope = "pets:read", description = "Read pet records")))
    private static final class PetResource {
        @ApiParam(
                name = "sort",
                value = "Default sort order",
                defaultValue = "name",
                allowableValues = "name,created",
                access = "public",
                example = "name",
                examples = @Example(@ExampleProperty(mediaType = "text/plain", value = "created")),
                type = "string",
                format = "field-name",
                collectionFormat = "csv")
        private final String defaultSort = "name";

        @ApiOperation(
                value = "Find pets",
                notes = "Returns a bounded collection of pets matching the requested type",
                tags = "pets",
                response = Pet.class,
                responseContainer = "List",
                responseReference = "#/definitions/Pet",
                httpMethod = "GET",
                nickname = "findPetsByType",
                produces = "application/json",
                consumes = "application/json",
                protocols = "https",
                authorizations = @Authorization(
                        value = "oauth2",
                        scopes = @AuthorizationScope(scope = "pets:read", description = "Read pet records")),
                responseHeaders = @ResponseHeader(
                        name = "X-Result-Count",
                        description = "Number of matching pets",
                        response = Integer.class,
                        responseContainer = "List"),
                code = 200,
                extensions = @Extension(
                        name = "x-operation",
                        properties = @ExtensionProperty(name = "cached", value = "true")),
                ignoreJsonView = true)
        @ApiResponses({
                @ApiResponse(
                        code = 200,
                        message = "Pets were returned",
                        response = Pet.class,
                        responseContainer = "List",
                        responseHeaders = @ResponseHeader(
                                name = "X-Trace-Id",
                                description = "Trace identifier",
                                response = String.class),
                        examples = @Example(@ExampleProperty(
                                mediaType = "application/json",
                                value = "[{\"name\":\"Fido\"}]"))),
                @ApiResponse(code = 404, message = "No pets matched", reference = "#/responses/NotFound")
        })
        @ApiImplicitParams({
                @ApiImplicitParam(
                        name = "type",
                        value = "Pet type filter",
                        defaultValue = "dog",
                        allowableValues = "dog,cat,bird",
                        required = true,
                        access = "public",
                        dataType = "string",
                        dataTypeClass = String.class,
                        paramType = "query",
                        example = "dog",
                        examples = @Example(@ExampleProperty(mediaType = "text/plain", value = "cat")),
                        type = "string",
                        format = "slug",
                        collectionFormat = "csv"),
                @ApiImplicitParam(
                        name = "includeInactive",
                        value = "Include inactive pets",
                        dataTypeClass = Boolean.class,
                        paramType = "query",
                        type = "boolean")
        })
        private List<Pet> findPets(
                String type,
                @ApiParam(
                        name = "limit",
                        value = "Maximum result count",
                        defaultValue = "10",
                        allowableValues = "range[1,100]",
                        required = true,
                        type = "integer",
                        format = "int32") int limit) {
            return Collections.singletonList(new Pet("pet-1", type + '-' + limit));
        }
    }

    @Api(value = "/admin", hidden = true)
    private static final class AdministrativeResource {
        @ApiOperation(value = "Purge cache", hidden = true)
        @ApiImplicitParam(
                name = "cacheName",
                value = "Cache names to purge",
                allowMultiple = true,
                dataTypeClass = String.class,
                paramType = "query",
                type = "string",
                allowEmptyValue = true,
                readOnly = true)
        private void purgeCache(
                @ApiParam(
                        name = "cacheNames",
                        value = "Cache names to purge",
                        allowMultiple = true,
                        hidden = true,
                        allowEmptyValue = true,
                        readOnly = true) List<String> cacheNames) {
        }
    }

    private static final class AdministrativeRequest {
        @ApiModelProperty(
                value = "Internal audit note",
                hidden = true,
                allowEmptyValue = true)
        private final String auditNote = "";
    }

    private static class BasePet {
    }

    private static final class Dog extends Pet {
        private Dog() {
            super("dog-1", "Fido");
        }
    }

    private static final class Cat extends Pet {
        private Cat() {
            super("cat-1", "Garfield");
        }
    }

    @ApiModel(
            value = "Pet",
            description = "A pet visible through the API",
            parent = BasePet.class,
            discriminator = "kind",
            subTypes = {Dog.class, Cat.class},
            reference = "#/definitions/Pet")
    private static class Pet extends BasePet {
        private final String identifier;

        @ApiModelProperty(
                value = "Display name",
                name = "name",
                allowableValues = "Fido,Garfield",
                access = "public",
                notes = "Names are shown in search results",
                dataType = "string",
                required = true,
                position = 1,
                example = "Fido",
                accessMode = ApiModelProperty.AccessMode.READ_WRITE,
                reference = "#/definitions/Pet/properties/name",
                extensions = @Extension(
                        name = "x-property",
                        properties = @ExtensionProperty(name = "sortable", value = "true")))
        private final String name;

        private Pet(String identifier, String name) {
            this.identifier = identifier;
            this.name = name;
        }

        @ApiModelProperty(
                value = "Stable identifier",
                name = "id",
                accessMode = ApiModelProperty.AccessMode.READ_ONLY)
        private String getIdentifier() {
            return identifier;
        }
    }
}
