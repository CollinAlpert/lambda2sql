package com.github.collinalpert.lambda2sql;

import com.trigersoft.jaque.expression.BinaryExpression;
import com.trigersoft.jaque.expression.ConstantExpression;
import com.trigersoft.jaque.expression.Expression;
import com.trigersoft.jaque.expression.ExpressionType;
import com.trigersoft.jaque.expression.ExpressionVisitor;
import com.trigersoft.jaque.expression.InvocationExpression;
import com.trigersoft.jaque.expression.LambdaExpression;
import com.trigersoft.jaque.expression.MemberExpression;
import com.trigersoft.jaque.expression.ParameterExpression;
import com.trigersoft.jaque.expression.UnaryExpression;

import java.util.LinkedList;
import java.util.List;

/**
 * Converts a lambda expression to an SQL where condition.
 */
public class ToSqlVisitor implements ExpressionVisitor<StringBuilder> {

	private final String prefix;
	private StringBuilder sb;

	private Expression body;
	private List<ConstantExpression> parameters;

	public ToSqlVisitor(String prefix) {
		this.prefix = prefix;
		this.sb = new StringBuilder();
		this.parameters = new LinkedList<>();
	}

	/**
	 * Converts the Java operation to an SQL operation.
	 *
	 * @param expressionType The {@link ExpressionType} representing the Java expression.
	 * @return A {@link String} which will be inserted into the query.
	 */
	private static String toSqlOp(int expressionType) {
		switch (expressionType) {
			case ExpressionType.Equal:
				return "=";
			case ExpressionType.LogicalAnd:
				return "AND";
			case ExpressionType.LogicalOr:
				return "OR";
			case ExpressionType.Convert:
				return "";
		}
		return ExpressionType.toString(expressionType);
	}

	/**
	 * Converts a binary expression to the SQL equivalent.
	 * For example:
	 * {@code person -> person.getId() == 2}
	 * becomes: id = 2
	 *
	 * @param e the {@link BinaryExpression} to convert
	 * @return the {@link StringBuilder} containing the where condition.
	 */
	@Override
	public StringBuilder visit(BinaryExpression e) {
		boolean quote = e != body && e.getExpressionType() == ExpressionType.LogicalOr;

		if (quote) sb.append('(');

		e.getFirst().accept(this);
		sb.append(' ').append(toSqlOp(e.getExpressionType())).append(' ');
		e.getSecond().accept(this);

		if (quote) sb.append(')');

		return sb;
	}

	/**
	 * Returns a constant used in a lambda expression as the SQL equivalent.
	 * The only time this equivalent differs is when it is a {@link String}.
	 *
	 * @param e The {@link ConstantExpression} to transform.
	 * @return A {@link StringBuilder} that has this constant appended.
	 */
	@Override
	public StringBuilder visit(ConstantExpression e) {
		if (e.getValue() instanceof String) {
			return sb.append("'").append(e.getValue().toString()).append("'");
		}
		return sb.append(e.getValue().toString());
	}

	/**
	 * An expression which represents an invocation of a lambda expression.
	 * It is the last {@link #visit} where the parameters of {@link ParameterExpression}s are available which is why
	 * they are temporarily saved in a list to be inserted into the SQL where condition later.
	 *
	 * @param e The {@link InvocationExpression} to convert.
	 * @return A {@link StringBuilder} containing the body/target of the lambda expression.
	 */
	@Override
	public StringBuilder visit(InvocationExpression e) {
		e.getArguments()
				.stream()
				.filter(x -> x instanceof ConstantExpression)
				.map(ConstantExpression.class::cast)
				.forEach(parameters::add);
		return e.getTarget().accept(this);
	}

	/**
	 * The entry point for converting lambda expressions.
	 *
	 * @param e The entire lambda expression to convert.
	 * @return A {@link StringBuilder} containing the body of the lambda expression.
	 */
	@Override
	public StringBuilder visit(LambdaExpression<?> e) {
		this.body = e.getBody();
		return body.accept(this);
	}

	/**
	 * An expression which represents a getter, and thus a field in a database, in the lambda expression.
	 * For example:
	 * {@code person -> person.getName();}
	 * becomes: name
	 *
	 * @param e The {@link MemberExpression} to convert.
	 * @return A {@link StringBuilder} with the name of the database field appended.
	 */
	@Override
	public StringBuilder visit(MemberExpression e) {
		String name = e.getMember().getName();
		name = name.replaceAll("^(get)", "");
		name = name.substring(0, 1).toLowerCase() + name.substring(1);
		if (prefix == null) {
			return sb.append(name);
		}
		return sb.append(prefix).append(".").append(name);
	}

	/**
	 * Represents a parameterized expression, for example if a variable is used in a query.
	 *
	 * @param e The parameterized expression.
	 * @return The {@link StringBuilder} with the SQL equivalent appended.
	 */
	@Override
	public StringBuilder visit(ParameterExpression e) {
		parameters.get(e.getIndex()).accept(this);
		return sb;
	}

	/**
	 * Converts a unary expression to the SQL equivalent.
	 * For example:
	 * {@code person -> !person.isActive();}
	 * becomes: !active
	 *
	 * @param e the {@link UnaryExpression} to convert
	 * @return A {@link StringBuilder} with the unary expression appended.
	 */
	@Override
	public StringBuilder visit(UnaryExpression e) {
		sb.append(toSqlOp(e.getExpressionType()));
		return e.getFirst().accept(this);
	}

}