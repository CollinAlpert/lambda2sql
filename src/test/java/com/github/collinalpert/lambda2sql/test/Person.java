package com.github.collinalpert.lambda2sql.test;

public interface Person {
	long getId();

	String getName();

	int getAge();

	int getHeight();

	boolean isActive();

	default boolean isAdult() {
		return getAge() >= 18;
	}
}
