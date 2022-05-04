package io.github.sebersole.quarkus;

import javax.inject.Inject;

import org.gradle.api.Action;
import org.gradle.api.JavaVersion;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationPublications;
import org.gradle.api.attributes.Bundling;
import org.gradle.api.attributes.Category;
import org.gradle.api.attributes.DocsType;
import org.gradle.api.attributes.LibraryElements;
import org.gradle.api.attributes.Usage;
import org.gradle.api.attributes.java.TargetJvmVersion;
import org.gradle.api.component.AdhocComponentWithVariants;
import org.gradle.api.component.SoftwareComponentFactory;
import org.gradle.api.file.Directory;
import org.gradle.api.internal.artifacts.ArtifactAttributes;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.JavaLibraryPlugin;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.Provider;
import org.gradle.api.publish.PublicationContainer;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.publish.maven.MavenPom;
import org.gradle.api.publish.maven.MavenPublication;
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin;
import org.gradle.api.publish.maven.tasks.GenerateMavenPom;
import org.gradle.api.publish.tasks.GenerateModuleMetadata;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.javadoc.Javadoc;
import org.gradle.jvm.tasks.Jar;

import io.github.sebersole.quarkus.tasks.GenerateBuildStepsList;
import io.github.sebersole.quarkus.tasks.GenerateConfigRootsList;
import io.github.sebersole.quarkus.tasks.GenerateDescriptor;
import io.github.sebersole.quarkus.tasks.GenerateExtensionPropertiesFile;
import io.github.sebersole.quarkus.tasks.IndexManager;
import io.github.sebersole.quarkus.tasks.IndexerTask;
import io.github.sebersole.quarkus.tasks.VerifyDeploymentDependencies;
import io.github.sebersole.quarkus.tasks.VerifyRuntimeDependencies;

import static io.github.sebersole.quarkus.Names.DSL_EXTENSION_NAME;

/**
 * Plugin for defining Quarkus extensions
 *
 * @author Steve Ebersole
 */
public class QuarkusExtensionPlugin implements Plugin<Project> {
	private final SoftwareComponentFactory softwareComponentFactory;

	@SuppressWarnings("unused")
	@Inject
	public QuarkusExtensionPlugin(SoftwareComponentFactory softwareComponentFactory) {
		this.softwareComponentFactory = softwareComponentFactory;
	}

	@Override
	public void apply(Project project) {
		project.getPluginManager().apply( JavaLibraryPlugin.class );
		project.getPluginManager().apply( MavenPublishPlugin.class );

		final ExtensionDescriptor config = project.getExtensions().create(
				DSL_EXTENSION_NAME,
				ExtensionDescriptor.class,
				project
		);

		final JavaPluginExtension javaPluginExtension = project.getExtensions().getByType( JavaPluginExtension.class );
		javaPluginExtension.withJavadocJar();
		javaPluginExtension.withSourcesJar();

		preparePlatforms( project );

		prepareRuntime( config, project );
		prepareDeployment( project );
		prepareSpi( project );

		applyAdjustments( project );
	}


	/**
	 * Prepares the {@value Names#PLATFORMS_CONFIG_NAME} Configuration and
	 * applies it to all Configurations (other than itself ofc)
	 */
	private void preparePlatforms(Project project) {
		final Configuration platforms = project.getConfigurations().maybeCreate( Names.PLATFORMS_CONFIG_NAME );
		platforms.setDescription( "Configuration to specify all Quarkus platforms (BOMs) to be applied" );

		project.getConfigurations().all( (configuration) -> {
			if ( configuration != platforms ) {
				configuration.extendsFrom( platforms );
			}
		} );
	}

	private void prepareRuntime(ExtensionDescriptor config, Project project) {
		final SourceSetContainer sourceSets = project.getExtensions().getByType( SourceSetContainer.class );
		final SourceSet mainSourceSet = sourceSets.getByName( "main" );

		project.getDependencies().add( mainSourceSet.getImplementationConfigurationName(), Helper.quarkusCore( project ) );

		final PublishingExtension publishingExtension = project.getExtensions().getByType( PublishingExtension.class );
		final PublicationContainer publications = publishingExtension.getPublications();
		publications.create( "runtime", MavenPublication.class, (publication) -> {
			publication.setArtifactId( project.getName() );
			publication.from( project.getComponents().getByName( "java" ) );
		} );

		final IndexManager indexManager = new IndexManager( mainSourceSet, project );
		final IndexerTask indexerTask = project.getTasks().create(
				mainSourceSet.getTaskName( "index", "classes" ),
				IndexerTask.class,
				indexManager
		);

		final GenerateDescriptor generateDescriptorTask = project.getTasks().create(
				GenerateDescriptor.TASK_NAME,
				GenerateDescriptor.class,
				config
		);

		final GenerateConfigRootsList configRootsTask = project.getTasks().create(
				GenerateConfigRootsList.TASK_NAME,
				GenerateConfigRootsList.class,
				indexManager
		);

		final GenerateExtensionPropertiesFile extensionPropertiesTask = project.getTasks().create(
				GenerateExtensionPropertiesFile.TASK_NAME,
				GenerateExtensionPropertiesFile.class
		);

		indexerTask.dependsOn( mainSourceSet.getCompileJavaTaskName() );
		configRootsTask.dependsOn( indexerTask );

		final Jar runtimeJarTask = (Jar) project.getTasks().getByName( mainSourceSet.getJarTaskName() );
		runtimeJarTask.from( generateDescriptorTask.getDescriptorFileReference(), (copySpec) -> copySpec.into( "META-INF" ) );
		runtimeJarTask.from( configRootsTask.getListFileReference(), (copySpec) -> copySpec.into( "META-INF" ) );
		runtimeJarTask.from( extensionPropertiesTask.getPropertiesFile(), (copySpec) -> copySpec.into( "META-INF" ) );
		runtimeJarTask.dependsOn( generateDescriptorTask );
		runtimeJarTask.dependsOn( configRootsTask );
		runtimeJarTask.dependsOn( extensionPropertiesTask );

		final VerifyRuntimeDependencies verifyRuntimeDependencies = project.getTasks().create(
				VerifyRuntimeDependencies.TASK_NAME,
				VerifyRuntimeDependencies.class,
				config
		);

		final VerifyDeploymentDependencies verifyDeploymentDependencies = project.getTasks().create(
				VerifyDeploymentDependencies.TASK_NAME,
				VerifyDeploymentDependencies.class,
				config
		);

		verifyDeploymentDependencies.dependsOn( verifyRuntimeDependencies );

		runtimeJarTask.finalizedBy( verifyRuntimeDependencies );
		runtimeJarTask.finalizedBy( verifyDeploymentDependencies );

		// can't remember if check includes jar. easy enough to just add it both places, so...
		project.getTasks().getByName( "check" ).dependsOn( verifyDeploymentDependencies );
	}

	private void prepareDeployment(Project project) {
		final SourceSetContainer sourceSets = project.getExtensions().getByType( SourceSetContainer.class );
		final SourceSet deploymentSourceSet = sourceSets.maybeCreate( "deployment" );

		final SourceSet mainSourceSet = sourceSets.getByName( "main" );
		final SourceSet testSourceSet = sourceSets.getByName( "test" );

		preparePublication( deploymentSourceSet, project );

		final TaskContainer taskContainer = project.getTasks();
		final Jar mainJarTask = (Jar) taskContainer.getByName( mainSourceSet.getJarTaskName() );
		final Jar deploymentJarTask = (Jar) taskContainer.getByName( deploymentSourceSet.getJarTaskName() );

		project.getDependencies().add(
				deploymentSourceSet.getImplementationConfigurationName(),
				Helper.quarkusCoreDeployment( project )
		);
		project.getDependencies().add(
				deploymentSourceSet.getImplementationConfigurationName(),
				project.files( mainJarTask.getArchiveFile() )
		);

		project.getDependencies().add(
				testSourceSet.getImplementationConfigurationName(),
				project.files( deploymentJarTask.getArchiveFile() )
		);

		taskContainer.getByName( testSourceSet.getCompileJavaTaskName() ).dependsOn( deploymentJarTask );


		final IndexManager indexManager = new IndexManager( deploymentSourceSet, project );
		final IndexerTask indexerTask = project.getTasks().create(
				deploymentSourceSet.getTaskName( "index", "classes" ),
				IndexerTask.class,
				indexManager
		);

		final GenerateBuildStepsList buildStepsListTask = project.getTasks().create(
				GenerateBuildStepsList.TASK_NAME,
				GenerateBuildStepsList.class,
				indexManager
		);
		deploymentJarTask.from( buildStepsListTask.getListFileReference(), (copySpec) -> copySpec.into( "META-INF" ) );

		indexerTask.dependsOn( deploymentSourceSet.getCompileJavaTaskName() );
		buildStepsListTask.dependsOn( indexerTask );
	}

	private void preparePublication(SourceSet sourceSet, Project project) {
		// e.g., `deployment` or `spi`
		final String publicationName = sourceSet.getName();

		final PublishingExtension publishingExtension = project.getExtensions().getByType( PublishingExtension.class );
		final MavenPublication publication = publishingExtension.getPublications().create( publicationName, MavenPublication.class );
		// `confungulator-deployment` or `confungulator-spi`
		publication.setArtifactId( publication.getArtifactId() + "-" + publicationName );

		// transfer any of the standard POM details defined on the `main`
		// artifact to the publication we are preparing
		transferBasicPomDetails(
				// from
				( (MavenPublication) publishingExtension.getPublications().getByName( "runtime" ) ).getPom(),
				// to
				publication.getPom(),
				project
		);

		// generate the extension component
		final AdhocComponentWithVariants publicationComponent = softwareComponentFactory.adhoc( publicationName );
		project.getComponents().add( publicationComponent );
		publication.from( publicationComponent );

		final SourceSetContainer sourceSets = project.getExtensions().getByType( SourceSetContainer.class );
		final SourceSet mainSourceSet = sourceSets.getByName( "main" );

		final TaskContainer taskContainer = project.getTasks();
		final Jar mainJarTask = (Jar) taskContainer.getByName( mainSourceSet.getJarTaskName() );

		final Jar jarTask = taskContainer.create( sourceSet.getJarTaskName(), Jar.class, (task) -> {
			task.setGroup( "build" );
			task.setDescription( "Creates the " + publicationName + " artifact" );
			task.dependsOn( taskContainer.getByName( sourceSet.getCompileJavaTaskName() ) );

			task.getArchiveBaseName().set( mainJarTask.getArchiveBaseName() );
			task.getArchiveAppendix().set( publicationName );
			task.getArchiveVersion().set( project.provider( () -> project.getVersion().toString() ) );

			task.from( sourceSet.getJava().getDestinationDirectory() );
			task.getDestinationDirectory().set( mainJarTask.getDestinationDirectory() );
		} );

		final Provider<Directory> javadocDir = project.getLayout().getBuildDirectory().dir( "docs/javadoc-" + publicationName );

		final Javadoc javadocTask = taskContainer.create( sourceSet.getJavadocTaskName(), Javadoc.class, (task) -> {
			task.setGroup( "documentation" );
			task.setDescription( "Generates the deployment Javadocs" );
			task.dependsOn( sourceSet.getCompileJavaTaskName(), sourceSet.getProcessResourcesTaskName() );

			task.source( sourceSet.getAllJava() );
			task.setClasspath( sourceSet.getCompileClasspath() );
			task.setDestinationDir( javadocDir.get().getAsFile() );
		} );

		final Jar javadocJarTask = taskContainer.create( sourceSet.getJavadocJarTaskName(), Jar.class, (task) -> {
			task.setGroup( "build" );
			task.setDescription( "Creates the " + publicationName + " Javadoc artifact" );
			task.dependsOn( javadocTask );

			task.getArchiveBaseName().set( mainJarTask.getArchiveBaseName() );
			task.getArchiveAppendix().set( publicationName );
			task.getArchiveClassifier().set( "javadoc" );
			task.getArchiveVersion().set( project.provider( () -> project.getVersion().toString() ) );

			task.from( javadocDir );
			task.getDestinationDirectory().set( mainJarTask.getDestinationDirectory() );
		} );

		final Jar sourcesJarTask = taskContainer.create( sourceSet.getSourcesJarTaskName(), Jar.class, (task) -> {
			task.setGroup( "build" );
			task.setDescription( "Creates the " + publicationName + " sources artifact" );

			task.getArchiveBaseName().set( mainJarTask.getArchiveBaseName() );
			task.getArchiveAppendix().set( publicationName );
			task.getArchiveClassifier().set( "sources" );
			task.getArchiveVersion().set( project.provider( () -> project.getVersion().toString() ) );

			task.from( sourceSet.getAllSource() );
			task.getDestinationDirectory().set( mainJarTask.getDestinationDirectory() );
		} );

		final Task buildTask = taskContainer.getByName( "build" );
		buildTask.dependsOn( jarTask );
		buildTask.dependsOn( javadocJarTask );
		buildTask.dependsOn( sourcesJarTask );

		// create the main, javadoc and sources variants
		applyModuleVariants( sourceSet, project, publicationComponent, jarTask, javadocJarTask, sourcesJarTask );
	}

	private void transferBasicPomDetails(MavenPom from, MavenPom to, Project project) {
		project.afterEvaluate( (p) -> {
			to.getInceptionYear().set( from.getInceptionYear() );
			to.getUrl().set( from.getUrl() );

			from.ciManagement( (fromCiManagement) -> to.ciManagement( (toCiManagement) -> {
				// for CI details listed in the main pom
				toCiManagement.getUrl().set( fromCiManagement.getUrl() );
				toCiManagement.getSystem().set( fromCiManagement.getSystem() );
			} ) );

			from.licenses( (fromLicenses) -> to.licenses( (toLicenses) -> fromLicenses.license( (fromLicense) -> {
				// for each license listed in the main pom
				toLicenses.license( (toLicense) -> {
					toLicense.getName().set( fromLicense.getName() );
					toLicense.getUrl().set( fromLicense.getUrl() );
					toLicense.getComments().set( fromLicense.getComments() );
					toLicense.getDistribution().set( fromLicense.getDistribution() );
				} );
			} ) ) );

			from.scm( (fromScm) -> to.scm( (toScm) -> {
				// for scm details in the main pom
				toScm.getUrl().set( fromScm.getUrl() );
				toScm.getConnection().set( fromScm.getConnection() );
				toScm.getDeveloperConnection().set( fromScm.getDeveloperConnection() );
				toScm.getTag().set( fromScm.getTag() );
			} ) );

			from.developers( (fromDevelopers) -> to.developers( (toDevelopers) -> fromDevelopers.developer( (fromDev) -> toDevelopers.developer( (toDev) -> {
				// for each developer listed in the main pom
				toDev.getId().set( fromDev.getId() );
				toDev.getName().set( fromDev.getName() );
				toDev.getEmail().set( fromDev.getEmail() );
				toDev.getUrl().set( fromDev.getUrl() );
				toDev.getOrganization().set( fromDev.getOrganization() );
				toDev.getOrganizationUrl().set( fromDev.getOrganizationUrl() );
				toDev.getTimezone().set( fromDev.getTimezone() );
				toDev.getProperties().set( fromDev.getProperties() );
				toDev.getRoles().set( fromDev.getRoles() );
			} ) ) ) );
		} );
	}

	private void applyModuleVariants(SourceSet sourceSet, Project project, AdhocComponentWithVariants publicationComponent, Jar jarTask, Jar javadocJarTask, Jar sourcesJarTask) {
		final JavaPluginExtension javaPluginExtension = project.getExtensions().getByType( JavaPluginExtension.class );
		final JavaVersion targetCompatibility = javaPluginExtension.getTargetCompatibility();
		final ObjectFactory objectFactory = project.getObjects();

		final Configuration apiElements = project.getConfigurations().maybeCreate( sourceSet.getApiElementsConfigurationName() );
		apiElements.setCanBeResolved(false);
		apiElements.setCanBeConsumed(true);
		apiElements.getAttributes().attribute( Usage.USAGE_ATTRIBUTE, objectFactory.named(Usage.class, Usage.JAVA_API) );
		apiElements.getAttributes().attribute( Category.CATEGORY_ATTRIBUTE, objectFactory.named(Category.class, Category.LIBRARY) );
		apiElements.getAttributes().attribute( TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, targetCompatibility.ordinal()+1 );
		apiElements.getAttributes().attribute( Bundling.BUNDLING_ATTRIBUTE, objectFactory.named(Bundling.class, Bundling.EXTERNAL) );
		apiElements.getAttributes().attribute( LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objectFactory.named(LibraryElements.class, LibraryElements.JAR) );
		final ConfigurationPublications apiElementsOutgoing = apiElements.getOutgoing();
		apiElementsOutgoing.artifact( jarTask );
		apiElementsOutgoing.getAttributes().attribute( ArtifactAttributes.ARTIFACT_FORMAT, "jar");
		publicationComponent.addVariantsFromConfiguration( apiElements, (details) -> details.mapToMavenScope( "compile" ) );


		final Configuration runtimeElements = project.getConfigurations().maybeCreate( sourceSet.getRuntimeElementsConfigurationName() );
		runtimeElements.setCanBeResolved(false);
		runtimeElements.setCanBeConsumed(true);
		runtimeElements.getAttributes().attribute( Usage.USAGE_ATTRIBUTE, objectFactory.named(Usage.class, Usage.JAVA_RUNTIME) );
		runtimeElements.getAttributes().attribute( Category.CATEGORY_ATTRIBUTE, objectFactory.named(Category.class, Category.LIBRARY) );
		runtimeElements.getAttributes().attribute( TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, targetCompatibility.ordinal()+1 );
		runtimeElements.getAttributes().attribute( Bundling.BUNDLING_ATTRIBUTE, objectFactory.named(Bundling.class, Bundling.EXTERNAL) );
		runtimeElements.getAttributes().attribute( LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objectFactory.named(LibraryElements.class, LibraryElements.JAR) );
		final ConfigurationPublications runtimeElementsOutgoing = runtimeElements.getOutgoing();
		runtimeElements.getOutgoing().artifact( jarTask );
		runtimeElementsOutgoing.getAttributes().attribute( ArtifactAttributes.ARTIFACT_FORMAT, "jar");
		publicationComponent.addVariantsFromConfiguration( runtimeElements, (details) -> details.mapToMavenScope( "runtime" ) );


		final Configuration javadocElements = project.getConfigurations().maybeCreate( sourceSet.getJavadocElementsConfigurationName() );
		javadocElements.setCanBeResolved(false);
		javadocElements.setCanBeConsumed(true);
		javadocElements.getAttributes().attribute( Usage.USAGE_ATTRIBUTE, objectFactory.named(Usage.class, Usage.JAVA_RUNTIME) );
		javadocElements.getAttributes().attribute( Category.CATEGORY_ATTRIBUTE, objectFactory.named(Category.class, Category.DOCUMENTATION) );
		javadocElements.getAttributes().attribute( Bundling.BUNDLING_ATTRIBUTE, objectFactory.named(Bundling.class, Bundling.EXTERNAL) );
		javadocElements.getAttributes().attribute( DocsType.DOCS_TYPE_ATTRIBUTE, objectFactory.named(DocsType.class, DocsType.JAVADOC) );
		final ConfigurationPublications javadocElementsOutgoing = javadocElements.getOutgoing();
		javadocElements.getOutgoing().artifact( javadocJarTask );
		javadocElementsOutgoing.getAttributes().attribute( ArtifactAttributes.ARTIFACT_FORMAT, "jar");
		publicationComponent.addVariantsFromConfiguration( javadocElements, (details) -> details.mapToMavenScope( "runtime" ) );


		final Configuration sourcesElements = project.getConfigurations().maybeCreate( sourceSet.getSourcesElementsConfigurationName() );
		sourcesElements.setCanBeResolved(false);
		sourcesElements.setCanBeConsumed(true);
		sourcesElements.getAttributes().attribute( Usage.USAGE_ATTRIBUTE, objectFactory.named(Usage.class, Usage.JAVA_RUNTIME) );
		sourcesElements.getAttributes().attribute( Category.CATEGORY_ATTRIBUTE, objectFactory.named(Category.class, Category.DOCUMENTATION) );
		sourcesElements.getAttributes().attribute( Bundling.BUNDLING_ATTRIBUTE, objectFactory.named(Bundling.class, Bundling.EXTERNAL) );
		sourcesElements.getAttributes().attribute( DocsType.DOCS_TYPE_ATTRIBUTE, objectFactory.named(DocsType.class, DocsType.SOURCES) );
		final ConfigurationPublications sourcesElementsOutgoing = sourcesElements.getOutgoing();
		sourcesElements.getOutgoing().artifact( sourcesJarTask );
		sourcesElementsOutgoing.getAttributes().attribute( ArtifactAttributes.ARTIFACT_FORMAT, "jar");
		publicationComponent.addVariantsFromConfiguration( sourcesElements, (details) -> details.mapToMavenScope( "runtime" ) );
	}

	private void prepareSpi(Project project) {
		final SourceSetContainer sourceSets = project.getExtensions().getByType( SourceSetContainer.class );
		final SourceSet spiSourceSet = sourceSets.maybeCreate( "spi" );
		final SourceSet mainSourceSet = sourceSets.getByName( "main" );

		project.afterEvaluate( (p) -> {
			if ( spiSourceSet.getJava().isEmpty() && spiSourceSet.getResources().isEmpty() ) {
				project.getLogger().debug( "Skipping SPI module set-up : no sources" );
				return;
			}

			project.getLogger().debug( "Starting SPI module set-up" );

			preparePublication( spiSourceSet, project );

			final Jar spiJarTask = (Jar) project.getTasks().getByName( spiSourceSet.getJarTaskName() );
			project.getDependencies().add(
					mainSourceSet.getImplementationConfigurationName(),
					project.files( spiJarTask.getArchiveFile() )
			);
		} );
	}

	/**
	 * We need to adjust:
	 *
	 * 	- the `deployment` pom and module descriptor files to add `runtime` as a dependency by its GAV
	 * 	- the `runtime` pom and module descriptor files to add `spi` as a dependency by its GAV
	 * 	- the `deployment` Gradle module descriptor to adjust `deploymentApiElements`, etc
	 * 	- the `spi` Gradle module descriptor to adjust `spiApiElements`, etc
	 */
	private void applyAdjustments(Project project) {
		final TaskContainer taskContainer = project.getTasks();

		final Task generatePomFiles = taskContainer.create( "generatePomFiles" );
		final Task generateMetadataFiles = taskContainer.create( "generateMetadataFiles" );
		final Task preparePublications = taskContainer.create( "preparePublications" );
		preparePublications.dependsOn( generatePomFiles, generateMetadataFiles );

		taskContainer.all( (task) -> {
			if ( task.getName().equals( "generatePomFileForRuntimePublication" ) ) {
				final GenerateMavenPom runtimePomTask = (GenerateMavenPom) task;
				adjustRuntimePomGeneration( runtimePomTask, project );
				generatePomFiles.dependsOn( runtimePomTask );
			}
			else if ( task.getName().equals( "generatePomFileForDeploymentPublication" ) ) {
				final GenerateMavenPom deploymentPomTask = (GenerateMavenPom) task;
				adjustDeploymentPomGeneration( deploymentPomTask, project );
				generatePomFiles.dependsOn( deploymentPomTask );
			}
			else if ( task.getName().equals( "generatePomFileForSpiPublication" ) ) {
				final GenerateMavenPom spiPomTask = (GenerateMavenPom) task;
				adjustSpiPomGeneration( spiPomTask, project );
				generatePomFiles.dependsOn( spiPomTask );
			}
			else if ( task.getName().equals( "generateMetadataFileForRuntimePublication" ) ) {
				final GenerateModuleMetadata runtimeModuleTask = (GenerateModuleMetadata) task;
				adjustRuntimeMetadataGeneration( runtimeModuleTask, project );
				generateMetadataFiles.dependsOn( runtimeModuleTask );
			}
			else if ( task.getName().equals( "generateMetadataFileForDeploymentPublication" ) ) {
				final GenerateModuleMetadata deploymentModuleTask = (GenerateModuleMetadata) task;
				adjustDeploymentMetadataGeneration( deploymentModuleTask, project );
				generateMetadataFiles.dependsOn( deploymentModuleTask );
			}
			else if ( task.getName().equals( "generateMetadataFileForSpiPublication" ) ) {
				final GenerateModuleMetadata spiModuleTask = (GenerateModuleMetadata) task;
				adjustSpiMetadataGeneration( spiModuleTask, project );
				generateMetadataFiles.dependsOn( spiModuleTask );
			}
		} );
	}

	private void adjustRuntimePomGeneration(GenerateMavenPom runtimePomTask, Project project) {
		//do not convert this to a lambda - causes the tasks to not be cacheable
		//noinspection Convert2Lambda
		runtimePomTask.doLast( new Action<>() {
			@Override
			public void execute(@SuppressWarnings("NullableProblems") Task task) {
				PomAdjuster.applyDependency( runtimePomTask.getDestination(), project.getName() + "-spi", project );
			}
		} );
	}

	private void adjustDeploymentPomGeneration(GenerateMavenPom deploymentPomTask, Project project) {
		deploymentPomTask.setDescription( "Generate the pom file for the `deployment` publication" );
		//do not convert this to a lambda - causes the tasks to not be cacheable
		//noinspection Convert2Lambda
		deploymentPomTask.doLast( new Action<>() {
			@Override
			public void execute(@SuppressWarnings("NullableProblems") Task task) {
				PomAdjuster.applyDependency( deploymentPomTask.getDestination(), project.getName(), project );
			}
		} );
	}

	static void adjustSpiPomGeneration(GenerateMavenPom spiPomTask, @SuppressWarnings("unused") Project project) {
		spiPomTask.setDescription( "Generate the pom file for the `spi` publication" );
	}

	private void adjustRuntimeMetadataGeneration(GenerateModuleMetadata runtimeModuleTask, Project project) {
		runtimeModuleTask.setDescription( "Generate the module descriptor file for the `runtime` publication" );

		//do not convert this to a lambda - causes the tasks to not be cacheable
		//noinspection Convert2Lambda
		runtimeModuleTask.doLast( new Action<>() {
			@Override
			public void execute(@SuppressWarnings("NullableProblems") Task task) {
				ModuleMetadataAdjuster.runtimeAdjustments( runtimeModuleTask.getOutputFile().get(), project );
			}
		} );
	}

	private void adjustDeploymentMetadataGeneration(GenerateModuleMetadata deploymentModuleTask, Project project) {
		deploymentModuleTask.setDescription( "Generate the module descriptor file for the `deployment` publication" );

		// should add the `runtime` artifact as a dependency

		//do not convert this to a lambda - causes the tasks to not be cacheable
		//noinspection Convert2Lambda
		deploymentModuleTask.doLast( new Action<>() {
			@Override
			public void execute(@SuppressWarnings("NullableProblems") Task task) {
				ModuleMetadataAdjuster.deploymentAdjustments( deploymentModuleTask.getOutputFile().get(), project );
			}
		} );
	}

	private void adjustSpiMetadataGeneration(GenerateModuleMetadata spiModuleTask, Project project) {
		spiModuleTask.setDescription( "Generate the module descriptor file for the `spi` publication" );

		//do not convert this to a lambda - causes the tasks to not be cacheable
		//noinspection Convert2Lambda
		spiModuleTask.doLast( new Action<>() {
			@Override
			public void execute(@SuppressWarnings("NullableProblems") Task task) {
				ModuleMetadataAdjuster.spiAdjustments( spiModuleTask.getOutputFile().get(), project );
			}
		} );
	}

}
