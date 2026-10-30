/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_test_autoconfigure;

import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.autoconfigure.json.JsonTestersAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.json.BasicJsonTester;
import org.springframework.boot.test.json.GsonTester;
import org.springframework.boot.test.json.JacksonTester;

import static org.assertj.core.api.Assertions.assertThat;

public class JsonTestersAutoConfigurationInnerJsonTesterFactoryBeanTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JsonTestersAutoConfiguration.class))
            .withPropertyValues("spring.test.jsontesters.enabled=true");

    @Test
    void injectsIndependentBasicJsonTesters() {
        this.contextRunner.withBean(BasicJsonClient.class, BasicJsonClient::new).run(context -> {
            BasicJsonClient client = context.getBean(BasicJsonClient.class);

            assertThat(client.first).isNotSameAs(client.second);
            assertThat(client.first.from("""
                    {"message":"ready","count":3}
                    """)).extractingJsonPathStringValue("$.message").isEqualTo("ready");
            assertThat(client.second.from("""
                    {"message":"waiting","count":5}
                    """)).extractingJsonPathNumberValue("$.count").isEqualTo(5);
        });
    }

    @Test
    void injectsJacksonTesterUsingTheConfiguredObjectMapper() {
        this.contextRunner.withBean(ObjectMapper.class, ObjectMapper::new)
                .withBean(JacksonJsonClient.class, JacksonJsonClient::new).run(context -> {
                    JacksonJsonClient client = context.getBean(JacksonJsonClient.class);
                    Map<String, String> message = Map.of("message", "jackson");

                    assertThat(client.json.write(message)).isEqualToJson("""
                            {"message":"jackson"}
                            """);
                    assertThat(client.json.parseObject("""
                            {"message":"received"}
                            """)).isEqualTo(Map.of("message", "received"));
                });
    }

    @Test
    void injectsGsonTesterUsingTheConfiguredGson() {
        this.contextRunner.withBean(Gson.class, Gson::new)
                .withBean(GsonJsonClient.class, GsonJsonClient::new).run(context -> {
                    GsonJsonClient client = context.getBean(GsonJsonClient.class);
                    Map<String, String> message = Map.of("message", "gson");

                    assertThat(client.json.write(message)).isEqualToJson("""
                            {"message":"gson"}
                            """);
                    assertThat(client.json.parseObject("""
                            {"message":"received"}
                            """)).isEqualTo(Map.of("message", "received"));
                });
    }

    public static class BasicJsonClient {
        @Autowired
        private BasicJsonTester first;

        @Autowired
        private BasicJsonTester second;
    }

    public static class JacksonJsonClient {
        @Autowired
        private JacksonTester<Map<String, String>> json;
    }

    public static class GsonJsonClient {
        @Autowired
        private GsonTester<Map<String, String>> json;
    }
}
