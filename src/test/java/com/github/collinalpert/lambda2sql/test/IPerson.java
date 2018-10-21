package com.github.collinalpert.lambda2sql.test;

public interface IPerson {
	long getId();

	String getName();

	int getAge();

	int getHeight();

	boolean isActive();

	ICar getCar();

	default boolean isAdult() {
		return getAge() >= 18;
	}
}
