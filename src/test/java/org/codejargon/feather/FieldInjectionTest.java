package org.codejargon.feather;

import dev.mccue.feather.Feather;
import org.junit.Test;

import jakarta.inject.Inject;

import java.lang.invoke.MethodHandles;

import static org.junit.Assert.assertNotNull;

public class FieldInjectionTest {
    @Test
    public void fieldsInjected() {
        Feather feather = Feather.with();
        Target target = new Target();
        feather.injectFields(target, MethodHandles.lookup());
        assertNotNull(target.a);
    }


    public static class Target {
        @Inject
        private A a;
    }

    public static class A {

    }
}
