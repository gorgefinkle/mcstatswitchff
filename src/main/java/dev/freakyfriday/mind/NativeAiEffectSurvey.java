package dev.freakyfriday.mind;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Conservative read-only survey of Java classfile method references.
 *
 * Works without linking against Minecraft, so modded Goal classes can be
 * surveyed as loaded resources. It does NOT execute native AI.
 *
 * Constant-pool references are a SUPerset of effects reachable in a given
 * frame, and classes can be transformed after their resource was loaded.
 * A clean report NEVER certifies that code can be sandboxed; dynamically
 * dispatched methods and unknown Java side effects still require trapping.
 */
public final class NativeAiEffectSurvey {
    private NativeAiEffectSurvey() {}

    public record Finding(String className, String operation, String concern) {}
    public record Report(String implementation, boolean bytesAvailable,
                         List<Finding> candidates, String limitation) {
        public Report {
            candidates = List.copyOf(candidates);
        }
    }

    private static final String[] MUTATING_PREFIXES = {
        "set", "add", "remove", "spawn", "destroy", "place", "explode",
        "hurt", "attack", "kill", "die", "ignite", "discard", "teleport",
        "move", "push", "knockback", "give", "award", "drop", "interact",
        "broadcast", "playSound", "schedule", "write", "save"
    };

    /**
     * Class-file constant pool only: no method is called on the actual mob.
     * Reports risky references, opaque dynamic invocation and missing bytes.
     */
    public static Report inspect(Class<?> implementation) {
        String qualified = implementation.getName();
        String resource = "/" + qualified.replace('.', '/') + ".class";
        try (InputStream stream = implementation.getResourceAsStream(resource)) {
            if (stream == null) return new Report(qualified, false, List.of(),
                "Class bytes unavailable; cannot audit native implementation");
            byte[] bytes = stream.readNBytes(8 * 1024 * 1024 + 1);
            if (bytes.length > 8 * 1024 * 1024)
                throw new IOException("Class resource larger than 8 MiB");
            return new Report(qualified, true, constantPool(bytes, qualified),
                "References are candidates, not proven executed effects. Runtime "
                    + "class transformation, dynamic dispatch and caller chains are NOT resolved.");
        } catch (IOException | RuntimeException failure) {
            return new Report(qualified, false, List.of(),
                "Could not inspect class bytes: " + failure.getClass().getSimpleName()
                    + ": " + failure.getMessage());
        }
    }

    private static List<Finding> constantPool(byte[] classBytes, String source)
            throws IOException {
        try (DataInputStream input =
                new DataInputStream(new ByteArrayInputStream(classBytes))) {
            if (input.readInt() != 0xCAFEBABE)
                throw new IOException("Not a JVM classfile");
            input.readUnsignedShort(); // minor
            input.readUnsignedShort(); // major
            int count = input.readUnsignedShort();
            if (count <= 1) throw new IOException("Empty constant pool");
            String[] utf8 = new String[count];
            int[] tags = new int[count];
            int[] first = new int[count], second = new int[count];
            for (int i = 1; i < count; i++) {
                int tag = input.readUnsignedByte();
                tags[i] = tag;
                switch (tag) {
                    case 1 -> utf8[i] = input.readUTF();
                    case 3, 4 -> input.readInt();
                    case 5, 6 -> { input.readLong(); i++; }
                    case 7, 8, 16, 19, 20 -> first[i] = input.readUnsignedShort();
                    case 9, 10, 11, 12, 17, 18 ->
                    {
                        first[i] = input.readUnsignedShort();
                        second[i] = input.readUnsignedShort();
                    }
                    case 15 -> {
                        first[i] = input.readUnsignedByte();
                        second[i] = input.readUnsignedShort();
                    }
                    default -> throw new IOException("Unexpected constant pool tag " + tag);
                }
            }

            Set<Finding> findings = new LinkedHashSet<>();
            for (int i = 1; i < count; i++) {
                if (tags[i] == 18 || tags[i] == 17) {
                    findings.add(new Finding(source, "invokedynamic/constantdynamic",
                        "Opaque dynamic bootstrap; not isolated"));
                    continue;
                }
                if (tags[i] != 9 && tags[i] != 10 && tags[i] != 11) continue;
                int classRef = first[i], nameRef = second[i];
                if (classRef <= 0 || classRef >= count || nameRef <= 0
                        || nameRef >= count || tags[classRef] != 7
                        || tags[nameRef] != 12) continue;
                int ownerUtf8 = first[classRef], nameUtf8 = first[nameRef];
                if (ownerUtf8 <= 0 || ownerUtf8 >= count || nameUtf8 <= 0
                        || nameUtf8 >= count) continue;
                String owner = utf8[ownerUtf8], operation = utf8[nameUtf8];
                if (owner == null || operation == null) continue;

                if (owner.startsWith("java/lang/reflect/")
                    || owner.startsWith("java/lang/invoke/MethodHandle")) {
                    findings.add(new Finding(owner, operation,
                        "Reflective/dynamic invocation can escape standard hooks"));
                    continue;
                }
                if (tags[i] == 9) {
                    if (owner.startsWith("net/minecraft/") &&
                        (operation.startsWith("level") || operation.startsWith("target"))) {
                        findings.add(new Finding(owner, operation,
                            "Field access may retain donor/real-world identity"));
                    }
                    continue;
                }

                boolean mutator = false;
                for (String prefix : MUTATING_PREFIXES) {
                    if (operation.startsWith(prefix)) { mutator = true; break; }
                }
                if (mutator) {
                    findings.add(new Finding(owner, operation,
                        "Possible state/world mutation; requires interception or prohibition"));
                }
            }
            return new ArrayList<>(findings);
        }
    }
}
