package org.codejargon.feather;

import dev.mccue.feather.Feather;
import org.junit.Test;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import static org.junit.Assert.assertNotNull;

public class ProviderInjectionTest {
    @Test
    public void providerInjected() {
        Feather feather = Feather.with();
        assertNotNull(feather.instance(A.class).plainProvider.get());
    }

    public static class A {
        private final Provider<B> plainProvider;

        @Inject
        public A(Provider<B> plainProvider) {
            this.plainProvider = plainProvider;
        }
    }

    public static class B {

    }
}
