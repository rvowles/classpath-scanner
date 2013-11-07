package com.bluetrainsoftware.classpathscanner;

import java.io.InputStream;
import java.net.URL;
import java.util.List;
import java.util.jar.JarEntry;

/**
 * @author: Richard Vowles - https://plus.google.com/+RichardVowles
 */
public interface ResourceScanListener {
	/*
	 * For each resource in the jar, please tell me about those resources.
	 *
	 * @return - a list of resources you now want
	*/
	List<Resource> resource(List<Resource> resources) throws Exception;

	/**
	 * Provides the requested resource.
	 *
	 * @param desire - the info on the resource wanted
	 * @param inputStream - the input stream from the jar file
	 */
	void deliver(Resource desire, InputStream inputStream);

	class Resource {
		public final URL url;
		public final JarEntry entry;
		public final String resourceName;

		public Resource(URL url, JarEntry entry, String resourceName) {
			this.url = url;
			this.entry = entry;
			this.resourceName = resourceName;
		}
	}


	/**
	 * If true, this is an interesting jar, please tell me about the entries in it.
	 *
	 * @param url
	 * @return jar is interesting
	 */
	boolean isInteresting(URL url);
}
