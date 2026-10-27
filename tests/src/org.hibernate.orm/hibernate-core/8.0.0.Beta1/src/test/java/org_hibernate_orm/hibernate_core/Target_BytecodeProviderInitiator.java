/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate_orm.hibernate_core;

import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import org.hibernate.bytecode.spi.BytecodeProvider;

@TargetClass(className = "org.hibernate.bytecode.internal.BytecodeProviderInitiator")
final class Target_BytecodeProviderInitiator {

    @Substitute
    public static BytecodeProvider buildDefaultBytecodeProvider() {
        return new org.hibernate.bytecode.internal.none.BytecodeProviderImpl();
    }

    @Substitute
    public static BytecodeProvider getBytecodeProvider(Iterable<BytecodeProvider> bytecodeProviders) {
        return new org.hibernate.bytecode.internal.none.BytecodeProviderImpl();
    }
}
