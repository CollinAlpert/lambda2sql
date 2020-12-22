package com.github.collinalpert.lambda2sql.test;

import com.github.collinalpert.lambda2sql.Lambda2Sql;
import com.github.collinalpert.lambda2sql.functions.*;
import org.junit.jupiter.api.*;

import java.io.Serializable;
import java.time.*;
import java.util.*;

import static com.github.collinalpert.lambda2sql.SqlFunctions.*;

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
		SqlPredicate<IPerson> person2 = p -> p.isNullable();
		SqlPredicate<IPerson> person3 = IPerson::isNullable;
		SqlPredicate<IPerson> personAnd = person.and(x -> true);

		assertPredicateEqual("`person`.`isAdult`", person);
		assertPredicateEqual("`person`.`isNullable`", person2);
		assertPredicateEqual("`person`.`isNullable`", person3);
		assertPredicateEqual("`person`.`isNullable` AND true", person2.and(x -> true));
		assertPredicateEqual("`person`.`isNullable` AND true", person3.and(x -> true));
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
		assertPredicateEqual("(`person`.`age` = 17 OR `person`.`name` IS NULL) AND true", p.and(x -> true));
		assertPredicateEqual("`person`.`name` IS NULL", p2);
		assertPredicateEqual("`person`.`name` IS NULL AND true", p2.and(x -> true));
		assertPredicateEqual("`person`.`age` >= NULL AND `person`.`age` <= 17", p3);
	}

	@Test
	void testBraces() {
		SqlPredicate<IPerson> p1 = person -> person.getName() == "Steve" && person.getAge() == 18 || person.getLastName() == "T";
		SqlPredicate<IPerson> p2 = person -> person.getName() == "Steve" && (person.getAge() == 18 || person.getLastName() == "T");
		assertPredicateEqual("`person`.`name` = 'Steve' AND `person`.`age` = 18 OR `person`.`lastName` = 'T'", p1);
		assertPredicateEqual("`person`.`name` = 'Steve' AND (`person`.`age` = 18 OR `person`.`lastName` = 'T')", p2);
	}

	@Test
	void testNotNull() {
		String isNull = null;
		var age = 17;
		SqlPredicate<IPerson> p = person -> person.getAge() == age || person.getName() != isNull;
		SqlPredicate<IPerson> p2 = person -> person.getName() != null;
		SqlPredicate<IPerson> p3 = person -> person.getName() != null;
		p3 = p3.and(t -> t.getAge() == 18);
		assertPredicateEqual("`person`.`age` = 17 OR `person`.`name` IS NOT NULL", p);
		assertPredicateEqual("`person`.`name` IS NOT NULL", p2);
		assertPredicateEqual("`person`.`name` IS NOT NULL AND `person`.`age` = 18", p3);
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
		assertPredicateEqual("`person`.`age` >= 18 OR (`person`.`name` LIKE 'Steve%' OR `person`.`name` LIKE '%Steve')", person -> person.getAge() >= age || person.getName().startsWith(name) || person.getName().endsWith(name));
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

	@Test
	void testLastParameterNull() {
		String s = null;
		SqlPredicate<IPerson> alwaysTrue = x -> true;
		SqlPredicate<IPerson> pred = x -> x.getName() == s;
		assertPredicateEqual("`person`.`name` IS NULL AND true", pred.and(alwaysTrue));
	}

	@Test
	void testMultipleSameParameter() {
		String s = "Steve";
		SqlPredicate<IPerson> p = x -> x.getName() == s || x.getLastName() == s;
		assertPredicateEqual("`person`.`name` = 'Steve' OR `person`.`lastName` = 'Steve'", p);
	}

	@Test
	void testMethods() {
		var name = "Steve";

		SqlPredicate<IPerson> yearPredicate = p -> p.getDate().getYear() == 1250 && p.getName() == "Steve";
		SqlPredicate<IPerson> yearNegatedPredicate = p -> p.getDate().getYear() != 1250 && p.getName() == name;
		SqlPredicate<IPerson> monthPredicate = p -> p.getDate().getMonthValue() <= 10;
		SqlPredicate<IPerson> lengthPredicate = p -> p.getName().length() > 6;

		assertPredicateEqual("YEAR(`person`.`date`) = 1250 AND `person`.`name` = 'Steve'", yearPredicate);
		assertPredicateEqual("YEAR(`person`.`date`) != 1250 AND `person`.`name` = 'Steve'", yearNegatedPredicate);
		assertPredicateEqual("MONTH(`person`.`date`) <= 10", monthPredicate);
		assertPredicateEqual("LENGTH(`person`.`name`) > 6", lengthPredicate);
	}

	@Test
	void testSqlFunctions() {
		var name = "Steve";

		SqlPredicate<IPerson> sumPredicate = p -> sum(p.getAge()) == 1250 && p.getName() == name;
		SqlPredicate<IPerson> minPredicate = p -> min(p.getAge()) == 1250 && p.getName() == "Steve";
		SqlPredicate<IPerson> maxPredicate = p -> max(p.getAge()) == 1250 || p.getName().startsWith(name);

		assertPredicateEqual("SUM(`person`.`age`) = 1250 AND `person`.`name` = 'Steve'", sumPredicate);
		assertPredicateEqual("MIN(`person`.`age`) = 1250 AND `person`.`name` = 'Steve'", minPredicate);
		assertPredicateEqual("MAX(`person`.`age`) = 1250 OR `person`.`name` LIKE 'Steve%'", maxPredicate);
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
