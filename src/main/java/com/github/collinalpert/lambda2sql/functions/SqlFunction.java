package com.github.collinalpert.lambda2sql.functions;

import java.util.Objects;
import java.util.function.Function;

/**
 * A serialized {@link Function}. Converting a function to be used in SQL is really only useful when working with ORDER BY, or similar DQL options.
 * For more on this, check out my ORM library, <a href="https://github.com/CollinAlpert/Java2DB">Java2DB</a>, which uses this feature.
 *
 * @author Collin Alpert
 * @see Function
 * @see SerializedFunctionalInterface
 */
@FunctionalInterface
public interface SqlFunction<T, R> extends Function<T, R>, SerializedFunctionalInterface {

	static <T> SqlFunction<T, T> identity() {
		return t -> t;
	}

	default <V> SqlFunction<V, R> compose(SqlFunction<? super V, ? extends T> before) {
		Objects.requireNonNull(before);
		return v -> apply(before.apply(v));
	}

	default <V> SqlFunction<T, V> andThen(SqlFunction<? super R, ? extends V> after) {
		Objects.requireNonNull(after);
		return t -> after.apply(apply(t));
	}
}
