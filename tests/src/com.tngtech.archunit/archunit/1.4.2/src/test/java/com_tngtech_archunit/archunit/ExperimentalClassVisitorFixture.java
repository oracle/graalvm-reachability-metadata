/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_tngtech_archunit.archunit;

import com.tngtech.archunit.thirdparty.org.objectweb.asm.ClassVisitor;
import com.tngtech.archunit.thirdparty.org.objectweb.asm.Opcodes;

public final class ExperimentalClassVisitorFixture extends ClassVisitor {
    public ExperimentalClassVisitorFixture() {
        super(previewApi(Opcodes.ASM10_EXPERIMENTAL));
    }

    private static int previewApi(long api) {
        return api instanceof int value ? value : 0;
    }
}
