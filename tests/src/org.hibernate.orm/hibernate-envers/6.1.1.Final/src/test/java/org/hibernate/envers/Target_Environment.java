/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.hibernate.envers;

import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import org.hibernate.bytecode.internal.none.BytecodeProviderImpl;
import org.hibernate.bytecode.spi.BytecodeProvider;

@TargetClass(className = "org.hibernate.cfg.Environment")
final class Target_Environment {

    @Substitute
    private static BytecodeProvider buildBytecodeProvider(String providerName) {
        return new BytecodeProviderImpl();
    }
}
