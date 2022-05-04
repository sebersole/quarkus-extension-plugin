package io.github.sebersole.quarkus;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.nio.file.Path;
import java.util.Locale;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.BuildTask;
import org.gradle.testkit.runner.GradleRunner;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.github.sebersole.quarkus.tasks.GenerateDescriptor;
import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;

import static io.github.sebersole.quarkus.tasks.GenerateDescriptor.STANDARD_YAML_PATH;
import static io.github.sebersole.quarkus.tasks.GenerateDescriptor.YAML_NAME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.gradle.testkit.runner.TaskOutcome.SUCCESS;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author Steve Ebersole
 */
public class BasicBundlingTest {

	@Test
	public void testBundle(@TempDir Path projectDir) {
		System.out.println( "#########################################################################" );
		System.out.println( "Project dir : " + projectDir.toFile().getAbsolutePath() );
		System.out.println( "#########################################################################" );
		prepareProjectDir( projectDir );

		final GradleRunner gradleRunner = GradleRunner.create()
				.withProjectDir( projectDir.toFile() )
				.withPluginClasspath()
				.withDebug( true )
				.withArguments( "showConfigurations", "build", "preparePublications", "--stacktrace", "--no-build-cache" )
				.forwardOutput();

		final BuildResult buildResult = gradleRunner.build();

		final BuildTask compileDeployment = buildResult.task( ":compileDeploymentJava" );
		assertThat( compileDeployment ).isNotNull();
		assertThat( compileDeployment.getOutcome() ).isEqualTo( SUCCESS );

		final BuildTask jarDeployment = buildResult.task( ":deploymentJar" );
		assertThat( jarDeployment ).isNotNull();
		assertThat( jarDeployment.getOutcome() ).isEqualTo( SUCCESS );


		final File buildDir = new File( projectDir.toFile(), "build" );
		assertThat( buildDir ).exists();

		checkExtension( buildDir );
		checkDeployment( buildDir );
		checkSpi( buildDir );
	}

	private void prepareProjectDir(Path projectDir) {
		Copier.copyProject( "basic-extension/build.gradle", projectDir );
	}

	private void checkExtension(File buildDir) {
		assertThat( new File( buildDir, "classes/java/main" ) ).exists();
		assertThat( new File( buildDir, "classes/java/test" ) ).exists();

		final File jar = new File( buildDir, "libs/basic-extension-1.0-SNAPSHOT.jar" );
		assertThat( jar ).exists();

		checkExtensionDescriptor( buildDir, jar );
		checkConfigRoots( buildDir, jar );
		checkExtensionProperties( buildDir, jar );

		checkForPomDescriptor( new File( buildDir, "publications/extension" ), "basic-extension-spi" );
		checkModuleDescriptor( new File( buildDir, "publications/extension" ), "basic-extension-spi" );
	}

	private void checkForPomDescriptor(File publicationDir, String dependencyArtifactId) {
		final String check = String.format(
				Locale.ROOT,
				"<artifactId>%s</artifactId>",
				dependencyArtifactId
		);

		final File pomFile = new File( publicationDir, "pom-default.xml" );
		accessContent( pomFile, (reader) -> {
			String line = reader.readLine();
			while ( line != null ) {
				if ( check.equals( line.trim() ) ) {
					return;
				}
				line = reader.readLine();
			}

			fail( "Could not find dependency `" + check + "` in `" + pomFile.getAbsolutePath() + "`" );
		} );
	}

	private void accessContent(File file, ContentAccess contentAccess) {
		assertThat( file ).exists();

		try ( final LineNumberReader reader = new LineNumberReader( new FileReader( file ) ) ) {
			contentAccess.accessContent( reader );
		}
		catch (IOException e) {
			fail( "Error accessing content of file " + file.getAbsolutePath(), e );
		}
	}

	@FunctionalInterface
	private interface ContentAccess {
		void accessContent(LineNumberReader reader) throws IOException;
	}

	private void checkModuleDescriptor(File publicationDir, String dependencyArtifactId) {
		final File metadataFile = new File( publicationDir, "module.json" );
		accessContent( metadataFile, (reader) -> {
			final JsonObject json = Json.createReader( reader ).readObject();
//			final JsonObject json = Json.createParser( reader ).getObject();
			final JsonArray variants = json.getJsonArray( "variants" );
			for ( int v = 0; v < variants.size(); v++ ) {
				boolean found = false;
				final JsonObject variant = variants.getJsonObject( v );

				final String variantName = variant.getString( "name" );
				assertThat( variantName ).doesNotContain( "deployment", "spi" );

				final JsonArray dependencies = variant.getJsonArray( "dependencies" );
				for ( int d = 0; d < dependencies.size(); d++ ) {
					final JsonObject dependency = dependencies.getJsonObject( d );
					if ( "io.github.sebersole.quarkus".equals( dependency.getString( "group" ) )
							&& dependencyArtifactId.equals( dependency.getString( "module" ) ) ) {
						found = true;
					}
				}

				if ( !found ) {
					fail(
							String.format(
									Locale.ROOT,
									"Could not find `%s` dependency as part of `%s` variant in `%s`",
									dependencyArtifactId,
									variantName,
									metadataFile.getAbsolutePath()
							)
					);
				}
			}
		} );
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

				assertThat( value.getName() ).isEqualTo( "Basic Quarkus extension" );
				assertThat( value.getDescription() ).isEqualTo( "Quarkus extension for testing this Gradle extension plugin" );

				assertThat( value.getMetadata().getStatus() ).isEqualTo( "stable" );
				assertThat( value.getMetadata().getGuide() ).isEqualTo( "https://hibernate.org" );
				assertThat( value.getMetadata().getCategories() ).contains( "sample" );
				assertThat( value.getMetadata().getKeywords() ).contains( "sample", "gradle" );
			}
			catch (Exception e) {
				throw new RuntimeException( "Unable to read extension YAML descriptor file", e );
			}
		}
		catch (IOException e) {
			throw new RuntimeException( "Error accessing runtime jar " + jar.getAbsolutePath() );
		}
	}

	private void checkConfigRoots(File buildDir, File jar) {
		final File configRootsList = new File( buildDir, "quarkus/quarkus-config-roots.list" );
		assertThat( configRootsList ).exists();
		try ( final LineNumberReader reader = new LineNumberReader( new FileReader( configRootsList ) ) ) {
			checkConfigRootFile( reader );
		}
		catch (FileNotFoundException e) {
			// should never happen since we explicitly checked existence earlier
		}
		catch (IOException e) {
			throw new RuntimeException( "Error reading lines from " + configRootsList.getAbsolutePath() );
		}


		try {
			final JarFile jarFile = new JarFile( jar );
			final ZipEntry configRootsEntry = jarFile.getEntry( "META-INF/quarkus-config-roots.list" );
			assertThat( configRootsEntry ).isNotNull();
			try ( LineNumberReader entryReader = new LineNumberReader( new InputStreamReader( jarFile.getInputStream( configRootsEntry ) ) ) ) {
				checkConfigRootFile( entryReader );
			}
			catch (IOException e) {
				throw new RuntimeException( "Error reading runtime jar entries " + jar.getAbsolutePath() );
			}
		}
		catch (IOException e) {
			throw new RuntimeException( "Error accessing runtime jar " + jar.getAbsolutePath() );
		}
	}

	private void checkConfigRootFile(LineNumberReader reader) throws IOException {
		final String line = reader.readLine();
		assertThat( line ).isEqualTo( "io.github.sebersole.quarkus.extension.MyExtensionConfig" );
		assertThat( reader.readLine() ).isNull();
	}

	private void checkExtensionProperties(File buildDir, File jar) {
		final File propFile = new File( buildDir, "quarkus/quarkus-extension.properties" );
		assertThat( propFile ).exists();

		try ( final LineNumberReader reader = new LineNumberReader( new FileReader( propFile ) ) ) {
			checkPropFile( reader );
		}
		catch (FileNotFoundException e) {
			// should never happen since we explicitly checked existence earlier
		}
		catch (IOException e) {
			throw new RuntimeException( "Error reading lines from " + propFile.getAbsolutePath() );
		}


		try {
			final JarFile jarFile = new JarFile( jar );
			final ZipEntry configRootsEntry = jarFile.getEntry( "META-INF/quarkus-extension.properties" );
			assertThat( configRootsEntry ).isNotNull();
			try ( LineNumberReader entryReader = new LineNumberReader( new InputStreamReader( jarFile.getInputStream( configRootsEntry ) ) ) ) {
				checkPropFile( entryReader );
			}
			catch (IOException e) {
				throw new RuntimeException( "Error reading runtime jar entries " + jar.getAbsolutePath() );
			}
		}
		catch (IOException e) {
			throw new RuntimeException( "Error accessing runtime jar " + jar.getAbsolutePath() );
		}
	}

	private void checkPropFile(LineNumberReader reader) throws IOException {
		final String line = reader.readLine();
		assertThat( line ).isEqualTo(
				String.format(
						Locale.ROOT,
						"deployment-artifact=%s\\:%s-deployment\\:%s",
						"io.github.sebersole.quarkus",
						"basic-extension",
						"1.0-SNAPSHOT"
				)
		);

		assertThat( reader.readLine() ).isNull();
	}

	private void checkDeployment(File buildDir) {
		assertThat( new File( buildDir, "classes/java/deployment" ) ).exists();

		final File jar = new File( buildDir, "libs/basic-extension-deployment-1.0-SNAPSHOT.jar" );
		assertThat( jar ).exists();

		checkBuildSteps( buildDir, jar );

		checkForPomDescriptor( new File( buildDir, "publications/deployment" ), "basic-extension" );
		checkModuleDescriptor( new File( buildDir, "publications/deployment" ), "basic-extension" );
	}

	private void checkBuildSteps(File buildDir, File jar) {
		final File stepsFile = new File( buildDir, "quarkus/quarkus-build-steps.list" );
		assertThat( stepsFile ).exists();
		try ( final LineNumberReader reader = new LineNumberReader( new FileReader( stepsFile ) ) ) {
			checkStepsFile( reader );
		}
		catch (FileNotFoundException e) {
			// should never happen since we explicitly checked existence earlier
		}
		catch (IOException e) {
			throw new RuntimeException( "Error reading lines from " + stepsFile.getAbsolutePath() );
		}


		try {
			final JarFile jarFile = new JarFile( jar );
			final ZipEntry configRootsEntry = jarFile.getEntry( "META-INF/quarkus-build-steps.list" );
			assertThat( configRootsEntry ).isNotNull();
			try ( LineNumberReader entryReader = new LineNumberReader( new InputStreamReader( jarFile.getInputStream( configRootsEntry ) ) ) ) {
				checkStepsFile( entryReader );
			}
			catch (IOException e) {
				throw new RuntimeException( "Error reading runtime jar entries " + jar.getAbsolutePath() );
			}
		}
		catch (IOException e) {
			throw new RuntimeException( "Error accessing runtime jar " + jar.getAbsolutePath() );
		}
	}

	private void checkStepsFile(LineNumberReader reader) throws IOException {
		final String line = reader.readLine();
		assertThat( line ).isEqualTo( "io.github.sebersole.quarkus.extension.MyExtensionProcessor" );
		assertThat( reader.readLine() ).isNull();
	}

	private void checkSpi(File buildDir) {
		assertThat( new File( buildDir, "classes/java/spi" ) ).exists();

		final File jar = new File( buildDir, "libs/basic-extension-spi-1.0-SNAPSHOT.jar" );
		assertThat( jar ).exists();
	}

}
