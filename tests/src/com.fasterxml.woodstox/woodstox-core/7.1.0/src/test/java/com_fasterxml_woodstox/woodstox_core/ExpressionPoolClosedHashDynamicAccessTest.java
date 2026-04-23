/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_woodstox.woodstox_core;

import static org.assertj.core.api.Assertions.assertThat;

import com.ctc.wstx.shaded.msv_core.grammar.Expression;
import com.ctc.wstx.shaded.msv_core.grammar.ExpressionPool;
import com.ctc.wstx.shaded.msv_core.grammar.ExpressionPool.ClosedHash;
import com.ctc.wstx.shaded.msv_core.grammar.SimpleNameClass;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import org.junit.jupiter.api.Test;

public class ExpressionPoolClosedHashDynamicAccessTest {
    @Test
    void serializesAndDeserializesClosedHashEntries() throws Exception {
        ExpressionPool pool = new ExpressionPool();
        Expression attribute = pool.createAttribute(new SimpleNameClass("urn:test", "value"));
        Expression repeatedAttribute = pool.createOneOrMore(attribute);
        Expression mixedAttribute = pool.createMixed(attribute);
        Expression sequence = pool.createSequence(repeatedAttribute, mixedAttribute);
        Expression choice = pool.createChoice(sequence, attribute);

        ClosedHash parent = new ClosedHash();
        parent.put(attribute);
        parent.put(repeatedAttribute);

        ClosedHash hash = new ClosedHash(parent);
        hash.put(mixedAttribute);
        hash.put(sequence);
        hash.put(choice);

        assertThat(hash.get(attribute)).isSameAs(attribute);
        assertThat(hash.get(choice)).isSameAs(choice);

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(hash);
        }

        ClosedHash restored;
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            restored = (ClosedHash) input.readObject();
        }

        Expression restoredEntry = pool.createList(choice);
        restored.put(restoredEntry);

        assertThat(bytes.toByteArray()).isNotEmpty();
        assertThat(restored).isNotNull();
        assertThat(restored.get(restoredEntry)).isSameAs(restoredEntry);
    }
}
