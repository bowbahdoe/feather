package org.codejargon.feather;

import dev.mccue.feather.Feather;
import dev.mccue.feather.Key;
import dev.mccue.feather.Provides;
import org.junit.Test;

import jakarta.inject.Named;

import static org.junit.Assert.assertEquals;

public class NamedDependencyTest {
    @Test
    public void namedInstanceWithModule() {
        Feather feather = Feather.with(new HelloWorldModule());
        assertEquals("Hello!", feather.instance(Key.of(String.class, "hello")));
        assertEquals("Hi!", feather.instance(Key.of(String.class, "hi")));
    }

    public static class HelloWorldModule {
        @Provides
        @Named("hello")
        String hello() {
            return "Hello!";
        }

        @Provides
        @Named("hi")
        String hi() {
            return "Hi!";
        }
    }

}
