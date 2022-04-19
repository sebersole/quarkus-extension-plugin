package io.github.sebersole.quarkus.tasks;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Locale;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import io.github.sebersole.quarkus.Names;

/**
 * @author Steve Ebersole
 */
@CacheableTask
public abstract class GenerateExtensionPropertiesFile extends DefaultTask {
	public static final String TASK_NAME = "generateExtensionProperties";

	private final RegularFileProperty propertiesFile;

	public GenerateExtensionPropertiesFile() {
		setGroup( Names.QUARKUS_GROUP );
		setDescription( "Generates the `quarkus-extension.properties` file ultimately bundled into the extension runtime artifact" );

		propertiesFile = getProject().getObjects().fileProperty();
		propertiesFile.convention( getProject().getLayout().getBuildDirectory().file( "quarkus/quarkus-extension.properties" ) );

		getInputs().property( "projectGroup", getProject().getGroup() );
		getInputs().property( "projectId", getProject().getName() );
		getInputs().property( "projectVersion", getProject().getVersion() );
	}

	@OutputFile
	public RegularFileProperty getPropertiesFile() {
		return propertiesFile;
	}

	@TaskAction
	public void generateProperties() {
		final File propertiesFileAsFile = propertiesFile.get().getAsFile();

		try ( final FileWriter fileWriter = new FileWriter( propertiesFileAsFile ) ) {
			fileWriter.write(
					String.format(
							Locale.ROOT,
							"deployment-artifact=%s\\:%s-deployment\\:%s",
							getProject().getGroup(),
							getProject().getName(),
							getProject().getVersion()
					)
			);
			fileWriter.flush();
		}
		catch (IOException e) {
			throw new RuntimeException( "Unable to generate `quarkus-extension.properties`", e );

		}
	}
}
