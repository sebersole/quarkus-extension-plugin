package io.github.sebersole.quarkus;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.LineNumberReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.inject.Inject;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.gradle.api.Action;
import org.gradle.api.JavaVersion;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationPublications;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.attributes.Bundling;
import org.gradle.api.attributes.Category;
import org.gradle.api.attributes.DocsType;
import org.gradle.api.attributes.LibraryElements;
import org.gradle.api.attributes.Usage;
import org.gradle.api.attributes.java.TargetJvmVersion;
import org.gradle.api.component.AdhocComponentWithVariants;
import org.gradle.api.component.SoftwareComponentFactory;
import org.gradle.api.file.Directory;
import org.gradle.api.file.RegularFile;
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
import io.github.sebersole.quarkus.tasks.VerifyDependenciesTask;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import static io.github.sebersole.quarkus.Names.DSL_EXTENSION_NAME;
import static io.github.sebersole.quarkus.Names.QUARKUS_CORE;
import static io.github.sebersole.quarkus.Names.QUARKUS_CORE_DEPLOYMENT;
import static io.github.sebersole.quarkus.Names.QUARKUS_GROUP;

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

		project.getDependencies().add( mainSourceSet.getImplementationConfigurationName(), quarkusCore( project ) );

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

		final VerifyDependenciesTask verificationTask = project.getTasks().create(
				VerifyDependenciesTask.TASK_NAME,
				VerifyDependenciesTask.class,
				config
		);

		runtimeJarTask.finalizedBy( verificationTask );

		// can't remember if check includes jar. easy enough to just add it both places, so...
		project.getTasks().getByName( "check" ).dependsOn( verificationTask );
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
		publicationComponent.addVariantsFromConfiguration( apiElements, (details) -> {
			details.mapToMavenScope( "compile" );
		} );


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
		publicationComponent.addVariantsFromConfiguration( runtimeElements, (details) -> {
			details.mapToMavenScope( "runtime" );
		} );


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
		publicationComponent.addVariantsFromConfiguration( javadocElements, (details) -> {
			details.mapToMavenScope( "runtime" );
		} );


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
		publicationComponent.addVariantsFromConfiguration( sourcesElements, (details) -> {
			details.mapToMavenScope( "runtime" );
		} );
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
	 * 	- the deployment pom file to add main as a dependency by its GAV
	 * 	- the main pom file to add spi as a dependency by its GAV
	 * 	- the deployment Gradle module descriptor to adjust `deploymentApiElements`, etc
	 * 	- the spi Gradle module descriptor to adjust `spiApiElements`, etc
	 */
	private void applyAdjustments(Project project) {
		final TaskContainer taskContainer = project.getTasks();
		taskContainer.all( (task) -> {
			if ( task.getName().equals( "generatePomFileForRuntimePublication" ) ) {
				final GenerateMavenPom runtimePomTask = (GenerateMavenPom) task;
				//do not convert this to a lambda - causes the tasks to not be cacheable
				//noinspection Convert2Lambda
				runtimePomTask.doLast( new Action<>() {
					@Override
					public void execute(@SuppressWarnings("NullableProblems") Task task) {
						applyDependency( runtimePomTask.getDestination(), project.getName() + "-spi", project );
					}
				} );
			}
			else if ( task.getName().equals( "generatePomFileForDeploymentPublication" ) ) {
				final GenerateMavenPom deploymentPomTask = (GenerateMavenPom) task;
				deploymentPomTask.setDescription( "Generate the pom file for the `deployment` publication" );
				//do not convert this to a lambda - causes the tasks to not be cacheable
				//noinspection Convert2Lambda
				deploymentPomTask.doLast( new Action<>() {
					@Override
					public void execute(@SuppressWarnings("NullableProblems") Task task) {
						applyDependency( deploymentPomTask.getDestination(), project.getName(), project );
					}
				} );
			}
			else if ( task.getName().equals( "generatePomFileForSpiPublication" ) ) {
				final GenerateMavenPom spiPomTask = (GenerateMavenPom) task;
				spiPomTask.setDescription( "Generate the pom file for the `spi` publication" );
			}
			else if ( task.getName().equals( "generateMetadataFileForDeploymentPublication" ) ) {
				final GenerateModuleMetadata deploymentModuleTask = (GenerateModuleMetadata) task;
				task.setDescription( "Generate the module descriptor file for the `deployment` publication" );

				//do not convert this to a lambda - causes the tasks to not be cacheable
				//noinspection Convert2Lambda
				deploymentModuleTask.doLast( new Action<>() {
					@Override
					public void execute(@SuppressWarnings("NullableProblems") Task task) {
						adjustVariantNames( "deployment", deploymentModuleTask.getOutputFile().get(), project );
					}
				} );
			}
			else if ( task.getName().equals( "generateMetadataFileForSpiPublication" ) ) {
				final GenerateModuleMetadata spiModuleTask = (GenerateModuleMetadata) task;
				task.setDescription( "Generate the module descriptor file for the `spi` publication" );

				//do not convert this to a lambda - causes the tasks to not be cacheable
				//noinspection Convert2Lambda
				spiModuleTask.doLast( new Action<>() {
					@Override
					public void execute(@SuppressWarnings("NullableProblems") Task task) {
						adjustVariantNames( "spi", spiModuleTask.getOutputFile().get(), project );
					}
				} );
			}
		} );
	}

	private void applyDependency(File pomFile, String artifactId, Project project) {
		final DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
		try {
			final DocumentBuilder db = dbf.newDocumentBuilder();
			final Document document = db.parse( pomFile );

			final Node dependenciesNode;
			final NodeList dependenciesList = document.getDocumentElement().getElementsByTagName( "dependencies" );
			if ( dependenciesList.item( 0 ) == null ) {
				dependenciesNode = document.createElement( "dependencies" );
				document.getDocumentElement().appendChild( dependenciesNode );
			}
			else {
				dependenciesNode = dependenciesList.item( 0 );
			}

			final Element dependencyNode = document.createElement( "dependency" );
			dependenciesNode.appendChild( dependencyNode );

			final Element groupIdNode = document.createElement( "groupId" );
			groupIdNode.setTextContent( project.getGroup().toString() );
			dependencyNode.appendChild( groupIdNode );

			final Element artifactIdNode = document.createElement( "artifactId" );
			artifactIdNode.setTextContent( artifactId );
			dependencyNode.appendChild( artifactIdNode );
			dependencyNode.appendChild( groupIdNode );

			final Element versionNode = document.createElement( "version" );
			versionNode.setTextContent( project.getVersion().toString() );
			dependencyNode.appendChild( versionNode );

			final DOMSource source = new DOMSource( document );
			final FileWriter writer = new FileWriter( pomFile );
			final StreamResult result = new StreamResult( writer );

			final Transformer transformer = createTransformer();
			try {
				transformer.transform( source, result );
			}
			catch (TransformerException e) {
				throw new RuntimeException( "Unable to write XML to file", e );
			}
		}
		catch (ParserConfigurationException | SAXException | IOException e) {
			throw new RuntimeException( "Unable to parse XML", e );
		}
	}

	private void adjustVariantNames(String publicationName, RegularFile moduleFile, Project project) {
		final File moduleFileAsFile = moduleFile.getAsFile();
		// would be a lot nicer to process these through json-b..

		final long moduleFileAsFileLastModified = moduleFileAsFile.lastModified();

		final List<String> lines = new ArrayList<>();

		try ( final LineNumberReader fileReader = new LineNumberReader( new FileReader( moduleFileAsFile ) ) ) {
			String line = fileReader.readLine();
			while ( line != null ) {
				final String trimmedLine = line.trim();
				if ( trimmedLine.equals( "\"name\": \"" + publicationName + "ApiElements\"," ) ) {
					lines.add( "      \"name\": \"apiElements\"," );
				}
				else if ( trimmedLine.equals( "\"name\": \"" + publicationName + "RuntimeElements\"," ) ) {
					lines.add( "      \"name\": \"runtimeElements\"," );
				}
				else if ( trimmedLine.equals( "\"name\": \"" + publicationName + "JavadocElements\"," ) ) {
					lines.add( "      \"name\": \"javadocElements\"," );
				}
				else if ( trimmedLine.equals( "\"name\": \"" + publicationName + "SourcesElements\"," ) ) {
					lines.add( "      \"name\": \"sourcesElements\"," );
				}
				else {
					lines.add( line );
				}

				line = fileReader.readLine();
			}
		}
		catch (IOException e) {
			throw new RuntimeException( "Could read Gradle module metadata file - " + moduleFileAsFile.getAbsolutePath(), e );
		}

		try ( final FileWriter fileWriter = new FileWriter( moduleFileAsFile ) ) {
			for ( String line : lines ) {
				fileWriter.write( line );
				fileWriter.write( Character.LINE_SEPARATOR );
			}
			fileWriter.flush();
		}
		catch (IOException e) {
			throw new RuntimeException( "Could re-write Gradle module metadata file - " + moduleFileAsFile.getAbsolutePath(), e );
		}

		if ( moduleFileAsFile.lastModified() != moduleFileAsFileLastModified ) {
			if ( !moduleFileAsFile.setLastModified( moduleFileAsFileLastModified ) ) {
				project.getLogger().info(
						"Unable to reset last-modified timestamp for Gradle module metadata file - {}; up-to-date checks may be affected",
						moduleFileAsFile.getAbsolutePath()
				);
			}
		}
	}

	private Transformer createTransformer() {
		try {
			final Transformer transformer = TransformerFactory.newInstance().newTransformer();
			transformer.setOutputProperty( OutputKeys.INDENT, "yes" );
			transformer.setOutputProperty( "{http://xml.apache.org/xslt}indent-amount", "4" );
			return transformer;
		}
		catch (TransformerConfigurationException e) {
			throw new RuntimeException( "Unable to create XML transformer", e );
		}
	}

	private static Dependency quarkusCore(Project project) {
		return project.getDependencies().module( groupArtifact( QUARKUS_GROUP, QUARKUS_CORE ) );
	}

	private static Dependency quarkusCoreDeployment(Project project) {
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
}
