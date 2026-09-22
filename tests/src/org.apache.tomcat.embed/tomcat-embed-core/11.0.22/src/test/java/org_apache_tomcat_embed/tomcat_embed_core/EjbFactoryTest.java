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
import javax.naming.spi.ObjectFactory;

import org.apache.naming.EjbRef;
import org.apache.naming.factory.EjbFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class EjbFactoryTest {

    @Test
    void createsDefaultEjbObjectFactory() throws Exception {
        String propertyName = "jakarta.ejb.Factory";
        String previous = System.getProperty(propertyName);
        System.setProperty(propertyName, TestEjbObjectFactory.class.getName());
        try {
            EjbRef reference = new EjbRef("Session", Object.class.getName(), Object.class.getName(), null);

            Object value = new EjbFactory().getObjectInstance(reference, null, null, null);

            assertThat(value).isEqualTo("ejb:Session");
        } finally {
            if (previous == null) {
                System.clearProperty(propertyName);
            } else {
                System.setProperty(propertyName, previous);
            }
        }
    }

    public static class TestEjbObjectFactory implements ObjectFactory {
        @Override
        public Object getObjectInstance(Object obj, Name name, Context nameCtx, Hashtable<?, ?> environment) {
            Reference reference = (Reference) obj;
            return "ejb:" + reference.get("type").getContent();
        }
    }
}
