package com.github.collinalpert.lambda2sql;

import com.github.collinalpert.lambda2sql.functions.SqlFunction;
import com.github.collinalpert.lambda2sql.functions.SqlPredicate;
import com.trigersoft.jaque.expression.LambdaExpression;
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
		assertPredicateEqual("person.name = 'Donald' AND person.age > 80", person -> person.getName() == name && person.getAge() > age);
	}

	@Test
	void testFunction() {
		assertFunctionEqual("name", Person::getName);
		assertFunctionEqual("age", person -> person.getAge());
	}

	@Test
	void testMethodReferences() {
		SqlPredicate<Person> person = Person::isAdult;
		SqlPredicate<Person> personAnd = person.and(x -> true);
		LambdaExpression<SqlPredicate<Person>> lambda = LambdaExpression.parse(personAnd);
	}

	@Test
	void testGetById() {
		int id = 1;
		SqlPredicate<Person> personPredicate = person -> person.getId() == id;
		SqlPredicate<Person> personSqlPredicateAnd = personPredicate.and(x -> true);
		LambdaExpression<SqlPredicate<Person>> pred = LambdaExpression.parse(personSqlPredicateAnd);
	}

	private void assertPredicateEqual(String expectedSql, SqlPredicate<Person> p) {
		var sql = Lambda2Sql.toSql(p, "person");
		Assertions.assertEquals(expectedSql, sql);
	}

	private void assertFunctionEqual(String expectedSql, SqlFunction<Person, ?> function) {
		var sql = Lambda2Sql.toSql(function);
		Assertions.assertEquals(expectedSql, sql);
	}
}
