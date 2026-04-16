/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com.mchange.v2.naming;

import java.util.Hashtable;

import javax.naming.Context;
import javax.naming.Name;
import javax.naming.Reference;
import javax.naming.StringRefAddr;
import javax.naming.spi.ObjectFactory;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ReferenceableUtilsTest {
    @Test
    void referenceToObjectLoadsAndInstantiatesTheConfiguredObjectFactory() throws Exception {
        Reference reference = new Reference(String.class.getName(), EchoObjectFactory.class.getName(), null);
        reference.add(new StringRefAddr("value", "resolved through referenceable utils"));

        Object resolved = ReferenceableUtils.referenceToObject(reference, null, null, null);

        assertThat(resolved).isEqualTo("resolved through referenceable utils");
    }

    public static final class EchoObjectFactory implements ObjectFactory {
        @Override
        public Object getObjectInstance(Object obj, Name name, Context nameCtx, Hashtable<?, ?> environment) {
            Reference reference = (Reference) obj;
            return reference.get("value").getContent();
        }
    }
}
