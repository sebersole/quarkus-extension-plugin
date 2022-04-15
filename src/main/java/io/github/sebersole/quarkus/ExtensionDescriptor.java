package io.github.sebersole.quarkus;

import javax.inject.Inject;

import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.api.provider.Property;

import groovy.lang.Closure;

/**
 * @author Steve Ebersole
 */
public abstract class ExtensionDescriptor implements ExtensionAware {
	private final Project project;

	private final Property<String> name;
	private final Property<String> description;
	private final ExtensionMetadataDescriptor metadata;

	@Inject
	public ExtensionDescriptor(Project project) {
		this.project = project;

		name = project.getObjects().property( String.class );
		name.convention( project.provider( project::getDisplayName ) );

		description = project.getObjects().property( String.class );
		description.convention( project.provider( project::getDescription ) );

		metadata = project.getObjects().newInstance( ExtensionMetadataDescriptor.class, project );
	}

	public Property<String> getNameProperty() {
		return name;
	}

	public String getName() {
		return name.getOrNull();
	}

	public void setName(String name) {
		this.name.set( name );
	}

	public void name(String name) {
		setName( name );
	}

	public Property<String> getDescriptionProperty() {
		return description;
	}

	public String getDescription() {
		return description.getOrNull();
	}

	public void setDescription(String description) {
		this.description.set( description );
	}

	public void description(String description) {
		setDescription( description );
	}

	public ExtensionMetadataDescriptor getMetadata() {
		return metadata;
	}

	public void metadata(Closure config) {
		project.configure( metadata, config );
	}

	public void metadata(Action<ExtensionMetadataDescriptor> config) {
		config.execute( metadata );
	}
}
