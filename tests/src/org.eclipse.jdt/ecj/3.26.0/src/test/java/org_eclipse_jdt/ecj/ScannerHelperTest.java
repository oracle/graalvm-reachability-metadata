/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_jdt.ecj;

import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.jdt.internal.compiler.classfmt.ClassFileConstants;
import org.eclipse.jdt.internal.compiler.parser.ScannerHelper;
import org.junit.jupiter.api.Test;

public class ScannerHelperTest {
    private static final int GREEK_SMALL_LETTER_ALPHA = 0x03B1;

    @Test
    void identifierChecksLoadLegacyUnicodeTables() {
        assertThat(ScannerHelper.isJavaIdentifierStart(ClassFileConstants.JDK1_6, GREEK_SMALL_LETTER_ALPHA)).isTrue();
        assertThat(ScannerHelper.isJavaIdentifierPart(ClassFileConstants.JDK1_6, GREEK_SMALL_LETTER_ALPHA)).isTrue();
    }

    @Test
    void identifierChecksLoadJava15UnicodeTables() {
        assertThat(ScannerHelper.isJavaIdentifierStart(ClassFileConstants.JDK15, GREEK_SMALL_LETTER_ALPHA)).isTrue();
        assertThat(ScannerHelper.isJavaIdentifierPart(ClassFileConstants.JDK15, GREEK_SMALL_LETTER_ALPHA)).isTrue();
    }
}
