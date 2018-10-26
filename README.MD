Lambda2sql (lambda) -> "sql"
==========

**Please note:** This is a Java 10 library so make sure you have at least Java 10 installed when using it.

Convert Java 8 lambdas to SQL statements.

For example, the following ``SqlPredicate<Person>``:
```jshelllanguage
 person -> person.getAge() < 100 && person.getHeight() > 200
```
 
is converted to a string:
 
```
  person.age < 100 AND person.height > 200
```
allowing you to write readable queries in a type safe way.

Usage
---------

```jshelllanguage
int age = 100;
int height = 200;
SqlPredicate<Person> predicate = person -> person.getAge() < age && person.getHeight() > height;

String sql = Lambda2Sql.toSql(predicate); // person.age < 100 AND person.height > 200
```

How it works
---------

It uses [JaQue](https://github.com/TrigerSoft/jaque) to build an expression tree for a lambda. The expression tree is then traversed and converted to a SQL statement. 

Under the hood, JaQue depends on the system property `jdk.internal.lambda.dumpProxyClasses`, if the lambda expression is not serialized:
See [https://bugs.openjdk.java.net/browse/JDK-8023524](https://bugs.openjdk.java.net/browse/JDK-8023524).

When the property is enabled, JVM generated classes for lambdas are saved to disk. JaQue then uses [ASM](http://asm.ow2.org/) to read the .class files and creates expression trees.

Since the functional interfaces included in this project are automatically serialized, there is no need to set this property.
The interfaces ``SqlPredicate<T>`` and ``SqlFunction<T>`` can be used exactly like the original functional interfaces.

Features
---------

Current version works with predicates, functions and supports the following operators: >, >=, <, <=, =, !=, &&, ||, !

Feel free to open an issue with any requests you might have.

Install
-------

You can include the Maven dependency:
```xml
<dependency>
    <groupId>com.github.collinalpert</groupId>
    <artifactId>lambda2sql</artifactId>
    <version>1.8.1</version>
</dependency>
```

Or add the [JAR](https://github.com/CollinAlpert/lambda2sql/releases/latest) to your project.
