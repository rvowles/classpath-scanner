package com.bluetrainsoftware.classpathscanner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;

/**
 * This implements an efficient classpath scanner for URL Class Loaders
 *
 * @author: Richard Vowles - https://plus.google.com/+RichardVowles
 */
public class ClasspathScanner {
	private static final Logger log = LoggerFactory.getLogger(ClasspathScanner.class);
	private static final String JAR_PREFIX = "jar:";
	private static final String FILE_PREFIX = "file:";
	private static ClasspathScanner globalScanner = new ClasspathScanner();

	/**
	 * The resources from the last scan
	 */
	public static Map<URLClassLoader, List<ClasspathResource>> resources = new HashMap<>();

	protected List<ResourceScanListener> listeners = new ArrayList<>();

	public static ClasspathScanner getInstance() {
		return globalScanner;
	}

	public void registerResourceScanner(ResourceScanListener listener) {
		listeners.add(listener);
	}

	public void fireResourceScannerListeners(List<ClasspathResource> resources) {
		for(ClasspathResource resource : resources) {
			resource.fireListeners();
		}
	}

	public void askForInterest(List<ClasspathResource> resources) {
		for(ClasspathResource resource : resources) {
			resource.askListeners(listeners);
		}
	}

	public List<ClasspathResource> scan(ClassLoader loader) {
		if (!(loader instanceof URLClassLoader)) {
			throw new RuntimeException("Attempted to scan without using a URL Class Loader");
		}

		List<ClasspathResource> cpResources = resources.get(loader);
		if (cpResources == null) {

			Map<String, ClasspathResource> fileMap = new HashMap<>();

			URLClassLoader cp = (URLClassLoader)loader;

			ArrayList<ClasspathResource> myResources = new ArrayList<>();

			for(URL url : cp.getURLs()) {
				String path = url.toString();

				if (path.startsWith(JAR_PREFIX)) {
					processJarResource(path, url, fileMap, myResources);
				} else if (path.startsWith(FILE_PREFIX)) {
					processFileResource(path, url, fileMap, myResources);
				}
			}

			cpResources = Collections.unmodifiableList(myResources);

			askForInterest(cpResources);
			fireResourceScannerListeners(cpResources);

			resources.put(cp, cpResources);
		}

		return cpResources;
	}

	private void processFileResource(String path, URL url, Map<String, ClasspathResource> fileMap, List<ClasspathResource> myResources) {
		path = path.substring(FILE_PREFIX.length());

		// does it already exist in the map? if so, stop processing and ignore
		ClasspathResource resource = fileMap.get(path);

		if (resource == null) {
			foundJar(path, url, fileMap, null, myResources);
		}

	}

	private void processJarResource(String path, URL url, Map<String, ClasspathResource> fileMap, List<ClasspathResource> myResources) {
		path = path.substring(JAR_PREFIX.length() + FILE_PREFIX.length());


		String offset = null;

		int offsetPos = path.indexOf('!');
		if (offsetPos != -1) {
			offset = path.substring(offsetPos + 1);
			path = path.substring(0, offsetPos);
		}

		// does it already exist in the map? if so, stop processing and ignore
		ClasspathResource resource = fileMap.get(path);

		if (resource != null) {
			if (offset != null) {
				resource.addJarOffset(offset, url);
			}
		} else {
			foundJar(path, url, fileMap, offset, myResources);
		}
	}

	private void foundJar(String path, URL url, Map<String, ClasspathResource> fileMap, String offset, List<ClasspathResource> myResources) {
		ClasspathResource resource;File jarFile = new File(path);

		if (jarFile.exists()) {
			resource = new ClasspathResource(jarFile, url);

			if (offset != null) {
				resource.addJarOffset(offset, url);
			}

			fileMap.put(path, resource);
			myResources.add(resource);
		} else {
			log.info("classpath scan: {} cannot be found", path);
		}
	}
}
