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
	 * @param tableName           The table name which the column belongs to. This will explicitly reference the column.
	 *                            It is optional to specify this.
	 * @param withBackticks       Specifies if the table and the column name should be escaped with backticks. The default behavior is {@code true}.
	 * @return A {@link String} describing the SQL where condition.
	 */
	public static String toSql(SerializedFunctionalInterface functionalInterface, String tableName, boolean withBackticks) {
		var lambdaExpression = LambdaExpression.parse(functionalInterface);
		return lambdaExpression.accept(new SqlVisitor(tableName, withBackticks)).toString();
	}

	public static String toSql(SerializedFunctionalInterface functionalInterface, String tableName) {
		return toSql(functionalInterface, tableName, true);
	}

	public static String toSql(SerializedFunctionalInterface functionalInterface) {
		return toSql(functionalInterface, null, false);
	}
}
