package io.github.sebersole.quarkus.tasks;

import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.Set;
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
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskAction;

import io.github.sebersole.quarkus.ExtensionDescriptor;
import io.github.sebersole.quarkus.Names;
import io.github.sebersole.quarkus.ValidationException;

/**
 * @author Steve Ebersole
 */
@CacheableTask
public abstract class VerifyDependenciesTask extends DefaultTask {
	public static final String TASK_NAME = "verifyQuarkusDependencies";

	private final Property<Configuration> runtimeDependencies;
	private final Provider<RegularFile> output;

	@Inject
	public VerifyDependenciesTask(ExtensionDescriptor config) {
		setGroup( Names.TASK_GROUP );
		setDescription( "Verifies that the runtime artifact of the Quarkus extension pulls no deployment artifacts into its runtime-classpath" );

		final JavaPluginExtension javaPluginExtension = getProject().getExtensions().getByType( JavaPluginExtension.class );
		final SourceSetContainer sourceSets = javaPluginExtension.getSourceSets();
		final SourceSet mainSourceSet = sourceSets.getByName( SourceSet.MAIN_SOURCE_SET_NAME );
		final ConfigurationContainer configurations = getProject().getConfigurations();

		runtimeDependencies = getProject().getObjects().property( Configuration.class );
		runtimeDependencies.set( configurations.getByName( mainSourceSet.getRuntimeClasspathConfigurationName() ) );

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
		final ResolvedConfiguration resolvedRuntimeDependencies = runtimeDependencies.get().getResolvedConfiguration();
		final Set<ResolvedArtifact> runtimeDependenciesArtifacts = resolvedRuntimeDependencies.getResolvedArtifacts();
		getLogger().info( "Checking `{}` runtime dependencies", runtimeDependenciesArtifacts.size() );
		for ( ResolvedArtifact resolvedRuntimeDependency : runtimeDependenciesArtifacts ) {
			final ResolvedModuleVersion dependencyModuleVersion = resolvedRuntimeDependency.getModuleVersion();
			final String dependencyArtifactId = dependencyModuleVersion.getId().getName();

			// for the time being we just check the artifactId to make sure it does not end in `-deployment`
			//
			// a better option would be to check each artifact file(s) for a `META-INF/quarkus-build-steps.list`
			// file, but not sure that that is a requirement for deployment artifacts

			if ( dependencyArtifactId.endsWith( "-deployment" ) ) {
				throw new ValidationException(
						String.format(
								Locale.ROOT,
								"The extension's runtime classpath depends on a deployment artifact : `%s:%s:%s`",
								dependencyModuleVersion.getId().getGroup(),
								dependencyArtifactId,
								dependencyModuleVersion.getId().getVersion()
						)
				);
			}
		}

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
