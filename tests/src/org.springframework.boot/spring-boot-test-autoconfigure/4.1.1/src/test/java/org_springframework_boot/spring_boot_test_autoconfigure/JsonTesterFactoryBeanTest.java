/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_test_autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import org.springframework.boot.test.autoconfigure.json.JsonTesterFactoryBean;
import org.springframework.boot.test.json.BasicJsonTester;
import org.springframework.boot.test.json.GsonTester;

public class JsonTesterFactoryBeanTest {
    @Test
    void createsPrototypeBasicJsonTestersWithoutAMarshaller() throws Exception {
        JsonTesterFactoryBean<BasicJsonTester, Void> factory =
                new JsonTesterFactoryBean<>(BasicJsonTester.class, null);

        BasicJsonTester first = factory.getObject();
        BasicJsonTester second = factory.getObject();

        assertThat(factory.getObjectType()).isEqualTo(BasicJsonTester.class);
        assertThat(factory.isSingleton()).isFalse();
        assertThat(first).isNotSameAs(second);
    }

    @Test
    void createsPrototypeGsonTestersUsingTheMarshallerConstructor() throws Exception {
        Gson gson = new Gson();
        JsonTesterFactoryBean<GsonTester<Object>, Gson> factory =
                new JsonTesterFactoryBean<>(GsonTester.class, gson);

        GsonTester<Object> first = factory.getObject();
        GsonTester<Object> second = factory.getObject();

        assertThat(factory.getObjectType()).isEqualTo(GsonTester.class);
        assertThat(factory.isSingleton()).isFalse();
        assertThat(first).isNotSameAs(second);
    }
}
