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

import org.apache.naming.NamingContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class NamingContextTest {

    @Test
    void resolvesReferenceWithItsConfiguredObjectFactory() throws Exception {
        NamingContext context = new NamingContext(null, "reference-context");
        Reference reference = new Reference(String.class.getName(), TextFactory.class.getName(), null);
        context.bind("message", reference);

        Object value = context.lookup("message");

        assertThat(value).isEqualTo("resolved-message");
    }

    public static final class TextFactory implements ObjectFactory {
        public TextFactory() {
        }

        @Override
        public Object getObjectInstance(Object object, Name name, Context nameContext, Hashtable<?, ?> environment) {
            return "resolved-" + name;
        }
    }
}
