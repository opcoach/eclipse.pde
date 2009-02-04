/*******************************************************************************
 * Copyright (c) 2008, 2009 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.pde.ui.tests.target;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.pde.internal.core.target.provisional.ITargetDefinition;

import java.io.*;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;
import junit.framework.*;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.*;
import org.eclipse.core.variables.VariablesPlugin;
import org.eclipse.equinox.internal.provisional.frameworkadmin.BundleInfo;
import org.eclipse.jdt.launching.JavaRuntime;
import org.eclipse.pde.core.plugin.TargetPlatform;
import org.eclipse.pde.internal.core.target.impl.*;
import org.eclipse.pde.internal.core.target.provisional.*;
import org.eclipse.pde.internal.ui.tests.macro.MacroPlugin;
import org.osgi.framework.ServiceReference;

/**
 * Tests the persistence of target definitions.  Tests memento creation, reading of old target files, and writing of the model.
 * 
 * @since 3.5 
 */
public class TargetDefinitionPersistenceTests extends TestCase {
	
	public static Test suite() {
		return new TestSuite(TargetDefinitionPersistenceTests.class);
	}
	
	protected void assertTargetDefinitionsEqual(ITargetDefinition targetA, ITargetDefinition targetB) {
		assertTrue("Target content not equal",((TargetDefinition)targetA).isContentEqual(targetB));
	}
	
	/**
	 * Returns the target platform service or <code>null</code> if none
	 * 
	 * @return target platform service
	 */
	protected ITargetPlatformService getTargetService() {
		ServiceReference reference = MacroPlugin.getBundleContext().getServiceReference(ITargetPlatformService.class.getName());
		assertNotNull("Missing target platform service", reference);
		if (reference == null)
			return null;
		return (ITargetPlatformService) MacroPlugin.getBundleContext().getService(reference);
	}
	
	/**
	 * Returns the resolved location of the specified bundle container.
	 * 
	 * @param container bundle container
	 * @return resolved location
	 * @throws CoreException 
	 */
	protected String getResolvedLocation(IBundleContainer container) throws CoreException {
		return ((AbstractBundleContainer)container).getLocation(true);
	}
	
	/**
	 * Returns the location of the JDT feature in the running host as
	 * a path in the local file system.
	 * 
	 * @return path to JDT feature
	 */
	protected IPath getJdtFeatureLocation() {
		IPath path = new Path(TargetPlatform.getDefaultLocation());
		path = path.append("features");
		File dir = path.toFile();
		assertTrue("Missing features directory", dir.exists() && !dir.isFile());
		String[] files = dir.list();
		String location = null;
		for (int i = 0; i < files.length; i++) {
			if (files[i].startsWith("org.eclipse.jdt_")) {
				location = path.append(files[i]).toOSString();
				break;
			}
		}
		assertNotNull("Missing JDT feature", location);
		return new Path(location);
	}
	
	/**
	 * Tests restoration of a handle to target definition in an IFile 
	 * @throws CoreException 
	 */
	public void testWorkspaceTargetHandleMemento() throws CoreException {
		ITargetPlatformService service = getTargetService();
		IFile file = ResourcesPlugin.getWorkspace().getRoot().getFile(new Path("does/not/exist"));
		ITargetHandle handle = service.getTarget(file);
		assertFalse("Target should not exist", handle.exists());
		String memento = handle.getMemento();
		assertNotNull("Missing memento", memento);
		ITargetHandle handle2 = service.getTarget(memento);
		assertEquals("Restore failed", handle, handle2);
		IFile file2 = ResourcesPlugin.getWorkspace().getRoot().getFile(new Path("does/not/exist/either"));
		ITargetHandle handle3 = service.getTarget(file2);
		assertFalse("Should be different targets", handle.equals(handle3));
	}
	
	/**
	 * Tests restoration of a handle to target definition in local metadata 
	 * 
	 * @throws CoreException 
	 * @throws InterruptedException 
	 */
	public void testLocalTargetHandleMemento() throws CoreException, InterruptedException {
		ITargetPlatformService service = getTargetService();
		ITargetHandle handle = service.newTarget().getHandle();
		assertFalse("Target should not exist", handle.exists());
		String memento = handle.getMemento();
		assertNotNull("Missing memento", memento);
		ITargetHandle handle2 = service.getTarget(memento);
		assertEquals("Restore failed", handle, handle2);
		ITargetHandle handle3 = service.newTarget().getHandle();
		assertFalse("Should be different targets", handle.equals(handle3));
	}
	
	/**
	 * Tests that a complex metadata based target definition can be serialized to xml, 
	 * then deserialized without any loss of data.
	 * 
	 * @throws Exception
	 */
	public void testPersistComplexMetadataDefinition() throws Exception {
		ITargetDefinition definitionA = getTargetService().newTarget();
		initComplexDefiniton(definitionA);
		
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		TargetDefinitionPersistenceHelper.persistXML(definitionA, outputStream);
		ITargetDefinition definitionB = getTargetService().newTarget();
		ByteArrayInputStream inputStream = new ByteArrayInputStream(outputStream.toByteArray());
		TargetDefinitionPersistenceHelper.initFromXML(definitionB, inputStream);
		
		assertTargetDefinitionsEqual(definitionA, definitionB);
	}
	
	/**
	 * Tests that a complex workspace file based target definition can be serialized to xml, 
	 * then deserialized without any loss of data.
	 * 
	 * @throws Exception
	 */
	public void testPersistComplexWorkspaceDefinition() throws Exception {
		IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject("TargetDefinitionPersistenceTests");
		try{
			if (!project.exists()){
				project.create(null);
			}
			assertTrue("Could not create test project",project.exists());
			project.open(null);
			assertTrue("Could not open test project", project.isOpen());

			IFile target = project.getFile(new Long(System.currentTimeMillis()).toString() + "A.target");
			ITargetDefinition definitionA = getTargetService().getTarget(target).getTargetDefinition();
			initComplexDefiniton(definitionA);
			
			ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
			TargetDefinitionPersistenceHelper.persistXML(definitionA, outputStream);
			IFile targetB = project.getFile(new Long(System.currentTimeMillis()).toString() + "B.target");
			ITargetDefinition definitionB = getTargetService().getTarget(targetB).getTargetDefinition();
			ByteArrayInputStream inputStream = new ByteArrayInputStream(outputStream.toByteArray());
			TargetDefinitionPersistenceHelper.initFromXML(definitionB, inputStream);
				
			assertTargetDefinitionsEqual(definitionA, definitionB);
		} finally {
			if (project.exists()){
				project.delete(true, null);
			}
			assertFalse("Could not delete test project",project.exists());
		}
	}
	
	
	protected void initComplexDefiniton(ITargetDefinition definition){
		definition.setName("name");
		definition.setDescription("description");
		definition.setOS("os");
		definition.setWS("ws");
		definition.setArch("arch");
		definition.setNL("nl");
		definition.setProgramArguments("program\nargs");
		definition.setVMArguments("vm\nargs");
		definition.setJREContainer(JavaRuntime.newDefaultJREContainerPath());
		
		BundleInfo[] implicit = new BundleInfo[]{
				new BundleInfo("org.eclipse.jdt.launching", null, null, BundleInfo.NO_LEVEL, false),
				new BundleInfo("org.eclipse.jdt.debug", null, null, BundleInfo.NO_LEVEL, false)
		};		
		definition.setImplicitDependencies(implicit);
		
		// Directory container
		IBundleContainer dirContainer = getTargetService().newDirectoryContainer(TargetPlatform.getDefaultLocation() + "/plugins");
		// Profile container with specific config area
		IBundleContainer profileContainer = getTargetService().newProfileContainer(TargetPlatform.getDefaultLocation(), new File(Platform.getConfigurationLocation().getURL().getFile()).getAbsolutePath());
		// Feature container with specific version
		IPath location = getJdtFeatureLocation();
		String segment = location.lastSegment();
		int index = segment.indexOf('_');
		assertTrue("Missing version id", index > 0);
		String version = segment.substring(index + 1);
		IBundleContainer featureContainer = getTargetService().newFeatureContainer("${eclipse_home}", "org.eclipse.jdt", version);
		// Profile container restricted to just two bundles
		IBundleContainer restrictedProfileContainer = getTargetService().newProfileContainer(TargetPlatform.getDefaultLocation(), null);
		BundleInfo[] restrictions = new BundleInfo[]{
				new BundleInfo("org.eclipse.jdt.launching", null, null, BundleInfo.NO_LEVEL, false),
				new BundleInfo("org.eclipse.jdt.debug", null, null, BundleInfo.NO_LEVEL, false)
		};
		restrictedProfileContainer.setIncludedBundles(restrictions);
		// Profile container restrict to zero bundles
		IBundleContainer emptyProfileContainer = getTargetService().newProfileContainer(TargetPlatform.getDefaultLocation(), null);
		BundleInfo[] completeRestrictions = new BundleInfo[0];
		emptyProfileContainer.setIncludedBundles(completeRestrictions);
		
		definition.setBundleContainers(new IBundleContainer[]{dirContainer, profileContainer, featureContainer, restrictedProfileContainer, emptyProfileContainer});
	}
	
	
	/**
	 * Tests that an empty target definition can be serialized to xml, then deserialized without
	 * any loss of data.
	 * 
	 * @throws Exception
	 */
	public void testPersistEmptyDefinition() throws Exception {
		ITargetDefinition definitionA = getTargetService().newTarget();
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		TargetDefinitionPersistenceHelper.persistXML(definitionA, outputStream);
		ITargetDefinition definitionB = getTargetService().newTarget();
		ByteArrayInputStream inputStream = new ByteArrayInputStream(outputStream.toByteArray());
		TargetDefinitionPersistenceHelper.initFromXML(definitionB, inputStream);
		assertTargetDefinitionsEqual(definitionA, definitionB);
	}
	
	/**
	 * Reads a target definition file from the tests/targets/target-files location
	 * with the given name. Note that ".target" will be appended.
	 * 
	 * @param name
	 * @return target definition
	 * @throws Exception
	 */
	protected ITargetDefinition readOldTarget(String name) throws Exception {
		URL url = MacroPlugin.getBundleContext().getBundle().getEntry("/tests/targets/target-files/" + name + ".target");
		File file = new File(FileLocator.toFileURL(url).getFile());
		ITargetDefinition target = getTargetService().newTarget();
		FileInputStream stream = new FileInputStream(file);
		TargetDefinitionPersistenceHelper.initFromXML(target, stream);
		stream.close();
		return target;
	}
	
	/**
	 * Tests that we can de-serialize an old style target definition file (version 3.2) and retrieve the correct
	 * contents.
	 * 
	 * @throws Exception
	 */
	public void testReadOldBasicTargetFile() throws Exception {
		ITargetDefinition target = readOldTarget("basic");
		
		assertEquals("Wrong name", "Basic", target.getName());
		assertNull(target.getDescription());
		assertNull(target.getArch());
		assertNull(target.getOS());
		assertNull(target.getNL());
		assertNull(target.getWS());
		assertNull(target.getProgramArguments());
		assertNull(target.getVMArguments());
		assertNull(target.getImplicitDependencies());
		assertNull(target.getJREContainer());
		
		IBundleContainer[] containers = target.getBundleContainers();
		assertEquals("Wrong number of bundles", 1, containers.length);
		assertTrue("Container should be a profile container", containers[0] instanceof ProfileBundleContainer);
		assertEquals("Wrong home location", new Path(TargetPlatform.getDefaultLocation()),
				new Path(getResolvedLocation(containers[0])));
	}
	
	/**
	 * Tests that we can de-serialize an old style target definition file (version 3.2) and retrieve the correct
	 * contents.
	 * 
	 * @throws Exception
	 */
	public void testReadOldBasicDirectoryTargetFile() throws Exception {
		ITargetDefinition target = readOldTarget("directory");
		
		assertEquals("Wrong name", "Directory", target.getName());
		assertNull(target.getDescription());
		assertNull(target.getArch());
		assertNull(target.getOS());
		assertNull(target.getNL());
		assertNull(target.getWS());
		assertNull(target.getProgramArguments());
		assertNull(target.getVMArguments());
		assertNull(target.getImplicitDependencies());
		assertNull(target.getJREContainer());
		
		IBundleContainer[] containers = target.getBundleContainers();
		assertEquals("Wrong number of bundles", 1, containers.length);
		assertTrue("Container should be a directory container", containers[0] instanceof DirectoryBundleContainer);
		assertEquals("Wrong home location", new Path(TargetPlatform.getDefaultLocation()).append("plugins"),
				new Path(getResolvedLocation(containers[0])));
	}	
	
	/**
	 * Tests that we can de-serialize an old style target definition file (version 3.2) and retrieve the correct
	 * contents.
	 * 
	 * @throws Exception
	 */
	public void testReadOldSpecificTargetFile() throws Exception {
		ITargetDefinition target = readOldTarget("specific");
		
		assertEquals("Wrong name", "Specific Settings", target.getName());
		assertNull(target.getDescription());
		assertEquals("x86", target.getArch());
		assertEquals("linux", target.getOS());
		assertEquals("en_US", target.getNL());
		assertEquals("gtk", target.getWS());
		assertEquals("pgm1 pgm2", target.getProgramArguments());
		assertEquals("-Dfoo=\"bar\"", target.getVMArguments());
		assertEquals(JavaRuntime.newJREContainerPath(JavaRuntime.getExecutionEnvironmentsManager().getEnvironment("J2SE-1.4")), target.getJREContainer());
		
		BundleInfo[] infos = target.getImplicitDependencies();
		assertEquals("Wrong number of implicit dependencies", 2, infos.length);
		Set set = new HashSet();
		for (int i = 0; i < infos.length; i++) {
			set.add(infos[i].getSymbolicName());
		}
		assertTrue("Missing ", set.remove("org.eclipse.jdt.debug"));
		assertTrue("Missing ", set.remove("org.eclipse.debug.core"));
		assertTrue(set.isEmpty());
		
		IBundleContainer[] containers = target.getBundleContainers();
		assertEquals("Wrong number of bundles", 1, containers.length);
		assertTrue("Container should be a directory container", containers[0] instanceof DirectoryBundleContainer);
		assertEquals("Wrong home location", new Path(TargetPlatform.getDefaultLocation()).append("plugins"),
				new Path(getResolvedLocation(containers[0])));
	}	
	
	/**
	 * Tests that we can de-serialize an old style target definition file (version 3.2) and retrieve the correct
	 * contents.
	 * 
	 * @throws Exception
	 */
	public void testReadOldAdditionLocationsTargetFile() throws Exception {
		ITargetDefinition target = readOldTarget("additionalLocations");
		
		assertEquals("Wrong name", "Additional Locations", target.getName());
		assertNull(target.getDescription());
		assertNull(target.getArch());
		assertNull(target.getOS());
		assertNull(target.getNL());
		assertNull(target.getWS());
		assertNull(target.getProgramArguments());
		assertNull(target.getVMArguments());
		assertNull(target.getJREContainer());
		assertNull(target.getImplicitDependencies());
		
		IBundleContainer[] containers = target.getBundleContainers();
		assertEquals("Wrong number of bundles", 3, containers.length);
		assertTrue(containers[0] instanceof ProfileBundleContainer);
		assertTrue(containers[1] instanceof DirectoryBundleContainer);
		assertTrue(containers[2] instanceof DirectoryBundleContainer);
		
		assertEquals("Wrong home location", new Path(TargetPlatform.getDefaultLocation()),
				new Path(getResolvedLocation(containers[0])));
		
		String string = VariablesPlugin.getDefault().getStringVariableManager().performStringSubstitution("${workspace_loc}");
		assertEquals("Wrong 1st additional location", new Path(string).append("stuff"),
				new Path(getResolvedLocation(containers[1])));
		
		assertEquals("Wrong 2nd additional location", new Path(TargetPlatform.getDefaultLocation()).append("dropins"),
				new Path(getResolvedLocation(containers[2])));
	}		
	
	/**
	 * Tests that we can de-serialize an old style target definition file (version 3.2) and retrieve the correct
	 * contents.
	 * 
	 * @throws Exception
	 */
	public void testReadOldFeaturesTargetFile() throws Exception {
		ITargetDefinition target = readOldTarget("featureLocations");
		
		assertEquals("Wrong name", "Features", target.getName());
		assertNull(target.getDescription());
		assertNull(target.getArch());
		assertNull(target.getOS());
		assertNull(target.getNL());
		assertNull(target.getWS());
		assertNull(target.getProgramArguments());
		assertNull(target.getVMArguments());
		assertNull(target.getJREContainer());
		assertNull(target.getImplicitDependencies());
		
		IBundleContainer[] containers = target.getBundleContainers();
		assertEquals("Wrong number of bundles", 2, containers.length);
		assertTrue(containers[0] instanceof FeatureBundleContainer);
		assertTrue(containers[1] instanceof FeatureBundleContainer);

		assertEquals("Wrong feature location", "org.eclipse.jdt", ((FeatureBundleContainer)containers[0]).getFeatureId());
		assertEquals("Wrong feature location", "org.eclipse.platform", ((FeatureBundleContainer)containers[1]).getFeatureId());
	}
	
	/**
	 * Tests that we can de-serialize an old style target definition file (version 3.2) and retrieve the correct
	 * contents.
	 * 
	 * @throws Exception
	 */
	public void testReadOldRestrictionsTargetFile() throws Exception {
		ITargetDefinition target = readOldTarget("restrictions");
		
		assertEquals("Wrong name", "Restrictions", target.getName());
		assertNull(target.getDescription());
		assertNull(target.getArch());
		assertNull(target.getOS());
		assertNull(target.getNL());
		assertNull(target.getWS());
		assertNull(target.getProgramArguments());
		assertNull(target.getVMArguments());
		assertNull(target.getJREContainer());
		assertNull(target.getImplicitDependencies());
		
		BundleInfo[] restrictions = new BundleInfo[]{
			new BundleInfo("org.eclipse.debug.core", null, null, BundleInfo.NO_LEVEL, false),
			new BundleInfo("org.eclipse.debug.ui", null, null, BundleInfo.NO_LEVEL, false),
			new BundleInfo("org.eclipse.jdt.debug", null, null, BundleInfo.NO_LEVEL, false),
			new BundleInfo("org.eclipse.jdt.debug.ui", null, null, BundleInfo.NO_LEVEL, false),
			new BundleInfo("org.eclipse.jdt.launching", null, null, BundleInfo.NO_LEVEL, false)
		};
		
		IBundleContainer[] containers = target.getBundleContainers();
		assertEquals("Wrong number of bundles", 3, containers.length);
		assertTrue(containers[0] instanceof ProfileBundleContainer);
		assertTrue(containers[1] instanceof FeatureBundleContainer);
		assertTrue(containers[2] instanceof DirectoryBundleContainer);
		
		assertEquals("Wrong home location", new Path(TargetPlatform.getDefaultLocation()), new Path(getResolvedLocation(containers[0])));
		assertEquals("Wrong 1st additional location", "org.eclipse.jdt",((FeatureBundleContainer)containers[1]).getFeatureId());
		assertEquals("Wrong 2nd additional location", new Path(TargetPlatform.getDefaultLocation()).append("dropins"),
				new Path(getResolvedLocation(containers[2])));
		
		for (int i = 0; i < containers.length; i++) {
			IBundleContainer container = containers[i];
			BundleInfo[] actual = container.getIncludedBundles();
			assertNotNull(actual);
			assertEquals("Wrong number of restrictions", restrictions.length, actual.length);
			for (int j = 0; j < actual.length; j++) {
				assertEquals("Wrong restriction", restrictions[j], actual[j]);
			}
		}
	}		
	
}