package com.github.collinalpert.lambda2sql.functions;

/**
 * @author Collin Alpert
 */
@FunctionalInterface
public interface TriFunction<A, B, C, R> {
	R apply(A a, B b, C c);
}
