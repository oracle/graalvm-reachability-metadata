/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_azure.azure_core;

import static org.assertj.core.api.Assertions.assertThat;

import com.azure.core.annotation.HeaderCollection;
import com.azure.core.http.HttpHeaders;
import com.azure.core.implementation.ReflectionUtils;
import com.azure.core.implementation.ReflectiveInvoker;
import com.azure.core.util.serializer.JacksonAdapter;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(60)
public class MethodReflectiveInvokerTest {
    @Test
    void invokesThePublicSetterForAHeaderCollection() throws Exception {
        HttpHeaders source = new HttpHeaders().set("x-tag-owner", "sdk");

        SetterHeaders decoded = new JacksonAdapter().deserialize(source, SetterHeaders.class);

        assertThat(decoded.tags).containsExactlyEntriesOf(Map.of("owner", "sdk"));
    }

    @Test
    void invokesStaticAndInstanceMethods() throws Exception {
        ReflectiveInvoker staticInvoker = ReflectionUtils.getMethodInvoker(MethodTargets.class,
                MethodTargets.class.getMethod("prefix", String.class));
        ReflectiveInvoker instanceInvoker = ReflectionUtils.getMethodInvoker(MethodTargets.class,
                MethodTargets.class.getMethod("join", String.class, String.class));

        String staticResult = (String) staticInvoker.invokeStatic("core");
        String instanceResult = (String) instanceInvoker.invokeWithArguments(new MethodTargets(), "azure", "core");

        assertThat(staticResult).isEqualTo("azure-core");
        assertThat(instanceResult).isEqualTo("azure-core");
    }

    public static final class SetterHeaders {
        @HeaderCollection("x-tag-")
        private Map<String, String> tags;

        public void setTags(Map<String, String> tags) {
            this.tags = tags;
        }
    }

    public static final class MethodTargets {
        public static String prefix(String value) {
            return "azure-" + value;
        }

        public String join(String left, String right) {
            return left + "-" + right;
        }
    }
}
