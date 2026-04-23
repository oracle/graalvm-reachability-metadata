/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_woodstox.woodstox_core;

import static org.assertj.core.api.Assertions.assertThat;

import com.ctc.wstx.shaded.msv.org_isorelax.dispatcher.IslandSchema;
import com.ctc.wstx.shaded.msv.org_isorelax.dispatcher.IslandVerifier;
import com.ctc.wstx.shaded.msv.org_isorelax.dispatcher.SchemaProvider;
import com.ctc.wstx.shaded.msv_core.grammar.ExpressionPool;
import com.ctc.wstx.shaded.msv_core.relaxns.verifier.IslandSchemaImpl.Binder;
import java.util.Collections;
import java.util.Iterator;
import org.junit.jupiter.api.Test;
import org.xml.sax.helpers.DefaultHandler;

public class IslandSchemaImplBinderDynamicAccessTest {
    @Test
    void localizesBinderMessages() {
        Binder binder = new Binder(new DummySchemaProvider(), new DefaultHandler(), new ExpressionPool());

        assertThat(binder.localize(Binder.ERR_UNDEFINED_NAMESPACE, "urn:test:missing"))
                .contains("referenced namespace")
                .contains("urn:test:missing");
    }

    private static final class DummySchemaProvider implements SchemaProvider {
        @Override
        public IslandVerifier createTopLevelVerifier() {
            return null;
        }

        @Override
        public IslandSchema getSchemaByNamespace(String namespaceURI) {
            return null;
        }

        @Override
        public Iterator iterateNamespace() {
            return Collections.emptyIterator();
        }

        @Override
        public IslandSchema[] getSchemata() {
            return new IslandSchema[0];
        }
    }
}
