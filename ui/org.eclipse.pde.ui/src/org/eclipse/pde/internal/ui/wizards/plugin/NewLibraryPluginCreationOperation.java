/*******************************************************************************
 * Copyright (c) 2000, 2007 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Bartosz Michalik <bartosz.michalik@gmail.com> - bug 109440
 *******************************************************************************/
package org.eclipse.pde.internal.ui.wizards.plugin;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Stack;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.zip.ZipFile;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubProgressMonitor;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.osgi.service.resolver.BundleDescription;
import org.eclipse.osgi.util.ManifestElement;
import org.eclipse.osgi.util.NLS;
import org.eclipse.pde.core.build.IBuildEntry;
import org.eclipse.pde.core.build.IBuildModelFactory;
import org.eclipse.pde.core.plugin.IPluginBase;
import org.eclipse.pde.core.plugin.IPluginLibrary;
import org.eclipse.pde.core.plugin.IPluginModelBase;
import org.eclipse.pde.core.plugin.ISharedPluginModel;
import org.eclipse.pde.core.plugin.PluginRegistry;
import org.eclipse.pde.internal.core.ICoreConstants;
import org.eclipse.pde.internal.core.build.WorkspaceBuildModel;
import org.eclipse.pde.internal.core.bundle.BundlePluginBase;
import org.eclipse.pde.internal.core.converter.PluginConverter;
import org.eclipse.pde.internal.core.ibundle.IBundle;
import org.eclipse.pde.internal.core.ibundle.IBundlePluginModelBase;
import org.eclipse.pde.internal.core.natures.PDE;
import org.eclipse.pde.internal.core.plugin.WorkspacePluginModelBase;
import org.eclipse.pde.internal.ui.IPDEUIConstants;
import org.eclipse.pde.internal.ui.PDEPlugin;
import org.eclipse.pde.internal.ui.PDEUIMessages;
import org.eclipse.pde.internal.ui.search.dependencies.AddNewBinaryDependenciesOperation;
import org.eclipse.pde.internal.ui.wizards.IProjectProvider;
import org.eclipse.pde.ui.IFieldData;
import org.eclipse.pde.ui.IPluginContentWizard;
import org.eclipse.ui.dialogs.IOverwriteQuery;
import org.eclipse.ui.wizards.datatransfer.ImportOperation;
import org.eclipse.ui.wizards.datatransfer.ZipFileStructureProvider;
import org.osgi.framework.BundleException;
import org.osgi.framework.Constants;

public class NewLibraryPluginCreationOperation extends
		NewProjectCreationOperation {

	private LibraryPluginFieldData fData;

	public NewLibraryPluginCreationOperation(LibraryPluginFieldData data,
			IProjectProvider provider, IPluginContentWizard contentWizard) {
		super(data, provider, contentWizard);
		fData = data;
	}

	private void addJar(File jarFile, IProject project, IProgressMonitor monitor)
			throws CoreException {
		String jarName = jarFile.getName();
		IFile file = project.getFile(jarName);
		monitor.subTask(NLS.bind(
				PDEUIMessages.NewProjectCreationOperation_copyingJar, jarName));
		InputStream in = null;
		try {
			in = new FileInputStream(jarFile);
			file.create(in, true, monitor);
		} catch (FileNotFoundException fnfe) {
		} finally {
			if (in != null) {
				try {
					in.close();
				} catch (IOException ioe) {
				}
			}
		}
	}

	private void adjustExportRoot(IProject project, IBundle bundle)
			throws CoreException {
		IResource[] resources = project.members(false);
		for (int j = 0; j < resources.length; j++) {
			if (resources[j] instanceof IFile) {
				if (".project".equals(resources[j].getName()) //$NON-NLS-1$
						|| ".classpath".equals(resources[j] //$NON-NLS-1$
								.getName())
						|| "plugin.xml".equals(resources[j] //$NON-NLS-1$
								.getName())
						|| "build.properties".equals(resources[j] //$NON-NLS-1$
								.getName())) {
					continue;
				}
				// resource at the root, export root
				return;
			}
		}
		removeExportRoot(bundle);
	}

	protected void adjustManifests(IProgressMonitor monitor, IProject project,
			IPluginBase base) throws CoreException {
		super.adjustManifests(monitor, project, base);
		int units = fData.doFindDependencies() ? 4 : 2;
		units += fData.isUpdateReferences() ? 1 : 0;
		monitor.beginTask(new String(), units);
		IBundle bundle = (base instanceof BundlePluginBase) ? ((BundlePluginBase) base)
				.getBundle()
				: null;
		if (bundle != null) {
			adjustExportRoot(project, bundle);
			monitor.worked(1);
			addExportedPackages(project, bundle);
			monitor.worked(1);
			if (fData.doFindDependencies()) {
				addDependencies(project, base.getModel(),
						new SubProgressMonitor(monitor, 2));
			}
			if (fData.isUpdateReferences()) {
				updateReferences(monitor, project);
				monitor.worked(1);
			}
		}
		monitor.done();
	}

	protected void updateReferences(IProgressMonitor monitor, IProject project)
			throws JavaModelException {
		IJavaProject currentProject = JavaCore.create(project);
		IPluginModelBase[] pluginstoUpdate = fData.getPluginsToUpdate();
		for (int i = 0; i < pluginstoUpdate.length; ++i) {
			IProject proj = pluginstoUpdate[i].getUnderlyingResource()
					.getProject();
			if (currentProject.getProject().equals(proj))
				continue;
			IJavaProject javaProject = JavaCore.create(proj);
			IClasspathEntry[] cp = javaProject.getRawClasspath();
			IClasspathEntry[] updated = getUpdatedClasspath(cp, currentProject);
			if (updated != null) {
				javaProject.setRawClasspath(updated, monitor);
			}
			try {
				updateRequiredPlugins(javaProject, monitor, pluginstoUpdate[i]);
			} catch (CoreException e) {
				PDEPlugin.log(e);
			}
		}
	}

	private static void updateRequiredPlugins(IJavaProject javaProject,
			IProgressMonitor monitor, IPluginModelBase model) throws CoreException {
		IClasspathEntry[] entries = javaProject.getRawClasspath();
		List classpath = new ArrayList();
		List requiredProjects = new ArrayList();
		for (int i = 0; i < entries.length; i++) {
			if (isPluginProjectEntry(entries[i])) {
				requiredProjects.add(entries[i]);
			} else {
				classpath.add(entries[i]);
			}
		}
		if (requiredProjects.size() <= 0)
			return;
		IFile file = javaProject.getProject().getFile("META-INF/MANIFEST.MF");
		try {
			// TODO format manifest
			Manifest manifest = new Manifest(file.getContents());
			String value = manifest.getMainAttributes().getValue(
					Constants.REQUIRE_BUNDLE);
			StringBuffer sb = value != null ? new StringBuffer(value)
					: new StringBuffer();
			if (sb.length() > 0)
				sb.append(",");
			for (int i = 0; i < requiredProjects.size(); i++) {
				IClasspathEntry entry = (IClasspathEntry) requiredProjects
						.get(i);
				if (i > 0)
					sb.append(",");
				sb.append(entry.getPath().segment(0));
				if (entry.isExported())
					sb.append(";visibility:=reexport"); // TODO is there a
														// constant?
			}
			manifest.getMainAttributes().putValue(Constants.REQUIRE_BUNDLE,
					sb.toString());
			ByteArrayOutputStream content = new ByteArrayOutputStream();
			manifest.write(content);
			file.setContents(new ByteArrayInputStream(content.toByteArray()),
					true, false, monitor);
			// now update .classpath
			javaProject.setRawClasspath((IClasspathEntry[]) classpath
					.toArray(new IClasspathEntry[classpath.size()]), monitor);
//			ClasspathComputer.setClasspath(javaProject.getProject(), model);
		} catch (IOException e) {
		} catch (CoreException e) {
		}
	}

	private static boolean isPluginProjectEntry(IClasspathEntry entry) {
		if (IClasspathEntry.CPE_PROJECT != entry.getEntryKind())
			return false;
		IWorkspaceRoot workspaceRoot = ResourcesPlugin.getWorkspace().getRoot();
		IProject other = workspaceRoot.getProject(entry.getPath().segment(0));
		if (!PDE.hasPluginNature(other))
			return false;
		if (other.findMember("fragment.xml") != null)
			return false;
		try {
			InputStream is = other.getFile("META-INF/MANIFEST.MF")
					.getContents();
			try {
				Manifest mf = new Manifest(is);
				if (mf.getMainAttributes().getValue(Constants.FRAGMENT_HOST) != null)
					return false;
			} finally {
				is.close();
			}
		} catch (IOException e) {
			// assume "not a fragment"
		} catch (CoreException e) {
			// assume "not a fragment"
		}
		return true;
	}

	/**
	 * @return updated classpath or null if there were no changes
	 */
	private IClasspathEntry[] getUpdatedClasspath(IClasspathEntry[] cp,
			IJavaProject currentProject) {
		boolean exposed = false;
		int refIndex = -1;
		List result = new ArrayList();
		Set manifests = new HashSet();
		for (int i = 0; i < fData.getLibraryPaths().length; ++i) {
			try {
				manifests.add(new JarFile(fData.getLibraryPaths()[i]).getManifest());
			} catch (IOException e) {
				PDEPlugin.log(e);
			}
		}
		IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
		for (int i = 0; i < cp.length; ++i) {
			IClasspathEntry cpe = cp[i];
			switch (cpe.getEntryKind()) {
			case IClasspathEntry.CPE_LIBRARY:
				String path = null;
				IPath location = root.getFile(cpe.getPath()).getLocation();
				if(location != null) {
					path = location.toString();
				}
				//try maybe path is absolute
				if(path == null) {
					path = cpe.getPath().toString();
				}
				try {
					JarFile jarFile = new JarFile(path);
					if (manifests.contains(jarFile.getManifest())) {
						if (refIndex < 0) {
							// allocate slot
							refIndex = result.size();
							result.add(null);
						}
						exposed |= cpe.isExported();
					} else {
						result.add(cpe);
					}
				} catch (IOException e) {
					PDEPlugin.log(e);
				}
				break;
			default:
				result.add(cpe);
				break;
			}
		}
		if (refIndex >= 0) {
			result.set(refIndex, JavaCore.newProjectEntry(currentProject
					.getPath(), exposed));
			return (IClasspathEntry[]) result
					.toArray(new IClasspathEntry[result.size()]);
		}
		return null;
	}

	protected void createContents(IProgressMonitor monitor, IProject project)
			throws CoreException, JavaModelException,
			InvocationTargetException, InterruptedException {
		// copy jars
		String[] paths = fData.getLibraryPaths();
		for (int i = paths.length - 1; i >= 0; i--) {
			File jarFile = new File(paths[i]);
			if (fData.isUnzipLibraries()) {
				importJar(jarFile, project, monitor);
			} else {
				addJar(jarFile, project, monitor);
			}
			monitor.worked(1);
		}

		// delete manifest.mf imported from libraries
		IFile importedManifest = project.getFile(ICoreConstants.BUNDLE_FILENAME_DESCRIPTOR); //$NON-NLS-1$
		if (importedManifest.exists()) {
			importedManifest.delete(true, false, monitor);
			if (!fData.hasBundleStructure()) {
				IFolder meta_inf = project.getFolder("META-INF"); //$NON-NLS-1$
				if (meta_inf.members().length == 0) {
					meta_inf.delete(true, false, monitor);
				}
			}
		}
	}

	protected void fillBinIncludes(IProject project, IBuildEntry binEntry)
			throws CoreException {
		if (fData.hasBundleStructure())
			binEntry.addToken("META-INF/"); //$NON-NLS-1$
		else
			binEntry.addToken(ICoreConstants.PLUGIN_FILENAME_DESCRIPTOR);

		if (fData.isUnzipLibraries()) {
			IResource[] resources = project.members(false);
			for (int j = 0; j < resources.length; j++) {
				if (resources[j] instanceof IFolder) {
					if (!binEntry.contains(resources[j].getName() + "/")) //$NON-NLS-1$
						binEntry.addToken(resources[j].getName() + "/"); //$NON-NLS-1$
				} else {
					if (".project".equals(resources[j].getName()) //$NON-NLS-1$
							|| ".classpath".equals(resources[j] //$NON-NLS-1$
									.getName())
							|| "build.properties".equals(resources[j] //$NON-NLS-1$
									.getName())) {
						continue;
					}
					if (!binEntry.contains(resources[j].getName()))
						binEntry.addToken(resources[j].getName());
				}
			}
		} else {
			String[] libraryPaths = fData.getLibraryPaths();
			for (int j = 0; j < libraryPaths.length; j++) {
				File jarFile = new File(libraryPaths[j]);
				String name = jarFile.getName();
				if (!binEntry.contains(name))
					binEntry.addToken(name);
			}
		}
	}

	protected IClasspathEntry[] getInternalClassPathEntries(IProject project,
			IFieldData data) {
		String[] libraryPaths;
		if (fData.isUnzipLibraries()) {
			libraryPaths = new String[] { "" }; //$NON-NLS-1$
		} else {
			libraryPaths = fData.getLibraryPaths();
		}
		IClasspathEntry[] entries = new IClasspathEntry[libraryPaths.length];
		for (int j = 0; j < libraryPaths.length; j++) {
			File jarFile = new File(libraryPaths[j]);
			String jarName = jarFile.getName();
			IPath path = project.getFullPath().append(jarName);
			entries[j] = JavaCore.newLibraryEntry(path, null, null, true);
		}
		return entries;
	}

	protected int getNumberOfWorkUnits() {
		int numUnits = super.getNumberOfWorkUnits();
		numUnits += fData.getLibraryPaths().length;
		return numUnits;
	}

	private void importJar(File jar, IResource destination,
			IProgressMonitor monitor) throws CoreException,
			InvocationTargetException, InterruptedException {
		ZipFile input = null;
		try {
			try {
				input = new ZipFile(jar);
				ZipFileStructureProvider provider = new ZipFileStructureProvider(
						input);
				ImportOperation op = new ImportOperation(destination
						.getFullPath(), provider.getRoot(), provider,
						new IOverwriteQuery() {
							public String queryOverwrite(String pathString) {
								return IOverwriteQuery.ALL;
							}
						});
				op.run(monitor);
			} finally {
				if (input != null)
					input.close();
			}
		} catch (IOException e) {
			throw new CoreException(
					new Status(
							IStatus.ERROR,
							IPDEUIConstants.PLUGIN_ID,
							IStatus.OK,
							NLS
							.bind(
									PDEUIMessages.NewProjectCreationOperation_errorImportingJar,
									jar), e));
		}
	}

	private void removeExportRoot(IBundle bundle) {
		String value = bundle.getHeader(Constants.BUNDLE_CLASSPATH);
		if (value == null)
			value = "."; //$NON-NLS-1$
		try {
			ManifestElement[] elems = ManifestElement.parseHeader(
					Constants.BUNDLE_CLASSPATH, value);
			StringBuffer buff = new StringBuffer(value.length());
			for (int i = 0; i < elems.length; i++) {
				if (!elems[i].getValue().equals(".")) //$NON-NLS-1$
					buff.append(elems[i].getValue());
			}
			bundle.setHeader(Constants.BUNDLE_CLASSPATH, buff.toString());
		} catch (BundleException e) {
		}
	}

	protected void setPluginLibraries(WorkspacePluginModelBase model)
			throws CoreException {
		IPluginBase pluginBase = model.getPluginBase();
		if (fData.isUnzipLibraries()) {
			IPluginLibrary library = model.getPluginFactory().createLibrary();
			library.setName("."); //$NON-NLS-1$
			library.setExported(true);
			pluginBase.add(library);
		} else {
			String[] paths = fData.getLibraryPaths();
			for (int i = 0; i < paths.length; i++) {
				File jarFile = new File(paths[i]);
				IPluginLibrary library = model.getPluginFactory()
						.createLibrary();
				library.setName(jarFile.getName());
				library.setExported(true);
				pluginBase.add(library);
			}
		}
	}

	protected void createSourceOutputBuildEntries(WorkspaceBuildModel model,
			IBuildModelFactory factory) throws CoreException {
		if (fData.isUnzipLibraries()) {
			// SOURCE.<LIBRARY_NAME>
			IBuildEntry entry = factory.createEntry(IBuildEntry.JAR_PREFIX
					+ "."); //$NON-NLS-1$
			entry.addToken("."); //$NON-NLS-1$
			model.getBuild().add(entry);

			// OUTPUT.<LIBRARY_NAME>
			entry = factory.createEntry(IBuildEntry.OUTPUT_PREFIX + "."); //$NON-NLS-1$
			entry.addToken("."); //$NON-NLS-1$
			model.getBuild().add(entry);
		}
	}

	private void addExportedPackages(IProject project, IBundle bundle) {
		String value = bundle.getHeader(Constants.BUNDLE_CLASSPATH);
		if (value == null)
			value = "."; //$NON-NLS-1$
		try {
			ManifestElement[] elems = ManifestElement.parseHeader(
					Constants.BUNDLE_CLASSPATH, value);
			HashMap map = new HashMap();
			for (int i = 0; i < elems.length; i++) {
				ArrayList filter = new ArrayList();
				filter.add("*"); //$NON-NLS-1$
				map.put(elems[i].getValue(), filter);
			}
			Set packages = PluginConverter.getDefault()
					.getExports(project, map);
			String pkgValue = getCommaValueFromSet(packages);
			bundle.setHeader(Constants.EXPORT_PACKAGE, pkgValue);
		} catch (BundleException e) {
		}
	}

	private void addDependencies(IProject project, ISharedPluginModel model,
			IProgressMonitor monitor) {
		if (!(model instanceof IBundlePluginModelBase)) {
			monitor.done();
			return;
		}
		final boolean unzip = fData.isUnzipLibraries();
		try {
			new AddNewBinaryDependenciesOperation(project,
					(IBundlePluginModelBase) model) {
				// Need to override this function to include every bundle in the
				// target platform as a possible dependency
				protected String[] findSecondaryBundles(IBundle bundle,
						Set ignorePkgs) {
					IPluginModelBase[] bases = PluginRegistry.getActiveModels();
					String[] ids = new String[bases.length];
					for (int i = 0; i < bases.length; i++) {
						BundleDescription desc = bases[i]
								.getBundleDescription();
						if (desc == null)
							ids[i] = bases[i].getPluginBase().getId();
						else
							ids[i] = desc.getSymbolicName();
					}
					return ids;
				}

				// Need to override this function because when jar is unzipped,
				// build.properties does not contain entry for '.'.
				// Therefore, the super.addProjectPackages will not find the
				// project packages(it includes only things in bin.includes)
				protected void addProjectPackages(IBundle bundle, Set ignorePkgs) {
					if (!unzip)
						super.addProjectPackages(bundle, ignorePkgs);
					Stack stack = new Stack();
					stack.push(fProject);
					try {
						while (!stack.isEmpty()) {
							IContainer folder = (IContainer) stack.pop();
							IResource[] children = folder.members();
							for (int i = 0; i < children.length; i++) {
								if (children[i] instanceof IContainer)
									stack.push(children[i]);
								else if ("class".equals(((IFile) children[i]).getFileExtension())) { //$NON-NLS-1$
									String path = folder
											.getProjectRelativePath()
											.toString();
									ignorePkgs.add(path.replace('/', '.'));
								}
							}
						}
					} catch (CoreException e) {
					}

				}
			}.run(monitor);
		} catch (InvocationTargetException e) {
		} catch (InterruptedException e) {
		}
	}

}
