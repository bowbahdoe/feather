package org.codejargon.feather;

import dev.mccue.feather.Feather;
import dev.mccue.feather.Provides;
import org.junit.Test;

import java.lang.invoke.MethodHandles;

import static org.junit.Assert.assertEquals;

public class ModuleOverrideTest {
    @Test
    public void dependencyOverridenByModule() {
        Feather feather = Feather.with(new PlainStubOverrideModule());
        assertEquals(PlainStub.class, feather.instance(Plain.class).getClass());
    }


    @Test
    public void moduleOverwrittenBySubClass() {
        assertEquals("foo", Feather.with(new FooModule()).instance(String.class, MethodHandles.lookup()));
        assertEquals("bar", Feather.with(new FooOverrideModule()).instance(String.class, MethodHandles.lookup()));
    }

    public static class Plain {
    }

    public static class PlainStub extends Plain {

    }

    public static class PlainStubOverrideModule {
        @Provides
        public Plain plain(PlainStub plainStub) {
            return plainStub;
        }

    }

    public static class FooModule {
        @Provides
        String foo() {
            return "foo";
        }
    }

    public static class FooOverrideModule extends FooModule {
        @Provides
        @Override
        String foo() {
            return "bar";
        }
    }




}
