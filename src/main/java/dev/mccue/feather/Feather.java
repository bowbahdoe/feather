package dev.mccue.feather;

import jakarta.inject.*;
import java.lang.annotation.Annotation;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class Feather {
    private final Map<Key, PermissionedProvider<?>> providers = new ConcurrentHashMap<>();
    private final Map<Key, Object> singletons = new ConcurrentHashMap<>();
    private final Map<Class, FieldInjector[]> injectFields = new ConcurrentHashMap<>(0);

    record FieldInjector(Field field, boolean hasProvider, Key<?> key) {}
    /**
     * Constructs Feather with configuration modules
     */
    public static Feather with(Object... modules) {
        return new Feather(Arrays.asList(modules));
    }

    /**
     * Constructs Feather with configuration modules
     */
    public static Feather with(Iterable<?> modules) {
        return new Feather(modules);
    }

    private Feather(Iterable<?> modules) {
        providers.put(Key.of(Feather.class), (lookup) -> this);
        for (final Object module : modules) {
            if (module instanceof Class) {
                throw new FeatherException(String.format("%s provided as class instead of an instance.", ((Class) module).getName()));
            }
            for (Method providerMethod : providers(module.getClass())) {
                providerMethod(module, providerMethod);
            }
        }
    }

    /**
     * @return an instance of type
     */
    public <T> T instance(Class<T> type) {
        return instance(type, MethodHandles.lookup());
    }

    /**
     * @return instance specified by key (type and qualifier)
     */
    public <T> T instance(Key<T> key) {
        return instance(key, MethodHandles.lookup());
    }

    /**
     * @return an instance of type
     */
    public <T> T instance(Class<T> type, MethodHandles.Lookup lookup) {
        return provider(Key.of(type), lookup).get();
    }

    /**
     * @return instance specified by key (type and qualifier)
     */
    public <T> T instance(Key<T> key, MethodHandles.Lookup lookup) {
        return provider(key, lookup).get();
    }

    /**
     * @return provider of type
     */
    public <T> Provider<T> provider(Class<T> type) {
        return provider(Key.of(type), null, MethodHandles.lookup());
    }

    /**
     * @return provider of key (type, qualifier)
     */
    public <T> Provider<T> provider(Key<T> key) {
        return provider(key, MethodHandles.lookup());
    }

    /**
     * @return provider of type
     */
    public <T> Provider<T> provider(Class<T> type, MethodHandles.Lookup lookup) {
        return provider(Key.of(type), null, lookup);
    }

    /**
     * @return provider of key (type, qualifier)
     */
    public <T> Provider<T> provider(Key<T> key, MethodHandles.Lookup lookup) {
        return provider(key, null, lookup);
    }

    /**
     * Injects fields to the target object
     */
    public void injectFields(Object target) {
        injectFields(target, MethodHandles.lookup());
    }

    /**
     * Injects fields to the target object
     */
    public void injectFields(Object target, MethodHandles.Lookup lookup) {
        if (!injectFields.containsKey(target.getClass())) {
            injectFields.put(target.getClass(), injectFields(target.getClass()));
        }
        for (FieldInjector f: injectFields.get(target.getClass())) {
            Field field = f.field;
            Key key = f.key;
            try {
                var fieldSetter = lookup.unreflectSetter(field);
                fieldSetter.invokeWithArguments(target, f.hasProvider ? provider(key, lookup) : instance(key, lookup));
            } catch (Throwable t) {
                throw new FeatherException(String.format("Can't inject field %s in %s", field.getName(), target.getClass().getName()), t);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private <T> Provider<T> provider(final Key<T> key, Set<Key> chain, MethodHandles.Lookup lookup) {
        if (!providers.containsKey(key)) {
            final Constructor constructor = constructor(key);
            final PermissionedProvider<?>[] paramProviders = paramProviders(key, constructor.getParameterTypes(), constructor.getGenericParameterTypes(), constructor.getParameterAnnotations(), chain);
            providers.put(key, singletonProvider(key, key.type.getAnnotation(Singleton.class), new PermissionedProvider<>() {
                        @Override
                        public Object get(MethodHandles.Lookup lookup) {
                            try {
                                return lookup.unreflectConstructor(constructor)
                                        .invokeWithArguments(params(paramProviders, lookup));
                            } catch (Throwable t) {
                                throw new FeatherException(String.format("Can't instantiate %s", key.toString()), t);
                            }
                        }
                    })
            );
        }
        return (Provider<T>) providers.get(key).asProvider(lookup);
    }

    private void providerMethod(final Object module, final Method m) {
        final Key key = Key.of(m.getReturnType(), qualifier(m.getAnnotations()));
        if (providers.containsKey(key)) {
            throw new FeatherException(String.format("%s has multiple providers, module %s", key.toString(), module.getClass()));
        }
        Singleton singleton = m.getAnnotation(Singleton.class) != null ? m.getAnnotation(Singleton.class) : m.getReturnType().getAnnotation(Singleton.class);
        final PermissionedProvider<?>[] paramProviders = paramProviders(
                key,
                m.getParameterTypes(),
                m.getGenericParameterTypes(),
                m.getParameterAnnotations(),
                Collections.singleton(key)
        );
        providers.put(key, singletonProvider(key, singleton, new PermissionedProvider<>() {
                            @Override
                            public Object get(MethodHandles.Lookup lookup) {
                                try {
                                    var arguments = new ArrayList<Object>();
                                    arguments.add(module);
                                    arguments.addAll(Arrays.asList(params(paramProviders, lookup)));
                                    return lookup.unreflect(m)
                                            .invokeWithArguments(arguments);
                                } catch (Throwable t) {
                                    throw new FeatherException(String.format("Can't instantiate %s with provider", key.toString()), t);
                                }
                            }
                        }
                )
        );
    }

    @SuppressWarnings("unchecked")
    private PermissionedProvider<?> singletonProvider(final Key key, Singleton singleton, final PermissionedProvider<?> provider) {
        return singleton != null ? new PermissionedProvider<>() {
            @Override
            public Object get(MethodHandles.Lookup lookup) {
                if (!singletons.containsKey(key)) {
                    synchronized (singletons) {
                        if (!singletons.containsKey(key)) {
                            singletons.put(key, provider.get(lookup));
                        }
                    }
                }
                return singletons.get(key);
            }
        } : provider;
    }

    private PermissionedProvider<?>[] paramProviders(
            final Key key,
            Class<?>[] parameterClasses,
            Type[] parameterTypes,
            Annotation[][] annotations,
            final Set<Key> chain
    ) {
        PermissionedProvider<?>[] providers = new PermissionedProvider<?>[parameterTypes.length];
        for (int i = 0; i < parameterTypes.length; ++i) {
            Class<?> parameterClass = parameterClasses[i];
            Annotation qualifier = qualifier(annotations[i]);
            Class<?> providerType = Provider.class.equals(parameterClass) ?
                    (Class<?>) ((ParameterizedType) parameterTypes[i]).getActualTypeArguments()[0] :
                    null;
            if (providerType == null) {
                final Key newKey = Key.of(parameterClass, qualifier);
                final Set<Key> newChain = append(chain, key);
                if (newChain.contains(newKey)) {
                    throw new FeatherException(String.format("Circular dependency: %s", chain(newChain, newKey)));
                }
                providers[i] = new PermissionedProvider() {
                    @Override
                    public Object get(MethodHandles.Lookup lookup) {
                        return provider(newKey, newChain, lookup).get();
                    }
                };
            } else {
                final Key newKey = Key.of(providerType, qualifier);
                providers[i] = new PermissionedProvider() {
                    @Override
                    public Object get(MethodHandles.Lookup lookup) {
                        return provider(newKey, lookup);
                    }
                };
            }
        }
        return providers;
    }

    private static Object[] params(PermissionedProvider<?>[] paramProviders, MethodHandles.Lookup lookup) {
        Object[] params = new Object[paramProviders.length];
        for (int i = 0; i < paramProviders.length; ++i) {
            params[i] = paramProviders[i].get(lookup);
        }
        return params;
    }

    private static Set<Key> append(Set<Key> set, Key newKey) {
        if (set != null && !set.isEmpty()) {
            Set<Key> appended = new LinkedHashSet<>(set);
            appended.add(newKey);
            return appended;
        } else {
            return Collections.singleton(newKey);
        }
    }

    private static FieldInjector[] injectFields(Class<?> target) {
        Set<Field> fields = fields(target);
        FieldInjector[] fs = new FieldInjector[fields.size()];
        int i = 0;
        for (Field f : fields) {
            Class<?> providerType = f.getType().equals(Provider.class) ?
                    (Class<?>) ((ParameterizedType) f.getGenericType()).getActualTypeArguments()[0] :
                    null;
            fs[i++] = new FieldInjector(
                    f,
                    providerType != null,
                    Key.of(
                            (Class) (providerType != null ? providerType : f.getType()),
                            qualifier(f.getAnnotations())
                    )
            );
        }
        return fs;
    }

    private static Set<Field> fields(Class<?> type) {
        Class<?> current = type;
        Set<Field> fields = new HashSet<>();
        while (!current.equals(Object.class)) {
            for (Field field : current.getDeclaredFields()) {
                if (field.isAnnotationPresent(Inject.class)) {
                    fields.add(field);
                }
            }
            current = current.getSuperclass();
        }
        return fields;
    }

    private static String chain(Set<Key> chain, Key lastKey) {
        StringBuilder chainString = new StringBuilder();
        for (Key key : chain) {
            chainString.append(key.toString()).append(" -> ");
        }
        return chainString.append(lastKey.toString()).toString();
    }

    private static Constructor constructor(Key key) {
        Constructor inject = null;
        Constructor noarg = null;
        for (Constructor c : key.type.getDeclaredConstructors()) {
            if (c.isAnnotationPresent(Inject.class)) {
                if (inject == null) {
                    inject = c;
                } else {
                    throw new FeatherException(String.format("%s has multiple @Inject constructors", key.type));
                }
            } else if (c.getParameterTypes().length == 0) {
                noarg = c;
            }
        }
        Constructor constructor = inject != null ? inject : noarg;
        if (constructor != null) {
            return constructor;
        } else {
            throw new FeatherException(String.format("%s doesn't have an @Inject or no-arg constructor, or a module provider", key.type.getName()));
        }
    }

    private static Set<Method> providers(Class<?> type) {
        Class<?> current = type;
        Set<Method> providers = new HashSet<>();
        while (!current.equals(Object.class)) {
            for (Method method : current.getDeclaredMethods()) {
                if (method.isAnnotationPresent(Provides.class) && (type.equals(current) || !providerInSubClass(method, providers))) {
                    providers.add(method);
                }
            }
            current = current.getSuperclass();
        }
        return providers;
    }

    private static Annotation qualifier(Annotation[] annotations) {
        for (Annotation annotation : annotations) {
            if (annotation.annotationType().isAnnotationPresent(Qualifier.class)) {
                return annotation;
            }
        }
        return null;
    }

    private static boolean providerInSubClass(Method method, Set<Method> discoveredMethods) {
        for (Method discovered : discoveredMethods) {
            if (discovered.getName().equals(method.getName()) && Arrays.equals(method.getParameterTypes(), discovered.getParameterTypes())) {
                return true;
            }
        }
        return false;
    }
}
