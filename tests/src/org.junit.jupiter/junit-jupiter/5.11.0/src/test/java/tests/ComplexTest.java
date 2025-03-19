package tests;

import org.junit.jupiter.api.Test;
import tests.common.Fruits;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;


public class ComplexTest {

    private static final String RESOURCE = "/resource.txt";

    @Test
    public void callMethodFromOtherClass() {
        String fruit = Fruits.getSomeFruit();
        assertNotNull(fruit);
        assertTrue(fruit.contains("berry"));
    }

    @Test
    public void accessMethodReflectively() {
        try {
            String methodName = "get" + "Blackberry";
            Method method = Fruits.class.getDeclaredMethod(methodName, (Class<?>[]) null);
            method.setAccessible(true);

            Fruits f = new Fruits();
            String retval = (String) method.invoke(f);
            assertTrue(retval.equalsIgnoreCase("blackberry"));
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }


    @Test
    public void accessFiledReflectively() {
        try {
            Field fruitsField = Fruits.class.getDeclaredField("fruits");
            fruitsField.setAccessible(true);

            Fruits f = new Fruits();
            List<String> fruits = (List<String>) fruitsField.get(f);

            assertEquals(3, fruits.size());
            assertEquals(3, fruits.stream().filter(fruit -> fruit.contains("berry")).collect(Collectors.toList()).size());
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void resourceTest() {
        try(InputStream is = ComplexTest.class.getResourceAsStream(RESOURCE)) {
            assertNotNull(is);
            InputStreamReader isr = new InputStreamReader(is);
            BufferedReader br = new BufferedReader(isr);

            assertTrue(br.readLine().equalsIgnoreCase("Hello from resource!"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}
