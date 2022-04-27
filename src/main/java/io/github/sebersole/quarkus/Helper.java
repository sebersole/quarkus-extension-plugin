package io.github.sebersole.quarkus;

import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.gradle.api.Project;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ModuleVersionIdentifier;

import static io.github.sebersole.quarkus.Names.QUARKUS_CORE;
import static io.github.sebersole.quarkus.Names.QUARKUS_CORE_DEPLOYMENT;
import static io.github.sebersole.quarkus.Names.QUARKUS_GROUP;

/**
 * @author Steve Ebersole
 */
public class Helper {
	public static final String STEPS_LIST_RELATIVE_PATH = "META-INF/quarkus-build-steps.list";
	public static final String EXTENSION_PROPERTIES_RELATIVE_PATH = "META-INF/quarkus-extension.properties";

	private Helper() {
		// disallow direct instantiation
	}

	public static void withJarFile(File file, Consumer<JarFile> jarFileConsumer) {
		try {
			final JarFile jarFile = new JarFile( file );
			jarFileConsumer.accept( jarFile );
		}
		catch (IOException e) {
			throw new RuntimeException( "Unable to treat file as JarFile - " + file.getAbsolutePath(), e );
		}
	}

	public static boolean hasBuildStepsList(JarFile jarFile) {
		final JarEntry jarEntry = jarFile.getJarEntry( STEPS_LIST_RELATIVE_PATH );
		return jarEntry != null;
	}

	public static boolean hasExtensionProperties(JarFile jarFile) {
		final JarEntry jarEntry = jarFile.getJarEntry( EXTENSION_PROPERTIES_RELATIVE_PATH );
		return jarEntry != null;
	}

	static Dependency quarkusCore(Project project) {
		return project.getDependencies().module( groupArtifact( QUARKUS_GROUP, QUARKUS_CORE ) );
	}

	static Dependency quarkusCoreDeployment(Project project) {
		return project.getDependencies().module( groupArtifact( QUARKUS_GROUP, QUARKUS_CORE_DEPLOYMENT ) );
	}

	public static String groupArtifact(String group, String artifact) {
		return String.format(
				Locale.ROOT,
				"%s:%s",
				group,
				artifact
		);
	}

	public static String groupArtifactVersion(ModuleVersionIdentifier coordinate) {
		return groupArtifactVersion( coordinate.getGroup(), coordinate.getName(), coordinate.getVersion() );
	}

	public static String groupArtifactVersion(String group, String artifact, String version) {
		return String.format(
				Locale.ROOT,
				"%s:%s:%s",
				group,
				artifact,
				version
		);
	}
}
