package org.codejargon.feather;

import dev.mccue.feather.Feather;
import dev.mccue.feather.Key;
import dev.mccue.feather.Provides;
import org.junit.Test;

import jakarta.inject.Inject;
import jakarta.inject.Named;

import static org.junit.Assert.assertEquals;

public class PolymorphicDependencyTest {
    @Test
    public void multipleImplementations() {
        Feather feather = Feather.with(new Module());
        assertEquals(FooA.class, feather.instance(Key.of(Foo.class, "A")).getClass());
        assertEquals(FooB.class, feather.instance(Key.of(Foo.class, "B")).getClass());
    }

    public static class Module {
        @Provides
        @Named("A")
        Foo a(FooA fooA) {
            return fooA;
        }

        @Provides @Named("B")
        Foo a(FooB fooB) {
            return fooB;
        }
    }

    interface Foo {

    }

    public static class FooA implements Foo {
        @Inject
        public FooA() {
        }
    }

    public static class FooB implements Foo {
        @Inject
        public FooB() {
        }

    }
}
