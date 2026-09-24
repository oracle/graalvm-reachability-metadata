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
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Order(1)
public class NamingContextTest {

    private static final String IMAGE_CODE_PROPERTY = "org.graalvm.nativeimage.imagecode";
    private static String previousImageCode;

    @BeforeAll
    static void enableNativeImageObjectFactoryPath() {
        previousImageCode = System.getProperty(IMAGE_CODE_PROPERTY);
        System.setProperty(IMAGE_CODE_PROPERTY, "runtime");
    }

    @AfterAll
    static void restoreNativeImageProperty() {
        if (previousImageCode == null) {
            System.clearProperty(IMAGE_CODE_PROPERTY);
        } else {
            System.setProperty(IMAGE_CODE_PROPERTY, previousImageCode);
        }
    }

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
