package org.eclipse.pde.model.plugin;
/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.resources.*;
import java.util.*;
import org.eclipse.pde.internal.base.model.*;
import org.eclipse.pde.model.*;
/**
 * A model object that represents the content of a plug-in or
 * fragment manifest. This object contains data that is common
 * for bo plug-ins and fragments.
 */
public interface IPluginBase extends IPluginObject, IIdentifiable {
	/**
	 * A property that will be used to notify that
	 * the provider name has changed.
	 */
	public static final String P_PROVIDER = "provider";
	/**
	 * A property that will be used to notify
	 * that a version has changed.
	 */
	public static final String P_VERSION = "version";

	/**
	 * A property that will be used to notify
	 * that library order in a plug-in has changed. 
	 */
	public static final String P_LIBRARY_ORDER = "library_order";
	/**
	 * A property that will be used to notify
	 * that extension order in a plug-in has changed. 
	 */
	public static final String P_EXTENSION_ORDER = "extension_order";
	/**
	 * Adds a new extension to this plugin. This
	 * method will throw a CoreException if
	 * model is not editable.
	 *
	 * @param extension the extension object
	 */
	void add(IPluginExtension extension) throws CoreException;
	/**
	 * @param extension org.eclipse.pde.model.plugin.IPluginExtension
	 */
	void add(IPluginExtensionPoint extension) throws CoreException;
	/**
	 * Adds a new library to this plugin.
	 * This method will throw a CoreException if
	 * model is not editable.
	 *
	 * @param library the new library object
	 */
	void add(IPluginLibrary library) throws CoreException;

	/**
	 * Adds a new plug-in import to this plugin.
	 * This method will throw a CoreException if
	 * model is not editable.
	 *
	 * @param pluginImport the new import object
	 */
	void add(IPluginImport pluginImport) throws CoreException;
	/**
	 * Removes an import from the plugin. This
	 * method will throw a CoreException if
	 * the model is not editable.
	 *
	 * @param import the import object
	 */
	void remove(IPluginImport pluginImport) throws CoreException;
	/**
	 * Returns extension points defined in this plug-in.
	 * @return an array of extension point objects
	 */
	IPluginExtensionPoint[] getExtensionPoints();
	/**
	 * Returns extensions defined in this plug-in.
	 *
	 * @return an array of extension objects
	 */
	IPluginExtension[] getExtensions();
	/**
	 * Returns libraries referenced in this plug-in.
	 *
	 * @return an array of libraries
	 */
	IPluginLibrary[] getLibraries();
	/**
	 * Returns imports defined in this plug-in.
	 *
	 * @return an array of import objects
	 */
	IPluginImport[] getImports();
	/**
	 * Returns a name of the plug-in provider.
	 *
	 * @return plug-in provider name
	 */
	String getProviderName();
	/**
	 * Returns this plug-in's version
	 * @return the version of the plug-in
	 */
	String getVersion();
	/**
	 * Removes an extension from the plugin. This
	 * method will throw a CoreException if
	 * the model is not editable.
	 *
	 * @param extension the extension object
	 */
	void remove(IPluginExtension extension) throws CoreException;
	/**
	 * Removes an extension point from the plugin. This
	 * method will throw a CoreException if
	 * the model is not editable.
	 *
	 * @param extensionPoint the extension point object
	 */
	void remove(IPluginExtensionPoint extensionPoint) throws CoreException;
	/**
	 * Removes a library from the plugin. This
	 * method will throw a CoreException if
	 * the model is not editable.
	 *
	 * @param library the library object
	 */
	void remove(IPluginLibrary library) throws CoreException;
	/**
	 * Sets the name of the plug-in provider.
	 * This method will throw a CoreException
	 * if the model is not editable.
	 *
	 * @param providerName the new provider name
	 */
	void setProviderName(String providerName) throws CoreException;
	/**
	 * Sets the version of the plug-in.
	 * This method will throw a CoreException
	 * if the model is not editable.
	 *
	 * @param version the new plug-in version
	 */
	void setVersion(String version) throws CoreException;
	/**
	 * Swaps the positions of the provided extensions
	 * in the list of extensions.
	 *
	 * @param e1 the first extension object
	 * @param e2 the second extension object
	 */
	void swap(IPluginExtension e1, IPluginExtension e2) throws CoreException;
	/**
	 * Swaps the positions of the provided libraries
	 * in the list of libraries. Libraries are looked up
	 * by the class loader in the order of declaration.
	 * If two libraries contain classes with the same
	 * name, library order will determine which one is
	 * encountered first.
	 *
	 * @param l1 the first library object
	 * @param l2 the second library object
	 */
	void swap(IPluginLibrary l1, IPluginLibrary l2) throws CoreException;
}