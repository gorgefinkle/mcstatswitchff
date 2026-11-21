package dev.freakyfriday.core;

import java.util.Objects;

/** Immutable transferable PERMANENT attribute modifier; transient effects stay with their owners. */
public record PermanentModifier(String id, double amount, String operation) {
    public PermanentModifier {
        Objects.requireNonNull(id, "modifier id");
        Objects.requireNonNull(operation, "modifier operation");
        if (id.isBlank() || operation.isBlank() || !Double.isFinite(amount))
            throw new IllegalArgumentException("Invalid permanent attribute modifier");
    }
}
