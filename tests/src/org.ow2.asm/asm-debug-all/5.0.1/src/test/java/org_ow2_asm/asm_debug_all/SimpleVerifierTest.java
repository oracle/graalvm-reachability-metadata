/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_ow2_asm.asm_debug_all;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.LinkedList;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.analysis.BasicValue;
import org.objectweb.asm.tree.analysis.SimpleVerifier;

import static org.assertj.core.api.Assertions.assertThat;

public class SimpleVerifierTest {
    @Test
    void mergesObjectTypesUsingRuntimeAssignabilityChecks() {
        SimpleVerifier verifier = new SimpleVerifier();

        BasicValue merged = verifier.merge(
                new BasicValue(Type.getType(ArrayList.class)),
                new BasicValue(Type.getType(LinkedList.class)));

        assertThat(merged.getType()).isEqualTo(Type.getType(AbstractList.class));
    }

    @Test
    void mergesArrayTypesUsingRuntimeAssignabilityChecks() {
        SimpleVerifier verifier = new SimpleVerifier();

        BasicValue merged = verifier.merge(
                new BasicValue(Type.getType(String[].class)),
                new BasicValue(Type.getType(Object[].class)));

        assertThat(merged.getType()).isEqualTo(Type.getType(Object[].class));
    }
}
