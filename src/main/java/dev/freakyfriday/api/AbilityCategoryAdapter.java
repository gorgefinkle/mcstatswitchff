package dev.freakyfriday.api;

import dev.freakyfriday.core.CategoryBundle;
import dev.freakyfriday.core.CategoryExchange;

/**
 * Future modded/vanilla ability integration API. ONE adapter represents a WHOLE
 * category, including ALL values, triggers and behavior state; no per-mob pair
 * handlers and no individual-ability book selection.
 *
 * Implementations must remove the original owner's behavior on replacement,
 * provide it to the recipient, validate before writes, and restore on failure.
 * An adapter is not reported as supported until actual functional transfer works.
 * No automatic reflection into arbitrary modded AI is promised.
 */
public interface AbilityCategoryAdapter<E> extends CategoryExchange.Port<E, CategoryBundle> {
    String categoryId();
}
