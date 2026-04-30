/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_module.jackson_module_parameter_names;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class Jackson_module_parameter_namesTest {
    @Test
    void deserializesAnnotatedConstructorUsingImplicitParameterNames() throws Exception {
        ObjectMapper mapper = mapperWithDefaultParameterNamesModule();

        Account account = mapper.readValue("""
                {
                  "firstName": "Ada",
                  "lastName": "Lovelace",
                  "age": 36,
                  "active": true
                }
                """, Account.class);

        assertThat(account.firstName()).isEqualTo("Ada");
        assertThat(account.lastName()).isEqualTo("Lovelace");
        assertThat(account.age()).isEqualTo(36);
        assertThat(account.active()).isTrue();
    }

    @Test
    void deserializesNestedValueUsingImplicitParameterNames() throws Exception {
        ObjectMapper mapper = mapperWithDefaultParameterNamesModule();

        ServiceEndpoint endpoint = mapper.readValue("""
                {
                  "serviceName": "metadata-api",
                  "address": {
                    "scheme": "https",
                    "host": "example.test",
                    "port": 443
                  }
                }
                """, ServiceEndpoint.class);

        assertThat(endpoint.serviceName()).isEqualTo("metadata-api");
        assertThat(endpoint.address().toUri()).isEqualTo("https://example.test:443");
    }

    @Test
    void propertiesCreatorModeTreatsSingleArgumentCreatorAsObjectPropertyBinding() throws Exception {
        ObjectMapper mapper = JsonMapper.builder()
                .addModule(new ParameterNamesModule(JsonCreator.Mode.PROPERTIES))
                .build();

        TrackingCode trackingCode = mapper.readValue("""
                {
                  "code": "PKG-42"
                }
                """, TrackingCode.class);

        assertThat(trackingCode.code()).isEqualTo("PKG-42");
        assertThatThrownBy(() -> mapper.readValue("\"PKG-42\"", TrackingCode.class))
                .isInstanceOf(Exception.class);
    }

    @Test
    void delegatingCreatorModeTreatsSingleArgumentCreatorAsScalarBinding() throws Exception {
        ObjectMapper mapper = JsonMapper.builder()
                .addModule(new ParameterNamesModule(JsonCreator.Mode.DELEGATING))
                .build();

        TrackingCode trackingCode = mapper.readValue("\"PKG-43\"", TrackingCode.class);

        assertThat(trackingCode.code()).isEqualTo("PKG-43");
        assertThatThrownBy(() -> mapper.readValue("{\"code\":\"PKG-43\"}", TrackingCode.class))
                .isInstanceOf(Exception.class);
    }

    @Test
    void serviceLoaderRegistrationDiscoversTheModule() throws Exception {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

        Credentials credentials = mapper.readValue("""
                {
                  "userName": "native-user",
                  "password": "secret"
                }
                """, Credentials.class);

        assertThat(credentials.userName()).isEqualTo("native-user");
        assertThat(credentials.matches("secret")).isTrue();
        assertThat(mapper.getRegisteredModuleIds())
                .anySatisfy(moduleId -> assertThat(moduleId.toString().toLowerCase()).contains("parameter"));
    }

    @Test
    void moduleEqualityIsIdentityBasedAndVersionMatchesLibrary() {
        ParameterNamesModule firstModule = new ParameterNamesModule();
        ParameterNamesModule secondModule = new ParameterNamesModule();

        assertThat(firstModule).isEqualTo(firstModule);
        assertThat(firstModule).isNotEqualTo(secondModule);
        assertThat(firstModule.version().getMajorVersion()).isEqualTo(2);
        assertThat(firstModule.version().getMinorVersion()).isEqualTo(13);
        assertThat(firstModule.version().getPatchLevel()).isEqualTo(5);
    }

    private static ObjectMapper mapperWithDefaultParameterNamesModule() {
        return JsonMapper.builder()
                .addModule(new ParameterNamesModule())
                .build();
    }

    public record Account(String firstName, String lastName, int age, boolean active) {
        @JsonCreator
        public Account {
        }
    }

    public record ServiceEndpoint(String serviceName, EndpointAddress address) {
        @JsonCreator
        public ServiceEndpoint {
        }
    }

    public record EndpointAddress(String scheme, String host, int port) {
        @JsonCreator
        public EndpointAddress {
        }

        public String toUri() {
            return scheme + "://" + host + ":" + port;
        }
    }

    public record TrackingCode(String code) {
        @JsonCreator
        public TrackingCode {
        }
    }

    public record Credentials(String userName, String password) {
        @JsonCreator
        public Credentials {
        }

        public boolean matches(String candidatePassword) {
            return password.equals(candidatePassword);
        }
    }
}
