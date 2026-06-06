/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_swagger.swagger_annotations;

import io.swagger.oas.annotations.ExternalDocumentation;
import io.swagger.oas.annotations.Operation;
import io.swagger.oas.annotations.Parameter;
import io.swagger.oas.annotations.callbacks.Callback;
import io.swagger.oas.annotations.enums.Explode;
import io.swagger.oas.annotations.extensions.Extension;
import io.swagger.oas.annotations.extensions.ExtensionProperty;
import io.swagger.oas.annotations.info.Contact;
import io.swagger.oas.annotations.info.Info;
import io.swagger.oas.annotations.info.License;
import io.swagger.oas.annotations.links.Link;
import io.swagger.oas.annotations.links.LinkParameters;
import io.swagger.oas.annotations.media.ArraySchema;
import io.swagger.oas.annotations.media.Content;
import io.swagger.oas.annotations.media.DiscriminatorMapping;
import io.swagger.oas.annotations.media.ExampleObject;
import io.swagger.oas.annotations.media.Schema;
import io.swagger.oas.annotations.parameters.RequestBody;
import io.swagger.oas.annotations.responses.ApiResponse;
import io.swagger.oas.annotations.security.OAuthFlow;
import io.swagger.oas.annotations.security.OAuthFlows;
import io.swagger.oas.annotations.security.Scopes;
import io.swagger.oas.annotations.security.SecurityRequirement;
import io.swagger.oas.annotations.security.SecurityScheme;
import io.swagger.oas.annotations.servers.Server;
import io.swagger.oas.annotations.servers.ServerVariable;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class Swagger_annotationsTest {
    @Test
    void infoAndSecurityAnnotationsRetainDocumentationAndAuthenticationMetadata() {
        Info info = AnnotatedPetApi.class.getAnnotationsByType(Info.class)[0];

        assertThat(info.title()).isEqualTo("Pet API");
        assertThat(info.version()).isEqualTo("1.0");
        assertThat(info.description()).isEqualTo("Endpoints for looking up pets");
        assertThat(info.termsOfService()).isEqualTo("https://example.test/terms");
        assertThat(info.contact().name()).isEqualTo("API Support");
        assertThat(info.contact().url()).isEqualTo("https://example.test/support");
        assertThat(info.contact().email()).isEqualTo("support@example.test");
        assertThat(info.license().name()).isEqualTo("Apache-2.0");
        assertThat(info.license().url()).isEqualTo("https://www.apache.org/licenses/LICENSE-2.0");

        SecurityScheme oauth = AnnotatedPetApi.class.getAnnotationsByType(SecurityScheme.class)[0];
        assertThat(oauth.type()).isEqualTo("oauth2");
        assertThat(oauth.name()).isEqualTo("oauth2");
        assertThat(oauth.description()).isEqualTo("OAuth2 authorization code flow");
        OAuthFlow authorizationCode = oauth.flows().authorizationCode();
        assertThat(authorizationCode.authorizationUrl()).isEqualTo("https://auth.example.test/authorize");
        assertThat(authorizationCode.tokenUrl()).isEqualTo("https://auth.example.test/token");
        assertThat(authorizationCode.refreshUrl()).isEqualTo("https://auth.example.test/refresh");
        assertThat(authorizationCode.scopes().name()).isEqualTo("pets:read");
        assertThat(authorizationCode.scopes().description()).isEqualTo("Read pet records");

        SecurityRequirement requirement = AnnotatedPetApi.class.getAnnotationsByType(SecurityRequirement.class)[0];
        assertThat(requirement.name()).isEqualTo("oauth2");
        assertThat(requirement.scopes()).containsExactly("pets:read");
    }

    @Test
    void operationAnnotationsRetainParametersResponsesExamplesExtensionsAndServers() throws NoSuchMethodException {
        Method findPets = PetResource.class.getDeclaredMethod("findPets", String.class, int.class);
        Operation operation = findPets.getAnnotationsByType(Operation.class)[0];

        assertThat(operation.method()).isEqualTo("GET");
        assertThat(operation.tags()).containsExactly("pets", "search");
        assertThat(operation.summary()).isEqualTo("Find pets");
        assertThat(operation.description())
                .isEqualTo("Returns a bounded collection of pets matching the requested type");
        assertThat(operation.operationId()).isEqualTo("findPetsByType");
        assertThat(operation.deprecated()).isFalse();
        assertThat(operation.externalDocs().description()).isEqualTo("Pet guide");
        assertThat(operation.externalDocs().url()).isEqualTo("https://example.test/docs/pets");
        assertThat(operation.extensions()[0].name()).isEqualTo("x-operation");
        assertThat(operation.extensions()[0].properties()[0].name()).isEqualTo("cached");
        assertThat(operation.extensions()[0].properties()[0].value()).isEqualTo("true");

        Parameter operationParameter = operation.parameters()[0];
        assertThat(operationParameter.name()).isEqualTo("type");
        assertThat(operationParameter.in()).isEqualTo("query");
        assertThat(operationParameter.description()).isEqualTo("Pet type filter");
        assertThat(operationParameter.required()).isTrue();
        assertThat(operationParameter.allowEmptyValue()).isFalse();
        assertThat(operationParameter.style()).isEqualTo("form");
        assertThat(operationParameter.explode()).isEqualTo(Explode.FALSE);
        assertThat(operationParameter.schema().type()).isEqualTo("string");
        assertThat(operationParameter.schema()._enum()).containsExactly("dog", "cat", "bird");
        assertThat(operationParameter.content()[0].mediaType()).isEqualTo("text/plain");
        assertThat(operationParameter.content()[0].examples()[0].value()).isEqualTo("dog");

        ApiResponse ok = operation.responses()[0];
        assertThat(ok.responseCode()).isEqualTo("200");
        assertThat(ok.description()).isEqualTo("Pets were returned");
        assertThat(ok.content().mediaType()).isEqualTo("application/json");
        assertThat(ok.content().schema().implementation()).isEqualTo(Pet.class);
        assertThat(ok.content().examples()[0].name()).isEqualTo("pets");
        assertThat(ok.content().examples()[0].value()).contains("Fido");
        assertThat(ok.links()[0].name()).isEqualTo("firstPet");
        assertThat(ok.links()[0].operationId()).isEqualTo("getPetById");
        assertThat(ok.links()[0].parameters().name()).isEqualTo("id");
        assertThat(ok.links()[0].parameters().expression()).isEqualTo("$response.body#/0/id");

        assertThat(operation.responses()[1].responseCode()).isEqualTo("404");
        assertThat(operation.responses()[1].description()).isEqualTo("No pets matched");
        assertThat(operation.servers()[0].url()).isEqualTo("https://api.example.test/{version}");
        assertThat(operation.servers()[0].description()).isEqualTo("Versioned production endpoint");
        assertThat(operation.servers()[0].variables()[0].name()).isEqualTo("version");
        assertThat(operation.servers()[0].variables()[0].allowableValues()).containsExactly("v1", "v2");
        assertThat(operation.servers()[0].variables()[0].value()).isEqualTo("v1");
    }

    @Test
    void parameterAndRequestBodyAnnotationsRetainRequestMetadata() throws NoSuchMethodException {
        Method createPet = PetResource.class.getDeclaredMethod("createPet", Pet.class, String.class);

        RequestBody requestBody = createPet.getParameters()[0].getAnnotationsByType(RequestBody.class)[0];
        assertThat(requestBody.description()).isEqualTo("Pet payload");
        assertThat(requestBody.required()).isTrue();
        assertThat(requestBody.content()[0].mediaType()).isEqualTo("application/json");
        assertThat(requestBody.content()[0].schema().implementation()).isEqualTo(Pet.class);
        assertThat(requestBody.content()[0].examples()[0].summary()).isEqualTo("New pet");

        Parameter correlationId = createPet.getParameters()[1].getAnnotationsByType(Parameter.class)[0];
        assertThat(correlationId.name()).isEqualTo("X-Correlation-Id");
        assertThat(correlationId.in()).isEqualTo("header");
        assertThat(correlationId.description()).isEqualTo("Correlation identifier");
        assertThat(correlationId.required()).isFalse();
        assertThat(correlationId.deprecated()).isFalse();
        assertThat(correlationId.allowReserved()).isTrue();
        assertThat(correlationId.schema().type()).isEqualTo("string");
        assertThat(correlationId.schema().format()).isEqualTo("uuid");
    }

    @Test
    void schemaArrayAndCallbackAnnotationsRetainModelDetails() throws NoSuchFieldException, NoSuchMethodException {
        Schema model = Pet.class.getAnnotationsByType(Schema.class)[0];
        assertThat(model.name()).isEqualTo("Pet");
        assertThat(model.description()).isEqualTo("A pet visible through the API");
        assertThat(model.implementation()).isEqualTo(Pet.class);
        assertThat(model.allOf()).containsExactly(BasePet.class);
        assertThat(model.oneOf()).containsExactly(Dog.class, Cat.class);
        assertThat(model.requiredProperties()).containsExactly("id", "name");
        assertThat(model.discriminatorProperty()).isEqualTo("kind");
        assertThat(model.discriminatorMapping()[0].value()).isEqualTo("dog");
        assertThat(model.discriminatorMapping()[0].schema()).isEqualTo(Dog.class);
        assertThat(model.externalDocs().description()).isEqualTo("Pet schema guide");
        assertThat(model.externalDocs().url()).isEqualTo("https://example.test/docs/schema/pet");

        Field nameField = Pet.class.getDeclaredField("name");
        Schema name = nameField.getAnnotationsByType(Schema.class)[0];
        assertThat(name.name()).isEqualTo("name");
        assertThat(name.title()).isEqualTo("Display name");
        assertThat(name.description()).isEqualTo("Names are shown in search results");
        assertThat(name.type()).isEqualTo("string");
        assertThat(name._enum()).containsExactly("Fido", "Garfield");
        assertThat(name.example()).isEqualTo("Fido");
        assertThat(name.required()).isTrue();
        assertThat(name.readOnly()).isFalse();
        assertThat(name.writeOnly()).isFalse();
        assertThat(name.ref()).isEqualTo("#/components/schemas/Pet/properties/name");

        Field tagsField = Pet.class.getDeclaredField("tags");
        ArraySchema tags = tagsField.getAnnotationsByType(ArraySchema.class)[0];
        assertThat(tags.schema().type()).isEqualTo("string");
        assertThat(tags.maxItems()).isEqualTo(5);
        assertThat(tags.minItems()).isEqualTo(1);
        assertThat(tags.uniqueItems()).isTrue();

        Method notifySubscribers = PetResource.class.getDeclaredMethod("notifySubscribers", Pet.class);
        Callback callback = notifySubscribers.getAnnotationsByType(Callback.class)[0];
        assertThat(callback.name()).isEqualTo("petChanged");
        assertThat(callback.callbackUrlExpression()).isEqualTo("{$request.body#/callbackUrl}");
        assertThat(callback.operation()[0].method()).isEqualTo("POST");
        assertThat(callback.operation()[0].summary()).isEqualTo("Send pet update");
        assertThat(callback.operation()[0].responses()[0].responseCode()).isEqualTo("204");
    }

    @Test
    void hiddenDeprecatedAndEnumOptionsRemainAvailable() throws NoSuchFieldException, NoSuchMethodException {
        Method purgeCache = AdministrativeResource.class.getDeclaredMethod("purgeCache", List.class);
        Operation operation = purgeCache.getAnnotationsByType(Operation.class)[0];
        assertThat(operation.summary()).isEqualTo("Purge cache");
        assertThat(operation.deprecated()).isTrue();

        Parameter implicitParameter = purgeCache.getAnnotationsByType(Parameter.class)[0];
        assertThat(implicitParameter.name()).isEqualTo("cacheName");
        assertThat(implicitParameter.hidden()).isTrue();
        assertThat(implicitParameter.allowEmptyValue()).isTrue();
        assertThat(implicitParameter.schema().deprecated()).isTrue();

        Field auditNoteField = AdministrativeRequest.class.getDeclaredField("auditNote");
        Schema auditNote = auditNoteField.getAnnotationsByType(Schema.class)[0];
        assertThat(auditNote.description()).isEqualTo("Internal audit note");
        assertThat(auditNote.hidden()).isTrue();
        assertThat(auditNote.writeOnly()).isTrue();

        assertThat(Explode.values()).containsExactly(Explode.DEFAULT, Explode.FALSE, Explode.TRUE);
    }

    @Info(
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
                    url = "https://www.apache.org/licenses/LICENSE-2.0"))
    @SecurityScheme(
            type = "oauth2",
            name = "oauth2",
            description = "OAuth2 authorization code flow",
            flows = @OAuthFlows(
                    authorizationCode = @OAuthFlow(
                            authorizationUrl = "https://auth.example.test/authorize",
                            tokenUrl = "https://auth.example.test/token",
                            refreshUrl = "https://auth.example.test/refresh",
                            scopes = @Scopes(
                                    name = "pets:read",
                                    description = "Read pet records"))))
    @SecurityRequirement(name = "oauth2", scopes = "pets:read")
    private static final class AnnotatedPetApi {
    }

    private static final class PetResource {
        @Operation(
                method = "GET",
                tags = {"pets", "search"},
                summary = "Find pets",
                description = "Returns a bounded collection of pets matching the requested type",
                operationId = "findPetsByType",
                externalDocs = @ExternalDocumentation(
                        description = "Pet guide",
                        url = "https://example.test/docs/pets"),
                parameters = @Parameter(
                        name = "type",
                        in = "query",
                        description = "Pet type filter",
                        required = true,
                        allowEmptyValue = false,
                        style = "form",
                        explode = Explode.FALSE,
                        schema = @Schema(type = "string", _enum = {"dog", "cat", "bird"}),
                        content = @Content(
                                mediaType = "text/plain",
                                examples = @ExampleObject(value = "dog"))),
                responses = {
                        @ApiResponse(
                                responseCode = "200",
                                description = "Pets were returned",
                                content = @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = Pet.class),
                                        examples = @ExampleObject(
                                                name = "pets",
                                                value = "[{\"id\":\"pet-1\",\"name\":\"Fido\"}]")),
                                links = @Link(
                                        name = "firstPet",
                                        operationId = "getPetById",
                                        parameters = @LinkParameters(
                                                name = "id",
                                                expression = "$response.body#/0/id"))),
                        @ApiResponse(responseCode = "404", description = "No pets matched")
                },
                servers = @Server(
                        url = "https://api.example.test/{version}",
                        description = "Versioned production endpoint",
                        variables = @ServerVariable(
                                name = "version",
                                allowableValues = {"v1", "v2"},
                                value = "v1",
                                description = "API version")),
                extensions = @Extension(
                        name = "x-operation",
                        properties = @ExtensionProperty(name = "cached", value = "true")))
        private List<Pet> findPets(String type, int limit) {
            return Collections.singletonList(new Pet("pet-1", type + '-' + limit));
        }

        private Pet createPet(
                @RequestBody(
                        description = "Pet payload",
                        required = true,
                        content = @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = Pet.class),
                                examples = @ExampleObject(summary = "New pet", value = "{\"name\":\"Fido\"}"))) Pet pet,
                @Parameter(
                        name = "X-Correlation-Id",
                        in = "header",
                        description = "Correlation identifier",
                        allowReserved = true,
                        schema = @Schema(type = "string", format = "uuid")) String correlationId) {
            return pet;
        }

        @Callback(
                name = "petChanged",
                callbackUrlExpression = "{$request.body#/callbackUrl}",
                operation = @Operation(
                        method = "POST",
                        summary = "Send pet update",
                        responses = @ApiResponse(responseCode = "204", description = "Subscriber accepted update")))
        private void notifySubscribers(Pet pet) {
        }
    }

    private static final class AdministrativeResource {
        @Operation(summary = "Purge cache", deprecated = true)
        @Parameter(
                name = "cacheName",
                in = "query",
                description = "Cache names to purge",
                allowEmptyValue = true,
                hidden = true,
                schema = @Schema(type = "string", deprecated = true))
        private void purgeCache(List<String> cacheNames) {
        }
    }

    private static final class AdministrativeRequest {
        @Schema(description = "Internal audit note", hidden = true, writeOnly = true)
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

    @Schema(
            name = "Pet",
            description = "A pet visible through the API",
            implementation = Pet.class,
            allOf = BasePet.class,
            oneOf = {Dog.class, Cat.class},
            requiredProperties = {"id", "name"},
            discriminatorProperty = "kind",
            discriminatorMapping = @DiscriminatorMapping(value = "dog", schema = Dog.class),
            externalDocs = @ExternalDocumentation(
                    description = "Pet schema guide",
                    url = "https://example.test/docs/schema/pet"))
    private static class Pet extends BasePet {
        private final String identifier;

        @Schema(
                name = "name",
                title = "Display name",
                description = "Names are shown in search results",
                type = "string",
                _enum = {"Fido", "Garfield"},
                example = "Fido",
                required = true,
                ref = "#/components/schemas/Pet/properties/name")
        private final String name;

        @ArraySchema(
                schema = @Schema(type = "string"),
                maxItems = 5,
                minItems = 1,
                uniqueItems = true)
        private final List<String> tags = Collections.singletonList("friendly");

        private Pet(String identifier, String name) {
            this.identifier = identifier;
            this.name = name;
        }
    }
}
