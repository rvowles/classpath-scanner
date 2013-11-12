package com.bluetrainsoftware.classpathscanner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URL;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * @author: Richard Vowles - https://plus.google.com/+RichardVowles
 */
public class ClasspathResource {
	private final static Logger log = LoggerFactory.getLogger(ClasspathResource.class);
	private static final int MAX_RESOURCES = 3000;

	/**
	 * The original resources.
	 */
	private final URL url;

	/**
	 * The war/jar file that contains the actual files
	 */
	private final File classesSource;

	/**
	 * Optimization step - there is only the null jar offset
	 */
	private boolean onlyNullJarOffset;


	/**
	 * Allows us to keep a track of who is interested in this classpath artifact
	 *
	 * @param listeners
	 */
	public void askListeners(List<ResourceScanListener> listeners) {
		if (jarOffsets.size() == 0) {
			OffsetListener offsetListener = new OffsetListener();

			offsetListener.jarOffset = "";
			offsetListener.url = url;

			jarOffsets.add(offsetListener);

			onlyNullJarOffset = true;
		}

		for (ResourceScanListener listener : listeners) {
			try {
				for (OffsetListener offsetListener : jarOffsets) {
					if (listener.isInteresting(offsetListener.url)) {
						offsetListener.listeners.add(listener);
					}
				}
			} catch (Exception ex) {
				throw new RuntimeException("Failed to ask listener for interest " + listener.getClass().getName() + ": " + ex.getMessage(), ex);
			}
		}
	}

	/**
	 * Spelunks through the classpath looking for the resources
	 */
	public void fireListeners() {
		if (jarOffsets.size() == 1 && jarOffsets.iterator().next().listeners.size() == 0) {
			return; // no-one is interested
		}

		List<ResourceScanListener.Resource> resources = new ArrayList<>(MAX_RESOURCES);

		if (classesSource.isDirectory()) {
			OffsetListener listener = jarOffsets.iterator().next();

			// only process if anyone is listening
			if (listener.listeners.size() > 0) {
				processDirectory(resources, classesSource, "", listener);

				fireListeners(resources, listener);
			}
		} else {
			processJarFile(resources);
		}
	}

	protected void processDirectory(List<ResourceScanListener.Resource> resources, File dir, String packageName, OffsetListener listener) {
		File[] files = dir.listFiles();

		if (files != null) {
			for (File file : files) {
				if (file.isDirectory()) {
					// as long as it isn't a "hidden" directory

					if (!file.getName().startsWith(".")) {
						String newPackageName = packageName;

						if (packageName.length() > 0) {
							newPackageName += ".";
						}

						processDirectory(resources, file, newPackageName + file.getName(), listener);
					}
				} else {
					resources.add(new ResourceScanListener.Resource(url, file, packageName + file.getName()));

					if (resources.size() >= MAX_RESOURCES) {
						fireListeners(resources, listener);
					}
				}
			}
		}

	}

	private void fireListeners(List<ResourceScanListener.Resource> resources, OffsetListener offsetListener) {
		if (resources.size() > 0) {

			for (ResourceScanListener listener : offsetListener.listeners) {
				try {
					List<ResourceScanListener.Resource> desired = listener.resource(resources);

					for (ResourceScanListener.Resource desire : desired) {
						FileInputStream stream = new FileInputStream(desire.file);

						listener.deliver(desire, stream);

						stream.close();
					}
				} catch (Exception e) {
					throw new RuntimeException("Unable to ask listener for resources", e);
				}
			}

			resources.clear();
		}
	}

	protected void processJarFile(List<ResourceScanListener.Resource> resources) {

		try {
			JarFile jf = new JarFile(classesSource);

			Enumeration<JarEntry> entries = jf.entries();

			String lastPrefix = "";
			int offsetStrip = 0;
			URL currentUrl = url;
			OffsetListener offsetListener = null;
			boolean thereAreListeners = false;

			if (onlyNullJarOffset) {
				offsetListener = jarOffsets.iterator().next();
				thereAreListeners = offsetListener.listeners.size() > 0;
			}

			while (entries.hasMoreElements()) {
				JarEntry entry = entries.nextElement();

				if (!onlyNullJarOffset && (lastPrefix.length() == 0 || !entry.getName().startsWith(lastPrefix))) {
					OffsetListener newOffsetListener = findOffsetListener(entry.getName());

					if (newOffsetListener != offsetListener) {
						fireListeners(resources, offsetListener, jf);

						offsetListener = newOffsetListener;

						thereAreListeners = offsetListener.listeners.size() > 0;

						lastPrefix = offsetListener.jarOffset;

						offsetStrip = lastPrefix.length();
					}
				} else if (resources.size() >= MAX_RESOURCES) {
					fireListeners(resources, offsetListener, jf);
				}

				if (thereAreListeners) {
					resources.add(new ResourceScanListener.Resource(currentUrl, entry, offsetStrip > 0 ? entry.getName().substring(offsetStrip) : entry.getName()));
				}
			}

			// anything remaining
			fireListeners(resources, offsetListener, jf);

			jf.close();
		} catch (IOException e) {
			throw new RuntimeException("Failed to process jar file " + url.toString(), e);
		}
	}

	private void fireListeners(List<ResourceScanListener.Resource> resources, OffsetListener offsetListener, JarFile jf) {
		if (resources.size() > 0) {

			for (ResourceScanListener listener : offsetListener.listeners) {
				try {
					List<ResourceScanListener.Resource> desired = listener.resource(resources);

					for (ResourceScanListener.Resource desire : desired) {
						listener.deliver(desire, jf.getInputStream(desire.entry));
					}
				} catch (Exception e) {
					throw new RuntimeException("Unable to ask listener for resources", e);
				}
			}

			resources.clear();
		}
	}

	/**
	 * Finds the name of the matching offset listener for this resource
	 *
	 * @param name - the name found inside the entry
	 * @return - the matching listener. If an "empty" one is found then use that as last resort.
	 */
	private OffsetListener findOffsetListener(String name) {
		OffsetListener emptyListener = null;

		for (OffsetListener listener : jarOffsets) {
			if (listener.jarOffset.length() == 0) {
				emptyListener = listener;
			} else if (name.startsWith(listener.jarOffset)) {
				return listener;
			}
		}

		return emptyListener;
	}

	class OffsetListener implements Comparable<OffsetListener> {
		public URL url;
		public String jarOffset;
		public Set<ResourceScanListener> listeners = new HashSet<>();

		@Override
		public int compareTo(OffsetListener o) {
			return o.jarOffset.compareTo(jarOffset);
		}
	}

	/**
	 * Used to store the offset(s) in the classesSource, there can be multiple offsets for
	 * one single file in a URL Class Path (e.g. Bathe Booter/Plugin)
	 */
	private Set<OffsetListener> jarOffsets = new TreeSet<>();

	public ClasspathResource(File jarFile, URL url) {
		this.classesSource = jarFile;
		this.url = url;
	}

	public URL getUrl() {
		return url;
	}

	public File getClassesSource() {
		return classesSource;
	}

	public Set<OffsetListener> getJarOffsets() {
		return jarOffsets;
	}

	public void addJarOffset(String offset, URL url) {
		OffsetListener listener = new OffsetListener();

		listener.jarOffset = offset.startsWith("/") ? offset.substring(1) : offset;
		listener.url = url;

		jarOffsets.add(listener);
	}

	public boolean isTestClasspath() {
		return (classesSource.isDirectory() && classesSource.getAbsolutePath().endsWith("target/test-classes"));
	}
}
