package com.bluetrainsoftware.classpathscanner;

import java.io.IOException;
import java.util.List;

/**
 * @author: Richard Vowles - https://plus.google.com/+RichardVowles
 */
public interface ClasspathScannerSpecialist {
	public boolean handlesClasspathResource(ClasspathResource resource, List<ResourceScanListener.ScanResource> scanResources) throws IOException;
}
