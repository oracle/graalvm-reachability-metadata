/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_javassist.javassist;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import javassist.ClassClassPath;
import javassist.ClassPool;
import javassist.tools.reflect.ClassMetaobject;
import javassist.tools.reflect.Metalevel;
import javassist.tools.reflect.Metaobject;

public final class TestSupport {
    private TestSupport() {
    }

    public static ClassPool classPool() {
        ClassPool pool = new ClassPool(false);
        pool.appendClassPath(new ClassClassPath(ClassPool.class));
        return pool;
    }

    public static Object roundTrip(Serializable value) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(value);
        }
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            return input.readObject();
        }
    }

    @Retention(RetentionPolicy.RUNTIME)
    public @interface Label {
        String value();
    }

    public static final class LabelValue implements Label {
        private final String value;

        public LabelValue(String value) {
            this.value = value;
        }

        @Override
        public String value() {
            return value;
        }

        @Override
        public Class<? extends Annotation> annotationType() {
            return Label.class;
        }
    }

    // A statically defined implementation of the public metalevel protocol.
    public static class Account implements Metalevel, Serializable {
        private static final long serialVersionUID = 1L;
        public static String currency = "USD";
        public int balance;
        private Metaobject metaobject;

        public Account(int balance) {
            this.balance = balance;
        }

        public int _m_0_deposit(int amount) {
            balance += amount;
            return balance;
        }

        public static String _m_1_quote(String amount) {
            return currency + " " + amount;
        }

        @Override
        public ClassMetaobject _getClass() {
            return new ClassMetaobject(new String[]{Account.class.getName()});
        }

        @Override
        public Metaobject _getMetaobject() {
            return metaobject;
        }

        @Override
        public void _setMetaobject(Metaobject value) {
            metaobject = value;
        }
    }

    public static class Greeting {
        public String greet(String name) {
            return "Hello, " + name;
        }
    }
}
