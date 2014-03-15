package com.bluetrainsoftware.classpathscanner;

import java.io.File;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
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
	 * @return - a list of resources you now want or null if you don't want any
	*/
	List<ScanResource> resource(List<ScanResource> scanResources) throws Exception;

	/**
	 * Provides the requested resource.
	 *
	 * @param desire - the info on the resource wanted
	 * @param inputStream - the input stream from the jar file
	 */
	void deliver(ScanResource desire, InputStream inputStream);

	enum InterestAction {
		NONE, // not interesting
		ONCE, // tell me once, delete me after that
		REPEAT // tell me each time a scan happens but no more
	}

	enum ScanAction {
		STARTING, // about to scan
		COMPLETE  // completed
	}

	class ScanResource {
		/**
		 * The URL of the directory or jar file
		 */
		public final URL url;
		/**
		 * The URL of the offset within the jar URL (null if a plain jar or directory)
		 */
		public final URL offsetUrl;
		/**
		 * The JarEntry if this resource came from a jar
		 */
		public final JarEntry entry;
		/**
		 * The File if this resource came from a directory
		 */
		public final File file;
		/**
		 * The resource's / separated path name within the "url" field.
		 * e.g. /META-INF/web-fragment.xml or /com/bluetrainsoftware
		 *
		 *
		 */
		public final String resourceName;

		public ScanResource(URL url, JarEntry entry, String resourceName, URL offsetUrl) {
			this.url = url;
			this.resourceName = resourceName;
			this.entry = entry;
			this.offsetUrl = offsetUrl;
			this.file = null;
		}

		public ScanResource(URL url, File file, String resourceName) {
			this.url = url;
			this.resourceName = resourceName;

			this.entry = null;
			this.offsetUrl = url;
			this.file = file;
		}

		private URL finalUrl = null;

		/**
		 * Figures out what the fully resolved url of this file is.
		 *
		 * @return - fully resolved url
		 */
		public URL getResolvedUrl() {
			if (finalUrl != null) {
				return finalUrl;
			}

			if (file != null) {
				try {
					finalUrl = file.toURI().toURL();
				} catch (MalformedURLException e) {
					throw new RuntimeException("Failed to convert file to URL " + file.getAbsolutePath(), e);
				}
			} else {
				try {
					String u = offsetUrl.toString();
					if (!u.contains("!/"))
						u = "jar:" + u + "!";

					finalUrl = new URL(u + (u.endsWith("/")?"":"/") + resourceName);
				} catch (MalformedURLException e) {
					throw new RuntimeException("Failed to convert url to offset URL " + offsetUrl.toString(), e);
				}
			}

			return finalUrl;
		}

		/**
		 * Tries to give you a new offset based on this offsetUrl. This is used when you detect a particular resource and
		 * you need to backtrack and get a parent resource as a URL reference.
		 *
		 * @param offset - the offset you want to add to the offsetUrl
		 * @return the newly combined url
		 */
		public URL newOffset(String offset) {
			String newUrl = offsetUrl.toString();

			if (!newUrl.contains("!/")) {
				newUrl = "jar:" + newUrl + "!";
			}

			if (!newUrl.endsWith("/")) {
				newUrl += "/";
			}

			if (offset.startsWith("/")) {
				newUrl = newUrl + offset.substring(1);
			} else {
				newUrl = newUrl + offset;
			}

			try {
				return new URL(newUrl);
			} catch (MalformedURLException e) {
				throw new RuntimeException("Unexpectedly bad URL with " + newUrl, e);
			}
		}
	}

	/**
	 * Holds a reference to the URL and the directory (if it exists).
	 */
	class InterestingResource {
		/**
		 * the Directory URL
		 */
		final public URL url;

		/**
		 * If it is a directory, this is the directory
		 */
		final public File directory;

		public InterestingResource(URL url) {
			this.url = url;

			if ("file".equals(url.getProtocol())) {
				File dir = new File(url.getPath());
				if (dir.isDirectory()) {
					this.directory = dir;
				} else {
					this.directory = null;
				}

			} else {
				directory = null;
			}
		}

		public boolean isTestDirectory() {
			return directory != null && directory.getPath().endsWith("/target/test-classes");
		}
	}

	/**
	 * If true, this is an interesting jar, please tell me about the entries in it.
	 *
	 * @param interestingResource each entry in the classpath gets checked for this
	 * @return resource is interesting, please tell me more...
	 */
	InterestAction isInteresting(InterestingResource interestingResource);

	void scanAction(ScanAction action);
}
