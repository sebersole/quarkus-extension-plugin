package io.github.sebersole.quarkus.tasks;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import javax.inject.Inject;

import org.gradle.api.DefaultTask;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.ResolvedConfiguration;
import org.gradle.api.artifacts.ResolvedModuleVersion;
import org.gradle.api.file.RegularFile;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskAction;

import io.github.sebersole.quarkus.ExtensionDescriptor;
import io.github.sebersole.quarkus.Names;
import io.github.sebersole.quarkus.ValidationException;

import static io.github.sebersole.quarkus.QuarkusExtensionPlugin.groupArtifact;

/**
 * @author Steve Ebersole
 */
@CacheableTask
public abstract class VerifyDependenciesTask extends DefaultTask {
	public static final String TASK_NAME = "verifyQuarkusDependencies";
	public static final String STEPS_LIST_RELATIVE_PATH = "META-INF/quarkus-build-steps.list";
	public static final String EXTENSION_PROPERTIES_RELATIVE_PATH = "META-INF/quarkus-extension.properties";

	private final Property<Configuration> runtimeDependencies;
	private final Property<Configuration> deploymentDependencies;
	private final Provider<RegularFile> output;

	@Inject
	public VerifyDependenciesTask(@SuppressWarnings("unused") ExtensionDescriptor config) {
		setGroup( Names.TASK_GROUP );
		setDescription( "Verifies that the runtime artifact of the Quarkus extension pulls no deployment artifacts into its runtime-classpath" );

		final JavaPluginExtension javaPluginExtension = getProject().getExtensions().getByType( JavaPluginExtension.class );
		final SourceSetContainer sourceSets = javaPluginExtension.getSourceSets();
		final SourceSet mainSourceSet = sourceSets.getByName( SourceSet.MAIN_SOURCE_SET_NAME );

		final ConfigurationContainer configurations = getProject().getConfigurations();

		runtimeDependencies = getProject().getObjects().property( Configuration.class );
		runtimeDependencies.set(
				getProject().provider( () -> configurations.getByName( mainSourceSet.getRuntimeClasspathConfigurationName() ) )
		);

		deploymentDependencies = getProject().getObjects().property( Configuration.class );
		deploymentDependencies.set(
				getProject().provider( () -> {
					final SourceSet deploymentSourceSet = sourceSets.getByName( "deployment" );
					return configurations.getByName( deploymentSourceSet.getRuntimeClasspathConfigurationName() );
				} )
		);

		output = getProject().getLayout().getBuildDirectory().file( "tmp/verifyQuarkusDependencies.txt" );
	}

	@Classpath
	public Provider<Configuration> getRuntimeDependencies() {
		return runtimeDependencies;
	}

	@OutputFile
	public Provider<RegularFile> getOutput() {
		return output;
	}

	@TaskAction
	public void verifyDependencies() {
		final Set<String> runtimeArtifactCoordinates = verifyRuntimeDependencies();
		verifyDeploymentDependencies( runtimeArtifactCoordinates );

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

	private Set<String> verifyRuntimeDependencies() {
		final ResolvedConfiguration resolvedRuntimeDependencies = runtimeDependencies.get().getResolvedConfiguration();
		final Set<ResolvedArtifact> runtimeDependenciesArtifacts = resolvedRuntimeDependencies.getResolvedArtifacts();
		getLogger().info( "Checking `{}` runtime dependencies", runtimeDependenciesArtifacts.size() );

		final Set<String> runtimeArtifactGavs = new HashSet<>();

		for ( ResolvedArtifact resolvedRuntimeDependency : runtimeDependenciesArtifacts ) {
			if ( !"jar".equals( resolvedRuntimeDependency.getExtension() ) ) {
				continue;
			}

			final ResolvedModuleVersion dependencyModuleVersion = resolvedRuntimeDependency.getModuleVersion();
			getLogger().debug( "Checking runtime dependency - {}", dependencyModuleVersion.getId() );

			withJarFile( resolvedRuntimeDependency.getFile(), (jar) -> {
				if ( hasBuildStepsList( jar ) ) {
					throw new ValidationException(
							String.format(
									Locale.ROOT,
									"The extension's runtime classpath depends on a deployment artifact : `%s:%s:%s`",
									dependencyModuleVersion.getId().getGroup(),
									dependencyModuleVersion.getId().getName(),
									dependencyModuleVersion.getId().getVersion()
							)
					);
				}

				if ( hasExtensionProperties( jar ) ) {
					final ModuleVersionIdentifier moduleId = resolvedRuntimeDependency.getModuleVersion().getId();
					runtimeArtifactGavs.add( groupArtifact( moduleId.getGroup(), moduleId.getName() ) );
				}
			} );
		}

		return runtimeArtifactGavs;
	}

	private void withJarFile(File file, Consumer<JarFile> jarFileConsumer) {
		try {
			final JarFile jarFile = new JarFile( file );
			jarFileConsumer.accept( jarFile );
		}
		catch (IOException e) {
			throw new RuntimeException( "Unable to treat file as JarFile - " + file.getAbsolutePath(), e );
		}
	}

	private boolean hasBuildStepsList(JarFile jarFile) {
		final JarEntry jarEntry = jarFile.getJarEntry( STEPS_LIST_RELATIVE_PATH );
		return jarEntry != null;
	}

	private boolean hasExtensionProperties(JarFile jarFile) {
		final JarEntry jarEntry = jarFile.getJarEntry( EXTENSION_PROPERTIES_RELATIVE_PATH );
		return jarEntry != null;
	}

	private void verifyDeploymentDependencies(Set<String> runtimeArtifactCoordinates) {
		final ResolvedConfiguration resolvedDeploymentDependencies = deploymentDependencies.get().getResolvedConfiguration();
		resolvedDeploymentDependencies.getResolvedArtifacts().forEach( (artifact) -> {
			final ModuleVersionIdentifier moduleId = artifact.getModuleVersion().getId();
			runtimeArtifactCoordinates.remove( groupArtifact( moduleId.getGroup(), moduleId.getName() ) );
		} );

		if ( ! runtimeArtifactCoordinates.isEmpty() ) {
			final StringBuilder buffer = new StringBuilder( "The dependency classpath defined dependencies on the following extension runtime artifacts : [" );

			runtimeArtifactCoordinates.forEach( (gav) -> {
				buffer.append( gav ).append( ", " );
			} );

			buffer.append( "]" );

			throw new ValidationException( buffer.toString() );
		}
	}
}
