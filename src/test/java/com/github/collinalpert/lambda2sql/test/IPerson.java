package com.github.collinalpert.lambda2sql.test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public interface IPerson {
	long getId();

	String getName();

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
}
