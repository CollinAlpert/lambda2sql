package com.github.collinalpert.lambda2sql.test;

import com.github.collinalpert.lambda2sql.Lambda2Sql;
import com.github.collinalpert.lambda2sql.functions.SqlFunction;
import com.github.collinalpert.lambda2sql.functions.SqlPredicate;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;

class Lambda2SqlTest implements Serializable {

	@Test
	void testComparisons() {
		assertPredicateEqual("`person`.`age` = 1", e -> e.getAge() == 1);
		assertPredicateEqual("`person`.`age` > 1", e -> e.getAge() > 1);
		assertPredicateEqual("`person`.`age` < 1", e -> e.getAge() < 1);
		assertPredicateEqual("`person`.`age` >= 1", e -> e.getAge() >= 1);
		assertPredicateEqual("`person`.`age` <= 1", e -> e.getAge() <= 1);
		assertPredicateEqual("`person`.`age` != 1", e -> e.getAge() != 1);
	}

	@Test
	void testLogicalOps() {
		assertPredicateEqual("!`person`.`isActive`", e -> !e.isActive());
		assertPredicateEqual("`person`.`age` < 100 AND `person`.`height` > 200", e -> e.getAge() < 100 && e.getHeight() > 200);
		assertPredicateEqual("`person`.`age` < 100 OR `person`.`height` > 200", e -> e.getAge() < 100 || e.getHeight() > 200);
	}

	@Test
	void testMultipleLogicalOps() {
		assertPredicateEqual("`person`.`isActive` AND (`person`.`age` < 100 OR `person`.`height` > 200)", e -> e.isActive() && (e.getAge() < 100 || e.getHeight() > 200));
		assertPredicateEqual("(`person`.`age` < 100 OR `person`.`height` > 200) AND `person`.`isActive`", e -> (e.getAge() < 100 || e.getHeight() > 200) && e.isActive());
	}

	@Test
	void testWithVariables() {
		var name = "Donald";
		var age = 80;
		assertPredicateEqual("`person`.`name` = 'Donald' OR `person`.`age` > 80", person -> person.getName().equals(name) || person.getAge() > age);
	}

	@Test
	void testFunction() {
		assertFunctionEqual("`person`.`name`", IPerson::getName);
		assertFunctionEqual("`person`.`age`", person -> person.getAge());
	}

	@Test
	void testMethodReferences() {
		SqlPredicate<IPerson> person = IPerson::isAdult;
		SqlPredicate<IPerson> personAnd = person.and(x -> true);
		assertPredicateEqual("`person`.`isAdult` AND true", personAnd);
	}

	@Test
	void testAndFunction() {
		var id = 2;
		SqlPredicate<IPerson> personPredicate = person -> person.getId() == id;
		SqlPredicate<IPerson> personSqlPredicateAnd = personPredicate.and(x -> true);
		assertPredicateEqual("`person`.`id` = 2 AND true", personSqlPredicateAnd);
	}

	@Test
	void testOrFunction() {
		var id = 2;
		SqlPredicate<IPerson> personPredicate = person -> person.getId() == id;
		SqlPredicate<IPerson> personSqlPredicateOr = personPredicate.or(x -> true);
		assertPredicateEqual("`person`.`id` = 2 OR true", personSqlPredicateOr);
	}

	@Test
	void testHigherLevelWithParameters() {
		var age = 80;
		var name = "Steve";
		var localDate = LocalDate.of(1984, 1, 1);
		SqlPredicate<IPerson> personPredicate = p -> (p.getName().equals("Donald") || p.getAge() == age);
		personPredicate = personPredicate.and(p -> p.getName() == name).and(p -> p.getDate().isAfter(localDate)).or(p -> !p.isActive()).or(p -> p.getHeight() < 150);
		assertPredicateEqual("((`person`.`name` = 'Donald' OR `person`.`age` = 80) AND `person`.`name` = 'Steve' AND `person`.`date` > '1984-01-01' OR !`person`.`isActive`) OR `person`.`height` < 150", personPredicate);
	}

	@Test
	void testNestedProperties() {
		SqlPredicate<IPerson> p = person -> person.getCar().getModel() == "Mercedes";
		var sql = Lambda2Sql.toSql(p, "car", false);
		Assertions.assertEquals("car.model = 'Mercedes'", sql);
	}

	@Test
	void testNull() {
		String isNull = null;
		Integer i = null;
		var age = 17;
		SqlPredicate<IPerson> p = person -> person.getAge() == age || person.getName() == isNull;
		SqlPredicate<IPerson> p2 = person -> person.getName() == null;
		SqlPredicate<IPerson> p3 = person -> person.getAge() >= i && person.getAge() <= age;
		assertPredicateEqual("`person`.`age` = 17 OR `person`.`name` IS NULL", p);
		assertPredicateEqual("`person`.`name` IS NULL", p2);
		assertPredicateEqual("`person`.`age` >= NULL AND `person`.`age` <= 17", p3);
	}

	@Test
	void testNotNull() {
		String isNull = null;
		var age = 17;
		SqlPredicate<IPerson> p = person -> person.getAge() == age || person.getName() != isNull;
		SqlPredicate<IPerson> p2 = person -> person.getName() != null;
		assertPredicateEqual("`person`.`age` = 17 OR `person`.`name` IS NOT NULL", p);
		assertPredicateEqual("`person`.`name` IS NOT NULL", p2);
	}

	@Test
	void testParentheses() {
		var age = 18;
		SqlPredicate<IPerson> p = person -> person.getAge() == age && person.isAdult() || person.getName() == "Steve";
		SqlPredicate<IPerson> p2 = person -> person.getAge() == age && (person.isAdult() || person.getName() == "Steve");
		assertPredicateEqual("`person`.`age` = 18 AND `person`.`isAdult` OR `person`.`name` = 'Steve'", p);
		assertPredicateEqual("`person`.`age` = 18 AND (`person`.`isAdult` OR `person`.`name` = 'Steve')", p2);
	}

	@Test
	void testJavaFunctions() {
		var name = "Steve";
		var age = 18;
		assertPredicateEqual("`person`.`name` LIKE 'Steve%' OR `person`.`age` >= 18", person -> person.getName().startsWith("Steve") || person.getAge() >= age);
		assertPredicateEqual("`person`.`age` >= 18 OR (`person`.`name` LIKE 'Steve%' OR `person`.`name` LIKE '%Steve')", person -> person.getAge() >= age || person.getName().startsWith("Steve") || person.getName().endsWith(name));
		assertPredicateEqual("`person`.`name` LIKE 'Steve%'", person -> person.getName().startsWith("Steve"));
		assertPredicateEqual("`person`.`age` >= 18 OR `person`.`name` LIKE 'Steve%'", person -> person.getAge() >= age || person.getName().startsWith(name));
		assertPredicateEqual("`person`.`name` LIKE 'Steve%'", person -> person.getName().startsWith(name));
		assertPredicateEqual("`person`.`age` >= 18 OR `person`.`name` NOT LIKE 'Steve%'", person -> person.getAge() >= age || !person.getName().startsWith(name));
		assertPredicateEqual("`person`.`name` NOT LIKE 'Steve%'", person -> !person.getName().startsWith(name));
		assertPredicateEqual("`person`.`name` NOT LIKE 'Steve%' AND !`person`.`isActive`", person -> !person.getName().startsWith(name) && !person.isActive());

		assertPredicateEqual("`person`.`age` >= 18 OR `person`.`name` LIKE '%Steve'", person -> person.getAge() >= age || person.getName().endsWith("Steve"));
		assertPredicateEqual("`person`.`name` LIKE '%Steve'", person -> person.getName().endsWith("Steve"));
		assertPredicateEqual("`person`.`age` >= 18 OR `person`.`name` LIKE '%Steve'", person -> person.getAge() >= age || person.getName().endsWith(name));
		assertPredicateEqual("`person`.`name` LIKE '%Steve'", person -> person.getName().endsWith(name));
		assertPredicateEqual("`person`.`age` >= 18 OR `person`.`name` NOT LIKE '%Steve'", person -> person.getAge() >= age || !person.getName().endsWith(name));
		assertPredicateEqual("`person`.`name` NOT LIKE '%Steve'", person -> !person.getName().endsWith(name));

		assertPredicateEqual("`person`.`age` >= 18 OR `person`.`name` LIKE '%Steve%'", person -> person.getAge() >= age || person.getName().contains("Steve"));
		assertPredicateEqual("`person`.`name` LIKE '%Steve%'", person -> person.getName().contains("Steve"));
		assertPredicateEqual("`person`.`age` >= 18 OR `person`.`name` LIKE '%Steve%'", person -> person.getAge() >= age || person.getName().contains(name));
		assertPredicateEqual("`person`.`name` LIKE '%Steve%'", person -> person.getName().contains(name));
		assertPredicateEqual("`person`.`age` >= 18 OR `person`.`name` NOT LIKE '%Steve%'", person -> person.getAge() >= age || !person.getName().contains(name));
		assertPredicateEqual("`person`.`name` NOT LIKE '%Steve%'", person -> !person.getName().contains(name));
	}

	@Test
	void testFunctionByMethod() {
		assertFunctionEqual("`person`.`height` ? `person`.`age`", this::getFunction);
	}

	@Test
	void testJavaTime() {
		var date = LocalDate.of(1990, 10, 5);
		var time = LocalTime.of(6, 24, 13);
		assertPredicateEqual("`person`.`name` LIKE 'Col%' AND `person`.`date` > '1990-10-05'", p -> p.getName().startsWith("Col") && p.getDate().isAfter(date));
		assertPredicateEqual("`person`.`name` LIKE 'Col%' AND `person`.`date` <= '1990-10-05'", p -> p.getName().startsWith("Col") && !p.getDate().isAfter(date));
		assertPredicateEqual("`person`.`name` LIKE 'Col%' AND `person`.`date` < '1990-10-05'", p -> p.getName().startsWith("Col") && p.getDate().isBefore(date));
		assertPredicateEqual("`person`.`name` LIKE 'Col%' AND `person`.`date` >= '1990-10-05'", p -> p.getName().startsWith("Col") && !p.getDate().isBefore(date));
		assertPredicateEqual("`person`.`name` LIKE 'Col%' AND `person`.`date` = '1990-10-05'", p -> p.getName().startsWith("Col") && p.getDate() == date);
		assertPredicateEqual("`person`.`time` = '06:24:13' AND `person`.`name` LIKE 'Col%'", p -> p.getTime() == time && p.getName().startsWith("Col"));
	}

	@Test
	void testContains() {
		var ids = Arrays.asList(1L, 2L, 3L, 4L);
		var ids2 = new ArrayList<Long>();
		ids2.add(2L);
		ids2.add(4L);
		ids2.add(6L);
		ids2.add(8L);

		var ids3 = new LinkedList<Long>();
		ids3.add(3L);
		ids3.add(6L);
		ids3.add(9L);
		ids3.add(12L);

		assertPredicateEqual("`person`.`id` IN (1, 2, 3, 4)", person -> ids.contains(person.getId()));
		assertPredicateEqual("`person`.`id` NOT IN (1, 2, 3, 4)", person -> !ids.contains(person.getId()));

		assertPredicateEqual("`person`.`id` IN (2, 4, 6, 8)", person -> ids2.contains(person.getId()));
		assertPredicateEqual("`person`.`id` NOT IN (2, 4, 6, 8)", person -> !ids2.contains(person.getId()));

		assertPredicateEqual("`person`.`id` IN (3, 6, 9, 12)", person -> ids3.contains(person.getId()));
		assertPredicateEqual("`person`.`id` NOT IN (3, 6, 9, 12)", person -> !ids3.contains(person.getId()));
	}

	private void assertPredicateEqual(String expectedSql, SqlPredicate<IPerson> p) {
		var sql = Lambda2Sql.toSql(p, "person");
		Assertions.assertEquals(expectedSql, sql);
	}

	private void assertFunctionEqual(String expectedSql, SqlFunction<IPerson, ?> function) {
		var sql = Lambda2Sql.toSql(function, "person");
		Assertions.assertEquals(expectedSql, sql);
	}

	private SqlFunction<IPerson, ?> getFunction(IPerson p) {
		if (p.getHeight() > 150) {
			return IPerson::getHeight;
		}

		return IPerson::getAge;
	}
}
