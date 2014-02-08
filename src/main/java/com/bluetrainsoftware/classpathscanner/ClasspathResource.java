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


	class ListenerInterest {
		final public ResourceScanListener listener;
		final public ResourceScanListener.InterestAction action;

		ListenerInterest(ResourceScanListener listener, ResourceScanListener.InterestAction action) {
			this.listener = listener;
			this.action = action;
		}
	}

	class OffsetListener implements Comparable<OffsetListener> {
		public ResourceScanListener.InterestingResource interestingResource;
		public String jarOffset;
		public List<ListenerInterest> listeners = new ArrayList<>();

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


	/**
	 * Allows us to keep a track of who is interested in this classpath artifact
	 *
	 * @param listeners
	 */
	public void askListeners(List<ResourceScanListener> listeners) {
		if (jarOffsets.size() == 0) {
			OffsetListener offsetListener = new OffsetListener();

			offsetListener.jarOffset = "";
			offsetListener.interestingResource = new ResourceScanListener.InterestingResource(url);

			jarOffsets.add(offsetListener);

			onlyNullJarOffset = true;
		}

		for (ResourceScanListener listener : listeners) {
			try {
				for (OffsetListener offsetListener : jarOffsets) {
					ResourceScanListener.InterestAction interestAction = listener.isInteresting(offsetListener.interestingResource);

					if (interestAction != ResourceScanListener.InterestAction.NONE) {
						offsetListener.listeners.add(new ListenerInterest(listener, interestAction));
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

		List<ResourceScanListener.ScanResource> scanResources = new ArrayList<>(MAX_RESOURCES);

		if (classesSource.isDirectory()) {
			OffsetListener listener = jarOffsets.iterator().next();

			// only process if anyone is listening
			if (listener.listeners.size() > 0) {
				processDirectory(scanResources, classesSource, "", listener);

				fireFileResourceListeners(scanResources, listener);
			}
		} else {
			processJarFile(scanResources);
		}
	}



	protected void processDirectory(List<ResourceScanListener.ScanResource> scanResources, File dir, String packageName, OffsetListener listener) {
		File[] files = dir.listFiles();

		if (files != null) {
			for (File file : files) {
				if (file.isDirectory()) {
					// as long as it isn't a "hidden" directory

					if (!file.getName().startsWith(".")) {
						processFile(scanResources, packageName, listener, file); // jars and dirs need to be added as resources

						String newPackageName = packageName;

						if (packageName.length() > 0) {
							newPackageName += "/";
						}

						processDirectory(scanResources, file, newPackageName + file.getName(), listener);
					}
				} else {
					processFile(scanResources, packageName, listener, file);
				}
			}
		}

	}

	private void processFile(List<ResourceScanListener.ScanResource> scanResources, String packageName, OffsetListener listener, File file) {
		scanResources.add(new ResourceScanListener.ScanResource(url, file, packageName + "/" + file.getName()));

		if (scanResources.size() >= MAX_RESOURCES) {
			fireFileResourceListeners(scanResources, listener);
		}
	}

	private void fireFileResourceListeners(List<ResourceScanListener.ScanResource> scanResources, OffsetListener offsetListener) {
		if (scanResources.size() > 0) {

			for (ListenerInterest interested : offsetListener.listeners) {
				try {
					List<ResourceScanListener.ScanResource> desired = interested.listener.resource(scanResources);

					if (desired != null) {
						for (ResourceScanListener.ScanResource desire : desired) {
							if (desire.file.isFile()) {
								FileInputStream stream = new FileInputStream(desire.file);

								interested.listener.deliver(desire, stream);

								stream.close();
							}
						}
					}
				} catch (Exception e) {
					throw new RuntimeException("Unable to ask listener for resources", e);
				}
			}

			scanResources.clear();
		}
	}

	protected void processJarFile(List<ResourceScanListener.ScanResource> scanResources) {

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
				thereAreListeners = offsetListener.listeners != null && offsetListener.listeners.size() > 0;
			}

			while (entries.hasMoreElements()) {
				JarEntry entry = entries.nextElement();

				if (!onlyNullJarOffset && (lastPrefix.length() == 0 || !entry.getName().startsWith(lastPrefix))) {
					OffsetListener newOffsetListener = findOffsetListener(entry.getName());

					if (newOffsetListener != offsetListener) {
						fireListeners(scanResources, offsetListener, jf);

						offsetListener = newOffsetListener;

						thereAreListeners = offsetListener != null && offsetListener.listeners != null && offsetListener.listeners.size() > 0;

						if (offsetListener == null) {
							lastPrefix = "";

							offsetStrip = 0;
						} else { // files from the main war popping up at the end
							lastPrefix = offsetListener.jarOffset;

							offsetStrip = lastPrefix.length();
						}
					}

				} else if (scanResources.size() >= MAX_RESOURCES) {
					fireListeners(scanResources, offsetListener, jf);
				}

				if (thereAreListeners) {
					scanResources.add(new ResourceScanListener.ScanResource(currentUrl, entry, resourceName(offsetStrip, entry.getName()), offsetListener.interestingResource.url));
				}
			}

			// anything remaining
			fireListeners(scanResources, offsetListener, jf);

			jf.close();
		} catch (IOException e) {
			throw new RuntimeException("Failed to process jar file " + url.toString(), e);
		}
	}

	private String resourceName(int offsetStrip, String name) {
		if (offsetStrip > 0) {
			name = name.substring(offsetStrip);
		}

		if (name.endsWith("/")) {
			name = name.substring(0, name.length() - 1);
		}

		return name;
	}

	private void fireListeners(List<ResourceScanListener.ScanResource> scanResources, OffsetListener offsetListener, JarFile jf) {
		if (scanResources.size() > 0) {

			for (ListenerInterest interested : offsetListener.listeners) {
				try {
					List<ResourceScanListener.ScanResource> desired = interested.listener.resource(scanResources);

					if (desired != null) {
						for (ResourceScanListener.ScanResource desire : desired) {
							interested.listener.deliver(desire, jf.getInputStream(desire.entry));
						}
					}
				} catch (Exception e) {
					throw new RuntimeException("Unable to ask listener for resources", e);
				}
			}

			scanResources.clear();
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


	/**
	 * Looks through any offsets and removes any listeners that asked to listen to this
	 * resource only once.
	 */
	public void removeSingleFireListeners() {
		for (OffsetListener listener : jarOffsets) {
			List<ListenerInterest> deleteds = new ArrayList<>();

			// find all offset listeners that only want to be asked once
			for(ListenerInterest check : listener.listeners) {
				if (check.action == ResourceScanListener.InterestAction.ONCE) {
					deleteds.add(check);
				}
			}

			listener.listeners.removeAll(deleteds);
		}
	}

	/**
	 * This is used to collect all of the unique listeners that will be triggered by the next CP scan. It allows us
	 * to notify them.
	 *
	 * @param listeners
	 */
	public void collectInUseListeners(Set<ResourceScanListener> listeners) {
		for(OffsetListener ol : jarOffsets) {
			for(ListenerInterest li : ol.listeners) {
				listeners.add(li.listener);
			}
		}
	}

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
		listener.interestingResource = new ResourceScanListener.InterestingResource(url);

		jarOffsets.add(listener);
	}

	public boolean isTestClasspath() {
		return (classesSource.isDirectory() && classesSource.getAbsolutePath().endsWith("target/test-classes"));
	}
}
