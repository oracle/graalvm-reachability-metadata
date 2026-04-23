/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_woodstox.woodstox_core;

import static org.assertj.core.api.Assertions.assertThat;

import com.ctc.wstx.shaded.msv_core.grammar.ExpressionPool;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import org.junit.jupiter.api.Test;

public class ExpressionPoolClosedHashDynamicAccessTest {
    @Test
    void serializesAndDeserializesTheExpressionPoolCache() throws Exception {
        ExpressionPool pool = new ExpressionPool();
        pool.createChoice(pool.createEpsilon(), pool.createAnyString());
        pool.createSequence(pool.createAnyString(), pool.createEpsilon());

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(pool);
        }

        ExpressionPool restored;
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            restored = (ExpressionPool) input.readObject();
        }

        assertThat(bytes.toByteArray()).isNotEmpty();
        assertThat(restored).isNotNull();
        assertThat(restored.createChoice(restored.createEpsilon(), restored.createAnyString())).isNotNull();
    }
}
