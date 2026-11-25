package dev.freakyfriday.mind;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Comparator;
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
        default Object afterCopy(Object source,Object copy) { return copy; }
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
        Copier(Rebinding policy) { this.policy = policy; }

        private Object copy(Object source, int depth) throws Uncapturable {
            if (source == null) return null;
            Object mapped = policy.substitute(source);
            if (mapped != source) return mapped;

            Class<?> type = source.getClass();
            if (primitiveLike(type)) return source;
            if (policy.shared(source)) return source;
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
                List<Object> result = new ArrayList<>(list.size());
                copies.put(source, result);
                for (Object value : list) result.add(copy(value, depth + 1));
                return result;
            }

            if (source instanceof Set<?> set) {
                Set<Object> result = new LinkedHashSet<>();
                copies.put(source, result);
                for (Object value : set) result.add(copy(value, depth + 1));
                return result;
            }

            if (source instanceof Map<?, ?> map) {
                Map<Object, Object> result = new LinkedHashMap<>();
                copies.put(source, result);
                for (var e : map.entrySet())
                    result.put(copy(e.getKey(), depth + 1), copy(e.getValue(), depth + 1));
                return result;
            }

            // Captured lambdas are hidden JVM classes with final capture
            // fields. Recreate them with their synthetic constructor after
            // recursively rebinding every captured value.
            if (type.isHidden() || type.isSynthetic() && type.getName().contains("$Lambda"))
                return copyCapturedLambda(source,type,depth);

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
                        writeField(result, field, rebound);
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
            Object completed=policy.afterCopy(source,result);
            if(completed==null)
                throw new Uncapturable("AI copy post-processor returned null: "+type.getName());
            copies.put(source,completed);
            return completed;
        }

        private Object copyCapturedLambda(Object source,Class<?> type,int depth)
                throws Uncapturable {
            if(copies.containsKey(source)) return copies.get(source);
            List<Field> captures=Arrays.stream(type.getDeclaredFields())
                .filter(field->!Modifier.isStatic(field.getModifiers()))
                .sorted(Comparator.comparingInt(DynamicObjectGraph::captureOrdinal))
                .toList();
            if(captures.isEmpty()) {
                Object shared=policy.afterCopy(source,source);
                copies.put(source,shared);
                return shared;
            }
            if(!policy.cloneable(type))
                throw new Uncapturable("Opaque captured lambda cannot be rebound: "+type.getName());
            Object[] values=new Object[captures.size()];
            Class<?>[] captureTypes=new Class<?>[captures.size()];
            try {
                for(int i=0;i<captures.size();i++) {
                    Field field=captures.get(i);
                    if(!field.trySetAccessible())
                        throw new Uncapturable("Cannot inspect captured lambda field: "+field);
                    Object original=field.get(source);
                    values[i]=copy(original,depth+1);
                    captureTypes[i]=field.getType();
                    if(values[i]!=null&&!boxed(captureTypes[i]).isInstance(values[i]))
                        throw new Uncapturable("Captured lambda field incompatible after rebind: "+field);
                }
                Constructor<?> constructor=null;
                for(Constructor<?> candidate:type.getDeclaredConstructors()) {
                    Class<?>[] parameters=candidate.getParameterTypes();
                    if(parameters.length!=captureTypes.length) continue;
                    boolean exact=true;
                    for(int i=0;i<parameters.length;i++) {
                        if(parameters[i]!=captureTypes[i]) { exact=false;break; }
                    }
                    if(exact) { constructor=candidate;break; }
                }
                if(constructor==null)
                    throw new Uncapturable("No constructor matches the complete captured lambda state: "+type.getName());
                if(!constructor.trySetAccessible())
                    throw new Uncapturable("Captured lambda constructor is inaccessible: "+type.getName());
                Object result=constructor.newInstance(values);
                Object completed=policy.afterCopy(source,result);
                if(completed==null)
                    throw new Uncapturable("AI lambda post-processor returned null: "+type.getName());
                copies.put(source,completed);
                return completed;
            } catch(Uncapturable failure) {
                throw failure;
            } catch(ReflectiveOperationException|RuntimeException failure) {
                throw new Uncapturable("Cannot reconstruct complete captured lambda: "
                    +type.getName()+": "+failure.getClass().getSimpleName(),failure);
            }
        }

        private static int captureOrdinal(Field field) {
            String name=field.getName();
            int marker=name.lastIndexOf('(Object array, int index, Object item, Class<?> component)
                throws Uncapturable {
            if (item != null && !boxed(component).isInstance(item))
                throw new Uncapturable("AI array element cannot be rebound to "
                    + component.getName() + ": " + item.getClass().getName());
            try { Array.set(array, index, item); }
            catch (IllegalArgumentException err) { throw new Uncapturable("Invalid AI array", err); }
        }
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

    private static Object unsafe() throws ReflectiveOperationException {
        Class<?> unsafeType=Class.forName("sun.misc.Unsafe");
        Field singleton=unsafeType.getDeclaredField("theUnsafe");
        if(!singleton.trySetAccessible())
            throw new IllegalAccessException("Unsafe singleton is inaccessible");
        return singleton.get(null);
    }

    /**
     * Reflection cannot write captured lambda final fields on modern JDKs.
     * The same bounded graph copier already requires Unsafe allocation, so use
     * its typed field-offset writes for final instance state as well.
     */
    private static void writeField(Object target,Field field,Object value)
            throws ReflectiveOperationException {
        if(!Modifier.isFinal(field.getModifiers())) {
            field.set(target,value);
            return;
        }
        Class<?> unsafeType=Class.forName("sun.misc.Unsafe");
        Object unsafe=unsafe();
        long offset=(Long)unsafeType.getMethod("objectFieldOffset",Field.class)
            .invoke(unsafe,field);
        Class<?> fieldType=field.getType();
        String operation;
        Class<?> argumentType;
        if(!fieldType.isPrimitive()) {
            operation="putObject";
            argumentType=Object.class;
        } else if(fieldType==boolean.class) {
            operation="putBoolean"; argumentType=boolean.class;
        } else if(fieldType==byte.class) {
            operation="putByte"; argumentType=byte.class;
        } else if(fieldType==short.class) {
            operation="putShort"; argumentType=short.class;
        } else if(fieldType==char.class) {
            operation="putChar"; argumentType=char.class;
        } else if(fieldType==int.class) {
            operation="putInt"; argumentType=int.class;
        } else if(fieldType==long.class) {
            operation="putLong"; argumentType=long.class;
        } else if(fieldType==float.class) {
            operation="putFloat"; argumentType=float.class;
        } else if(fieldType==double.class) {
            operation="putDouble"; argumentType=double.class;
        } else {
            throw new IllegalArgumentException("Unsupported final AI field type: "+fieldType);
        }
        unsafeType.getMethod(operation,Object.class,long.class,argumentType)
            .invoke(unsafe,target,offset,value);
    }

    private static Object allocate(Class<?> type) throws Uncapturable {
        try {
            Class<?> unsafeType = Class.forName("sun.misc.Unsafe");
            Object unsafe = unsafe();
            Method method = unsafeType.getMethod("allocateInstance", Class.class);
            return method.invoke(unsafe, type);
        } catch (ReflectiveOperationException | RuntimeException failure) {
            throw new Uncapturable("Cannot allocate whole AI instance of " + type.getName(), failure);
        }
    }}
);
            if(marker>=0&&marker+1<name.length()) {
                try { return Integer.parseInt(name.substring(marker+1)); }
                catch(NumberFormatException ignored) {}
            }
            return Integer.MAX_VALUE;
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

    private static Object unsafe() throws ReflectiveOperationException {
        Class<?> unsafeType=Class.forName("sun.misc.Unsafe");
        Field singleton=unsafeType.getDeclaredField("theUnsafe");
        if(!singleton.trySetAccessible())
            throw new IllegalAccessException("Unsafe singleton is inaccessible");
        return singleton.get(null);
    }

    /**
     * Reflection cannot write captured lambda final fields on modern JDKs.
     * The same bounded graph copier already requires Unsafe allocation, so use
     * its typed field-offset writes for final instance state as well.
     */
    private static void writeField(Object target,Field field,Object value)
            throws ReflectiveOperationException {
        if(!Modifier.isFinal(field.getModifiers())) {
            field.set(target,value);
            return;
        }
        Class<?> unsafeType=Class.forName("sun.misc.Unsafe");
        Object unsafe=unsafe();
        long offset=(Long)unsafeType.getMethod("objectFieldOffset",Field.class)
            .invoke(unsafe,field);
        Class<?> fieldType=field.getType();
        String operation;
        Class<?> argumentType;
        if(!fieldType.isPrimitive()) {
            operation="putObject";
            argumentType=Object.class;
        } else if(fieldType==boolean.class) {
            operation="putBoolean"; argumentType=boolean.class;
        } else if(fieldType==byte.class) {
            operation="putByte"; argumentType=byte.class;
        } else if(fieldType==short.class) {
            operation="putShort"; argumentType=short.class;
        } else if(fieldType==char.class) {
            operation="putChar"; argumentType=char.class;
        } else if(fieldType==int.class) {
            operation="putInt"; argumentType=int.class;
        } else if(fieldType==long.class) {
            operation="putLong"; argumentType=long.class;
        } else if(fieldType==float.class) {
            operation="putFloat"; argumentType=float.class;
        } else if(fieldType==double.class) {
            operation="putDouble"; argumentType=double.class;
        } else {
            throw new IllegalArgumentException("Unsupported final AI field type: "+fieldType);
        }
        unsafeType.getMethod(operation,Object.class,long.class,argumentType)
            .invoke(unsafe,target,offset,value);
    }

    private static Object allocate(Class<?> type) throws Uncapturable {
        try {
            Class<?> unsafeType = Class.forName("sun.misc.Unsafe");
            Object unsafe = unsafe();
            Method method = unsafeType.getMethod("allocateInstance", Class.class);
            return method.invoke(unsafe, type);
        } catch (ReflectiveOperationException | RuntimeException failure) {
            throw new Uncapturable("Cannot allocate whole AI instance of " + type.getName(), failure);
        }
    }}
