/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_javaslang.javaslang_match;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import javax.lang.model.SourceVersion;
import javaslang.API;
import javaslang.Tuple;
import javaslang.Tuple0;
import javaslang.Tuple1;
import javaslang.Tuple2;
import javaslang.Tuple3;
import javaslang.Tuple4;
import javaslang.control.Option;
import javaslang.match.PatternsProcessor;
import javaslang.match.annotation.Patterns;
import javaslang.match.annotation.Unapply;
import org.junit.jupiter.api.Test;

public class Javaslang_matchTest {
    @Test
    void generatedPattern2DeconstructsObjectsAndPassesTupleElementsToMatchCase() {
        Person person = new Person("Ada", 36);

        String result = API.Match(person)
                .of(API.Case(
                        Javaslang_matchTest_PatternDefinitionsPatterns.person(API.$("Ada"), API.<Integer>$()),
                        (String name, Integer age) -> name + " is " + age));

        assertThat(result).isEqualTo("Ada is 36");
    }

    @Test
    void generatedPattern3ComposesNestedMatchersAndRejectsNonMatchingComponents() {
        Task task = new Task("native-image", 8, Priority.HIGH);

        Option<String> exactMatch = API.Match(task)
                .option(API.Case(
                        Javaslang_matchTest_PatternDefinitionsPatterns.task(
                                API.$("native-image"), API.$(8), API.$(Priority.HIGH)),
                        (String title, Integer estimate, Priority priority) -> title + ":" + estimate + ":" + priority));
        Option<String> wrongEstimate = API.Match(task)
                .option(API.Case(
                        Javaslang_matchTest_PatternDefinitionsPatterns.task(
                                API.$("native-image"), API.$(3), API.$(Priority.HIGH)),
                        (String title, Integer estimate, Priority priority) -> "unreachable"));

        assertThat(exactMatch.isDefined()).isTrue();
        assertThat(exactMatch.get()).isEqualTo("native-image:8:HIGH");
        assertThat(wrongEstimate.isEmpty()).isTrue();
    }

    @Test
    void generatedPattern4PassesAllTupleElementsToMatchCase() {
        Deployment deployment = new Deployment("metadata-service", "eu-west", 3, true);

        String result = API.Match(deployment)
                .of(API.Case(
                        Javaslang_matchTest_PatternDefinitionsPatterns.deployment(
                                API.$("metadata-service"), API.$("eu-west"), API.$(3), API.$(true)),
                        (String service, String region, Integer replicas, Boolean healthy) ->
                                service + "@" + region + ":" + replicas + ":" + healthy));

        assertThat(result).isEqualTo("metadata-service@eu-west:3:true");
    }

    @Test
    void generatedPattern0MatchesByTypeOnly() {
        Option<String> closedResult = API.Match((Object) new ClosedDoor())
                .option(API.Case(Javaslang_matchTest_PatternDefinitionsPatterns.closedDoor, "closed"));
        Option<String> openResult = API.Match((Object) new OpenDoor())
                .option(API.Case(Javaslang_matchTest_PatternDefinitionsPatterns.closedDoor, "closed"));

        assertThat(closedResult.isDefined()).isTrue();
        assertThat(closedResult.get()).isEqualTo("closed");
        assertThat(openResult.isEmpty()).isTrue();
    }

    @Test
    void generatedGenericPatternPreservesTypeInformation() {
        Box<String> box = new Box<>("payload");

        String result = API.Match(box)
                .of(API.Case(
                        Javaslang_matchTest_PatternDefinitionsPatterns.box(API.$("payload")),
                        (String value) -> value.toUpperCase()));

        assertThat(result).isEqualTo("PAYLOAD");
    }

    @Test
    void generatedPatternAcceptsPredicateSubpatternsForDeconstructedValues() {
        Score score = new Score("build", 92);

        Option<String> successfulMatch = API.Match(score)
                .option(API.Case(
                        Javaslang_matchTest_PatternDefinitionsPatterns.score(
                                API.$((String category) -> category.startsWith("bu")),
                                API.$((Integer points) -> points >= 90)),
                        (String category, Integer points) -> category + "=" + points));
        Option<String> rejectedMatch = API.Match(score)
                .option(API.Case(
                        Javaslang_matchTest_PatternDefinitionsPatterns.score(
                                API.$((String category) -> category.startsWith("doc")),
                                API.$((Integer points) -> points >= 90)),
                        (String category, Integer points) -> "unreachable"));

        assertThat(successfulMatch.isDefined()).isTrue();
        assertThat(successfulMatch.get()).isEqualTo("build=92");
        assertThat(rejectedMatch.isEmpty()).isTrue();
    }

    @Test
    void unapplyMethodsExposeExpectedTupleShapesForGeneratedPatterns() {
        Tuple0 closedDoor = PatternDefinitions.closedDoor(new ClosedDoor());
        Tuple1<String> box = PatternDefinitions.box(new Box<>("value"));
        Tuple2<String, Integer> person = PatternDefinitions.person(new Person("Grace", 30));
        Tuple3<String, Integer, Priority> task = PatternDefinitions.task(new Task("metadata", 5, Priority.NORMAL));

        assertThat(closedDoor.arity()).isZero();
        assertThat(box._1()).isEqualTo("value");
        assertThat(person._1()).isEqualTo("Grace");
        assertThat(person._2()).isEqualTo(30);
        assertThat(task._1()).isEqualTo("metadata");
        assertThat(task._2()).isEqualTo(5);
        assertThat(task._3()).isEqualTo(Priority.NORMAL);
    }

    @Test
    void processorAdvertisesPatternsAnnotationAndLatestSupportedSourceVersion() {
        PatternsProcessor processor = new PatternsProcessor();

        Set<String> supportedAnnotationTypes = processor.getSupportedAnnotationTypes();
        SourceVersion sourceVersion = processor.getSupportedSourceVersion();

        assertThat(supportedAnnotationTypes).containsExactly("javaslang.match.annotation.Patterns");
        assertThat(sourceVersion).isEqualTo(SourceVersion.latestSupported());
    }

    @Patterns
    public static final class PatternDefinitions {
        private PatternDefinitions() {
        }

        @Unapply
        public static Tuple0 closedDoor(ClosedDoor door) {
            return Tuple.empty();
        }

        @Unapply
        public static <T> Tuple1<T> box(Box<T> box) {
            return Tuple.of(box.value);
        }

        @Unapply
        public static Tuple2<String, Integer> person(Person person) {
            return Tuple.of(person.name, person.age);
        }

        @Unapply
        public static Tuple2<String, Integer> score(Score score) {
            return Tuple.of(score.category, score.points);
        }

        @Unapply
        public static Tuple3<String, Integer, Priority> task(Task task) {
            return Tuple.of(task.title, task.estimate, task.priority);
        }

        @Unapply
        public static Tuple4<String, String, Integer, Boolean> deployment(Deployment deployment) {
            return Tuple.of(deployment.service, deployment.region, deployment.replicas, deployment.healthy);
        }
    }

    public static final class Person {
        private final String name;
        private final int age;

        private Person(String name, int age) {
            this.name = name;
            this.age = age;
        }
    }

    public static final class Box<T> {
        private final T value;

        private Box(T value) {
            this.value = value;
        }
    }

    public static final class Score {
        private final String category;
        private final int points;

        private Score(String category, int points) {
            this.category = category;
            this.points = points;
        }
    }

    public interface Door {
    }

    public static final class ClosedDoor implements Door {
    }

    public static final class OpenDoor implements Door {
    }

    public enum Priority {
        NORMAL,
        HIGH
    }

    public static final class Task {
        private final String title;
        private final int estimate;
        private final Priority priority;

        private Task(String title, int estimate, Priority priority) {
            this.title = title;
            this.estimate = estimate;
            this.priority = priority;
        }
    }

    public static final class Deployment {
        private final String service;
        private final String region;
        private final int replicas;
        private final boolean healthy;

        private Deployment(String service, String region, int replicas, boolean healthy) {
            this.service = service;
            this.region = region;
            this.replicas = replicas;
            this.healthy = healthy;
        }
    }
}
