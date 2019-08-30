package com.github.collinalpert.lambda2sql;

import com.github.collinalpert.lambda2sql.functions.TriFunction;
import com.trigersoft.jaque.expression.BinaryExpression;
import com.trigersoft.jaque.expression.ConstantExpression;
import com.trigersoft.jaque.expression.DelegateExpression;
import com.trigersoft.jaque.expression.Expression;
import com.trigersoft.jaque.expression.ExpressionType;
import com.trigersoft.jaque.expression.ExpressionVisitor;
import com.trigersoft.jaque.expression.InvocationExpression;
import com.trigersoft.jaque.expression.LambdaExpression;
import com.trigersoft.jaque.expression.MemberExpression;
import com.trigersoft.jaque.expression.ParameterExpression;
import com.trigersoft.jaque.expression.UnaryExpression;

import java.lang.reflect.Member;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.chrono.ChronoLocalDate;
import java.time.chrono.ChronoLocalDateTime;
import java.time.temporal.Temporal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Converts a lambda expression to an SQL where condition.
 */
public class SqlVisitor implements ExpressionVisitor<StringBuilder> {

	/**
	 * The supported methods that can be used on Java objects inside the lambda expressions.
	 */
	private static final Map<Member, Integer> registeredMethods = new HashMap<>() {{
		try {
			put(String.class.getDeclaredMethod("equals", Object.class), ExpressionType.Equal);
			put(Object.class.getDeclaredMethod("equals", Object.class), ExpressionType.Equal);
			put(LocalDate.class.getDeclaredMethod("isAfter", ChronoLocalDate.class), ExpressionType.GreaterThan);
			put(LocalTime.class.getDeclaredMethod("isAfter", LocalTime.class), ExpressionType.GreaterThan);
			put(LocalDateTime.class.getDeclaredMethod("isAfter", ChronoLocalDateTime.class), ExpressionType.GreaterThan);
			put(LocalDate.class.getDeclaredMethod("isBefore", ChronoLocalDate.class), ExpressionType.LessThan);
			put(LocalTime.class.getDeclaredMethod("isBefore", LocalTime.class), ExpressionType.LessThan);
			put(LocalDateTime.class.getDeclaredMethod("isBefore", ChronoLocalDateTime.class), ExpressionType.LessThan);
		} catch (NoSuchMethodException e) {
			e.printStackTrace();
		}
	}};

	private final String tableName;
	private final boolean withBackticks;
	private final LinkedListStack<List<ConstantExpression>> arguments;

	/**
	 * More complex methods that can be used on Java objects inside the lambda expressions.
	 */
	private final Map<Member, TriFunction<Expression, Expression, Boolean, StringBuilder>> complexMethods = new HashMap<>() {{
		try {
			put(String.class.getDeclaredMethod("startsWith", String.class), SqlVisitor.this::stringStartsWith);
			put(String.class.getDeclaredMethod("endsWith", String.class), SqlVisitor.this::stringEndsWith);
			put(String.class.getDeclaredMethod("contains", CharSequence.class), SqlVisitor.this::stringContains);
			put(List.class.getDeclaredMethod("contains", Object.class), SqlVisitor.this::listContains);
			put(ArrayList.class.getDeclaredMethod("contains", Object.class), SqlVisitor.this::listContains);
			put(LinkedList.class.getDeclaredMethod("contains", Object.class), SqlVisitor.this::listContains);
		} catch (NoSuchMethodException e) {
			e.printStackTrace();
		}
	}};

	private StringBuilder sb;
	private Expression body;
	private Expression javaMethodParameter;

	SqlVisitor(String tableName, boolean withBackTicks) {
		this(tableName, withBackTicks, new LinkedListStack<>());
	}

	private SqlVisitor(String tableName, boolean withBackticks, LinkedListStack<List<ConstantExpression>> arguments) {
		this.tableName = tableName;
		this.withBackticks = withBackticks;
		this.arguments = arguments;
		this.sb = new StringBuilder();
	}

	/**
	 * Converts a Java operator to an SQL operator.
	 *
	 * @param expressionType The {@link ExpressionType} representing the Java expression.
	 * @return A {@link String} which will be inserted into the query.
	 */
	private static String toSqlOperator(int expressionType) {
		switch (expressionType) {
			case ExpressionType.Equal:
				return "=";
			case ExpressionType.LogicalAnd:
				return "AND";
			case ExpressionType.LogicalOr:
				return "OR";
			case ExpressionType.IsNull:
				return " IS NULL";
			case ExpressionType.IsNonNull:
				return " IS NOT NULL";
			case ExpressionType.Convert:
				return "";
			default:
				return ExpressionType.toString(expressionType);
		}
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
		//Handling for null parameters
		if (e.getSecond() instanceof ParameterExpression && !arguments.top().isEmpty() && arguments.top().get(((ParameterExpression) e.getSecond()).getIndex()).getValue() == null) {
			return Expression.unary(e.getExpressionType() == ExpressionType.Equal ? ExpressionType.IsNull : ExpressionType.IsNonNull, Boolean.TYPE, e.getFirst()).accept(this);
		}

		boolean quote = e != this.body && e.getExpressionType() == ExpressionType.LogicalOr;

		if (quote) sb.append('(');

		e.getFirst().accept(this);

		sb.append(' ').append(toSqlOperator(e.getExpressionType())).append(' ');

		e.getSecond().accept(this);

		if (quote) sb.append(')');

		return sb;
	}

	/**
	 * Returns a constant used in a lambda expression as the SQL equivalent.
	 *
	 * @param e The {@link ConstantExpression} to transform.
	 * @return A {@link StringBuilder} that has this constant appended.
	 */
	@Override
	public StringBuilder visit(ConstantExpression e) {
		if (e.getValue() == null) {
			return sb.append("NULL");
		}

		if (e.getValue() instanceof LambdaExpression) {
			return ((LambdaExpression) e.getValue()).getBody().accept(this);
		}

		if (e.getValue() instanceof String || e.getValue() instanceof Temporal) {
			return sb.append("'").append(escapeString(e.getValue().toString())).append("'");
		}

		return sb.append(e.getValue().toString());
	}

	/**
	 * An expression which represents an invocation of a lambda expression.
	 * It is the last {@link #visit} where the arguments of {@link ParameterExpression}s are available which is why
	 * they are temporarily saved in a list to be inserted into the SQL where condition later.
	 *
	 * @param e The {@link InvocationExpression} to convert.
	 * @return A {@link StringBuilder} containing the body/target of the lambda expression.
	 */
	@Override
	public StringBuilder visit(InvocationExpression e) {
		if (e.getTarget() instanceof LambdaExpression) {
			var list = e.getArguments()
					.stream()
					.filter(x -> x instanceof ConstantExpression)
					.map(ConstantExpression.class::cast)
					.collect(Collectors.toList());
			if (!list.isEmpty()) {
				arguments.push(list);
			}
		}

		if (e.getTarget().getExpressionType() == ExpressionType.MethodAccess && !e.getArguments().isEmpty()) {
			javaMethodParameter = e.getArguments().get(0);
		}

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
		if (this.body == null && e.getBody() instanceof BinaryExpression) {
			this.body = e.getBody();
		}

		return e.getBody().accept(this);
	}

	@Override
	public StringBuilder visit(DelegateExpression e) {
		return e.getDelegate().accept(this);
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
		if (registeredMethods.containsKey(e.getMember())) {
			return Expression.binary(registeredMethods.get(e.getMember()), e.getInstance(), this.javaMethodParameter).accept(this);
		}

		if (this.complexMethods.containsKey(e.getMember())) {
			return sb.append(this.complexMethods.get(e.getMember()).apply(e.getInstance(), this.javaMethodParameter, false));
		}

		var nameArray = e.getMember().getName().replaceAll("^(get)", "").toCharArray();
		nameArray[0] = Character.toLowerCase(nameArray[0]);
		var name = new String(nameArray);
		if (this.tableName == null) {
			return sb.append(name);
		}

		String escape = this.withBackticks ? "`" : "";
		return sb.append(escape).append(this.tableName).append(escape).append(".").append(escape).append(name).append(escape);
	}

	/**
	 * Represents a parameterized expression, for example if a variable is used in a query.
	 *
	 * @param e The parameterized expression.
	 * @return The {@link StringBuilder} with the SQL equivalent appended.
	 */
	@Override
	public StringBuilder visit(ParameterExpression e) {
		arguments.top().get(e.getIndex()).accept(this);
		if (e.getIndex() == arguments.top().size() - 1) {
			arguments.pop();
		}

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
		if (e.getExpressionType() == ExpressionType.LogicalNot) {
			//for support for negated Java methods
			var invocationExpression = (InvocationExpression) e.getFirst();
			var memberExpression = (MemberExpression) invocationExpression.getTarget();
			if (registeredMethods.containsKey(memberExpression.getMember())) {
				return Expression.logicalNot(Expression.binary(registeredMethods.get(memberExpression.getMember()), memberExpression.getInstance(), invocationExpression.getArguments().get(0))).accept(this);
			} else if (complexMethods.containsKey(memberExpression.getMember())) {
				return sb.append(complexMethods.get(memberExpression.getMember()).apply(memberExpression.getInstance(), invocationExpression.getArguments().get(0), true));
			} else {
				sb.append("!");
			}

			return e.getFirst().accept(this);
		}

		e.getFirst().accept(this);
		return sb.append(toSqlOperator(e.getExpressionType()));
	}

	//region Complex Java methods

	private StringBuilder stringStartsWith(Expression member, Expression argument, boolean negated) {
		return doStringOperation(member, argument, negated, valueBuilder -> valueBuilder.insert(valueBuilder.length() - 1, '%'));
	}

	private StringBuilder stringEndsWith(Expression member, Expression argument, boolean negated) {
		return doStringOperation(member, argument, negated, valueBuilder -> valueBuilder.insert(1, '%'));
	}

	private StringBuilder stringContains(Expression member, Expression argument, boolean negated) {
		return doStringOperation(member, argument, negated, valueBuilder -> valueBuilder.insert(1, '%').insert(valueBuilder.length() - 1, '%'));
	}

	private StringBuilder listContains(Expression listAsArgument, Expression argument, boolean negated) {
		List l = (List) arguments.pop().get(((ParameterExpression) listAsArgument).getIndex()).getValue();
		var joiner = new StringJoiner(", ", "(", ")");
		l.forEach(x -> joiner.add(x.toString()));
		return argument.accept(new SqlVisitor(this.tableName, this.withBackticks, this.arguments)).append(negated ? " NOT" : "").append(" IN ").append(joiner.toString());
	}

	//endregion

	private StringBuilder doStringOperation(Expression member, Expression argument, boolean negated, Consumer<StringBuilder> modifier) {
		var valueBuilder = argument.accept(new SqlVisitor(this.tableName, this.withBackticks, this.arguments));
		modifier.accept(valueBuilder);
		return member.accept(new SqlVisitor(this.tableName, this.withBackticks, this.arguments)).append(negated ? " NOT" : "").append(" LIKE ").append(valueBuilder);
	}

	private String escapeString(String input) {
		input = input.replace("\\", "\\\\").replace("'", "\\'");
		return input;
	}
}