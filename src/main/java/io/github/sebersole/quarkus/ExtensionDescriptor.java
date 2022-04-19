package io.github.sebersole.quarkus;

import java.util.ArrayList;
import javax.inject.Inject;

import org.gradle.api.Project;
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;

/**
 * Descriptor for the extension.
 *
 * Registered as a project DSL extension under {@value Names#DSL_EXTENSION_NAME}.
 *
 * @author Steve Ebersole
 */
public abstract class ExtensionDescriptor implements ExtensionAware  {
	private final Property<String> name;
	private final Property<String> description;
	private final Property<String> status;
	private final Property<String> guide;
	private final ListProperty<String> categories;
	private final ListProperty<String> keywords;

	@Inject
	public ExtensionDescriptor(Project project) {
		name = project.getObjects().property( String.class );
		name.convention( project.provider( project::getName ) );

		description = project.getObjects().property( String.class );
		description.convention( project.provider( project::getDescription ) );

		status = project.getObjects().property( String.class );
		status.convention( "development" );

		guide = project.getObjects().property( String.class );
		guide.convention( (String) null );

		categories = project.getObjects().listProperty( String.class );
		categories.convention( project.provider( ArrayList::new ) );

		keywords = project.getObjects().listProperty( String.class );
		keywords.convention( project.provider( ArrayList::new ) );
	}

	@Input
	public Property<String> getName() {
		return name;
	}

	@Input
	public Property<String> getDescription() {
		return description;
	}

	@Input
	public Property<String> getStatus() {
		return status;
	}

	@SuppressWarnings("unused")
	public void status(String status) {
		getStatus().set( status );
	}

	@Input
	@Optional
	public Property<String> getGuide() {
		return guide;
	}

	@SuppressWarnings("unused")
	public void guide(String guide) {
		getGuide().set( guide );
	}

	@Input
	public ListProperty<String> getCategories() {
		return categories;
	}

	@SuppressWarnings("unused")
	public void category(String... categories) {
		this.categories.addAll( categories );
	}

	@Input
	public ListProperty<String> getKeywords() {
		return keywords;
	}

	@SuppressWarnings("unused")
	public void keyword(String... keywords) {
		this.keywords.addAll( keywords );
	}
}
