/*******************************************************************************
 * Copyright (c) 2020 IBM Corporation and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Fabrice TIERCELIN - initial API and implementation from AutoRefactor
 *******************************************************************************/
package org.eclipse.jdt.internal.corext.dom;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.core.dom.ASTMatcher;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.AssertStatement;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.BreakStatement;
import org.eclipse.jdt.core.dom.ConditionalExpression;
import org.eclipse.jdt.core.dom.ConstructorInvocation;
import org.eclipse.jdt.core.dom.ContinueStatement;
import org.eclipse.jdt.core.dom.DoStatement;
import org.eclipse.jdt.core.dom.EmptyStatement;
import org.eclipse.jdt.core.dom.EnhancedForStatement;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.ForStatement;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.LabeledStatement;
import org.eclipse.jdt.core.dom.NumberLiteral;
import org.eclipse.jdt.core.dom.ParenthesizedExpression;
import org.eclipse.jdt.core.dom.PostfixExpression;
import org.eclipse.jdt.core.dom.PrefixExpression;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.SuperConstructorInvocation;
import org.eclipse.jdt.core.dom.SwitchStatement;
import org.eclipse.jdt.core.dom.SynchronizedStatement;
import org.eclipse.jdt.core.dom.ThrowStatement;
import org.eclipse.jdt.core.dom.TryStatement;
import org.eclipse.jdt.core.dom.TypeDeclarationStatement;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.eclipse.jdt.core.dom.WhileStatement;

/**
 * Matches two pieces of code on semantic (not on syntax).
 * ASTSemanticMatcher.java matches more cases than ASTMatcher.java:
 * - Inverted commutative operations:
 *   k + 1
 *   1 + k
 *
 * - Mirrored boolean expressions:
 *   k > 1
 *   1 < k
 *
 * - Neutral operands:
 *   i + j
 *   i + j + 0
 *
 * - Pushed down negations:
 *   !(k > 0)
 *   (k <= 0)
 *
 * - Integer identical operations:
 *   i >= 1
 *   i > 0
 *
 * - Identical statements (not expressions):
 *   i++;
 *   ++i;
 *   i += 1;
 *   i = i + 1;
 *
 * - Blocked vs lone statements:
 *   if (isValid) {i++;}
 *   if (isValid) i++;
 *
 * - Parentherized vs unparentherized expression:
 *   if ((isValid)) {i++;}
 *   if (isValid) {i++;}
 *
 * - Inverted if statements or ternary expressions:
 *   if (isValid) k++; else m--;
 *   if (!isValid) m--; else k++;
 *
 * You can also match opposite boolean expressions:
 * ASTSemanticMatcher.matchOpposite()
 */
public class ASTSemanticMatcher extends ASTMatcher {
	/**
	 * Singleton.
	 */
	public static final ASTSemanticMatcher INSTANCE= new ASTSemanticMatcher();

	private static final Map<PrefixExpression.Operator, InfixExpression.Operator> PREFIX_TO_INFIX_OPERATOR= new HashMap<>() {
		private static final long serialVersionUID= -8949107654517355855L;

		{
			put(PrefixExpression.Operator.INCREMENT, InfixExpression.Operator.PLUS);
			put(PrefixExpression.Operator.DECREMENT, InfixExpression.Operator.MINUS);
		}
	};

	private static final Map<PrefixExpression.Operator, Assignment.Operator> PREFIX_TO_ASSIGN_OPERATOR= new HashMap<>() {
		private static final long serialVersionUID= -8949107654517355856L;

		{
			put(PrefixExpression.Operator.INCREMENT, Assignment.Operator.PLUS_ASSIGN);
			put(PrefixExpression.Operator.DECREMENT, Assignment.Operator.MINUS_ASSIGN);
		}
	};

	private static final Map<PostfixExpression.Operator, InfixExpression.Operator> POSTFIX_TO_INFIX_OPERATOR= new HashMap<>() {
		private static final long serialVersionUID= -8949107654517355857L;

		{
			put(PostfixExpression.Operator.INCREMENT, InfixExpression.Operator.PLUS);
			put(PostfixExpression.Operator.DECREMENT, InfixExpression.Operator.MINUS);
		}
	};

	private static final Map<PostfixExpression.Operator, Assignment.Operator> POSTFIX_TO_ASSIGN_OPERATOR= new HashMap<>() {
		private static final long serialVersionUID= -8949107654517355858L;

		{
			put(PostfixExpression.Operator.INCREMENT, Assignment.Operator.PLUS_ASSIGN);
			put(PostfixExpression.Operator.DECREMENT, Assignment.Operator.MINUS_ASSIGN);
		}
	};

	private static final Map<PrefixExpression.Operator, PostfixExpression.Operator> PREFIX_TO_POSTFIX_OPERATOR= new HashMap<>() {
		private static final long serialVersionUID= -8949107654517355859L;

		{
			put(PrefixExpression.Operator.INCREMENT, PostfixExpression.Operator.INCREMENT);
			put(PrefixExpression.Operator.DECREMENT, PostfixExpression.Operator.DECREMENT);
		}
	};

	private static final Map<Assignment.Operator, InfixExpression.Operator> ASSIGN_TO_INFIX_OPERATOR= new HashMap<>() {
		private static final long serialVersionUID= -8949107654517355859L;

		{
			put(Assignment.Operator.PLUS_ASSIGN, InfixExpression.Operator.PLUS);
			put(Assignment.Operator.MINUS_ASSIGN, InfixExpression.Operator.MINUS);
			put(Assignment.Operator.TIMES_ASSIGN, InfixExpression.Operator.TIMES);
			put(Assignment.Operator.DIVIDE_ASSIGN, InfixExpression.Operator.DIVIDE);
			put(Assignment.Operator.BIT_AND_ASSIGN, InfixExpression.Operator.AND);
			put(Assignment.Operator.BIT_OR_ASSIGN, InfixExpression.Operator.OR);
			put(Assignment.Operator.BIT_XOR_ASSIGN, InfixExpression.Operator.XOR);
			put(Assignment.Operator.REMAINDER_ASSIGN, InfixExpression.Operator.REMAINDER);
			put(Assignment.Operator.LEFT_SHIFT_ASSIGN, InfixExpression.Operator.LEFT_SHIFT);
			put(Assignment.Operator.RIGHT_SHIFT_SIGNED_ASSIGN, InfixExpression.Operator.RIGHT_SHIFT_SIGNED);
			put(Assignment.Operator.RIGHT_SHIFT_UNSIGNED_ASSIGN, InfixExpression.Operator.RIGHT_SHIFT_UNSIGNED);
		}
	};

	private static final Map<InfixExpression.Operator, InfixExpression.Operator> INFIX_TO_MIRROR_OPERATOR= new HashMap<>() {
		private static final long serialVersionUID= -8949107654517355857L;

		{
			put(InfixExpression.Operator.EQUALS, InfixExpression.Operator.EQUALS);
			put(InfixExpression.Operator.NOT_EQUALS, InfixExpression.Operator.NOT_EQUALS);
			put(InfixExpression.Operator.CONDITIONAL_AND, InfixExpression.Operator.CONDITIONAL_AND);
			put(InfixExpression.Operator.CONDITIONAL_OR, InfixExpression.Operator.CONDITIONAL_OR);
			put(InfixExpression.Operator.AND, InfixExpression.Operator.AND);
			put(InfixExpression.Operator.OR, InfixExpression.Operator.OR);
			put(InfixExpression.Operator.XOR, InfixExpression.Operator.XOR);
			put(InfixExpression.Operator.PLUS, InfixExpression.Operator.PLUS);
			put(InfixExpression.Operator.TIMES, InfixExpression.Operator.TIMES);

			put(InfixExpression.Operator.GREATER, InfixExpression.Operator.LESS);
			put(InfixExpression.Operator.LESS, InfixExpression.Operator.GREATER);
			put(InfixExpression.Operator.LESS_EQUALS, InfixExpression.Operator.GREATER_EQUALS);
			put(InfixExpression.Operator.GREATER_EQUALS, InfixExpression.Operator.LESS_EQUALS);
		}
	};

	@Override
	public boolean match(final NumberLiteral node, final Object otherObject) {
		Object other= unbracket(otherObject);

		if (super.match(node, other)) {
			return true;
		}

		if (!(other instanceof Expression)) {
			return false;
		}


		Expression expression= (Expression) other;

		return node.resolveTypeBinding() != null
				&& node.resolveTypeBinding().equals(expression.resolveTypeBinding())
				&& node.resolveConstantExpressionValue() != null
				&& node.resolveConstantExpressionValue().equals(expression.resolveConstantExpressionValue());
	}

	@Override
	public boolean match(final InfixExpression node, final Object otherObject) {
		Object other= unbracket(otherObject);

		if (other instanceof NumberLiteral) {
			NumberLiteral numberLiteral= (NumberLiteral) other;

			return match(numberLiteral, node);
		}

		if (other instanceof PrefixExpression) {
			PrefixExpression prefixExpression= (PrefixExpression) other;

			if (ASTNodes.hasOperator(prefixExpression, PrefixExpression.Operator.NOT)) {
				return matchNegative(node, prefixExpression.getOperand());
			}
		}

		if (other instanceof InfixExpression) {
			InfixExpression infixExpression= (InfixExpression) other;

			if (!ASTNodes.hasOperator(infixExpression, InfixExpression.Operator.PLUS)
					|| ASTNodes.hasType(node.getLeftOperand(), short.class.getSimpleName(), int.class.getSimpleName(), long.class.getSimpleName(), float.class.getSimpleName(), double.class.getSimpleName(), Short.class.getCanonicalName(),
							Integer.class.getCanonicalName(), Long.class.getCanonicalName(), Float.class.getCanonicalName(), Double.class.getCanonicalName())
							&& ASTNodes.hasType(node.getRightOperand(), short.class.getSimpleName(), int.class.getSimpleName(), long.class.getSimpleName(), float.class.getSimpleName(), double.class.getSimpleName(), Short.class.getCanonicalName(),
									Integer.class.getCanonicalName(), Long.class.getCanonicalName(), Float.class.getCanonicalName(), Double.class.getCanonicalName())) {
				if (!node.hasExtendedOperands() && !infixExpression.hasExtendedOperands()
						&& node.getOperator().equals(INFIX_TO_MIRROR_OPERATOR.get(infixExpression.getOperator()))
						&& ASTNodes.isPassiveWithoutFallingThrough(node.getLeftOperand())
						&& ASTNodes.isPassiveWithoutFallingThrough(node.getRightOperand())
						&& safeSubtreeMatch(node.getLeftOperand(), infixExpression.getRightOperand())
						&& safeSubtreeMatch(node.getRightOperand(), infixExpression.getLeftOperand())) {
					return true;
				}

				if (node.getOperator().equals(infixExpression.getOperator()) && ASTNodes.hasOperator(infixExpression, InfixExpression.Operator.PLUS, InfixExpression.Operator.TIMES, InfixExpression.Operator.AND,
								InfixExpression.Operator.CONDITIONAL_AND, InfixExpression.Operator.OR,
								InfixExpression.Operator.CONDITIONAL_OR, InfixExpression.Operator.XOR)
						&& isOperandsMatching(node, infixExpression, true)) {
					return true;
				}
			}
		}

		return super.match(node, other);
	}

	@Override
	public boolean match(final ParenthesizedExpression node, final Object otherObject) {
		Object other= unbracket(otherObject);

		return safeSubtreeMatch(node.getExpression(), other);
	}

	private Object unbracket(final Object otherObject) {
		if (otherObject instanceof ParenthesizedExpression) {
			return ((ParenthesizedExpression) otherObject).getExpression();
		}

		return otherObject;
	}

	@Override
	public boolean match(final PrefixExpression node, final Object otherObject) {
		Object other= unbracket(otherObject);

		if (other instanceof NumberLiteral) {
			NumberLiteral numberLiteral= (NumberLiteral) other;

			return match(numberLiteral, node);
		}

		if (!(other instanceof PrefixExpression) && ASTNodes.hasOperator(node, PrefixExpression.Operator.NOT)) {
			return matchNegative(node.getOperand(), other);
		}

		if (node.getParent() instanceof Statement) {
			if (other instanceof Assignment) {
				return match0(node, (Assignment) other);
			}
			if (other instanceof PostfixExpression) {
				return match0(node, (PostfixExpression) other);
			}
		}

		return super.match(node, other);
	}

	@Override
	public boolean match(final PostfixExpression node, final Object otherObject) {
		Object other= unbracket(otherObject);

		if (other instanceof NumberLiteral) {
			NumberLiteral numberLiteral= (NumberLiteral) other;

			return match(numberLiteral, node);
		}

		if (node.getParent() instanceof Statement) {
			if (other instanceof Assignment) {
				return match0(node, (Assignment) other);
			}
			if (other instanceof PrefixExpression) {
				return match0((PrefixExpression) other, node);
			}
		}

		return super.match(node, other);
	}

	@Override
	public boolean match(final Assignment node, final Object otherObject) {
		Object other= unbracket(otherObject);

		if (other instanceof PrefixExpression && ((PrefixExpression) other).getParent() instanceof Statement) {
			return match0((PrefixExpression) other, node);
		}
		if (other instanceof PostfixExpression && ((PostfixExpression) other).getParent() instanceof Statement) {
			return match0((PostfixExpression) other, node);
		}
		if (other instanceof Assignment) {
			return matchAssignmentWithAndWithoutEqual(node, (Assignment) other)
					|| matchAssignmentWithAndWithoutEqual((Assignment) other, node) || super.match(node, other);
		}

		return super.match(node, other);
	}

	private boolean matchAssignmentWithAndWithoutEqual(final Assignment node, final Assignment assignment) {
		if (ASTNodes.hasOperator(node, Assignment.Operator.ASSIGN)
				&& node.getRightHandSide() instanceof InfixExpression) {
			InfixExpression infixExpression= (InfixExpression) node.getRightHandSide();

			if (!infixExpression.hasExtendedOperands()
					&& ASTNodes.hasOperator(assignment, Assignment.Operator.PLUS_ASSIGN, Assignment.Operator.MINUS_ASSIGN,
							Assignment.Operator.TIMES_ASSIGN, Assignment.Operator.DIVIDE_ASSIGN,
							Assignment.Operator.BIT_AND_ASSIGN, Assignment.Operator.BIT_OR_ASSIGN,
							Assignment.Operator.BIT_XOR_ASSIGN, Assignment.Operator.REMAINDER_ASSIGN,
							Assignment.Operator.LEFT_SHIFT_ASSIGN, Assignment.Operator.RIGHT_SHIFT_SIGNED_ASSIGN,
							Assignment.Operator.RIGHT_SHIFT_UNSIGNED_ASSIGN)
					&& ASSIGN_TO_INFIX_OPERATOR.get(assignment.getOperator()).equals(infixExpression.getOperator())) {
				return safeSubtreeMatch(node.getLeftHandSide(), assignment.getLeftHandSide())
						&& safeSubtreeMatch(infixExpression.getLeftOperand(), assignment.getLeftHandSide())
						&& safeSubtreeMatch(infixExpression.getRightOperand(), assignment.getRightHandSide());
			}
		}

		return false;
	}

	private boolean match0(final PrefixExpression prefixExpression, final PostfixExpression postfixExpression) {
		return postfixExpression.getOperator().equals(PREFIX_TO_POSTFIX_OPERATOR.get(prefixExpression.getOperator()))
				&& safeSubtreeMatch(prefixExpression.getOperand(), postfixExpression.getOperand());
	}

	private boolean match0(final PrefixExpression prefixExpression, final Assignment assignment) {
		return match0(assignment, prefixExpression.getOperand(), PREFIX_TO_INFIX_OPERATOR.get(prefixExpression.getOperator()),
				PREFIX_TO_ASSIGN_OPERATOR.get(prefixExpression.getOperator()));
	}

	private boolean match0(final PostfixExpression postfixExpression, final Assignment assignment) {
		return match0(assignment, postfixExpression.getOperand(), POSTFIX_TO_INFIX_OPERATOR.get(postfixExpression.getOperator()),
				POSTFIX_TO_ASSIGN_OPERATOR.get(postfixExpression.getOperator()));
	}

	private boolean match0(final Assignment assignment, final Expression prefixOrPostfixOperand,
			final InfixExpression.Operator infixAssociatedOperator, final Assignment.Operator assignmentAssociatedOperator) {
		if (ASTNodes.hasOperator(assignment, Assignment.Operator.ASSIGN)
				&& assignment.getRightHandSide() instanceof InfixExpression) {
			InfixExpression infixExpression= (InfixExpression) assignment.getRightHandSide();

			if (!infixExpression.hasExtendedOperands() && infixAssociatedOperator.equals(infixExpression.getOperator())) {
				if (isOneLiteral(infixExpression.getRightOperand())) {
					return safeSubtreeMatch(prefixOrPostfixOperand, assignment.getLeftHandSide())
							&& safeSubtreeMatch(prefixOrPostfixOperand, infixExpression.getLeftOperand());
				}

				if (ASTNodes.hasOperator(infixExpression, InfixExpression.Operator.PLUS) && isOneLiteral(infixExpression.getLeftOperand())) {
					return safeSubtreeMatch(prefixOrPostfixOperand, assignment.getLeftHandSide())
							&& safeSubtreeMatch(prefixOrPostfixOperand, infixExpression.getRightOperand());
				}
			}
		} else if (ASTNodes.hasOperator(assignment, Assignment.Operator.PLUS_ASSIGN, Assignment.Operator.MINUS_ASSIGN) && assignmentAssociatedOperator.equals(assignment.getOperator())
				&& isOneLiteral(assignment)) {
			return safeSubtreeMatch(prefixOrPostfixOperand, assignment.getLeftHandSide());
		}

		return false;
	}

	private boolean isOneLiteral(final Expression operand) {
		return Long.valueOf(1L).equals(ASTNodes.getIntegerLiteral(operand));
	}

	@Override
	public boolean match(final Block node, final Object otherObject) {
		Object other= unbracket(otherObject);

		if (other instanceof AssertStatement || other instanceof BreakStatement
				|| other instanceof ConstructorInvocation || other instanceof ContinueStatement
				|| other instanceof DoStatement || other instanceof EmptyStatement
				|| other instanceof EnhancedForStatement || other instanceof ExpressionStatement
				|| other instanceof ForStatement || other instanceof IfStatement || other instanceof LabeledStatement
				|| other instanceof ReturnStatement || other instanceof SuperConstructorInvocation
				|| other instanceof SwitchStatement || other instanceof SynchronizedStatement
				|| other instanceof ThrowStatement || other instanceof TryStatement
				|| other instanceof TypeDeclarationStatement || other instanceof VariableDeclarationStatement
				|| other instanceof WhileStatement) {
			return match0(node, (Statement) other);
		}

		return super.match(node, other);
	}

	@Override
	public boolean match(final AssertStatement node, final Object otherObject) {
		Object other= unbracket(otherObject);

		if (other instanceof Block) {
			return match0((Block) other, (Statement) node);
		}

		return super.match(node, other);
	}

	@Override
	public boolean match(final BreakStatement node, final Object otherObject) {
		Object other= unbracket(otherObject);

		if (other instanceof Block) {
			return match0((Block) other, (Statement) node);
		}

		return super.match(node, other);
	}

	@Override
	public boolean match(final ConstructorInvocation node, final Object otherObject) {
		Object other= unbracket(otherObject);

		if (other instanceof Block) {
			return match0((Block) other, (Statement) node);
		}

		return super.match(node, other);
	}

	@Override
	public boolean match(final ContinueStatement node, final Object otherObject) {
		Object other= unbracket(otherObject);

		if (other instanceof Block) {
			return match0((Block) other, (Statement) node);
		}

		return super.match(node, other);
	}

	@Override
	public boolean match(final DoStatement node, final Object otherObject) {
		Object other= unbracket(otherObject);

		if (other instanceof Block) {
			return match0((Block) other, (Statement) node);
		}

		return super.match(node, other);
	}

	@Override
	public boolean match(final EmptyStatement node, final Object otherObject) {
		Object other= unbracket(otherObject);

		if (other instanceof Block) {
			return match0((Block) other, (Statement) node);
		}

		return super.match(node, other);
	}

	@Override
	public boolean match(final EnhancedForStatement node, final Object otherObject) {
		Object other= unbracket(otherObject);

		if (other instanceof Block) {
			return match0((Block) other, (Statement) node);
		}

		return super.match(node, other);
	}

	@Override
	public boolean match(final ExpressionStatement node, final Object otherObject) {
		Object other= unbracket(otherObject);

		if (other instanceof Block) {
			return match0((Block) other, (Statement) node);
		}

		return super.match(node, other);
	}

	@Override
	public boolean match(final ConditionalExpression node, final Object otherObject) {
		Object other= unbracket(otherObject);

		if (super.match(node, other)) {
			return true;
		}

		if (other instanceof ConditionalExpression) {
			ConditionalExpression ce= (ConditionalExpression) other;

			if (node.getElseExpression() != null && ce.getElseExpression() != null) {
				return matchNegative(node.getExpression(), ce.getExpression())
						&& safeSubtreeMatch(node.getThenExpression(), ce.getElseExpression())
						&& safeSubtreeMatch(node.getElseExpression(), ce.getThenExpression());
			}
		}

		return false;
	}

	@Override
	public boolean match(final ForStatement node, final Object otherObject) {
		Object other= unbracket(otherObject);

		if (other instanceof Block) {
			return match0((Block) other, (Statement) node);
		}

		return super.match(node, other);
	}

	@Override
	public boolean match(final IfStatement node, final Object otherObject) {
		Object other= unbracket(otherObject);

		if (other instanceof Block) {
			return match0((Block) other, (Statement) node);
		}

		if (super.match(node, other)) {
			return true;
		}

		if (other instanceof IfStatement) {
			IfStatement is= (IfStatement) other;

			if (node.getElseStatement() != null && is.getElseStatement() != null) {
				return matchNegative(node.getExpression(), is.getExpression())
						&& safeSubtreeMatch(node.getThenStatement(), is.getElseStatement())
						&& safeSubtreeMatch(node.getElseStatement(), is.getThenStatement());
			}
		}

		return false;
	}

	@Override
	public boolean match(final LabeledStatement node, final Object otherObject) {
		Object other= unbracket(otherObject);

		if (other instanceof Block) {
			return match0((Block) other, (Statement) node);
		}

		return super.match(node, other);
	}

	@Override
	public boolean match(final ReturnStatement node, final Object otherObject) {
		Object other= unbracket(otherObject);

		if (other instanceof Block) {
			return match0((Block) other, (Statement) node);
		}

		return super.match(node, other);
	}

	@Override
	public boolean match(final SuperConstructorInvocation node, final Object otherObject) {
		Object other= unbracket(otherObject);

		if (other instanceof Block) {
			return match0((Block) other, (Statement) node);
		}

		return super.match(node, other);
	}

	@Override
	public boolean match(final SwitchStatement node, final Object otherObject) {
		Object other= unbracket(otherObject);

		if (other instanceof Block) {
			return match0((Block) other, (Statement) node);
		}

		return super.match(node, other);
	}

	@Override
	public boolean match(final SynchronizedStatement node, final Object otherObject) {
		Object other= unbracket(otherObject);

		if (other instanceof Block) {
			return match0((Block) other, (Statement) node);
		}

		return super.match(node, other);
	}

	@Override
	public boolean match(final ThrowStatement node, final Object otherObject) {
		Object other= unbracket(otherObject);

		if (other instanceof Block) {
			return match0((Block) other, (Statement) node);
		}

		return super.match(node, other);
	}

	@Override
	public boolean match(final TryStatement node, final Object otherObject) {
		Object other= unbracket(otherObject);

		if (other instanceof Block) {
			return match0((Block) other, (Statement) node);
		}

		return super.match(node, other);
	}

	@Override
	public boolean match(final TypeDeclarationStatement node, final Object otherObject) {
		Object other= unbracket(otherObject);

		if (other instanceof Block) {
			return match0((Block) other, (Statement) node);
		}

		return super.match(node, other);
	}

	@Override
	public boolean match(final VariableDeclarationStatement node, final Object otherObject) {
		Object other= unbracket(otherObject);

		if (other instanceof Block) {
			return match0((Block) other, (Statement) node);
		}

		return super.match(node, other);
	}

	@Override
	public boolean match(final WhileStatement node, final Object otherObject) {
		Object other= unbracket(otherObject);

		if (other instanceof Block) {
			return match0((Block) other, (Statement) node);
		}

		return super.match(node, other);
	}

	private boolean match0(final Block node, final Statement other) {
		if ((node.getParent() instanceof IfStatement || node.getParent() instanceof ForStatement
				|| node.getParent() instanceof EnhancedForStatement || node.getParent() instanceof WhileStatement
				|| node.getParent() instanceof DoStatement) && node.statements().size() == 1) {
			return safeSubtreeMatch(node.statements().get(0), other) || super.match(node, other);
		}

		return super.match(node, other);
	}

	/**
	 * Match the negative boolean expressions.
	 *
	 * @param node        Node to check
	 * @param otherObject Node to compare
	 * @return True if one boolean expression is the negative of the other boolean expression.
	 */
	public boolean matchNegative(final ASTNode node, final Object otherObject) {
		Object other= unbracket(otherObject);

		if (node instanceof ParenthesizedExpression) {
			return matchNegative(((ParenthesizedExpression) node).getExpression(), other);
		}

		if (node instanceof PrefixExpression) {
			PrefixExpression prefixExpression= (PrefixExpression) node;

			if (ASTNodes.hasOperator(prefixExpression, PrefixExpression.Operator.NOT)) {
				if (other instanceof PrefixExpression
						&& ASTNodes.hasOperator((PrefixExpression) other, PrefixExpression.Operator.NOT)) {
					return matchNegative(prefixExpression.getOperand(), ((PrefixExpression) other).getOperand());
				}

				return safeSubtreeMatch(prefixExpression.getOperand(), other);
			}
		} else if (other instanceof PrefixExpression
				&& ASTNodes.hasOperator((PrefixExpression) other, PrefixExpression.Operator.NOT)) {
			return safeSubtreeMatch(node, ((PrefixExpression) other).getOperand());
		}

		if (other instanceof ASTNode) {
			// Matches two negative boolean values
			Boolean value= ASTNodes.getBooleanLiteral(node);
			Boolean otherValue= ASTNodes.getBooleanLiteral((ASTNode) other);

			if (value != null && otherValue != null) {
				return value ^ otherValue;
			}
		}

		// Now, only arithmetic and logical operations are handled
		if (!(node instanceof InfixExpression) || !(other instanceof InfixExpression)) {
			return false;
		}

		InfixExpression infixExpression1= (InfixExpression) node;
		InfixExpression infixExpression2= (InfixExpression) other;

		Expression leftOperand1= infixExpression1.getLeftOperand();
		Expression rightOperand1= infixExpression1.getRightOperand();
		Expression leftOperand2= infixExpression2.getLeftOperand();
		Expression rightOperand2= infixExpression2.getRightOperand();

		if (infixExpression1.getOperator().equals(infixExpression2.getOperator())) {
			// Matches two comparisons with same operator and exactly one negative operand:
			//   isValid == true
			//   isValid == false
			//
			//   isValid == true
			//   false == isValid
			//
			//   isValid != true
			//   isValid != false
			//
			//   isValid == (i > 0)
			//   isValid == (i <= 0)
			if (infixExpression1.hasExtendedOperands()
					|| infixExpression2.hasExtendedOperands()) {
				return false;
			}

			if (ASTNodes.hasOperator(infixExpression1, InfixExpression.Operator.EQUALS, InfixExpression.Operator.NOT_EQUALS)
					&& ((!ASTNodes.hasType(leftOperand1, boolean.class.getCanonicalName()) && !ASTNodes.hasType(rightOperand1, boolean.class.getCanonicalName()))
							|| (!ASTNodes.hasType(leftOperand2, boolean.class.getCanonicalName()) && !ASTNodes.hasType(rightOperand2, boolean.class.getCanonicalName())))) {
				return false;
			}

			if (ASTNodes.hasOperator(infixExpression1, InfixExpression.Operator.EQUALS, InfixExpression.Operator.NOT_EQUALS, InfixExpression.Operator.XOR)) {
				return matchOneNegativeOther(leftOperand1, leftOperand2, rightOperand2, rightOperand1)
						|| matchOneNegativeOther(rightOperand2, rightOperand1, leftOperand1, leftOperand2)
						|| ASTNodes.isPassiveWithoutFallingThrough(leftOperand1) && ASTNodes.isPassiveWithoutFallingThrough(rightOperand1) && ASTNodes.isPassiveWithoutFallingThrough(leftOperand2)
						&& ASTNodes.isPassiveWithoutFallingThrough(rightOperand2)
						&& (matchOneNegativeOther(leftOperand1, leftOperand2, rightOperand2, rightOperand1)
								|| matchOneNegativeOther(rightOperand2, rightOperand1, leftOperand1,
										leftOperand2));
			}

			return false;
		}

		InfixExpression.Operator negatedOperator= ASTNodes.negatedInfixOperator(infixExpression1.getOperator());

		if (infixExpression2.getOperator().equals(negatedOperator)) {
			if (ASTNodes.hasOperator(infixExpression1, InfixExpression.Operator.CONDITIONAL_AND, InfixExpression.Operator.CONDITIONAL_OR,
					InfixExpression.Operator.AND, InfixExpression.Operator.OR)) {
				// De Morgan's laws (matches negative logical operator with negative operands):
				//   a && b && c
				//   !a || !b || !c
				//
				//   !a || b || !c
				//   c && !b && a
				return isOperandsMatching(infixExpression1, infixExpression2, false);
			}

			// Now, only operations with two operands are handled
			if (infixExpression1.hasExtendedOperands() || infixExpression2.hasExtendedOperands()) {
				return false;
			}

			if (ASTNodes.hasOperator(infixExpression1, InfixExpression.Operator.EQUALS, InfixExpression.Operator.NOT_EQUALS)) {
				// Matches two comparisons with negative operator and two equal operands:
				//   isValid == true
				//   isValid != true
				//
				//   isValid == true
				//   true != isValid
				//
				// ...or matches two comparisons with negative operator and two negative operands:
				//   isValid == true
				//   !isValid != false
				//
				//   isValid == false
				//   true != !isValid
				return isOperandsMatching(infixExpression1, infixExpression2, true)
						|| isOperandsMatching(infixExpression1, infixExpression2, false);
			}

			if (ASTNodes.hasOperator(infixExpression1, InfixExpression.Operator.GREATER, InfixExpression.Operator.GREATER_EQUALS,
					InfixExpression.Operator.LESS, InfixExpression.Operator.LESS_EQUALS)
					&& ASTNodes.isPassiveWithoutFallingThrough(leftOperand1)
					&& ASTNodes.isPassiveWithoutFallingThrough(rightOperand1)
					&& ASTNodes.isPassiveWithoutFallingThrough(leftOperand2)
					&& ASTNodes.isPassiveWithoutFallingThrough(rightOperand2)) {
				// Matches two inequalities with negative operator and unmoved operands:
				//   i < j
				//   i => j
				//
				//   k <= 123
				//   k > 123
				return safeSubtreeMatch(leftOperand1, leftOperand2) && safeSubtreeMatch(rightOperand1, rightOperand2);
			}

			return false;
		}

		if ((ASTNodes.hasOperator(infixExpression1, InfixExpression.Operator.GREATER) && ASTNodes.hasOperator(infixExpression2, InfixExpression.Operator.GREATER_EQUALS) || ASTNodes.hasOperator(infixExpression1, InfixExpression.Operator.GREATER_EQUALS) && ASTNodes.hasOperator(infixExpression2, InfixExpression.Operator.GREATER) || ASTNodes.hasOperator(infixExpression1, InfixExpression.Operator.LESS) && ASTNodes.hasOperator(infixExpression2, InfixExpression.Operator.LESS_EQUALS) || ASTNodes.hasOperator(infixExpression1, InfixExpression.Operator.LESS_EQUALS) && ASTNodes.hasOperator(infixExpression2, InfixExpression.Operator.LESS))) {
			// Matches two inequalities with negative and mirrored operator and exchanged operands:
			//   j > i
			//   i => j
			//
			//   k <= 123
			//   123 < k
			return !infixExpression1.hasExtendedOperands()
					&& !infixExpression2.hasExtendedOperands()
					&& ASTNodes.isPassiveWithoutFallingThrough(leftOperand1)
					&& ASTNodes.isPassiveWithoutFallingThrough(rightOperand1)
					&& ASTNodes.isPassiveWithoutFallingThrough(leftOperand2)
					&& ASTNodes.isPassiveWithoutFallingThrough(rightOperand2)
					&& safeSubtreeMatch(leftOperand1, rightOperand2)
					&& safeSubtreeMatch(rightOperand1, leftOperand2);
		}

		return false;
	}

	private boolean matchOneNegativeOther(final Expression equalOperand1, final Expression equalOperand2,
			final Expression negativeOperand1, final Expression negativeOperand2) {
		return safeSubtreeMatch(equalOperand1, equalOperand2) && matchNegative(negativeOperand1, negativeOperand2);
	}

	private boolean isOperandsMatching(final InfixExpression infixExpression1, final InfixExpression infixExpression2, final boolean equal) {
		List<Expression> operands1 = getConsistentOperands(infixExpression1);
		List<Expression> operands2 = getConsistentOperands(infixExpression2);

		if (operands1.size() != operands2.size()) {
			return false;
		}

		boolean isMatching= true;
		Iterator<Expression> iterator1= operands1.iterator();
		Iterator<Expression> iterator2= operands2.iterator();

		while (iterator1.hasNext() && iterator2.hasNext()) {
			Expression expression= iterator1.next();
			Expression otherExpression= iterator2.next();

			if (equal ? !safeSubtreeMatch(expression, otherExpression) : !matchNegative(expression, otherExpression)) {
				isMatching= false;
				break;
			}
		}

		if (isMatching) {
			return true;
		}

		for (Expression expression : operands1) {
			if (!ASTNodes.isPassiveWithoutFallingThrough(expression)) {
				return false;
			}
		}

		for (Expression expression : operands2) {
			if (!ASTNodes.isPassiveWithoutFallingThrough(expression)) {
				return false;
			}
		}

		for (Iterator<Expression> iterator3= operands1.iterator(); iterator3.hasNext();) {
			Expression expression= iterator3.next();

			for (Iterator<Expression> iterator4= operands2.iterator(); iterator4.hasNext();) {
				Expression otherExpression= iterator4.next();

				if (equal ? safeSubtreeMatch(expression, otherExpression) : matchNegative(expression, otherExpression)) {
					iterator3.remove();
					iterator4.remove();
					break;
				}
			}
		}

		return operands1.isEmpty() && operands2.isEmpty();
	}

	private List<Expression> getConsistentOperands(final InfixExpression infixExpression) {
		List<Expression> operands= ASTNodes.allOperands(infixExpression);

		for (Iterator<Expression> iterator= operands.iterator(); iterator.hasNext() && operands.size() > 1;) {
			Expression operand= iterator.next();

			Long numberLiteral= ASTNodes.getIntegerLiteral(operand);
			Boolean booleanValue= ASTNodes.getBooleanLiteral(operand);

			if (ASTNodes.hasOperator(infixExpression, InfixExpression.Operator.CONDITIONAL_AND)) {
				if (Boolean.TRUE.equals(booleanValue)) {
					iterator.remove();
				}
			} else if (ASTNodes.hasOperator(infixExpression, InfixExpression.Operator.CONDITIONAL_OR)) {
				if (Boolean.FALSE.equals(booleanValue)) {
					iterator.remove();
				}
			} else if (ASTNodes.hasOperator(infixExpression, InfixExpression.Operator.PLUS)) {
				if (Long.valueOf(0L).equals(numberLiteral)) {
					iterator.remove();
				}
			} else if (ASTNodes.hasOperator(infixExpression, InfixExpression.Operator.TIMES) && Long.valueOf(1L).equals(numberLiteral)) {
				iterator.remove();
			}
		}

		return operands;
	}
}
