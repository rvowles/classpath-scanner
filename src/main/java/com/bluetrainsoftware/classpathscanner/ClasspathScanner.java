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
	class Classpath {
		final List<ClasspathResource> classpaths;
		final List<ResourceScanListener> uncheckedListeners;

		public Classpath(List<ClasspathResource> classpaths) {
			this.classpaths = Collections.unmodifiableList(classpaths);

			this.uncheckedListeners = new ArrayList<>();
			this.uncheckedListeners.addAll(allUncheckedListeners); // set it to the existing list
		}

		public void askForInterest() {
			if (uncheckedListeners.size() > 0) {
				for(ClasspathResource resource : classpaths) {
					resource.askListeners(uncheckedListeners);
				}
			}

			// clearing them allows us to all this over and over and not worry
			uncheckedListeners.clear();
		}

		public void fireListeners() {
			for(ClasspathResource resource : classpaths) {
				resource.fireListeners();
			}
		}

		public void cleanListeners() {
			for(ClasspathResource resource : classpaths) {
				resource.removeSingleFireListeners();
			}
		}

		public void triggerNotifications() {
			Set<ResourceScanListener> listeners = new HashSet<>();

			for(ClasspathResource resource : classpaths) {
				resource.collectInUseListeners(listeners);
			}

			listeners.addAll(uncheckedListeners);

			notifyAction(listeners, ResourceScanListener.ScanAction.STARTING);

			askForInterest();
			fireListeners();

			cleanListeners();

			notifyAction(listeners, ResourceScanListener.ScanAction.COMPLETE);

		}

		private void notifyAction(Set<ResourceScanListener> listeners, ResourceScanListener.ScanAction action) {
			for(ResourceScanListener listener : listeners) {
				listener.scanAction(action);
			}
		}
	}

	public static Map<URLClassLoader, Classpath> resources = new HashMap<>();
	protected static List<ResourceScanListener> allUncheckedListeners = new ArrayList<>();

	public static ClasspathScanner getInstance() {
		return globalScanner;
	}

	public static void resetScannerForTesting() {
		globalScanner = new ClasspathScanner();
		allUncheckedListeners = new ArrayList<>();
		resources = new HashMap<>();
	}

	public void registerResourceScanner(ResourceScanListener listener) {
		for(Classpath cp : resources.values()) {
			cp.uncheckedListeners.add(listener);
		}

		allUncheckedListeners.add(listener);
	}

	public List<ClasspathResource> scan(ClassLoader loader) {
		return scan(loader, true);
	}

	public List<ClasspathResource> scan(ClassLoader loader, boolean triggerNotification) {
		if (!URLClassLoader.class.isInstance(loader)) {
			throw new RuntimeException("Attempted to scan without using a URL Class Loader");
		}

		Classpath cpResources = resources.get((URLClassLoader)loader);
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

			cpResources = new Classpath(myResources);

			resources.put(cp, cpResources);
		}

		if (triggerNotification) {
			cpResources.triggerNotifications();
		}

		return cpResources.classpaths;
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
