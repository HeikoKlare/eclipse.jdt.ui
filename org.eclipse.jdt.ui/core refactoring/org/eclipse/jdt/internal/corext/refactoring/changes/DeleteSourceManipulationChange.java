/*******************************************************************************
 * Copyright (c) 2000, 2003 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.corext.refactoring.changes;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.SubProgressMonitor;

import org.eclipse.core.filebuffers.ITextFileBuffer;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.ISourceManipulation;
import org.eclipse.jdt.core.JavaCore;

import org.eclipse.jdt.internal.corext.Assert;
import org.eclipse.jdt.internal.corext.refactoring.RefactoringCoreMessages;
import org.eclipse.jdt.internal.corext.refactoring.util.JavaElementUtil;
import org.eclipse.jdt.internal.corext.refactoring.util.RefactoringFileBuffers;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;

public class DeleteSourceManipulationChange extends AbstractDeleteChange {

	private String fHandle;
	
	public DeleteSourceManipulationChange(ISourceManipulation sm){
		Assert.isNotNull(sm);
		fHandle= getJavaElement(sm).getHandleIdentifier();
	}

	/*
	 * @see IChange#getName()
	 */
	public String getName() {
		return RefactoringCoreMessages.getFormattedString("DeleteSourceManipulationChange.0", getElementName()); //$NON-NLS-1$
	}
	
	public RefactoringStatus isValid(IProgressMonitor pm) throws CoreException {
		ISourceManipulation element= getSourceModification();
		if (element instanceof ICompilationUnit) {
			return super.isValid(pm, false, false);
		} else {
			return super.isValid(pm, false, true);
		}
	}

	private String getElementName() {
		IJavaElement javaElement= getJavaElement(getSourceModification());
		if (JavaElementUtil.isDefaultPackage(javaElement))
			return RefactoringCoreMessages.getString("DeleteSourceManipulationChange.1"); //$NON-NLS-1$
		return javaElement.getElementName();
	}

	/*
	 * @see IChange#getModifiedLanguageElement()
	 */
	public Object getModifiedElement() {
		return JavaCore.create(fHandle);
	}
	
	/*
	 * @see DeleteChange#doDelete(IProgressMonitor)
	 */
	protected void doDelete(IProgressMonitor pm) throws CoreException {
		ISourceManipulation element= getSourceModification();
		// we have to save dirty compilation units before deleting them. Otherwise
		// we will end up showing ghost compilation units in the package explorer
		// since the primary working copy still exists.
		if (element instanceof ICompilationUnit) {
			pm.beginTask("", 2); //$NON-NLS-1$
			ICompilationUnit unit= (ICompilationUnit)element;
			ITextFileBuffer buffer= RefactoringFileBuffers.getTextFileBuffer(unit);
			int deleteTicks= 1;
			if (buffer != null && buffer.isDirty() &&  buffer.isStateValidated() && buffer.isSynchronized()) {
				buffer.commit(new SubProgressMonitor(pm, 1), false);
			} else {
				deleteTicks= 2;
			}
			element.delete(false, new SubProgressMonitor(pm, deleteTicks));
		} else {
			element.delete(false, pm);
		}
	}
		
	private ISourceManipulation getSourceModification() {
		return (ISourceManipulation)getModifiedElement();
	}

	private static IJavaElement getJavaElement(ISourceManipulation sm) {
		//all known ISourceManipulations are IJavaElements
		return (IJavaElement)sm;
	}
}

