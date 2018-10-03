package com.github.collinalpert.lambda2sql;

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
