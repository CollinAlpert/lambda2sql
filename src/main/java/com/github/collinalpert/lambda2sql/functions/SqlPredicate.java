package com.github.collinalpert.lambda2sql.functions;

import java.util.Objects;
import java.util.function.Predicate;

/**
 * A serialized {@link Predicate}
 *
 * @author Collin Alpert
 * @see Predicate
 * @see SerializedFunctionalInterface
 */
@FunctionalInterface
public interface SqlPredicate<T> extends Predicate<T>, SerializedFunctionalInterface {

	default SqlPredicate<T> and(SqlPredicate<? super T> other) {
		Objects.requireNonNull(other);
		return t -> test(t) && other.test(t);
	}
}
