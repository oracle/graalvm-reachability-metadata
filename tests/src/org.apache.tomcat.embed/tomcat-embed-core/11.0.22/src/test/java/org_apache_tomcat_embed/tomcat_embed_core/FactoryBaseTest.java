/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_core;

import java.util.Hashtable;

import javax.naming.Context;
import javax.naming.Name;
import javax.naming.Reference;
import javax.naming.StringRefAddr;
import javax.naming.spi.ObjectFactory;

import org.apache.naming.ResourceRef;
import org.apache.naming.factory.Constants;
import org.apache.naming.factory.ResourceFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class FactoryBaseTest {

    @Test
    void createsExplicitlyConfiguredObjectFactory() throws Exception {
        ResourceRef reference = new ResourceRef(String.class.getName(), null, null, null, true);
        reference.add(new StringRefAddr(Constants.FACTORY, TextObjectFactory.class.getName()));

        Object value = new ResourceFactory().getObjectInstance(reference, null, null, null);

        assertThat(value).isEqualTo("created:" + String.class.getName());
    }

    public static class TextObjectFactory implements ObjectFactory {
        @Override
        public Object getObjectInstance(Object obj, Name name, Context nameCtx, Hashtable<?, ?> environment) {
            return "created:" + ((Reference) obj).getClassName();
        }
    }
}
