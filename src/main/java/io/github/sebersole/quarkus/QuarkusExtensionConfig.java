package io.github.sebersole.quarkus;

import javax.inject.Inject;

import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.api.provider.Property;

import groovy.lang.Closure;

/**
 * Configuration for {@link QuarkusExtensionPlugin} as a Gradle DSL extension
 *
 * @author Steve Ebersole
 */
public abstract class QuarkusExtensionConfig implements ExtensionAware  {

	private final Project project;

	private final Property<String> quarkusVersionProperty;
	private final Property<Boolean> applyUniversePlatformProperty;

	private final Property<Project> deploymentProjectProperty;
	private final Property<Project> runtimeProjectProperty;
	private final Property<Project> spiProjectProperty;

	private final ExtensionDescriptor extensionDescriptor;


	@Inject
	public QuarkusExtensionConfig(Project project) {
		this.project = project;

		quarkusVersionProperty = project.getObjects().property( String.class );
		quarkusVersionProperty.convention( "2.8.0.Final" );

		applyUniversePlatformProperty = project.getObjects().property( Boolean.class );
		applyUniversePlatformProperty.convention( false );

		deploymentProjectProperty = project.getObjects().property( Project.class );
		deploymentProjectProperty.convention( project.provider( () -> project.findProject( project.getPath() + ":deployment" ) ) );

		runtimeProjectProperty = project.getObjects().property( Project.class );
		runtimeProjectProperty.convention( project.provider( () -> project.findProject( project.getPath() + ":runtime" ) ) );

		spiProjectProperty = project.getObjects().property( Project.class );
		spiProjectProperty.convention( project.provider( () -> project.findProject( project.getPath() + ":spi" ) ) );

		extensionDescriptor = project.getObjects().newInstance( ExtensionDescriptor.class, project );
	}

	public String getQuarkusVersion() {
		return quarkusVersionProperty.get();
	}

	@SuppressWarnings("unused")
	public void setQuarkusVersion(String version) {
		quarkusVersionProperty.set( version );
	}

	public Property<String> getQuarkusVersionProperty() {
		return quarkusVersionProperty;
	}

	@SuppressWarnings("unused")
	public boolean shouldApplyUniversePlatform() {
		return applyUniversePlatformProperty.getOrElse( false );
	}

	@SuppressWarnings("unused")
	public void applyUniversePlatform(boolean apply) {
		setApplyUniversePlatform( apply );
	}

	public void setApplyUniversePlatform(boolean apply) {
		applyUniversePlatformProperty.set( apply );
	}

	public Property<Boolean> getApplyUniversePlatformProperty() {
		return applyUniversePlatformProperty;
	}

	@SuppressWarnings("unused")
	public Property<Project> getDeploymentProjectProperty() {
		return deploymentProjectProperty;
	}

	@SuppressWarnings("unused")
	public Property<Project> getRuntimeProjectProperty() {
		return runtimeProjectProperty;
	}

	@SuppressWarnings("unused")
	public Property<Project> getSpiProjectProperty() {
		return spiProjectProperty;
	}

	public ExtensionDescriptor getDescriptor() {
		return extensionDescriptor;
	}

	@SuppressWarnings("unused")
	public void descriptor(Closure<ExtensionDescriptor> config) {
		project.configure( extensionDescriptor, config );
	}

	@SuppressWarnings("unused")
	public void descriptor(Action<ExtensionDescriptor> config) {
		config.execute( extensionDescriptor );
	}
}
