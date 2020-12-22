package com.github.collinalpert.lambda2sql.test;

import java.time.*;

public interface IPerson {
	long getId();

	String getName();

	String getLastName();

	int getAge();

	int getHeight();

	boolean isActive();

	ICar getCar();

	LocalDate getDate();

	LocalTime getTime();

	LocalDateTime getDateTime();

	default boolean isAdult() {
		return getAge() >= 18;
	}

	default Boolean isNullable() {
		return null;
	}
}
