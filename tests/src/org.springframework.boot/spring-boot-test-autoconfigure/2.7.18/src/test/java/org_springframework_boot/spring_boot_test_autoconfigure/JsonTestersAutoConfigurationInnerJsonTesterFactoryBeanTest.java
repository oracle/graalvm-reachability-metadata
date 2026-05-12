/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_test_autoconfigure;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.autoconfigure.json.JsonTestersAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.json.BasicJsonTester;
import org.springframework.boot.test.json.GsonTester;

import static org.assertj.core.api.Assertions.assertThat;

public class JsonTestersAutoConfigurationInnerJsonTesterFactoryBeanTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withPropertyValues("spring.test.jsontesters.enabled=true")
            .withBean(Gson.class, Gson::new)
            .withConfiguration(AutoConfigurations.of(JsonTestersAutoConfiguration.class));

    @Test
    void createsBasicJsonTesterWithNoArgumentConstructor() {
        this.contextRunner.run((context) -> assertThat(context.getBean("basicJsonTesterFactoryBean"))
                .isInstanceOf(BasicJsonTester.class));
    }

    @Test
    void createsGsonTesterWithMarshallerConstructor() {
        this.contextRunner.run((context) -> assertThat(context.getBean("gsonTesterFactoryBean"))
                .isInstanceOf(GsonTester.class));
    }

}
