package com.github.collinalpert.lambda2sql;

import com.github.collinalpert.lambda2sql.functions.SerializedFunctionalInterface;
import com.trigersoft.jaque.expression.LambdaExpression;

/**
 * A utility class for converting java lambdas to SQL.
 */
public class Lambda2Sql {

	/**
	 * Converts a lambda expression to SQL.
	 * <pre>{@code person -> person.getAge() > 50 && person.isActive() }</pre>
	 * Becomes a string:
	 * <pre>{@code "age > 50 AND active" }</pre>
	 * Supported operators: {@code >,>=,<,<=,=,!=,&&,||,!}
	 *
	 * @param functionalInterface The lambda to convert.
	 * @return A {@link String} describing the SQL where condition.
	 */
	public static String toSql(SerializedFunctionalInterface functionalInterface) {
		var lambdaExpression = LambdaExpression.parse(functionalInterface);
		return lambdaExpression.accept(new ToSqlVisitor()).toString();
	}
}
