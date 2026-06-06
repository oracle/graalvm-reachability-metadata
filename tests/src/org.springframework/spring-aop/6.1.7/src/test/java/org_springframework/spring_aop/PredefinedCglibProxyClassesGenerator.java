/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_aop;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.aopalliance.intercept.MethodInterceptor;

import org.springframework.aop.framework.AdvisedSupport;
import org.springframework.aop.framework.AopProxy;
import org.springframework.aop.framework.DefaultAopProxyFactory;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.objenesis.SpringObjenesis;

public final class PredefinedCglibProxyClassesGenerator {
    private static final String DEBUG_LOCATION_PROPERTY =
            "cglib.debugLocation";
    private static final List<String> EXPECTED_CLASS_FILES = List.of(
            "org_springframework/spring_aop/CglibAopProxyTest$GreetingTarget$$SpringCGLIB$$0.class",
            "org_springframework/spring_aop/ObjenesisCglibAopProxyTest$ConstructorGreetingService$$SpringCGLIB$$0.class",
            "org_springframework/spring_aop/ObjenesisCglibAopProxyTest$DefaultGreetingService$$SpringCGLIB$$0.class"
    );

    private PredefinedCglibProxyClassesGenerator() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected a single output directory argument");
        }
        Path outputDirectory = Path.of(args[0]).toAbsolutePath().normalize();
        Files.createDirectories(outputDirectory);

        String previousDebugLocation = System.getProperty(DEBUG_LOCATION_PROPERTY);
        String previousObjenesisMode = System.getProperty(SpringObjenesis.IGNORE_OBJENESIS_PROPERTY_NAME);
        try {
            System.setProperty(DEBUG_LOCATION_PROPERTY, outputDirectory.toString());
            System.setProperty(SpringObjenesis.IGNORE_OBJENESIS_PROPERTY_NAME, "true");
            generateProxyClasses();
            verifyExpectedClassFiles(outputDirectory);
        } finally {
            restoreSystemProperty(DEBUG_LOCATION_PROPERTY, previousDebugLocation);
            restoreSystemProperty(SpringObjenesis.IGNORE_OBJENESIS_PROPERTY_NAME, previousObjenesisMode);
        }
    }

    private static void generateProxyClasses() throws Exception {
        generateGreetingTargetProxyClass();
        generateObjenesisProxyClass(
                ObjenesisCglibAopProxyTest.DefaultGreetingService.class,
                new ObjenesisCglibAopProxyTest.DefaultGreetingService()
        );
        generateObjenesisProxyClass(
                ObjenesisCglibAopProxyTest.ConstructorGreetingService.class,
                new ObjenesisCglibAopProxyTest.ConstructorGreetingService("Hello")
        );
    }

    private static void generateGreetingTargetProxyClass() {
        ProxyFactory proxyFactory = new ProxyFactory(new CglibAopProxyTest.GreetingTarget());
        proxyFactory.setProxyTargetClass(true);
        proxyFactory.addAdvice((MethodInterceptor) invocation -> invocation.proceed());
        proxyFactory.setFrozen(true);
        proxyFactory.getProxyClass(CglibAopProxyTest.GreetingTarget.class.getClassLoader());
    }

    private static void generateObjenesisProxyClass(
            Class<? extends ObjenesisCglibAopProxyTest.GreetingService> targetClass,
            ObjenesisCglibAopProxyTest.GreetingService target
    ) throws Exception {
        AdvisedSupport config = new AdvisedSupport(ObjenesisCglibAopProxyTest.GreetingService.class);
        config.setTargetClass(targetClass);
        config.setTarget(target);
        config.setProxyTargetClass(true);
        AopProxy aopProxy = new DefaultAopProxyFactory().createAopProxy(config);

        Method getProxyClassMethod = aopProxy.getClass().getMethod("getProxyClass", ClassLoader.class);
        getProxyClassMethod.setAccessible(true);
        getProxyClassMethod.invoke(aopProxy, targetClass.getClassLoader());
    }

    private static void verifyExpectedClassFiles(Path outputDirectory) throws IOException {
        List<String> missingClassFiles = EXPECTED_CLASS_FILES.stream()
                .filter(relativePath -> !Files.isRegularFile(outputDirectory.resolve(relativePath)))
                .toList();
        if (missingClassFiles.isEmpty()) {
            return;
        }

        List<String> availableClassFiles;
        try (Stream<Path> stream = Files.walk(outputDirectory)) {
            availableClassFiles = stream
                    .filter(Files::isRegularFile)
                    .map(outputDirectory::relativize)
                    .map(Path::toString)
                    .sorted()
                    .collect(Collectors.toList());
        }
        throw new IOException("Missing generated CGLIB proxy classes: " + missingClassFiles + "; available=" + availableClassFiles);
    }

    private static void restoreSystemProperty(String key, String value) {
        if (value == null) {
            System.clearProperty(key);
            return;
        }
        System.setProperty(key, value);
    }
}
