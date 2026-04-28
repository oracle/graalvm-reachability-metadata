/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_jr.jackson_jr_objects;

import com.fasterxml.jackson.jr.ob.JSON;
import com.fasterxml.jackson.jr.ob.JSONObjectException;
import com.fasterxml.jackson.jr.ob.impl.ClassKey;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class BeanReaderDynamicAccessTest {
    private static final JSON JSON_WITH_FORCE_ACCESS = JSON.std.with(JSON.Feature.FORCE_REFLECTION_ACCESS);

    @Test
    void createsLibraryBeansThroughPublicDefaultConstructors() throws Exception {
        ClassKey key = JSON.std.beanFrom(ClassKey.class, "{}");

        assertThat(key).isNotNull();
        assertThat(key.hashCode()).isZero();
    }

    @Test
    void createsLibraryBeansThroughNonPublicDefaultConstructorsWhenAccessIsForced() throws Exception {
        JSONObjectException.Reference reference = JSON_WITH_FORCE_ACCESS.beanFrom(JSONObjectException.Reference.class,
                "{\"from\":\"source\",\"fieldName\":\"name\",\"index\":2}");

        assertThat(reference.getFrom()).isEqualTo("source");
        assertThat(reference.getFieldName()).isEqualTo("name");
        assertThat(reference.getIndex()).isEqualTo(2);
    }
}
