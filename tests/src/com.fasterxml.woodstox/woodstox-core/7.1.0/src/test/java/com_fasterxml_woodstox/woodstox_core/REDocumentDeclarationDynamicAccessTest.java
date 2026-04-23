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
import com.ctc.wstx.shaded.msv_core.verifier.regexp.REDocumentDeclaration;
import org.junit.jupiter.api.Test;

public class REDocumentDeclarationDynamicAccessTest {
    @Test
    void localizesRegexpVerifierMessagesFromResourceBundles() {
        REDocumentDeclaration declaration = new REDocumentDeclaration(Expression.epsilon, new ExpressionPool());

        String message = declaration.localizeMessage(
                REDocumentDeclaration.DIAG_ELEMENT_NOT_ALLOWED,
                new Object[]{"item"});

        assertThat(message).contains("item");
    }
}
