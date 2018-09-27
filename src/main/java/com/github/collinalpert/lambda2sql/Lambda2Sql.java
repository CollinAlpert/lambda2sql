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
	 * @param functionalInterface A {@link FunctionalInterface} lambda to convert.
	 * @param prefix              An optional prefix to proceed the column name.
	 *                            Usually it is supposed to be used to reference the column name including the table name.
	 * @return A {@link String} describing the SQL where condition.
	 */
	public static String toSql(SerializedFunctionalInterface functionalInterface, String prefix) {
		var lambdaExpression = LambdaExpression.parse(functionalInterface);
		return lambdaExpression.accept(new ToSqlVisitor(prefix)).toString();
	}

	public static String toSql(SerializedFunctionalInterface functionalInterface) {
		return toSql(functionalInterface, null);
	}
}
