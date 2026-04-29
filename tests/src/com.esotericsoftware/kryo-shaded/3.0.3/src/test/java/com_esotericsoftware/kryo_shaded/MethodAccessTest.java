/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_esotericsoftware.kryo_shaded;

import static org.assertj.core.api.Assertions.assertThat;

import com.esotericsoftware.reflectasm.MethodAccess;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

public class MethodAccessTest {
    @Test
    void createsReusableMethodAccessForNonPrivateMethodsDeclaredAcrossHierarchy() {
        MethodAccess access = MethodAccess.get(Subject.class);
        Subject subject = new Subject("initial");

        Object renamed = access.invoke(subject, access.getIndex("rename", String.class), "updated");
        Object total = access.invoke(subject, "add", new Class<?>[] {int.class, int.class}, 3, 4);
        Object score = access.invoke(subject, "changeScore", 2.5d);
        Object activeResult = access.invoke(subject, "markActive");
        Object label = access.invoke(subject, "baseLabel", new Class<?>[] {String.class}, "subject");

        assertThat(access.getMethodNames())
                .contains("rename", "add", "changeScore", "markActive", "baseLabel")
                .doesNotContain("privateNote", "resetAll");
        assertThat(access.getParameterTypes()[access.getIndex("rename")]).containsExactly(String.class);
        assertThat(access.getReturnTypes()[access.getIndex("add", 2)]).isEqualTo(int.class);
        assertThat(renamed).isEqualTo("initial->updated");
        assertThat(total).isEqualTo(7);
        assertThat(score).isEqualTo(2.5d);
        assertThat(activeResult).isNull();
        assertThat(label).isEqualTo("subject:updated");
        assertThat(subject.name).isEqualTo("updated");
        assertThat(subject.active).isTrue();
    }

    public static class BaseSubject {
        public String baseLabel(String prefix) {
            return prefix + ":" + currentName();
        }

        protected String currentName() {
            return "base";
        }
    }

    public static class Subject extends BaseSubject {
        String name;
        boolean active;
        double score;

        public Subject(String name) {
            this.name = name;
        }

        public String rename(String newName) {
            String previousName = name;
            name = newName;
            return previousName + "->" + name;
        }

        public int add(int first, int second) {
            return first + second;
        }

        void markActive() {
            active = true;
        }

        double changeScore(double newScore) {
            score = newScore;
            return score;
        }

        @Override
        protected String currentName() {
            return name;
        }

        private String privateNote() {
            return "private";
        }

        public static void resetAll() {
        }
    }

    public static class SubjectMethodAccess extends MethodAccess {
        public SubjectMethodAccess() {
        }

        @Override
        public Object invoke(Object instance, int methodIndex, Object... args) {
            Subject subject = (Subject) instance;
            String methodName = getMethodNames()[methodIndex];
            Class<?>[] parameterTypes = getParameterTypes()[methodIndex];
            if ("rename".equals(methodName) && Arrays.equals(parameterTypes, new Class<?>[] {String.class})) {
                return subject.rename((String) args[0]);
            }
            if ("add".equals(methodName) && Arrays.equals(parameterTypes, new Class<?>[] {int.class, int.class})) {
                return subject.add((Integer) args[0], (Integer) args[1]);
            }
            if ("markActive".equals(methodName) && parameterTypes.length == 0) {
                subject.markActive();
                return null;
            }
            if ("changeScore".equals(methodName) && Arrays.equals(parameterTypes, new Class<?>[] {double.class})) {
                return subject.changeScore((Double) args[0]);
            }
            if ("baseLabel".equals(methodName) && Arrays.equals(parameterTypes, new Class<?>[] {String.class})) {
                return subject.baseLabel((String) args[0]);
            }
            if ("currentName".equals(methodName) && parameterTypes.length == 0) {
                return subject.currentName();
            }
            throw new IllegalArgumentException("Method not found: " + methodIndex);
        }
    }
}
