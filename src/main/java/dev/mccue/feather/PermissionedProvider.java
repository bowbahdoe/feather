package dev.mccue.feather;

import jakarta.inject.Provider;

import java.lang.invoke.MethodHandles;

interface PermissionedProvider<T> {
    T get(MethodHandles.Lookup lookup);

    default Provider<?> asProvider(MethodHandles.Lookup lookup) {
        return () -> get(lookup);
    }
}
