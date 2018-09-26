package com.github.collinalpert.lambda2sql;

@TableName("person")
public interface Person {
	String getName();

	int getAge();

	int getHeight();

	boolean isActive();
}
