package com.github.collinalpert.lambda2sql;

import com.github.collinalpert.expressions.expression.*;
import com.github.collinalpert.lambda2sql.functions.TriFunction;

import java.lang.reflect.Member;
import java.time.*;
import java.time.chrono.*;
import java.time.temporal.Temporal;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Converts a lambda expression to an SQL where condition.
 */
public class SqlVisitor implements ExpressionVisitor<StringBuilder> {

	/**
	 * The supported methods that can be used on Java objects inside the lambda expressions.
	 */
	private static final Map<Member, Integer> operatorMethods;
	private static final Map<Member, String> sqlFunctionMethods;

	static {
		operatorMethods = new HashMap<>(8, 1);
		sqlFunctionMethods = new HashMap<>(4, 1);

		try {
			operatorMethods.put(String.class.getDeclaredMethod("equals", Object.class), ExpressionType.Equal);
			operatorMethods.put(Object.class.getDeclaredMethod("equals", Object.class), ExpressionType.Equal);
			operatorMethods.put(LocalDate.class.getDeclaredMethod("isAfter", ChronoLocalDate.class), ExpressionType.GreaterThan);
			operatorMethods.put(LocalTime.class.getDeclaredMethod("isAfter", LocalTime.class), ExpressionType.GreaterThan);
			operatorMethods.put(LocalDateTime.class.getDeclaredMethod("isAfter", ChronoLocalDateTime.class), ExpressionType.GreaterThan);
			operatorMethods.put(LocalDate.class.getDeclaredMethod("isBefore", ChronoLocalDate.class), ExpressionType.LessThan);
			operatorMethods.put(LocalTime.class.getDeclaredMethod("isBefore", LocalTime.class), ExpressionType.LessThan);
			operatorMethods.put(LocalDateTime.class.getDeclaredMethod("isBefore", ChronoLocalDateTime.class), ExpressionType.LessThan);

			sqlFunctionMethods.put(SqlFunctions.class.getDeclaredMethod("sum", Object.class), "SUM");
			sqlFunctionMethods.put(SqlFunctions.class.getDeclaredMethod("min", Object.class), "MIN");
			sqlFunctionMethods.put(SqlFunctions.class.getDeclaredMethod("max", Object.class), "MAX");
		} catch (NoSuchMethodException e) {
			e.printStackTrace();
		}
	}

	private final String tableName;
	private final boolean withBackticks;
	private final LinkedListStack<List<ConstantExpression>> arguments;
	private final Map<String, Integer> parameterConsumptionCount;

	/**
	 * More complex methods that can be used on Java objects inside the lambda expressions.
	 */
	private final Map<Member, TriFunction<Expression, Expression, Boolean, StringBuilder>> complexMethods;

	private final StringBuilder sb;
	private Expression body;
	private Expression javaMethodParameter;

	SqlVisitor(String tableName, boolean withBackTicks) {
		this(tableName, withBackTicks, null, new LinkedListStack<>());
	}

	private SqlVisitor(String tableName, boolean withBackticks, Expression body, LinkedListStack<List<ConstantExpression>> arguments) {
		this.tableName = tableName;
		this.withBackticks = withBackticks;
		this.body = body;
		this.arguments = arguments;
		this.sb = new StringBuilder();
		this.parameterConsumptionCount = new HashMap<>();

		this.complexMethods = new HashMap<>(32, 1);
		try {
			this.complexMethods.put(String.class.getDeclaredMethod("startsWith", String.class), this::stringStartsWith);
			this.complexMethods.put(String.class.getDeclaredMethod("endsWith", String.class), this::stringEndsWith);
			this.complexMethods.put(String.class.getDeclaredMethod("contains", CharSequence.class), this::stringContains);
			this.complexMethods.put(String.class.getDeclaredMethod("length"), (string, argument, isNegated) -> applySqlFunction(string, "LENGTH"));

			this.complexMethods.put(List.class.getDeclaredMethod("contains", Object.class), this::listContains);
			this.complexMethods.put(ArrayList.class.getDeclaredMethod("contains", Object.class), this::listContains);
			this.complexMethods.put(LinkedList.class.getDeclaredMethod("contains", Object.class), this::listContains);

			this.complexMethods.put(LocalTime.class.getDeclaredMethod("getSecond"), (date, argument, isNegated) -> applySqlFunction(date, "SECOND"));
			this.complexMethods.put(LocalDateTime.class.getDeclaredMethod("getSecond"), (date, argument, isNegated) -> applySqlFunction(date, "SECOND"));
			this.complexMethods.put(LocalTime.class.getDeclaredMethod("getMinute"), (date, argument, isNegated) -> applySqlFunction(date, "MINUTE"));
			this.complexMethods.put(LocalDateTime.class.getDeclaredMethod("getMinute"), (date, argument, isNegated) -> applySqlFunction(date, "MINUTE"));
			this.complexMethods.put(LocalTime.class.getDeclaredMethod("getHour"), (date, argument, isNegated) -> applySqlFunction(date, "HOUR"));
			this.complexMethods.put(LocalDateTime.class.getDeclaredMethod("getHour"), (date, argument, isNegated) -> applySqlFunction(date, "HOUR"));
			this.complexMethods.put(LocalDate.class.getDeclaredMethod("getDayOfWeek"), (date, argument, isNegated) -> applySqlFunction(date, "DAYOFWEEK"));
			this.complexMethods.put(LocalDateTime.class.getDeclaredMethod("getDayOfWeek"), (date, argument, isNegated) -> applySqlFunction(date, "DAYOFWEEK"));
			this.complexMethods.put(LocalDate.class.getDeclaredMethod("getDayOfMonth"), (date, argument, isNegated) -> applySqlFunction(date, "DAY"));
			this.complexMethods.put(LocalDateTime.class.getDeclaredMethod("getDayOfWeek"), (date, argument, isNegated) -> applySqlFunction(date, "DAY"));
			this.complexMethods.put(LocalDate.class.getDeclaredMethod("getDayOfYear"), (date, argument, isNegated) -> applySqlFunction(date, "DAYOFYEAR"));
			this.complexMethods.put(LocalDateTime.class.getDeclaredMethod("getDayOfYear"), (date, argument, isNegated) -> applySqlFunction(date, "DAYOFYEAR"));
			this.complexMethods.put(LocalDate.class.getDeclaredMethod("getMonthValue"), (date, argument, isNegated) -> applySqlFunction(date, "MONTH"));
			this.complexMethods.put(LocalDateTime.class.getDeclaredMethod("getMonthValue"), (date, argument, isNegated) -> applySqlFunction(date, "MONTH"));
			this.complexMethods.put(LocalDate.class.getDeclaredMethod("getYear"), (date, argument, isNegated) -> applySqlFunction(date, "YEAR"));
			this.complexMethods.put(LocalDateTime.class.getDeclaredMethod("getYear"), (date, argument, isNegated) -> applySqlFunction(date, "YEAR"));
		} catch (NoSuchMethodException e) {
			e.printStackTrace();
		}
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
			//If we don't pop here and there are more expressions after this one, they will work with an incorrect argument.
			arguments.pop();
			return Expression.unary(e.getExpressionType() == ExpressionType.Equal ? ExpressionType.IsNull : ExpressionType.IsNonNull, Boolean.TYPE, e.getFirst()).accept(this);
		}

		boolean quote = e != this.body && e.getExpressionType() == ExpressionType.LogicalOr;

		if (quote) {
			sb.append('(');
		}

		e.getFirst().accept(this);

		sb.append(' ').append(toSqlOperator(e.getExpressionType())).append(' ');

		e.getSecond().accept(this);

		if (quote) {
			sb.append(')');
		}

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
		var target = e.getTarget();
		if (target instanceof LambdaExpression) {
			var list = e.getArguments()
					.stream()
					.filter(x -> x instanceof ConstantExpression)
					.map(ConstantExpression.class::cast)
					.collect(Collectors.toList());
			if (!list.isEmpty()) {
				arguments.push(list);
			}
		}

		String sqlFunctionName;
		if (target instanceof MemberExpression && (sqlFunctionName = sqlFunctionMethods.get(((MemberExpression) e.getTarget()).getMember())) != null) {
			sb.append(sqlFunctionName).append('(');
			e.getArguments().get(0).accept(this);
			sb.append(')');

			return sb;
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
	public StringBuilder visit(LambdaExpression e) {
		if (this.body == null && !(e.getBody() instanceof InvocationExpression)
				|| (e.getBody() instanceof InvocationExpression && !(((InvocationExpression) (e.getBody())).getTarget() instanceof LambdaExpression))) {
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
		if (operatorMethods.containsKey(e.getMember())) {
			return Expression.binary(operatorMethods.get(e.getMember()), e.getInstance(), this.javaMethodParameter).accept(this);
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
			var numberOfParameterUsages = countParameterUsages(e);
			// If parameter is used exactly once in the expression or has been consumed for as many times as it occurs, it can be removed.
			if (this.arguments.size() > 1 || numberOfParameterUsages == 1 || numberOfParameterUsages == parameterConsumptionCount.merge(e.toString(), 1, Integer::sum)) {
				arguments.pop();
			}
		}

		return sb;
	}

	private int countParameterUsages(ParameterExpression e) {
		var identifier = e.toString();
		var expressionString = this.body.toString();

		return expressionString.split(identifier, -1).length - 1;
	}

	/**
	 * Converts a unary expression to the SQL equivalent.
	 * For example:
	 * {@code person -> !person.isActive();}
	 * becomes: !isActive
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
			if (operatorMethods.containsKey(memberExpression.getMember())) {
				return Expression.logicalNot(Expression.binary(operatorMethods.get(memberExpression.getMember()), memberExpression.getInstance(), invocationExpression.getArguments().get(0))).accept(this);
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

	private StringBuilder stringStartsWith(Expression string, Expression argument, boolean isNegated) {
		return doStringOperation(string, argument, isNegated, valueBuilder -> valueBuilder.insert(valueBuilder.length() - 1, '%'));
	}

	private StringBuilder stringEndsWith(Expression string, Expression argument, boolean isNegated) {
		return doStringOperation(string, argument, isNegated, valueBuilder -> valueBuilder.insert(1, '%'));
	}

	private StringBuilder stringContains(Expression string, Expression argument, boolean isNegated) {
		return doStringOperation(string, argument, isNegated, valueBuilder -> valueBuilder.insert(1, '%').insert(valueBuilder.length() - 1, '%'));
	}

	private StringBuilder listContains(Expression list, Expression argument, boolean isNegated) {
		List l = (List) arguments.pop().get(((ParameterExpression) list).getIndex()).getValue();
		var joiner = new StringJoiner(", ", "(", ")");
		l.forEach(x -> joiner.add(x.toString()));

		return argument.accept(new SqlVisitor(this.tableName, this.withBackticks, this.body, this.arguments)).append(isNegated ? " NOT" : "").append(" IN ").append(joiner.toString());
	}

	private StringBuilder applySqlFunction(Expression date, String field) {
		return new StringBuilder().append(field).append("(").append(date.accept(new SqlVisitor(this.tableName, this.withBackticks, this.body, this.arguments))).append(')');
	}

	//endregion

	private StringBuilder doStringOperation(Expression member, Expression argument, boolean isNegated, Consumer<StringBuilder> modifier) {
		var valueBuilder = argument.accept(new SqlVisitor(this.tableName, this.withBackticks, this.body, this.arguments));
		modifier.accept(valueBuilder);

		return member.accept(new SqlVisitor(this.tableName, this.withBackticks, this.body, this.arguments)).append(isNegated ? " NOT" : "").append(" LIKE ").append(valueBuilder);
	}

	private String escapeString(String input) {
		return input.replace("\\", "\\\\").replace("'", "\\'");
	}
}