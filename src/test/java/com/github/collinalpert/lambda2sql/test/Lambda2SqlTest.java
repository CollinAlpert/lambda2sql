package com.github.collinalpert.lambda2sql.test;

import com.github.collinalpert.lambda2sql.Lambda2Sql;
import com.github.collinalpert.lambda2sql.functions.SqlFunction;
import com.github.collinalpert.lambda2sql.functions.SqlPredicate;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class Lambda2SqlTest {

	@Test
	void testComparisons() {
		assertPredicateEqual("person.age = 1", e -> e.getAge() == 1);
		assertPredicateEqual("person.age > 1", e -> e.getAge() > 1);
		assertPredicateEqual("person.age < 1", e -> e.getAge() < 1);
		assertPredicateEqual("person.age >= 1", e -> e.getAge() >= 1);
		assertPredicateEqual("person.age <= 1", e -> e.getAge() <= 1);
		assertPredicateEqual("person.age != 1", e -> e.getAge() != 1);
	}

	@Test
	void testLogicalOps() {
		assertPredicateEqual("!person.isActive", e -> !e.isActive());
		assertPredicateEqual("person.age < 100 AND person.height > 200", e -> e.getAge() < 100 && e.getHeight() > 200);
		assertPredicateEqual("person.age < 100 OR person.height > 200", e -> e.getAge() < 100 || e.getHeight() > 200);
	}

	@Test
	void testMultipleLogicalOps() {
		assertPredicateEqual("person.isActive AND (person.age < 100 OR person.height > 200)", e -> e.isActive() && (e.getAge() < 100 || e.getHeight() > 200));
		assertPredicateEqual("(person.age < 100 OR person.height > 200) AND person.isActive", e -> (e.getAge() < 100 || e.getHeight() > 200) && e.isActive());
	}

	@Test
	void testWithVariables() {
		var name = "Donald";
		var age = 80;
		assertPredicateEqual("person.name = 'Donald' OR person.age > 80", person -> person.getName() == name || person.getAge() > age);
	}

	@Test
	void testFunction() {
		assertFunctionEqual("name", IPerson::getName);
		assertFunctionEqual("age", person -> person.getAge());
	}

	@Test
	void testMethodReferences() {
		SqlPredicate<IPerson> person = IPerson::isAdult;
		SqlPredicate<IPerson> personAnd = person.and(x -> true);
		assertPredicateEqual("person.isAdult AND true", personAnd);
	}

	@Test
	void testAndFunction() {
		var id = 1;
		SqlPredicate<IPerson> personPredicate = person -> person.getId() == id;
		SqlPredicate<IPerson> personSqlPredicateAnd = personPredicate.and(x -> true);
		assertPredicateEqual("person.id = 1 AND true", personSqlPredicateAnd);
	}

	@Test
	void testOrFunction() {
		var id = 1;
		SqlPredicate<IPerson> personPredicate = person -> person.getId() == id;
		SqlPredicate<IPerson> personSqlPredicateOr = personPredicate.or(x -> true);
		assertPredicateEqual("person.id = 1 OR true", personSqlPredicateOr);
	}

	@Test
	void testHigherLevelWithParameters() {
		var name1 = "Donald";
		var age1 = 80;
		var name2 = "Steve";
		SqlPredicate<IPerson> personPredicate = p -> (p.getName() == name1 || p.getAge() == age1);
		personPredicate = personPredicate.and(p -> p.getName() == name2);
		assertPredicateEqual("(person.name = 'Donald' OR person.age = 80) AND person.name = 'Steve'", personPredicate);
	}

	@Test
	void testNestedProperties() {
		SqlPredicate<IPerson> p = person -> person.getCar().getModel() == "Mercedes";
		var sql = Lambda2Sql.toSql(p, "car");
		Assertions.assertEquals("car.model = 'Mercedes'", sql);
	}

	private void assertPredicateEqual(String expectedSql, SqlPredicate<IPerson> p) {
		var sql = Lambda2Sql.toSql(p, "person");
		Assertions.assertEquals(expectedSql, sql);
	}

	private void assertFunctionEqual(String expectedSql, SqlFunction<IPerson, ?> function) {
		var sql = Lambda2Sql.toSql(function);
		Assertions.assertEquals(expectedSql, sql);
	}
}
