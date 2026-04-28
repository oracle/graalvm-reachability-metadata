/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_xbean.xbean_asm7_shaded;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.LinkedList;

import org.apache.xbean.asm7.Type;
import org.apache.xbean.asm7.tree.analysis.BasicValue;
import org.apache.xbean.asm7.tree.analysis.SimpleVerifier;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SimpleVerifierTest {
    @Test
    void mergesSiblingConcreteClassesToNearestCommonSuperclass() {
        SimpleVerifier verifier = new SimpleVerifier();
        BasicValue firstType = new BasicValue(Type.getType(ArrayList.class));
        BasicValue secondType = new BasicValue(Type.getType(LinkedList.class));

        BasicValue mergedType = verifier.merge(firstType, secondType);

        assertThat(mergedType.getType()).isEqualTo(Type.getType(AbstractList.class));
    }

    @Test
    void keepsWiderArrayTypeWhenMergingAssignableArrayTypes() {
        SimpleVerifier verifier = new SimpleVerifier();
        BasicValue objectArrayType = new BasicValue(Type.getType(Object[].class));
        BasicValue stringArrayType = new BasicValue(Type.getType(String[].class));

        BasicValue mergedType = verifier.merge(objectArrayType, stringArrayType);

        assertThat(mergedType.getType()).isEqualTo(Type.getType(Object[].class));
    }
}
