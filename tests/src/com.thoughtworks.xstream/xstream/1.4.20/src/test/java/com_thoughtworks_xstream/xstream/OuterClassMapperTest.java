/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_thoughtworks_xstream.xstream;

import com.thoughtworks.xstream.mapper.DefaultMapper;
import com.thoughtworks.xstream.mapper.OuterClassMapper;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class OuterClassMapperTest {
    @Test
    void mapsSyntheticOuterReferenceFieldToStableAlias() {
        OuterClassMapper mapper = new OuterClassMapper(
            new DefaultMapper(OuterClassMapperTest.class.getClassLoader()));

        assertThat(mapper.serializedMember(InnerDocument.class, "this$0")).isEqualTo("outer-class");
        assertThat(mapper.realMember(InnerDocument.class, "outer-class")).isEqualTo("this$0");
    }

    public final class InnerDocument {
    }
}
