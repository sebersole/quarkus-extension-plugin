package io.github.sebersole.quarkus.tasks;

import java.util.Locale;
import java.util.Set;

import javax.inject.Inject;

import org.gradle.api.DefaultTask;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.ResolvedConfiguration;
import org.gradle.api.artifacts.ResolvedModuleVersion;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskAction;

import io.github.sebersole.quarkus.ExtensionDescriptor;
import io.github.sebersole.quarkus.Names;
import io.github.sebersole.quarkus.ValidationException;

/**
 * @author Steve Ebersole
 */
public abstract class VerifyDependenciesTask extends DefaultTask {
	public static final String TASK_NAME = "verifyQuarkusDependencies";

	@Inject
	public VerifyDependenciesTask(ExtensionDescriptor config) {
		setGroup( Names.TASK_GROUP );
		setDescription( "Verifies that the runtime artifact of the Quarkus extension pulls no deployment artifacts into its runtime-classpath" );
	}

	@TaskAction
	public void verifyDependencies() {
		final JavaPluginExtension javaPluginExtension = getProject().getExtensions().getByType( JavaPluginExtension.class );
		final SourceSetContainer sourceSets = javaPluginExtension.getSourceSets();
		final SourceSet mainSourceSet = sourceSets.getByName( SourceSet.MAIN_SOURCE_SET_NAME );

		final ConfigurationContainer configurations = getProject().getConfigurations();
		final Configuration runtimeDependencies = configurations.getByName( mainSourceSet.getRuntimeClasspathConfigurationName() );

		final ResolvedConfiguration resolvedRuntimeDependencies = runtimeDependencies.getResolvedConfiguration();
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

	}
}
