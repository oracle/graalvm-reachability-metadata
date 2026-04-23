/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_woodstox.woodstox_core;

import static org.assertj.core.api.Assertions.assertThat;

import com.ctc.wstx.shaded.msv_core.grammar.xmlschema.XMLSchemaGrammar;
import com.ctc.wstx.shaded.msv_core.verifier.regexp.xmlschema.XSREDocDecl;
import org.junit.jupiter.api.Test;

public class XSREDocDeclDynamicAccessTest {
    @Test
    void localizesXmlSchemaRegexpMessagesFromResourceBundles() {
        XSREDocDecl declaration = new XSREDocDecl(new XMLSchemaGrammar());

        String message = declaration.localizeMessage(
                XSREDocDecl.ERR_NON_NILLABLE_ELEMENT,
                new Object[]{"item"});

        assertThat(message).contains("item");
    }
}
