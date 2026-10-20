/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba.dubbo;

import static org.assertj.core.api.Assertions.assertThat;

import com.alibaba.dubbo.common.utils.PojoUtils;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class PojoUtilsInnerPojoInvocationHandlerTest {
    @Test
    void realizesMapAsInterfaceProxy() {
        Map<String, Object> view = new HashMap<String, Object>();
        view.put("name", "Grace");

        PojoUtilsTest.Named named =
                (PojoUtilsTest.Named) PojoUtils.realize(view, PojoUtilsTest.Named.class);

        assertThat(named.getName()).isEqualTo("Grace");
        assertThat(named.toString()).contains("name=Grace");
    }
}
