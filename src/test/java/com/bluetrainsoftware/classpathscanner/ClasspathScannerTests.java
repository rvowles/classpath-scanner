package com.bluetrainsoftware.classpathscanner;

import junit.framework.Assert;
import org.apache.commons.io.IOUtils;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static junit.framework.Assert.assertNotNull;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author: Richard Vowles - https://plus.google.com/+RichardVowles
 */
public class ClasspathScannerTests {
	Logger log = LoggerFactory.getLogger(getClass());

	protected void action(Map<ResourceScanListener.ScanAction, Integer> scanChecker, ResourceScanListener.ScanAction action) {
		Integer counter = scanChecker.get(action);

		if (counter == null) {
			scanChecker.put(action, 1);
		} else {
			scanChecker.put(action, counter + 1);
		}
	}

	@Test
	public void cpTest() throws IOException {
		ClasspathScanner.resetScannerForTesting();

		File jarFile = File.createTempFile("bang", ".war");

		URL[] bangUrls =
			createBangJar(jarFile, new String[] {WEB_INF_CLASSES, WEB_INF_MYCLASSES},
				new Class[] {SimpleJarBangClass.class, SimpleJarClass.class});

		File noBangFile = File.createTempFile("nobang", ".jar");

		URL[] normalUrls = createBangJar(noBangFile, new String[] {""},
			new Class[] {SimpleJarBangClass.class, SimpleJarClass.class});

		URL[] all = new URL[bangUrls.length + normalUrls.length + 2];

		all[0] = normalUrls[0];
		all[1] = new File("blah.jar").toURI().toURL();
		all[2] = new URL("jar:file:/whoop/sie/daisy.jar!/myclasses/");

		int count = 3;

		for(URL url: bangUrls) {
			all[count++] = url;
		}

		ClasspathScanner cp = new ClasspathScanner();

		final List<ResourceScanListener.ScanResource> allScanResources = new ArrayList<>();

		final Map<ResourceScanListener.ScanAction, Integer> scanChecker = new HashMap<>();

		cp.registerResourceScanner(new ResourceScanListener() {
			@Override
			public List<ScanResource> resource(List<ScanResource> scanResources) throws Exception {
				allScanResources.addAll(scanResources);

//				for(ScanResource r : scanResources) {
//					log.info("resource is {}:{}", r.url.toString(), r.resourceName);
//				}

				return null;
			}

			@Override
			public void deliver(ScanResource desire, InputStream inputStream) {
			}

			@Override
			public InterestAction isInteresting(InterestingResource interestingResource) {
				return InterestAction.ONCE;
			}

			@Override
			public void scanAction(ScanAction action) {
				action(scanChecker, action);
			}
		});

		URLClassLoader loader = new URLClassLoader(all);

		cp.scan(loader); // we should only be called once
		cp.scan(loader);
		cp.scan(loader);

		ClasspathScanner.Classpath cpResources = ClasspathScanner.resources.get(loader);

		assertNotNull(cpResources);
		assertEquals(2, cpResources.classpaths.size());

		for(ClasspathResource resource : cpResources.classpaths) {
			assertTrue(resource.getClassesSource().equals(jarFile) || resource.getClassesSource().equals(noBangFile));

			if (resource.getClassesSource().equals(jarFile)) {
				assertEquals(2, resource.getJarOffsets().size());
				Set<String> offsets = new HashSet<>();
				for(ClasspathResource.OffsetListener cr: resource.getJarOffsets()) {
					offsets.add(cr.jarOffset);
				}
				assertTrue(offsets.contains(WEB_INF_CLASSES));
				assertTrue(offsets.contains(WEB_INF_MYCLASSES));
			}
		}

		assertEquals("Should have found six classes", 6, allScanResources.size());
		assertEquals("Should always keep a track of one listener for new classpaths", 1, ClasspathScanner.allUncheckedListeners.size());
		Assert.assertEquals("This classpath should have no listeners", 0, ClasspathScanner.resources.get(loader).uncheckedListeners.size());
		assertEquals("Should have two scan actions", 2, scanChecker.size());
		assertEquals("Should have 1 start action1", 1, scanChecker.get(ResourceScanListener.ScanAction.STARTING).intValue());
		assertEquals("Should have 1 complete action1", 1, scanChecker.get(ResourceScanListener.ScanAction.COMPLETE).intValue());
	}

	class MutableInteger {
		int count;
	}
	@Test
	public void bigClassLoader() {
		ClasspathScanner.resetScannerForTesting();
		ClasspathScanner cp = new ClasspathScanner();

		final MutableInteger counter = new MutableInteger();

		cp.registerResourceScanner(new ResourceScanListener() {
			@Override
			public List<ScanResource> resource(List<ScanResource> scanResources) throws Exception {
//				for(Resource r : resources) {
//					log.info("resource is {}:{}", r.url.toString(), r.resourceName);
//				}

				counter.count = counter.count + scanResources.size();

				return null;
			}

			@Override
			public void deliver(ScanResource desire, InputStream inputStream) {
			}

			@Override
			public InterestAction isInteresting(InterestingResource interestingResource) {
				return InterestAction.ONCE;
			}

			@Override
			public void scanAction(ScanAction action) {
			}
		});

		long now = System.currentTimeMillis();
		List<ClasspathResource> cpResources = cp.scan(getClass().getClassLoader());
		log.info("Total time {}ms number {}", System.currentTimeMillis() - now, counter.count);
		for(ClasspathResource resource : cpResources) {
			log.info("Resource is {}", resource.getUrl().toString());
		}
	}

	private static final String WEB_INF_CLASSES = "WEB-INF/classes/";
	private static final String WEB_INF_MYCLASSES = "WEB-INF/jars/my-file-1.1/";


	private URL[] createBangJar(File jarFile, String[] offsets, Class[] clazzes) throws IOException {
		FileOutputStream stream = new FileOutputStream(jarFile);
		JarOutputStream jarOutputStream = new JarOutputStream(stream);

		URL[] urls = new URL[offsets.length];

		int count = 0;

		for(String offset: offsets) {
			for(Class clazz: clazzes) {
				String clazzPath = clazz.getPackage().getName().replace(".", "/") + "/" + clazz.getSimpleName() + ".class";

				JarEntry entry = new JarEntry(offset + clazzPath);
				jarOutputStream.putNextEntry(entry);
				InputStream classStream = getClass().getResourceAsStream("/" + clazzPath);
				IOUtils.copy(classStream, jarOutputStream);
			}

			if (offset.length() > 0) {
				urls[count++] = new URL("jar:" + jarFile.toURI().toString() + "!/" + offset);
			} else {
				urls[count++] = jarFile.toURI().toURL();
			}
		}

		jarOutputStream.close();
		stream.close();

		return urls;
	}
}
