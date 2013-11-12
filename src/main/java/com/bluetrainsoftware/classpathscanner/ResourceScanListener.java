package com.bluetrainsoftware.classpathscanner;

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.util.List;
import java.util.jar.JarEntry;

/**
 * @author Richard Vowles - https://plus.google.com/+RichardVowles
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
		public final File file;
		public final String resourceName;

		public Resource(URL url, JarEntry entry, String resourceName) {
			this.url = url;
			this.resourceName = resourceName;

			this.entry = entry;
			this.file = null;
		}

		public Resource(URL url, File file, String resourceName) {
			this.url = url;
			this.resourceName = resourceName;

			this.entry = null;
			this.file = file;
		}
	}


	/**
	 * If true, this is an interesting jar, please tell me about the entries in it.
	 *
	 * @param url the url of the resource
	 * @return resource is interesting, please tell me more...
	 */
	boolean isInteresting(URL url);
}
