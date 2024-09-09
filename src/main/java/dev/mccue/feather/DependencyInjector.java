package dev.mccue.feather;

import jakarta.inject.Provider;

import java.util.ArrayList;

public sealed interface DependencyInjector
        permits Feather {
    /**
     * @return an instance of type
     */
    <T> T instance(Class<T> type);

    /**
     * @return instance specified by key (type and qualifier)
     */
    <T> T instance(Key<T> key);

    /**
     * @return provider of type
     */
    <T> Provider<T> provider(Class<T> type);

    /**
     * @return provider of key (type, qualifier)
     */
    <T> Provider<T> provider(Key<T> key);

    /**
     * Injects fields to the target object
     */
    void injectFields(Object target);

    /**
     * @return A builder to make a {@link DependencyInjector}.
     */
    static Builder builder() {
        return new Builder();
    }

    final class Builder {
        ArrayList<Object> modules = new ArrayList<>();

        private Builder() {}

        public Builder module(Object o) {
            modules.add(o);
            return this;
        }

        public DependencyInjector build() {
            return Feather.with(modules);
        }
    }
}
