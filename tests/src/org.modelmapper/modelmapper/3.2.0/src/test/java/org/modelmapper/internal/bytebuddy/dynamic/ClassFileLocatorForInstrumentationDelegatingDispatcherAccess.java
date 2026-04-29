/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.modelmapper.internal.bytebuddy.dynamic;

import java.lang.reflect.Field;
import java.util.Vector;

import org.modelmapper.internal.bytebuddy.dynamic.ClassFileLocator.ForInstrumentation.ClassLoadingDelegate.ForDelegatingClassLoader.Dispatcher;
import org.modelmapper.internal.bytebuddy.dynamic.ClassFileLocator.ForInstrumentation.ClassLoadingDelegate.ForDelegatingClassLoader.Dispatcher.Resolved;

public final class ClassFileLocatorForInstrumentationDelegatingDispatcherAccess {
    private ClassFileLocatorForInstrumentationDelegatingDispatcherAccess() {
    }

    public static Vector<Class<?>> extractClasses(ClassLoader classLoader) throws NoSuchFieldException {
        Field classesField = classLoader.getClass().getField("classes");
        Dispatcher dispatcher = new Resolved(classesField).initialize();
        return dispatcher.extract(classLoader);
    }
}
