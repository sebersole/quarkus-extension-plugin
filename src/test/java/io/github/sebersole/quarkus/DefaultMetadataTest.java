package io.github.sebersole.quarkus;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.github.sebersole.quarkus.tasks.GenerateDescriptor;

import static io.github.sebersole.quarkus.tasks.GenerateDescriptor.STANDARD_YAML_PATH;
import static io.github.sebersole.quarkus.tasks.GenerateDescriptor.YAML_NAME;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Steve Ebersole
 */
public class DefaultMetadataTest {
	@Test
	public void defaultValuesTest(@TempDir Path projectDir) {
		prepareProjectDir( projectDir );

		final GradleRunner gradleRunner = GradleRunner.create()
				.withProjectDir( projectDir.toFile() )
				.withPluginClasspath()
				.withDebug( true )
				.withArguments( "build", "--stacktrace", "--no-build-cache" )
				.forwardOutput();

		final BuildResult buildResult = gradleRunner.build();

		final File buildDir = new File( projectDir.toFile(), "build" );
		assertThat( buildDir ).exists();

		checkRuntime( buildDir );
		checkDeployment( buildDir );
	}

	private void checkDeployment(File buildDir) {
		final File jar = new File( buildDir, "libs/default-metadata-extension-deployment-1.0-SNAPSHOT.jar" );
		assertThat( jar ).exists();
	}

	private void checkRuntime(File buildDir) {
		final File jar = new File( buildDir, "libs/default-metadata-extension-1.0-SNAPSHOT.jar" );
		assertThat( jar ).exists();

		checkExtensionDescriptor( buildDir, jar );
	}

	private void checkExtensionDescriptor(File buildDir, File jar) {
		final File yamlFile = new File( buildDir, STANDARD_YAML_PATH );
		assertThat( yamlFile ).exists();

		try {
			final JarFile jarFile = new JarFile( jar );
			final ZipEntry yamlEntry = jarFile.getEntry( "META-INF/" + YAML_NAME );
			assertThat( yamlEntry ).isNotNull();


			final ObjectMapper mapper = new ObjectMapper( new YAMLFactory() )
					.findAndRegisterModules()
					.disable( SerializationFeature.WRITE_DATES_AS_TIMESTAMPS );

			try {
				final GenerateDescriptor.ExternalizableDescriptor value = mapper
						.readValue( jarFile.getInputStream( yamlEntry ), GenerateDescriptor.ExternalizableDescriptor.class );

				assertThat( value.getName() ).isEqualTo( "default-metadata-extension" );
				assertThat( value.getDescription() ).isEqualTo( "Quarkus extension for testing this Gradle extension plugin (default values)" );

				assertThat( value.getMetadata().getStatus() ).isEqualTo( "development" );
				assertThat( value.getMetadata().getGuide() ).isNull();
				assertThat( value.getMetadata().getCategories() ).isEmpty();
				assertThat( value.getMetadata().getKeywords() ).isEmpty();
			}
			catch (Exception e) {
				throw new RuntimeException( "Unable to read extension YAML descriptor file", e );
			}
		}
		catch (IOException e) {
			throw new RuntimeException( "Error accessing runtime jar " + jar.getAbsolutePath() );
		}
	}

	private void prepareProjectDir(Path projectDir) {
		Copier.copyProject( "default-metadata-extension/build.gradle", projectDir );
	}
}
