/*******************************************************************************
 * Copyright (c) 2019 IBM Corporation and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Red Hat Inc. - copied from AbstractMultiFix and modified for jdt.core.manipulation
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.fix;

import java.util.ArrayList;
import java.util.Map;

import org.eclipse.core.runtime.CoreException;

import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.manipulation.CleanUpContextCore;
import org.eclipse.jdt.core.manipulation.ICleanUpFixCore;

import org.eclipse.jdt.internal.ui.text.correction.IProblemLocationCore;
import org.eclipse.jdt.internal.ui.text.correction.ProblemLocationCore;


public abstract class AbstractMultiFixCore extends AbstractCleanUpCore implements IMultiFixCore {

	protected AbstractMultiFixCore() {
	}

	protected AbstractMultiFixCore(Map<String, String> settings) {
		super(settings);
	}

	@Override
	public final ICleanUpFixCore createFixCore(CleanUpContextCore context) throws CoreException {
		CompilationUnit unit= context.getAST();
		if (unit == null)
			return null;

		if (context instanceof MultiFixContext) {
			return createFix(unit, ((MultiFixContext)context).getProblemLocations());
		} else {
			return createFix(unit);
		}
	}

	protected abstract ICleanUpFixCore createFix(CompilationUnit unit) throws CoreException;

	protected abstract ICleanUpFixCore createFix(CompilationUnit unit, IProblemLocationCore[] problems) throws CoreException;

	@Override
	public int computeNumberOfFixes(CompilationUnit compilationUnit) {
		return -1;
	}

	/**
	 * Utility method to: count number of problems in <code>problems</code> with <code>problemId</code>
	 * @param problems the set of problems
	 * @param problemId the problem id to look for
	 * @return number of problems with problem id
	 */
	protected static int getNumberOfProblems(IProblem[] problems, int problemId) {
		int result= 0;
		for (IProblem problem : problems) {
			if (problem.getID() == problemId)
				result++;
		}
		return result;
	}

	/**
	 * Convert set of IProblems to IProblemLocationCore
	 * @param problems the problems to convert
	 * @return the converted set
	 */
	protected static IProblemLocationCore[] convertProblems(IProblem[] problems) {
		IProblemLocationCore[] result= new IProblemLocationCore[problems.length];

		for (int i= 0; i < problems.length; i++) {
			result[i]= new ProblemLocationCore(problems[i]);
		}

		return result;
	}

	/**
	 * Returns unique problem locations. All locations in result
	 * have an id element <code>problemIds</code>.
	 *
	 * @param problems the problems to filter
	 * @param problemIds the ids of the resulting problem locations
	 * @return problem locations
	 */
	protected static IProblemLocationCore[] filter(IProblemLocationCore[] problems, int[] problemIds) {
		ArrayList<IProblemLocationCore> result= new ArrayList<>();

		for (IProblemLocationCore problem : problems) {
			if (contains(problemIds, problem.getProblemId()) && !contains(result, problem)) {
				result.add(problem);
			}
		}

		return result.toArray(new IProblemLocationCore[result.size()]);
	}

	private static boolean contains(ArrayList<IProblemLocationCore> problems, IProblemLocationCore problem) {
		for (IProblemLocationCore existing : problems) {
			if (existing.getProblemId() == problem.getProblemId() && existing.getOffset() == problem.getOffset() && existing.getLength() == problem.getLength()) {
				return true;
			}
		}

		return false;
	}

	private static boolean contains(int[] ids, int id) {
		for (int id2 : ids) {
			if (id2 == id)
				return true;
		}
		return false;
	}
}
