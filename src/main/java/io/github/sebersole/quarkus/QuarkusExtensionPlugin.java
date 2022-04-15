package io.github.sebersole.quarkus;

import java.util.Locale;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.file.Directory;
import org.gradle.api.plugins.JavaLibraryPlugin;
import org.gradle.api.provider.Provider;
import org.gradle.api.publish.PublicationContainer;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.publish.maven.MavenPublication;
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin;
import org.gradle.api.publish.plugins.PublishingPlugin;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.javadoc.Javadoc;
import org.gradle.jvm.tasks.Jar;

import static io.github.sebersole.quarkus.QuarkusExtensionConfig.DSL_EXTENSION_NAME;
import static io.github.sebersole.quarkus.QuarkusExtensionConfig.QUARKUS_BOM;
import static io.github.sebersole.quarkus.QuarkusExtensionConfig.QUARKUS_CORE;
import static io.github.sebersole.quarkus.QuarkusExtensionConfig.QUARKUS_CORE_DEPLOYMENT;
import static io.github.sebersole.quarkus.QuarkusExtensionConfig.QUARKUS_GROUP;
import static io.github.sebersole.quarkus.QuarkusExtensionConfig.QUARKUS_UNIVERSE_COMMUNITY_BOM;

/**
 * Plugin for defining Quarkus extensions
 *
 * @author Steve Ebersole
 */
public class QuarkusExtensionPlugin implements Plugin<Project> {
	private Project project;

	@Override
	public void apply(Project target) {
		project = target;

		project.getPluginManager().apply( JavaLibraryPlugin.class );
		//project.getPluginManager().apply( PublishingPlugin.class );
		project.getPluginManager().apply( MavenPublishPlugin.class );

		final QuarkusExtensionConfig config = project.getExtensions().create(
				DSL_EXTENSION_NAME,
				QuarkusExtensionConfig.class,
				project
		);

		preparePlatforms( config, project );
		prepareRuntime( config, project );
		prepareDeployment( config, project );
		prepareSpi( config, project );
	}

	private void preparePlatforms(QuarkusExtensionConfig config, Project project) {
		final Configuration quarkusPlatforms = project.getConfigurations().maybeCreate( "quarkusPlatforms" );
		quarkusPlatforms.setDescription( "Configuration to specify all Quarkus platforms (BOMs) to be applied" );

		project.getConfigurations().all( (configuration) -> {
			if ( configuration != quarkusPlatforms ) {
				configuration.extendsFrom( quarkusPlatforms );
			}
		} );

		project.getDependencies().add(
				quarkusPlatforms.getName(),
				config.getQuarkusVersionProperty().map( (version) -> quarkusPlatform( version, project ) )
		);

		project.getDependencies().add(
				quarkusPlatforms.getName(),
				config.getApplyUniversePlatformProperty().map( (enabled) -> universePlatform( enabled, project, config ) )
		);
	}

	private void prepareRuntime(QuarkusExtensionConfig config, Project project) {
		project.getDependencies().add(
				"implementation",
				quarkusCore( project )
		);

		final PublishingExtension publishingExtension = project.getExtensions().getByType( PublishingExtension.class );
		final PublicationContainer publications = publishingExtension.getPublications();
		publications.create( "runtime", MavenPublication.class, (pub) -> {
			pub.setArtifactId( project.getName() );
			pub.from( project.getComponents().getByName( "java" ) );
		} );
	}

	private void prepareDeployment(QuarkusExtensionConfig config, Project project) {
		final SourceSetContainer sourceSets = project.getExtensions().getByType( SourceSetContainer.class );
		final SourceSet deploymentSourceSet = sourceSets.maybeCreate( "deployment" );

		final SourceSet mainSourceSet = sourceSets.getByName( "main" );
		final SourceSet testSourceSet = sourceSets.getByName( "test" );

//		final Jar deploymentJarTask = taskContainer.create( "deploymentJar", Jar.class, (task) -> {
//			task.setDescription( "Creates the deployment artifact" );
//			task.dependsOn( taskContainer.getByName( "compileDeploymentJava" ) );
//
//			task.getArchiveBaseName().set( mainJarTask.getArchiveBaseName() );
//			task.getArchiveAppendix().set( "deployment" );
//
//			task.from( sourceSets.getByName( "deployment" ).getJava().getDestinationDirectory() );
//			task.getDestinationDirectory().set( mainJarTask.getDestinationDirectory() );
//		} );
//

		preparePublication( deploymentSourceSet, config, project );

		final TaskContainer taskContainer = project.getTasks();
		final Jar mainJarTask = (Jar) taskContainer.getByName( mainSourceSet.getJarTaskName() );
		final Jar deploymentJarTask = (Jar) taskContainer.getByName( deploymentSourceSet.getJarTaskName() );

		project.getDependencies().add(
				deploymentSourceSet.getImplementationConfigurationName(),
				quarkusCoreDeployment( project )
		);
		project.getDependencies().add(
				deploymentSourceSet.getCompileOnlyConfigurationName(),
				project.files( mainJarTask.getArchiveFile() )
		);

		project.getDependencies().add(
				testSourceSet.getImplementationConfigurationName(),
				project.files( deploymentJarTask.getArchiveFile() )
		);

		taskContainer.getByName( testSourceSet.getCompileJavaTaskName() ).dependsOn( deploymentJarTask );
	}

	private void preparePublication(SourceSet sourceSet, QuarkusExtensionConfig config, Project project) {
		final String publicationName = sourceSet.getName();

		final SourceSetContainer sourceSets = project.getExtensions().getByType( SourceSetContainer.class );
		final SourceSet mainSourceSet = sourceSets.getByName( "main" );

		final TaskContainer taskContainer = project.getTasks();
		final Jar mainJarTask = (Jar) taskContainer.getByName( mainSourceSet.getJarTaskName() );
		final Jar deploymentJarTask = taskContainer.create( sourceSet.getJarTaskName(), Jar.class, (task) -> {
			task.setDescription( "Creates the " + publicationName + " artifact" );
			task.dependsOn( taskContainer.getByName( sourceSet.getCompileJavaTaskName() ) );

			task.getArchiveBaseName().set( mainJarTask.getArchiveBaseName() );
			task.getArchiveAppendix().set( publicationName );
			task.getArchiveVersion().set( project.provider( () -> project.getVersion().toString() ) );

			task.from( sourceSet.getJava().getDestinationDirectory() );
			task.getDestinationDirectory().set( mainJarTask.getDestinationDirectory() );
		} );

		// for the moment, just support Maven publishing
		final PublishingExtension publishingExtension = project.getExtensions().getByType( PublishingExtension.class );
		final PublicationContainer publications = publishingExtension.getPublications();
		final MavenPublication publication = publications.create( publicationName, MavenPublication.class );
		publication.artifact( deploymentJarTask );

		if ( project.getTasks().findByName( mainSourceSet.getJavadocJarTaskName() ) != null ) {
			// assume to publish the deployment's javadoc jar as well
			// todo : or add a DSL flag?  or always do it?

			final Provider<Directory> javadocDir = project.getLayout().getBuildDirectory().dir( "docs/javadoc-deployment" );

			final Javadoc javadocTask = taskContainer.create( sourceSet.getJavadocTaskName(), Javadoc.class, (task) -> {
				task.setDescription( "Generates the deployment Javadocs" );
				task.dependsOn( sourceSet.getCompileJavaTaskName(), sourceSet.getProcessResourcesTaskName() );

				task.source( sourceSet.getJava().getDestinationDirectory() );
				task.setClasspath( sourceSet.getCompileClasspath() );
				task.setDestinationDir( javadocDir.get().getAsFile() );
			} );

			final Jar javadocJarTask = taskContainer.create( sourceSet.getJavadocJarTaskName(), Jar.class );
			javadocJarTask.setDescription( "Creates the " + publicationName + " Javadoc artifact" );
			javadocJarTask.dependsOn( javadocTask );

			javadocJarTask.getArchiveBaseName().set( mainJarTask.getArchiveBaseName() );
			javadocJarTask.getArchiveAppendix().set( "publicationName" );
			javadocJarTask.getArchiveClassifier().set( "javadoc" );
			javadocJarTask.getArchiveVersion().set( project.provider( () -> project.getVersion().toString() ) );

			javadocJarTask.from( javadocDir );
			javadocJarTask.getDestinationDirectory().set( mainJarTask.getDestinationDirectory() );

			publication.artifact( javadocJarTask );

			final Jar sourcesJarTask = taskContainer.create( sourceSet.getSourcesJarTaskName(), Jar.class );
			sourcesJarTask.setDescription( "Creates the " + publicationName + " sources artifact" );

			sourcesJarTask.getArchiveBaseName().set( mainJarTask.getArchiveBaseName() );
			sourcesJarTask.getArchiveAppendix().set( publicationName );
			sourcesJarTask.getArchiveClassifier().set( "sources" );
			sourcesJarTask.getArchiveVersion().set( project.provider( () -> project.getVersion().toString() ) );

			sourcesJarTask.from( sourceSet.getAllSource() );
			sourcesJarTask.getDestinationDirectory().set( mainJarTask.getDestinationDirectory() );

			publication.artifact( sourcesJarTask );
		}
	}

	private void prepareSpi(QuarkusExtensionConfig config, Project project) {
		final SourceSetContainer sourceSets = project.getExtensions().getByType( SourceSetContainer.class );
		final SourceSet spiSourceSet = sourceSets.maybeCreate( "spi" );
		final SourceSet mainSourceSet = sourceSets.getByName( "main" );

		project.afterEvaluate( (p) -> {
			if ( spiSourceSet.getJava().isEmpty() && spiSourceSet.getResources().isEmpty() ) {
				project.getLogger().debug( "Skipping SPI module set-up : no sources" );
				return;
			}

			project.getLogger().debug( "Starting SPI module set-up" );

			preparePublication( spiSourceSet, config, project );

			final Jar spiJarTask = (Jar) project.getTasks().getByName( spiSourceSet.getJarTaskName() );
			project.getDependencies().add(
					mainSourceSet.getImplementationConfigurationName(),
					project.files( spiJarTask.getArchiveFile() )
			);

		} );
	}

	private Dependency quarkusCore(Project project) {
		return project.getDependencies().module( groupArtifact( QUARKUS_GROUP, QUARKUS_CORE ) );
	}

	private Dependency quarkusCoreDeployment(Project project) {
		return project.getDependencies().module( groupArtifact( QUARKUS_GROUP, QUARKUS_CORE_DEPLOYMENT ) );
	}

	private Dependency quarkusPlatform(String version, Project project) {
		return project.getDependencies().enforcedPlatform( groupArtifactVersion( QUARKUS_GROUP, QUARKUS_BOM, version ) );
	}

	private Dependency universePlatform(Boolean enabled, Project project, QuarkusExtensionConfig config) {
		if ( enabled != Boolean.TRUE ) {
			return null;
		}

		return project.getDependencies().enforcedPlatform(
				groupArtifactVersion( QUARKUS_GROUP, QUARKUS_UNIVERSE_COMMUNITY_BOM, config.getQuarkusVersion() )
		);
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

	public static String groupArtifact(String group, String artifact) {
		return String.format(
				Locale.ROOT,
				"%s:%s",
				group,
				artifact
		);
	}
}
