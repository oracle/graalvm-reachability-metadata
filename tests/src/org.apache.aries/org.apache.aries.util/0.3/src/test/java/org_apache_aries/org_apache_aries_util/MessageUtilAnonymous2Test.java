/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_aries.org_apache_aries_util;

import java.util.ListResourceBundle;
import java.util.ResourceBundle;

import org.apache.aries.util.nls.MessageUtil;
import org.junit.jupiter.api.Test;
import org.osgi.framework.Bundle;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class MessageUtilAnonymous2Test {
    @Test
    void loadsAndFormatsClassBackedBundleMessages() {
        ResourceBundle.clearCache();
        Bundle bundle = new MessageResourceBundle(false) {
            @Override
            public Class<?> loadClass(String name) throws ClassNotFoundException {
                if (name.equals(ClassMessages.class.getName())) {
                    return ClassMessages.class;
                }
                throw new ClassNotFoundException(name);
            }
        };

        MessageUtil messages = MessageUtil.createMessageUtil(bundle, ClassMessages.class.getName());

        assertEquals("Started inventory for warehouse", messages.getMessage("started", "inventory", "warehouse"));
        assertEquals("Ready", messages.getMessage("ready"));
        assertEquals("unknown.message", messages.getMessage("unknown.message"));
    }

    public static class ClassMessages extends ListResourceBundle {
        @Override
        protected Object[][] getContents() {
            return new Object[][] {
                    {"started", "Started {0} for {1}"},
                    {"ready", "Ready"}
            };
        }
    }
}
