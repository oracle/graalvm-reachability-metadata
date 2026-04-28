/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.h2.util;

public final class NullBytecodeSourceCompiler extends SourceCompiler {
    @Override
    byte[] javacCompile(String packageName, String className, String source) {
        return null;
    }
}
