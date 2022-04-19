package io.github.sebersole.quarkus;

import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;

import org.gradle.api.Project;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;

/**
 * @author Steve Ebersole
 */
public abstract class ExtensionMetadataDescriptor {
	// I suspect `#status` can be converted to use an enum
	private final Property<String> status;
	private final Property<String> guide;
	private final ListProperty<String> categories;
	private final ListProperty<String> keywords;

	@Inject
	public ExtensionMetadataDescriptor(Project project) {
		status = project.getObjects().property( String.class );
		status.convention( "development" );

		guide = project.getObjects().property( String.class );

		categories = project.getObjects().listProperty( String.class );
		categories.convention( project.provider( ArrayList::new ) );

		keywords = project.getObjects().listProperty( String.class );
		keywords.convention( project.provider( ArrayList::new ) );
	}

	@Input
	public Property<String> getStatusProperty() {
		return status;
	}

	@Internal
	@SuppressWarnings("unused")
	public String getStatus() {
		return getStatusProperty().getOrNull();
	}

	public void setStatus(String status) {
		getStatusProperty().set( status );
	}

	@SuppressWarnings("unused")
	public void status(String status) {
		setStatus( status );
	}

	@Input
	public Property<String> getGuideProperty() {
		return guide;
	}

	@Internal
	@SuppressWarnings("unused")
	public String getGuide() {
		return getGuideProperty().getOrNull();
	}

	public void setGuide(String guide) {
		getGuideProperty().set( guide );
	}

	@SuppressWarnings("unused")
	public void guide(String guide) {
		setGuide( guide );
	}

	@Input
	public ListProperty<String> getCategoriesProperty() {
		return categories;
	}

	@Internal
	@SuppressWarnings("unused")
	public List<String> getCategories() {
		return getCategoriesProperty().get();
	}

	@SuppressWarnings("unused")
	public void setCategories(List<String> categories) {
		getCategoriesProperty().set( categories );
	}

	@Input
	public ListProperty<String> getKeywordsProperty() {
		return keywords;
	}

	@Internal
	@SuppressWarnings("unused")
	public List<String> getKeywords() {
		return getKeywordsProperty().get();
	}

	@SuppressWarnings("unused")
	public void setKeywords(List<String> keywords) {
		getKeywordsProperty().set( keywords );
	}
}
