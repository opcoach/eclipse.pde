/*******************************************************************************
 * Copyright (c) 2005, 2014 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.pde.ui.tests.imports;

import junit.framework.Test;
import junit.framework.TestSuite;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.Platform;
import org.eclipse.jdt.core.*;
import org.eclipse.pde.internal.core.BinaryRepositoryProvider;
import org.eclipse.pde.internal.core.natures.PDE;
import org.eclipse.pde.internal.ui.wizards.imports.PluginImportOperation;
import org.eclipse.team.core.RepositoryProvider;

public class ImportWithLinksTestCase extends BaseImportTestCase {

	private static int TYPE = PluginImportOperation.IMPORT_BINARY_WITH_LINKS;

	public static Test suite() {
		return new TestSuite(ImportWithLinksTestCase.class);
	}

	protected int getType() {
		return TYPE;
	}

	public void testImportAnt() {
		// Note: Ant is exempt from importing as source
		doSingleImport("org.apache.ant", true);
	}

	protected void verifyProject(String projectName, boolean isJava) {
		try {
			IProject project = verifyProject(projectName);
			RepositoryProvider provider = RepositoryProvider.getProvider(project);
			// When self hosting the tests, import tests may fail if you have the imported project in the host
			if (!Platform.inDevelopmentMode()) {
				assertTrue(provider instanceof BinaryRepositoryProvider);
			}
			assertTrue(project.hasNature(PDE.PLUGIN_NATURE));
			assertEquals(isJava, project.hasNature(JavaCore.NATURE_ID));
			if (isJava) {
				IJavaProject jProject = JavaCore.create(project);
				assertTrue(checkSourceAttached(jProject));
				assertTrue(checkLibraryEntry(jProject));
			}
		} catch (CoreException e) {
			fail(e.getMessage());
		}
	}

	private boolean checkLibraryEntry(IJavaProject jProject) throws JavaModelException {
		// When self hosting the tests, import tests may fail if you have the imported project in the host
		if (Platform.inDevelopmentMode()) {
			return true;
		}

		IClasspathEntry[] entries = jProject.getRawClasspath();
		for (int i = 0; i < entries.length; i++) {
			if (entries[i].getEntryKind() == IClasspathEntry.CPE_LIBRARY)
				return true;
		}
		return false;
	}

}
