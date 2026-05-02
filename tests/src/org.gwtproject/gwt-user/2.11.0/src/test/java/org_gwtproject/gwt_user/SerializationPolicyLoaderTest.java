/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_gwtproject.gwt_user;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gwt.user.server.rpc.SerializationPolicy;
import com.google.gwt.user.server.rpc.SerializationPolicyLoader;
import com.google.gwt.user.server.rpc.impl.StandardSerializationPolicy;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class SerializationPolicyLoaderTest {
    private static final String STRING_TYPE_ID = "java.lang.String/type-id";

    @Test
    void loadsPolicyEntriesAndEnhancedClientFieldsFromStream() throws Exception {
        List<ClassNotFoundException> classNotFoundExceptions = new ArrayList<>();
        SerializationPolicy policy = SerializationPolicyLoader.loadFromStream(policyStream(),
                classNotFoundExceptions);
        StandardSerializationPolicy standardPolicy = (StandardSerializationPolicy) policy;

        assertThat(classNotFoundExceptions).isEmpty();

        assertThat(policy.shouldSerializeFinalFields()).isTrue();
        assertThat(policy.shouldSerializeFields(String.class)).isTrue();
        assertThat(policy.shouldDeserializeFields(String.class)).isTrue();
        policy.validateSerialize(String.class);
        policy.validateDeserialize(String.class);

        assertThat(policy.hasClientFields()).isTrue();
        assertThat(policy.getClientFieldNamesForEnhancedClass(ArrayList.class))
                .containsExactlyInAnyOrder("size", "elementData");
        assertThat(standardPolicy.getTypeIdForClass(String.class)).isEqualTo(STRING_TYPE_ID);
        assertThat(standardPolicy.getClassNameForTypeId(STRING_TYPE_ID))
                .isEqualTo(String.class.getName());
    }

    private static ByteArrayInputStream policyStream() {
        String policy = String.join("\n",
                SerializationPolicyLoader.FINAL_FIELDS_KEYWORD + ", true",
                String.class.getName() + ", true, true, true, true, " + STRING_TYPE_ID
                        + ", signature",
                SerializationPolicyLoader.CLIENT_FIELDS_KEYWORD + "," + ArrayList.class.getName()
                        + ",size,elementData");
        return new ByteArrayInputStream(policy.getBytes(StandardCharsets.UTF_8));
    }
}
