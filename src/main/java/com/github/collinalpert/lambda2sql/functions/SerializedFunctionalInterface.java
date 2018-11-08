package com.github.collinalpert.lambda2sql.functions;

import java.io.Serializable;

/**
 * An interface functional interfaces can extend to become serialized.
 * Functional interfaces which extend this interface can be converted to SQL statements.
 *
 * @author Collin Alpert
 * @see Serializable
 */
public interface SerializedFunctionalInterface extends Serializable {
}
