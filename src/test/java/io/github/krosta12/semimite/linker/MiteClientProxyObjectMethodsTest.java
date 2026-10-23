package io.github.krosta12.semimite.linker;

import org.junit.jupiter.api.Test;

import java.lang.foreign.Arena;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Object and default methods on a client proxy. */
class MiteClientProxyObjectMethodsTest {

    public interface Calculator {
        int add(int a, int b);

        default int twice(int a) {
            return add(a, a);
        }
    }

    static int nativeAdd(int a, int b) {
        return a + b;
    }

    private ManagedLibrary library() throws Exception {
        MethodHandle handle = MethodHandles.lookup().findStatic(
                MiteClientProxyObjectMethodsTest.class, "nativeAdd",
                MethodType.methodType(int.class, int.class, int.class));
        return new ManagedLibrary(
                new NativeLibraryLinker.LinkedLibrary(Map.of("add", handle), Arena.ofShared()));
    }

    @Test
    void proxySupportsObjectMethods() throws Exception {
        Calculator calc = new MiteClientProxyFactory().create(Calculator.class, library());

        assertEquals(5, calc.add(2, 3));
        assertTrue(calc.toString().contains("Calculator"), calc.toString());
        assertEquals(calc.hashCode(), calc.hashCode());
        assertEquals(calc, calc);
        assertNotEquals(calc, new Object());

        Set<Object> set = new HashSet<>();
        set.add(calc);
        assertTrue(set.contains(calc), "proxy must be usable as a hash key");
    }

    @Test
    void proxyInvokesDefaultMethods() throws Exception {
        Calculator calc = new MiteClientProxyFactory().create(Calculator.class, library());

        assertEquals(4, calc.twice(2), "default methods must not be routed to native code");
    }

    public interface WithDefaultChar {
        int add(int a, int b);

        default char initial() {
            return 'x';
        }
    }

    @Test
    void defaultMethodWithCharDoesNotBlockProxyCreation() throws Exception {
        WithDefaultChar client = new MiteClientProxyFactory().create(WithDefaultChar.class, library());

        assertEquals('x', client.initial(), "a default method has no native binding to validate");
        assertEquals(3, client.add(1, 2));
    }
}
