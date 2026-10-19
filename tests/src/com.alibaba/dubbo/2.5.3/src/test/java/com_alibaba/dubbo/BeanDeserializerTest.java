/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba.dubbo;

import static org.assertj.core.api.Assertions.assertThat;

import com.alibaba.com.caucho.hessian.io.BeanSerializerFactory;
import org.junit.jupiter.api.Test;

public class BeanDeserializerTest {
    @Test
    void constructsBeanAndInvokesItsSetters() throws Exception {
        ResolvedBean source = new ResolvedBean();
        source.setValue("before-resolve");

        ResolvedBean copy =
                (ResolvedBean) HessianTestSupport.roundTrip(source, new BeanSerializerFactory());

        assertThat(copy.getValue()).isEqualTo("resolved");
    }

    public static class ResolvedBean {
        private String value;

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }

        public Object readResolve() {
            value = "resolved";
            return this;
        }
    }
}
