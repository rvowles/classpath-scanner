package com.bluetrainsoftware.classpathscanner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

/**
 * @author: Richard Vowles - https://plus.google.com/+RichardVowles
 */
public class ClasspathSpecialistLoader {
	private static final Logger log = LoggerFactory.getLogger(ClasspathSpecialistLoader.class);
	public static List<ClasspathScannerSpecialist> specialists = new ArrayList<>();

	static {
		ServiceLoader<ClasspathScannerSpecialist> services = ServiceLoader.load(ClasspathScannerSpecialist.class, Thread.currentThread().getContextClassLoader());

		for(ClasspathScannerSpecialist service : services) {
			log.debug("Classpath Scanner adding support for {}", service.getClass().getName());

			specialists.add(service);
		}
	}
}
