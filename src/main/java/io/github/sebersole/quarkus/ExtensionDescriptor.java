package io.github.sebersole.quarkus;

import javax.inject.Inject;

import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;

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
		name.convention( project.provider( project::getName ) );

		description = project.getObjects().property( String.class );
		description.convention( project.provider( project::getDescription ) );

		metadata = project.getObjects().newInstance( ExtensionMetadataDescriptor.class, project );
	}

	@Input
	public Property<String> getNameProperty() {
		return name;
	}

	@Internal
	@SuppressWarnings("unused")
	public String getName() {
		return name.getOrNull();
	}

	@SuppressWarnings("unused")
	public void setName(String name) {
		this.name.set( name );
	}

	@SuppressWarnings("unused")
	public void name(String name) {
		this.name.set( name );
	}

	@Input
	public Property<String> getDescriptionProperty() {
		return description;
	}

	@Internal
	@SuppressWarnings("unused")
	public String getDescription() {
		return description.getOrNull();
	}

	@SuppressWarnings("unused")
	public void setDescription(String description) {
		this.description.set( description );
	}

	@SuppressWarnings("unused")
	public void description(String description) {
		this.description.set( description );
	}

	@Nested
	public ExtensionMetadataDescriptor getMetadata() {
		return metadata;
	}

	@SuppressWarnings("unused")
	public void metadata(Closure<ExtensionMetadataDescriptor> config) {
		project.configure( metadata, config );
	}

	@SuppressWarnings("unused")
	public void metadata(Action<ExtensionMetadataDescriptor> config) {
		config.execute( metadata );
	}
}
