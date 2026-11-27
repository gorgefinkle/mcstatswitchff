package dev.freakyfriday.mind;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Captures an actual runtime object graph and reconstructs it with substituted
 * owner-bound references. No species names, known goal classes or hand-written
 * recipes. Fail closed if the full graph cannot be rebound.
 *
 * This is deliberately stricter than a shallow copy: a donor mob, navigation
 * or control reference must never survive on a reconstructed recipient goal.
 * The caller must whitelist only the appropriate GOAL OBJECTS as cloneable and
 * explicitly bind any owner-derived services. Unknown mutable objects fail.
 *
 * Goal instances are constructed without calling their original constructors,
 * because those constructors accept species-dependent bodies and run behavior
 * registration side effects. All instance fields (including inherited ones)
 * are then copied and checked. This depends on jdk.unsupported's Unsafe; the
 * capability is checked at runtime, and inability to clone rejects BEFORE the
 * original AI is modified. It is an experimental engine, NOT proof of arbitrary
 * modded-goal portability.
 */
public final class DynamicObjectGraph {
    private DynamicObjectGraph() {}

    public interface Rebinding {
        /** Return a destination object, or the same reference if not bound. */
        Object substitute(Object source);
        /** True only for provably independent, safe-to-share dependencies. */
        boolean shared(Object source);
        /** True only for types whose complete instance state we can capture. */
        boolean cloneable(Class<?> type);
    }

    public static final class Uncapturable extends Exception {
        public Uncapturable(String reason) { super(reason); }
        public Uncapturable(String reason, Throwable cause) { super(reason, cause); }
    }

    @SuppressWarnings("unchecked")
    public static <T> T capture(T source, Class<T> expected, Rebinding rebinding) throws Uncapturable {
        Objects.requireNonNull(expected, "expected type");
        Objects.requireNonNull(rebinding, "rebinding");
        if (source == null || !expected.isInstance(source))
            throw new Uncapturable("Missing or wrong-type AI root");
        final Object result;
        try {
            result = new Copier(rebinding).copy(source, 0);
        } catch (Uncapturable unsupported) {
            throw unsupported;
        } catch (RuntimeException problem) {
            // E.g. InaccessibleObjectException on a JDK-owned helper class.
            // Never let an opaque field silently crash the game thread.
            throw new Uncapturable("Cannot safely inspect and rebind the complete AI graph: "
                + problem.getClass().getSimpleName(), problem);
        }
        if (!expected.isInstance(result) || result == source)
            throw new Uncapturable("AI root was shared instead of captured");
        return (T) result;
    }

    private static final class Copier {
        private static final int MAX_OBJECTS = 4096;
        private static final int MAX_DEPTH = 40;
        private final Rebinding policy;
        private final IdentityHashMap<Object, Object> copies = new IdentityHashMap<>();
        private final IdentityHashMap<Object, Boolean> inProgressImmutable = new IdentityHashMap<>();
        Copier(Rebinding policy) { this.policy = policy; }

        private Object copy(Object source, int depth) throws Uncapturable {
            if (source == null) return null;
            Object mapped = policy.substitute(source);
            if (mapped != source) return mapped;

            Class<?> type = source.getClass();
            if (primitiveLike(type)) return source;
            if (policy.shared(source)) return source;
            if (inProgressImmutable.containsKey(source))
                throw new Uncapturable("Cyclic immutable collection cannot be safely rebound: " + type.getName());
            if (copies.containsKey(source)) return copies.get(source);
            if (depth > MAX_DEPTH || copies.size() >= MAX_OBJECTS)
                throw new Uncapturable("AI capture object graph is too large or too deep: " + type.getName());

            if (type.isArray()) {
                int length = Array.getLength(source);
                Object result = Array.newInstance(type.getComponentType(), length);
                copies.put(source, result);
                for (int i = 0; i < length; i++) {
                    Object item = copy(Array.get(source, i), depth + 1);
                    storeArray(result, i, item, type.getComponentType());
                }
                return result;
            }

            if (source instanceof EnumSet<?> enumSet) {
                Object result = enumSet.clone();
                copies.put(source, result);
                return result;
            }

            if (source instanceof Optional<?> optional) {
                if (optional.isEmpty()) return source;
                Object content = copy(optional.orElseThrow(), depth + 1);
                Object result = Optional.ofNullable(content);
                copies.put(source, result);
                return result;
            }

            if (source instanceof List<?> list) {
                String name = type.getName();
                boolean immutable = name.startsWith("java.util.ImmutableCollections$List");
                boolean unmodifiable = name.startsWith("java.util.Collections$Unmodifiable");
                List<Object> backing;
                if (type == ArrayList.class) backing = new ArrayList<>(list.size());
                else if (type == LinkedList.class) backing = new LinkedList<>();
                else if (immutable) backing = new ArrayList<>(list.size());
                else if (unmodifiable && name.endsWith("UnmodifiableRandomAccessList"))
                    backing = new ArrayList<>(list.size());
                else if (unmodifiable && name.endsWith("UnmodifiableList"))
                    backing = new LinkedList<>();
                else throw new Uncapturable("Unsupported List implementation; refusing to change collection semantics: " + name);
                Object result = unmodifiable ? Collections.unmodifiableList(backing) : backing;
                copies.put(source, result);
                if (immutable) inProgressImmutable.put(source, Boolean.TRUE);
                for (Object value : list) backing.add(copy(value, depth + 1));
                if (immutable) {
                    result = List.copyOf(backing);
                    copies.put(source, result);
                    inProgressImmutable.remove(source);
                }
                return result;
            }

            if (source instanceof Set<?> set) {
                String name = type.getName();
                boolean immutable = name.startsWith("java.util.ImmutableCollections$Set");
                boolean unmodifiable = name.startsWith("java.util.Collections$Unmodifiable");
                if (unmodifiable)
                    throw new Uncapturable("Unmodifiable Set wrapper hides its equality/order semantics: " + name);
                Set<Object> backing;
                if (type == HashSet.class) backing = new HashSet<>();
                else if (type == LinkedHashSet.class) backing = new LinkedHashSet<>();
                else if (immutable) backing = new LinkedHashSet<>();
                else throw new Uncapturable("Unsupported Set implementation; refusing to change collection semantics: " + name);
                Object result = unmodifiable ? Collections.unmodifiableSet(backing) : backing;
                copies.put(source, result);
                if (immutable) inProgressImmutable.put(source, Boolean.TRUE);
                for (Object value : set) backing.add(copy(value, depth + 1));
                if (backing.size() != set.size())
                    throw new Uncapturable("Rebinding collapsed distinct Set entries: " + name);
                if (immutable) {
                    result = Set.copyOf(backing);
                    copies.put(source, result);
                    inProgressImmutable.remove(source);
                }
                return result;
            }

            if (source instanceof Map<?, ?> map) {
                String name = type.getName();
                boolean immutable = name.startsWith("java.util.ImmutableCollections$Map");
                boolean unmodifiable = name.startsWith("java.util.Collections$Unmodifiable");
                if (unmodifiable)
                    throw new Uncapturable("Unmodifiable Map wrapper hides its key semantics: " + name);
                Map<Object, Object> backing;
                if (type == HashMap.class) backing = new HashMap<>();
                else if (type == LinkedHashMap.class) backing = new LinkedHashMap<>();
                else if (type == IdentityHashMap.class) backing = new IdentityHashMap<>();
                else if (immutable) backing = new LinkedHashMap<>();
                else throw new Uncapturable("Unsupported Map implementation; refusing to change collection semantics: " + name);
                Object result = unmodifiable ? Collections.unmodifiableMap(backing) : backing;
                copies.put(source, result);
                if (immutable) inProgressImmutable.put(source, Boolean.TRUE);
                for (var e : map.entrySet())
                    backing.put(copy(e.getKey(), depth + 1), copy(e.getValue(), depth + 1));
                if (backing.size() != map.size())
                    throw new Uncapturable("Rebinding collapsed distinct Map keys: " + name);
                if (immutable) {
                    result = Map.copyOf(backing);
                    copies.put(source, result);
                    inProgressImmutable.remove(source);
                }
                return result;
            }

            // A lambda may capture a shared vanilla predicate or method
            // reference. Reuse it only when each captured dependency is
            // immutable, another recursively safe lambda, or an object with
            // no mutable instance state. A static-final reference alone is
            // not enough: its referent could still be mutable.
            if (isLambdaType(type)) {
                String unsafeCapture = unsafeLambdaCapture(source, 0, new IdentityHashMap<>());
                if (unsafeCapture != null)
                    throw new Uncapturable("Captured lambda depends on non-shareable state at "
                        + unsafeCapture + ": " + type.getName());
                return source;
            }

            if (!policy.cloneable(type))
                throw new Uncapturable("Unknown AI object is mutable or owner-bound: " + type.getName());
            if (type.isRecord())
                throw new Uncapturable("Cannot safely replace a record's final fields: " + type.getName());

            Object result = allocate(type);
            copies.put(source, result);
            for (Class<?> declaring = type; declaring != null && declaring != Object.class;
                 declaring = declaring.getSuperclass()) {
                for (Field field : declaring.getDeclaredFields()) {
                    if (Modifier.isStatic(field.getModifiers())) continue;
                    if (!field.trySetAccessible())
                        throw new Uncapturable("Cannot inspect complete AI field: "
                            + declaring.getName() + "." + field.getName());
                    try {
                        Object before = field.get(source);
                        Object rebound = copy(before, depth + 1);
                        if (rebound != null && !boxed(field.getType()).isInstance(rebound))
                            throw new Uncapturable("AI field incompatible with recipient: "
                                + declaring.getName() + "." + field.getName()
                                + " requires " + field.getType().getName()
                                + " but got " + rebound.getClass().getName());
                        field.set(result, rebound);
                        Object readBack = field.get(result);
                        if (field.getType().isPrimitive()) {
                            if (!Objects.equals(readBack, rebound))
                                throw new Uncapturable("Primitive AI field changed during capture: " + field);
                        } else if (readBack != rebound) {
                            throw new Uncapturable("AI field was not rebound exactly: " + field);
                        }
                    } catch (IllegalAccessException | IllegalArgumentException err) {
                        throw new Uncapturable("Could not copy complete AI field: " + field, err);
                    }
                }
            }
            return result;
        }

        private static void storeArray(Object array, int index, Object item, Class<?> component)
                throws Uncapturable {
            if (item != null && !boxed(component).isInstance(item))
                throw new Uncapturable("AI array element cannot be rebound to "
                    + component.getName() + ": " + item.getClass().getName());
            try { Array.set(array, index, item); }
            catch (IllegalArgumentException err) { throw new Uncapturable("Invalid AI array", err); }
        }
    }

    /**
     * Validate a callback/dependency before a semantic adapter deliberately
     * reuses it in a freshly constructed recipient-bound goal.
     */
    public static void requireShareableDependency(Object dependency, String role)
            throws Uncapturable {
        if (dependency == null || primitiveLike(dependency.getClass())
                || isKnownImmutableCapture(dependency.getClass())) return;
        Class<?> type = dependency.getClass();
        if (isLambdaType(type)) {
            String unsafe = unsafeLambdaCapture(dependency, 0, new IdentityHashMap<>());
            if (unsafe != null)
                throw new Uncapturable("Cannot port " + role + " because its captured state is body-bound: " + unsafe);
            return;
        }
        if (isStatelessObject(dependency)) return;
        throw new Uncapturable("Cannot port " + role + " because dependency has mutable instance state: "
            + type.getName());
    }

    private static boolean isLambdaType(Class<?> type) {
        return type.isHidden() || type.isSynthetic() && type.getName().contains("$Lambda");
    }

    /**
     * Returns the first unsafe capture path, or null when sharing the lambda
     * instance cannot retain a donor-body or mutable owner reference.
     */
    private static String unsafeLambdaCapture(
            Object lambda, int depth, IdentityHashMap<Object, Boolean> visited) {
        if (lambda == null) return null;
        if (depth > 12) return "capture graph exceeds safety depth";
        // Shared lambda dependencies can form a DAG or a cycle. A revisit is
        // not itself unsafe; every distinct captured field is still inspected
        // on its first traversal, and any owner-bound reference rejects.
        if (visited.put(lambda, Boolean.TRUE) != null) return null;
        Class<?> type = lambda.getClass();
        if (!isLambdaType(type)) return null;

        for (Field field : type.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) continue;
            if (!field.trySetAccessible()) return type.getName() + "." + field.getName()
                + " (inaccessible capture)";
            try {
                Object value = field.get(lambda);
                if (value == null || primitiveLike(value.getClass())) continue;

                Class<?> capturedType = value.getClass();
                if (isLambdaType(capturedType)) {
                    String nested = unsafeLambdaCapture(value, depth + 1, visited);
                    if (nested != null) return type.getName() + "." + field.getName() + " -> " + nested;
                    continue;
                }

                if (!isKnownImmutableCapture(capturedType) && !isStatelessObject(value))
                    return type.getName() + "." + field.getName() + " (" + capturedType.getName() + ")";
            } catch (IllegalAccessException failure) {
                return type.getName() + "." + field.getName() + " (capture read failed)";
            }
        }
        return null;
    }

    private static boolean isKnownImmutableCapture(Class<?> type) {
        String name = type.getName();
        return type.isEnum()
            || name.equals("net.minecraft.resources.Identifier")
            || name.equals("net.minecraft.tags.TagKey")
            || name.equals("net.minecraft.world.item.Item");
    }

    /**
     * A captured non-lambda service can be shared only when its complete class
     * hierarchy has no mutable instance fields. Static singleton identity does
     * not make the object's instance state safe to share.
     */
    private static boolean isStatelessObject(Object value) {
        for (Class<?> declaring = value.getClass();
             declaring != null && declaring != Object.class;
             declaring = declaring.getSuperclass()) {
            for (Field field : declaring.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers())) return false;
            }
        }
        return true;
    }

    private static boolean primitiveLike(Class<?> type) {
        return type.isPrimitive() || type.isEnum() || type == String.class || type == Class.class
            || type == Boolean.class || type == Byte.class || type == Short.class
            || type == Integer.class || type == Long.class || type == Float.class
            || type == Double.class || type == Character.class || type == java.util.UUID.class
            || type == java.math.BigInteger.class || type == java.math.BigDecimal.class;
    }

    private static Class<?> boxed(Class<?> type) {
        if (!type.isPrimitive()) return type;
        if (type == int.class) return Integer.class;
        if (type == long.class) return Long.class;
        if (type == boolean.class) return Boolean.class;
        if (type == float.class) return Float.class;
        if (type == double.class) return Double.class;
        if (type == byte.class) return Byte.class;
        if (type == short.class) return Short.class;
        if (type == char.class) return Character.class;
        return type;
    }

    private static Object allocate(Class<?> type) throws Uncapturable {
        try {
            Class<?> unsafeType = Class.forName("sun.misc.Unsafe");
            Field singleton = unsafeType.getDeclaredField("theUnsafe");
            if (!singleton.trySetAccessible())
                throw new Uncapturable("Unsafe allocation is inaccessible on this runtime");
            Object unsafe = singleton.get(null);
            Method method = unsafeType.getMethod("allocateInstance", Class.class);
            return method.invoke(unsafe, type);
        } catch (ReflectiveOperationException | RuntimeException failure) {
            throw new Uncapturable("Cannot allocate whole AI instance of " + type.getName(), failure);
        }
    }
}
