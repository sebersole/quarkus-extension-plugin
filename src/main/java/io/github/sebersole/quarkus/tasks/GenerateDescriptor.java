package io.github.sebersole.quarkus.tasks;

import java.io.File;
import java.util.List;
import javax.inject.Inject;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.github.sebersole.quarkus.ExtensionDescriptor;
import io.github.sebersole.quarkus.ExtensionMetadataDescriptor;
import io.github.sebersole.quarkus.Names;
import io.github.sebersole.quarkus.QuarkusExtensionConfig;

/**
 * @author Steve Ebersole
 */
@CacheableTask
public abstract class GenerateDescriptor extends DefaultTask {
	public static final String TASK_NAME = "generateExtensionDescriptor";
	public static final String YAML_NAME = "quarkus-extension.yaml";
	public static final String STANDARD_YAML_PATH = "quarkus/" + YAML_NAME;

	private final RegularFileProperty descriptorFileReference;
	private final ExtensionDescriptor extensionDescriptor;

	@Inject
	public GenerateDescriptor(QuarkusExtensionConfig config) {
		setGroup( Names.TASK_GROUP );
		setDescription( "Generates the extension descriptor file" );

		extensionDescriptor = config.getDescriptor();

		descriptorFileReference = getProject().getObjects().fileProperty();
		descriptorFileReference.convention(
				getProject().getLayout().getBuildDirectory().file( STANDARD_YAML_PATH )
		);

		getInputs().property( "projectGroup", getProject().getGroup() );
		getInputs().property( "projectId", getProject().getName() );
		getInputs().property( "projectVersion", getProject().getVersion() );
	}

	@Nested
	public ExtensionDescriptor getDescriptor() {
		return extensionDescriptor;
	}

	@OutputFile
	public RegularFileProperty getDescriptorFileReference() {
		return descriptorFileReference;
	}

	@TaskAction
	public void generateDescriptor() {
		final File descriptorFile = descriptorFileReference.getAsFile().get();

		final ObjectMapper mapper = new ObjectMapper( new YAMLFactory() )
				.findAndRegisterModules()
				.disable( SerializationFeature.WRITE_DATES_AS_TIMESTAMPS );

		try {
			mapper.writeValue( descriptorFile, new ExternalizableDescriptor( getDescriptor() ) );
		}
		catch (Exception e) {
			throw new RuntimeException( "Unable to write extension descriptor file - " + descriptorFile.getAbsolutePath(), e );
		}
	}

	/**
	 * Externalizable form of the descriptor used for reading/writing YAML
	 */
	public static class ExternalizableDescriptor {
		private String name;
		private String description;
		private ExternalizableMetadata metadata;

		@SuppressWarnings("unused")
		public ExternalizableDescriptor() {
		}

		public ExternalizableDescriptor(ExtensionDescriptor descriptor) {
			name = descriptor.getNameProperty().get();
			description = descriptor.getDescriptionProperty().get();
			metadata = new ExternalizableMetadata( descriptor.getMetadata() );
		}


		public String getName() {
			return name;
		}

		@SuppressWarnings("unused")
		public void setName(String name) {
			this.name = name;
		}

		public String getDescription() {
			return description;
		}

		@SuppressWarnings("unused")
		public void setDescription(String description) {
			this.description = description;
		}

		public ExternalizableMetadata getMetadata() {
			return metadata;
		}

		@SuppressWarnings("unused")
		public void setMetadata(ExternalizableMetadata metadata) {
			this.metadata = metadata;
		}
	}

	/**
	 * Externalizable form of the metadata used for reading/writing YAML
	 */
	public static class ExternalizableMetadata {
		private String status;
		private String guide;
		private List<String> categories;
		private List<String> keywords;

		@SuppressWarnings("unused")
		public ExternalizableMetadata() {
		}

		public ExternalizableMetadata(ExtensionMetadataDescriptor metadata) {
			status = metadata.getStatusProperty().get();
			guide = metadata.getGuideProperty().get();
			categories = metadata.getCategoriesProperty().get();
			keywords = metadata.getKeywordsProperty().get();
		}

		public String getStatus() {
			return status;
		}

		@SuppressWarnings("unused")
		public void setStatus(String status) {
			this.status = status;
		}

		public String getGuide() {
			return guide;
		}

		@SuppressWarnings("unused")
		public void setGuide(String guide) {
			this.guide = guide;
		}

		public List<String> getCategories() {
			return categories;
		}

		@SuppressWarnings("unused")
		public void setCategories(List<String> categories) {
			this.categories = categories;
		}

		public List<String> getKeywords() {
			return keywords;
		}

		@SuppressWarnings("unused")
		public void setKeywords(List<String> keywords) {
			this.keywords = keywords;
		}
	}
}
