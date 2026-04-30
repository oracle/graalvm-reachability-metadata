/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package org_jetbrains_kotlin.kotlin_stdlib

import java.lang.reflect.Method
import kotlin.collections.AbstractList
import kotlin.jvm.internal.KotlinGenericDeclaration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

public class KotlinGenericDeclarationKtTest {
    @Test
    public fun functionReferenceFindsBackingGenericMethod() {
        val getFunction: (AbstractList<String>, Int) -> String = AbstractList<String>::get
        val genericDeclaration: KotlinGenericDeclaration = getFunction as KotlinGenericDeclaration

        val declaration = genericDeclaration.findJavaDeclaration()

        assertThat(declaration).isInstanceOf(Method::class.java)
        val method: Method = declaration as Method
        assertThat(method.declaringClass).isEqualTo(AbstractList::class.java)
        assertThat(method.name).isEqualTo("get")
        assertThat(method.parameterTypes).containsExactly(Int::class.javaPrimitiveType)
        assertThat(method.genericReturnType.typeName).isEqualTo("E")
    }
}
