package io.github.sebersole.quarkus.tasks;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.jar.JarEntry;
import javax.inject.Inject;

import org.gradle.api.DefaultTask;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
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
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskAction;

import io.github.sebersole.quarkus.ExtensionDescriptor;
import io.github.sebersole.quarkus.Helper;
import io.github.sebersole.quarkus.Names;
import io.github.sebersole.quarkus.ValidationException;

import static io.github.sebersole.quarkus.Helper.EXTENSION_PROPERTIES_RELATIVE_PATH;
import static io.github.sebersole.quarkus.Helper.groupArtifactVersion;
import static io.github.sebersole.quarkus.Helper.withJarFile;

/**
 * Resolves the extension projects' `runtimeClasspath` dependencies, and iterates
 * them looking for extensions (by presence of `META-INF/quarkus-extension.properties`)
 * and saving the mapping of an extension to its deployment artifact
 *
 * @author Steve Ebersole
 */
@CacheableTask
public class VerifyRuntimeDependencies extends DefaultTask {
	public static final String TASK_NAME = "verifyRuntimeDependencies";

	private final Property<Configuration> runtimeDependencies;
	private final Provider<RegularFile> output;

	@Inject
	public VerifyRuntimeDependencies(@SuppressWarnings("unused") ExtensionDescriptor config) {
		setGroup( Names.TASK_GROUP );
		setDescription( "Verifies `runtimeClasspath`, making sure there are no deployment artifacts" );

		final JavaPluginExtension javaPluginExtension = getProject().getExtensions().getByType( JavaPluginExtension.class );
		final SourceSetContainer sourceSets = javaPluginExtension.getSourceSets();
		final SourceSet mainSourceSet = sourceSets.getByName( SourceSet.MAIN_SOURCE_SET_NAME );

		final ConfigurationContainer configurations = getProject().getConfigurations();

		runtimeDependencies = getProject().getObjects().property( Configuration.class );
		runtimeDependencies.set(
				getProject().provider( () -> configurations.getByName( mainSourceSet.getRuntimeClasspathConfigurationName() ) )
		);

		output = getProject().getLayout().getBuildDirectory().file( "quarkus/runtime-dependencies-catalog.properties" );
	}

	@Classpath
	public Property<Configuration> getRuntimeDependencies() {
		return runtimeDependencies;
	}

	@OutputFile
	public Provider<RegularFile> getOutput() {
		return output;
	}

	@TaskAction
	public void verifyDependencies() {
		final Properties catalog = generateCatalog();
		storeCatalog( catalog );
	}

	private Properties generateCatalog() {
		final ResolvedConfiguration resolvedRuntimeDependencies = runtimeDependencies.get().getResolvedConfiguration();
		final Set<ResolvedArtifact> runtimeDependenciesArtifacts = resolvedRuntimeDependencies.getResolvedArtifacts();
		getLogger().info( "Checking `{}` runtime dependencies", runtimeDependenciesArtifacts.size() );

		final Properties catalog = new Properties();


		for ( ResolvedArtifact resolvedRuntimeDependency : runtimeDependenciesArtifacts ) {
			if ( !"jar".equals( resolvedRuntimeDependency.getExtension() ) ) {
				continue;
			}

			final ResolvedModuleVersion dependencyModuleVersion = resolvedRuntimeDependency.getModuleVersion();
			getLogger().debug( "Checking runtime dependency - {}", dependencyModuleVersion.getId() );

			withJarFile( resolvedRuntimeDependency.getFile(), (jarFile) -> {
				if ( Helper.hasBuildStepsList( jarFile ) ) {
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

				final JarEntry jarEntry = jarFile.getJarEntry( EXTENSION_PROPERTIES_RELATIVE_PATH );
				if ( jarEntry != null ) {
					final String extensionCoordinate = groupArtifactVersion( resolvedRuntimeDependency.getModuleVersion().getId() );

					try ( final InputStream stream = jarFile.getInputStream( jarEntry ) ) {
						final Properties deploymentMapping = new Properties();
						deploymentMapping.load( stream );
						catalog.put( extensionCoordinate, deploymentMapping.getProperty( "deployment-artifact" ) );
					}
					catch (IOException e) {
						throw new RuntimeException( "Error accessing quarkus-extension.properties for `" + extensionCoordinate + "`", e );
					}
				}
			} );
		}

		return catalog;
	}

	private void storeCatalog(Properties catalog) {
		final RegularFile output = this.getOutput().get();
		final File outputAsFile = output.getAsFile();
		try ( final OutputStream stream = new FileOutputStream( outputAsFile) ) {
			catalog.store( stream, null );
		}
		catch (IOException e) {
			throw new RuntimeException( "Error storing extension catalog - " + outputAsFile.getAbsolutePath(), e );
		}
	}

}
