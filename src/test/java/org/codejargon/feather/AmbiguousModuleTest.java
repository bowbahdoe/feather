package org.codejargon.feather;

import dev.mccue.feather.Feather;
import dev.mccue.feather.FeatherException;
import dev.mccue.feather.Provides;
import org.junit.Test;

public class AmbiguousModuleTest {
    @Test(expected = FeatherException.class)
    public void ambiguousModule() {
        Feather.with(new Module());
    }

    public static class Module {
        @Provides
        String foo() {
            return "foo";
        }

        @Provides
        String bar() {
            return "bar";
        }
    }
}
