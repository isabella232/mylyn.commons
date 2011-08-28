/*******************************************************************************
 * Copyright (c) 2011 Tasktop Technologies and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Tasktop Technologies - initial API and implementation
 *******************************************************************************/

package org.eclipse.mylyn.commons.sdk.util;

/**
 * @author Steffen Pingel
 */
public class TestConfiguration {

	private static final String SERVER = System.getProperty("mylyn.test.server", "mylyn.org");

	public static String getRepositoryUrl(String service) {
		return getRepositoryUrl(service, false);
	}

	public static String getRepositoryUrl(String service, boolean secure) {
		// FIXME support https
		return ((secure) ? "https://" : "http://") + SERVER + "/" + service;
	}

}
