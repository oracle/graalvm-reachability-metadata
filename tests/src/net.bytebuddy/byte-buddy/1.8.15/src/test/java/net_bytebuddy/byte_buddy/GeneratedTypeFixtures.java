/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_bytebuddy.byte_buddy;

import net.bytebuddy.ByteBuddy;
import net.bytebuddy.ClassFileVersion;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.implementation.FixedValue;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Callable;

import static net.bytebuddy.matcher.ElementMatchers.named;

final class GeneratedTypeFixtures {
    static final String BYTE_ARRAY_LOADED_TYPE = "net_bytebuddy.byte_buddy.generated.ByteArrayLoadedType";
    static final String STATIC_FIELD_CALLABLE = "net_bytebuddy.byte_buddy.generated.StaticFieldCallable";
    static final String LOCK_HELD_SUPER_TYPE = "net_bytebuddy.byte_buddy.generated.agentbuilder.LockHeldSuperType";
    static final String LOCK_HELD_SUB_TYPE = "net_bytebuddy.byte_buddy.generated.agentbuilder.LockHeldSubType";
    static final String SIMPLE_ACTION_SUPER_TYPE = "net_bytebuddy.byte_buddy.generated.agentbuilder.SimpleActionSuperType";
    static final String SIMPLE_ACTION_SUB_TYPE = "net_bytebuddy.byte_buddy.generated.agentbuilder.SimpleActionSubType";

    private GeneratedTypeFixtures() {
    }

    static DynamicType.Unloaded<?> byteArrayLoadedType() {
        return new ByteBuddy(ClassFileVersion.JAVA_V8)
                .subclass(Object.class)
                .name(BYTE_ARRAY_LOADED_TYPE)
                .make();
    }

    static DynamicType.Unloaded<?> staticFieldCallable(Object fixedValue) {
        return new ByteBuddy(ClassFileVersion.JAVA_V8)
                .subclass(Object.class)
                .implement(Callable.class)
                .name(STATIC_FIELD_CALLABLE)
                .method(named("call"))
                .intercept(FixedValue.reference(fixedValue, "fixedValue"))
                .make();
    }

    static Map<String, byte[]> agentBuilderTypeDefinitions(String superTypeName, String subTypeName) {
        DynamicType.Unloaded<?> superType = new ByteBuddy(ClassFileVersion.JAVA_V8)
                .subclass(Object.class)
                .name(superTypeName)
                .make();
        DynamicType.Unloaded<?> subType = new ByteBuddy(ClassFileVersion.JAVA_V8)
                .subclass(superType.getTypeDescription())
                .name(subTypeName)
                .make();
        Map<String, byte[]> typeDefinitions = new LinkedHashMap<String, byte[]>();
        typeDefinitions.put(superType.getTypeDescription().getName(), superType.getBytes());
        typeDefinitions.put(subType.getTypeDescription().getName(), subType.getBytes());
        return typeDefinitions;
    }
}
