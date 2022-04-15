package io.github.sebersole.quarkus;

import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;

import org.gradle.api.Project;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;

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

	public Property<String> getStatusProperty() {
		return status;
	}

	public String getStatus() {
		return getStatusProperty().getOrNull();
	}

	public void setStatus(String status) {
		getStatusProperty().set( status );
	}

	public void status(String status) {
		setStatus( status );
	}

	public Property<String> getGuideProperty() {
		return guide;
	}

	public String getGuide() {
		return getGuideProperty().getOrNull();
	}

	public void setGuide(String guide) {
		getGuideProperty().set( guide );
	}

	public void guide(String guide) {
		setGuide( guide );
	}

	public ListProperty<String> getCategoriesProperty() {
		return categories;
	}

	public List<String> getCategories() {
		return getCategoriesProperty().get();
	}

	public void setCategories(List<String> categories) {
		getCategoriesProperty().set( categories );
	}

	public ListProperty<String> getKeywordsProperty() {
		return keywords;
	}

	public List<String> getKeywords() {
		return getKeywordsProperty().get();
	}

	public void setKeywords(List<String> keywords) {
		getKeywordsProperty().set( keywords );
	}
}
