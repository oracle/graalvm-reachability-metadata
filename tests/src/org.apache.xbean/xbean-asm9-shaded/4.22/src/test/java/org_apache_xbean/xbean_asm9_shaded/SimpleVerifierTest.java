/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_xbean.xbean_asm9_shaded;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.LinkedList;

import org.apache.xbean.asm9.Type;
import org.apache.xbean.asm9.tree.analysis.BasicValue;
import org.apache.xbean.asm9.tree.analysis.SimpleVerifier;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SimpleVerifierTest {
    @Test
    void mergesConcreteObjectValuesToTheirCommonSuperclass() {
        SimpleVerifier verifier = new SimpleVerifier();
        BasicValue arrayListValue = verifier.newValue(Type.getType(ArrayList.class));
        BasicValue linkedListValue = verifier.newValue(Type.getType(LinkedList.class));

        BasicValue mergedValue = verifier.merge(arrayListValue, linkedListValue);

        assertThat(mergedValue.getType()).isEqualTo(Type.getType(AbstractList.class));
    }

    @Test
    void recognizesAssignableArrayValues() {
        SimpleVerifier verifier = new SimpleVerifier();
        BasicValue objectArrayValue = verifier.newValue(Type.getType(Object[].class));
        BasicValue stringArrayValue = verifier.newValue(Type.getType(String[].class));

        BasicValue mergedValue = verifier.merge(objectArrayValue, stringArrayValue);

        assertThat(mergedValue).isEqualTo(objectArrayValue);
        assertThat(mergedValue.getType()).isEqualTo(Type.getType(Object[].class));
    }
}
