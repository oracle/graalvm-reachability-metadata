/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_javaslang.javaslang_match;

import javaslang.Tuple;
import javaslang.Tuple0;
import javaslang.Tuple1;
import javaslang.Tuple2;
import javaslang.Tuple3;
import javaslang.Tuple4;
import javaslang.control.Option;
import javaslang.match.annotation.Patterns;
import javaslang.match.annotation.Unapply;
import org.junit.jupiter.api.Test;

import java.util.Objects;

import static javaslang.API.$;
import static javaslang.API.Case;
import static javaslang.API.Match;
import static org.assertj.core.api.Assertions.assertThat;

@Patterns
public class Javaslang_matchTest {
    @Test
    void generatedPatternDeconstructsObjectInsideMatchCase() {
        Person person = new Person("Ada", 37);

        String description = Match(person).of(
                Case(Javaslang_matchTestPatterns.person($("Grace"), $()), (name, age) -> "unexpected"),
                Case(Javaslang_matchTestPatterns.person($("Ada"), $((Integer age) -> age >= 30)),
                        (name, age) -> name + " is " + age),
                Case($(), ignored -> "unknown"));

        assertThat(description).isEqualTo("Ada is 37");
    }

    @Test
    void generatedPatternReportsDefinedAndEmptyOptionsForAcceptedParts() {
        Person person = new Person("Edsger", 72);

        Option<Tuple2<String, Integer>> matched = Javaslang_matchTestPatterns
                .person($((String name) -> name.startsWith("E")), $((Integer age) -> age > 70))
                .apply(person);
        Option<Tuple2<String, Integer>> rejected = Javaslang_matchTestPatterns
                .person($("Edsger"), $((Integer age) -> age < 70))
                .apply(person);

        assertThat(matched.isDefined()).isTrue();
        assertThat(matched.get()._1).isEqualTo("Edsger");
        assertThat(matched.get()._2).isEqualTo(72);
        assertThat(rejected.isEmpty()).isTrue();
    }

    @Test
    void generatedGenericPatternComposesWithNestedGeneratedPattern() {
        Envelope<Person> envelope = new Envelope<>("primary", new Person("Barbara", 44));

        String routed = Match(envelope).of(
                Case(Javaslang_matchTestPatterns.envelope(
                                $("secondary"),
                                Javaslang_matchTestPatterns.person($(), $())),
                        (label, person) -> "wrong route"),
                Case(Javaslang_matchTestPatterns.envelope(
                                $("primary"),
                                Javaslang_matchTestPatterns.person($("Barbara"), $((Integer age) -> age > 40))),
                        (label, person) -> label + ":" + person.name()),
                Case($(), ignored -> "unrouted"));

        assertThat(routed).isEqualTo("primary:Barbara");
    }

    @Test
    void generatedSingleArityGenericPatternExtractsBoxValue() {
        Box<String> box = new Box<>("token-42");

        String value = Match(box).of(
                Case(Javaslang_matchTestPatterns.box($("other")), ignored -> "wrong"),
                Case(Javaslang_matchTestPatterns.box($((String text) -> text.startsWith("token"))),
                        String::toUpperCase),
                Case($(), ignored -> "missing"));

        assertThat(value).isEqualTo("TOKEN-42");
    }

    @Test
    void generatedZeroArityPatternMatchesByRuntimeType() {
        Heartbeat heartbeat = new Heartbeat("node-a");

        String result = Match(heartbeat).of(
                Case(Javaslang_matchTestPatterns.heartbeat, value -> "heartbeat from " + value.node()),
                Case($(), ignored -> "other"));

        assertThat(result).isEqualTo("heartbeat from node-a");
        assertThat(Javaslang_matchTestPatterns.heartbeat.apply(heartbeat).isDefined()).isTrue();
        assertThat(Javaslang_matchTestPatterns.heartbeat.apply(null).isEmpty()).isTrue();
    }

    @Test
    void generatedThreeArityPatternCanFilterEveryExtractedPart() {
        Point3 point = new Point3(2, 3, 5);

        Integer product = Match(point).of(
                Case(Javaslang_matchTestPatterns.point3($(1), $(), $()), (x, y, z) -> -1),
                Case(Javaslang_matchTestPatterns.point3($(2), $((Integer y) -> y % 2 == 1), $(5)),
                        (x, y, z) -> x * y * z),
                Case($(), ignored -> 0));

        assertThat(product).isEqualTo(30);
    }

    @Test
    void generatedFourArityPatternCanMatchHigherArityExtractor() {
        Rectangle rectangle = new Rectangle(2, 3, 5, 7);

        Integer perimeter = Match(rectangle).of(
                Case(Javaslang_matchTestPatterns.rectangle($(0), $(), $(), $()), (left, top, right, bottom) -> -1),
                Case(Javaslang_matchTestPatterns.rectangle(
                                $(2),
                                $((Integer top) -> top > 0),
                                $(5),
                                $((Integer bottom) -> bottom > 6)),
                        (left, top, right, bottom) -> 2 * ((right - left) + (bottom - top))),
                Case($(), ignored -> 0));

        assertThat(perimeter).isEqualTo(14);
    }

    @Unapply
    static Tuple2<String, Integer> person(Person person) {
        return Tuple.of(person.name(), person.age());
    }

    @Unapply
    static <T> Tuple2<String, T> envelope(Envelope<T> envelope) {
        return Tuple.of(envelope.label(), envelope.value());
    }

    @Unapply
    static Tuple0 heartbeat(Heartbeat heartbeat) {
        return Tuple.empty();
    }

    @Unapply
    static Tuple3<Integer, Integer, Integer> point3(Point3 point) {
        return Tuple.of(point.x(), point.y(), point.z());
    }

    @Unapply
    static <T> Tuple1<T> box(Box<T> box) {
        return Tuple.of(box.value());
    }

    @Unapply
    static Tuple4<Integer, Integer, Integer, Integer> rectangle(Rectangle rectangle) {
        return Tuple.of(rectangle.left(), rectangle.top(), rectangle.right(), rectangle.bottom());
    }

    static final class Person {
        private final String name;
        private final int age;

        Person(String name, int age) {
            this.name = Objects.requireNonNull(name, "name");
            this.age = age;
        }

        String name() {
            return name;
        }

        int age() {
            return age;
        }
    }

    static final class Envelope<T> {
        private final String label;
        private final T value;

        Envelope(String label, T value) {
            this.label = Objects.requireNonNull(label, "label");
            this.value = Objects.requireNonNull(value, "value");
        }

        String label() {
            return label;
        }

        T value() {
            return value;
        }
    }

    static final class Heartbeat {
        private final String node;

        Heartbeat(String node) {
            this.node = Objects.requireNonNull(node, "node");
        }

        String node() {
            return node;
        }
    }

    static final class Point3 {
        private final int x;
        private final int y;
        private final int z;

        Point3(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        int x() {
            return x;
        }

        int y() {
            return y;
        }

        int z() {
            return z;
        }
    }

    static final class Box<T> {
        private final T value;

        Box(T value) {
            this.value = Objects.requireNonNull(value, "value");
        }

        T value() {
            return value;
        }
    }

    static final class Rectangle {
        private final int left;
        private final int top;
        private final int right;
        private final int bottom;

        Rectangle(int left, int top, int right, int bottom) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
        }

        int left() {
            return left;
        }

        int top() {
            return top;
        }

        int right() {
            return right;
        }

        int bottom() {
            return bottom;
        }
    }
}
