package io.github.sebersole.quarkus.tasks;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import javax.inject.Inject;

import org.gradle.api.DefaultTask;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.ResolvedConfiguration;
import org.gradle.api.file.RegularFile;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskAction;

import io.github.sebersole.quarkus.ExtensionDescriptor;
import io.github.sebersole.quarkus.Helper;
import io.github.sebersole.quarkus.Names;
import io.github.sebersole.quarkus.ValidationException;

import static io.github.sebersole.quarkus.Helper.groupArtifactVersion;
import static io.github.sebersole.quarkus.Helper.hasExtensionProperties;

/**
 * @author Steve Ebersole
 */
@CacheableTask
public abstract class VerifyDeploymentDependencies extends DefaultTask {
	public static final String TASK_NAME = "verifyDeploymentDependencies";

	private final Property<Configuration> deploymentDependencies;
	private final Provider<RegularFile> dependencyCatalog;

	private final Provider<RegularFile> output;

	@Inject
	public VerifyDeploymentDependencies(@SuppressWarnings("unused") ExtensionDescriptor config) {
		setGroup( Names.TASK_GROUP );
		setDescription( "Verifies the runtime and deployment classpaths for the Quarkus extension" );

		final JavaPluginExtension javaPluginExtension = getProject().getExtensions().getByType( JavaPluginExtension.class );
		final SourceSetContainer sourceSets = javaPluginExtension.getSourceSets();

		final ConfigurationContainer configurations = getProject().getConfigurations();

		deploymentDependencies = getProject().getObjects().property( Configuration.class );
//		deploymentDependencies.set(
//				sourceSets.named( "deployment" ).map( sourceSet -> configurations.getByName( sourceSet.getRuntimeClasspathConfigurationName() ) )
//		);
		deploymentDependencies.set(
				getProject().provider( () -> {
					final SourceSet deploymentSourceSet = sourceSets.getByName( "deployment" );
					return configurations.getByName( deploymentSourceSet.getRuntimeClasspathConfigurationName() );
				} )
		);

		final VerifyRuntimeDependencies verifyRuntimeDependencies = (VerifyRuntimeDependencies) getProject().getTasks().getByName( VerifyRuntimeDependencies.TASK_NAME );
		dependencyCatalog = verifyRuntimeDependencies.getOutput();

		output = getProject().getLayout().getBuildDirectory().file( "tmp/verifyQuarkusDependencies.txt" );

		dependsOn( verifyRuntimeDependencies );
	}

	@Classpath
	public Provider<Configuration> getDeploymentDependencies() {
		return deploymentDependencies;
	}

	@InputFile
	@PathSensitive( PathSensitivity.RELATIVE )
	public Provider<RegularFile> getDependencyCatalog() {
		return dependencyCatalog;
	}

	@OutputFile
	public Provider<RegularFile> getOutput() {
		return output;
	}

	@TaskAction
	public void verifyDependencies() {
		final Properties catalog = loadCatalog();
		verifyDeploymentDependencies( catalog );

		touchOutputFile();
	}

	private Properties loadCatalog() {
		final Properties catalog = new Properties();

		final RegularFile catalogFile = dependencyCatalog.get();
		final File catalogFileAsFile = catalogFile.getAsFile();
		try ( final InputStream stream = new FileInputStream( catalogFileAsFile ) ) {
			catalog.load( stream );
		}
		catch (IOException e) {
			throw new RuntimeException( "Unable to load catalog file - " + catalogFileAsFile.getAbsolutePath(), e );
		}

		return catalog;
	}

	private void verifyDeploymentDependencies(Properties catalog) {
		final ResolvedConfiguration resolvedDeploymentDependencies = deploymentDependencies.get().getResolvedConfiguration();
		final Set<String> extensionsOnDeploymentClasspath = new HashSet<>();
		resolvedDeploymentDependencies.getResolvedArtifacts().forEach( (artifact) -> {
			Helper.withJarFile( artifact.getFile(), (jarFile) -> {
				if ( hasExtensionProperties( jarFile ) ) {
					final ModuleVersionIdentifier moduleId = artifact.getModuleVersion().getId();
					extensionsOnDeploymentClasspath.add( groupArtifactVersion( moduleId ) );
				}
			} );
		} );

		extensionsOnDeploymentClasspath.removeAll( catalog.stringPropertyNames() );

		if ( ! extensionsOnDeploymentClasspath.isEmpty() ) {
			final StringBuilder buffer = new StringBuilder( "The dependency classpath defined dependencies on the following extension runtime artifacts : [" );
			extensionsOnDeploymentClasspath.forEach( (gav) -> buffer.append( gav ).append( ", " ) );
			buffer.append( "]" );

			throw new ValidationException( buffer.toString() );
		}
	}

	private void touchOutputFile() {
		final File outputAsFile = output.get().getAsFile();

		if ( ! outputAsFile.getParentFile().exists() ) {
			if ( !outputAsFile.getParentFile().mkdirs() ) {
				getProject().getLogger().warn( "Unable to create output file directories" );
			}
		}

		try {
			final boolean created = outputAsFile.createNewFile();
			if ( !created ) {
				getProject().getLogger().warn( "Unable to create output file" );
			}
		}
		catch (IOException e) {
			getProject().getLogger().warn( "Unable to create output file", e );
		}
	}

}
