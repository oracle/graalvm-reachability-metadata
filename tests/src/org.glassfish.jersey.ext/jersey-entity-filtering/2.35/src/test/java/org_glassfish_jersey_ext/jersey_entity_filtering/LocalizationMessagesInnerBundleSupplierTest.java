/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_jersey_ext.jersey_entity_filtering;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.ResourceBundle;

import org.glassfish.jersey.internal.l10n.Localizable;
import org.glassfish.jersey.message.filtering.internal.LocalizationMessages;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class LocalizationMessagesInnerBundleSupplierTest {
    @Test
    void retrievesScopeMergingMessageFromSuppliedBundle() {
        Localizable message = LocalizationMessages.localizableMERGING_FILTERING_SCOPES();
        ResourceBundle bundle = message.getResourceBundle(Locale.ROOT);

        assertEquals("Merging two sets of entity-filtering scopes obtained from two different scope resolvers.",
                bundle.getString(message.getKey()));
    }

    @Test
    void formatsScopeConfigurationDiagnosticFromSuppliedBundle() {
        Localizable message = LocalizationMessages.localizableENTITY_FILTERING_SCOPE_NOT_ANNOTATIONS("scope-name");
        ResourceBundle bundle = message.getResourceBundle(Locale.ROOT);
        MessageFormat format = new MessageFormat(bundle.getString(message.getKey()), Locale.ROOT);

        assertEquals("The entity-filtering scope property does not contain an instance of Annotation or Annotation[] (scope-name).",
                format.format(message.getArguments()));
    }
}
