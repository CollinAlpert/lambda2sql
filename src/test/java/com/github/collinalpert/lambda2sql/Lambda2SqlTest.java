package com.github.collinalpert.lambda2sql;

import com.github.collinalpert.lambda2sql.functions.SqlFunction;
import com.github.collinalpert.lambda2sql.functions.SqlPredicate;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class Lambda2SqlTest {

	@Test
	void testComparisons() {
		assertPredicateEqual("age = 1", e -> e.getAge() == 1);
		assertPredicateEqual("age > 1", e -> e.getAge() > 1);
		assertPredicateEqual("age < 1", e -> e.getAge() < 1);
		assertPredicateEqual("age >= 1", e -> e.getAge() >= 1);
		assertPredicateEqual("age <= 1", e -> e.getAge() <= 1);
		assertPredicateEqual("age != 1", e -> e.getAge() != 1);
	}

	@Test
	void testLogicalOps() {
		assertPredicateEqual("!isActive", e -> !e.isActive());
		assertPredicateEqual("age < 100 AND height > 200", e -> e.getAge() < 100 && e.getHeight() > 200);
		assertPredicateEqual("age < 100 OR height > 200", e -> e.getAge() < 100 || e.getHeight() > 200);
	}

	@Test
	void testMultipleLogicalOps() {
		assertPredicateEqual("isActive AND (age < 100 OR height > 200)", e -> e.isActive() && (e.getAge() < 100 || e.getHeight() > 200));
		assertPredicateEqual("(age < 100 OR height > 200) AND isActive", e -> (e.getAge() < 100 || e.getHeight() > 200) && e.isActive());
	}

	@Test
	void testWithVariables() {
		var name = "Donald";
		var age = 80;
		assertPredicateEqual("name = 'Donald' AND age > 80", person -> person.getName() == name && person.getAge() > age);
	}

	@Test
	void testFunction() {
		assertFunctionEqual("name", Person::getName);
		assertFunctionEqual("age", person -> person.getAge());
	}

	private void assertPredicateEqual(String expectedSql, SqlPredicate<Person> p) {
		var sql = Lambda2Sql.toSql(p);
		Assertions.assertEquals(expectedSql, sql);
	}

	private void assertFunctionEqual(String expectedSql, SqlFunction<Person, ?> function) {
		var sql = Lambda2Sql.toSql(function);
		Assertions.assertEquals(expectedSql, sql);
	}
}
