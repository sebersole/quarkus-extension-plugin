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

import static org.assertj.core.api.Assertions.assertThat;
import static org.gradle.testkit.runner.TaskOutcome.SUCCESS;

/**
 * @author Steve Ebersole
 */
public class BasicBundlingTest {

	@Test
	public void testBundle(@TempDir Path projectDir) {
		prepareProjectDir( projectDir );

		final GradleRunner gradleRunner = GradleRunner.create()
				.withProjectDir( projectDir.toFile() )
				.withPluginClasspath()
				.withDebug( true )
				.withArguments( "build", "--stacktrace", "--no-build-cache" )
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

		checkRuntime( buildDir );
		checkDeployment( buildDir );
		checkSpi( buildDir );
	}

	private void prepareProjectDir(Path projectDir) {
		Copier.copyProject( "basic-extension/build.gradle", projectDir );
	}

	private void checkRuntime(File buildDir) {
		assertThat( new File( buildDir, "classes/java/main" ) ).exists();
		assertThat( new File( buildDir, "classes/java/test" ) ).exists();

		final File jar = new File( buildDir, "libs/basic-extension-1.0-SNAPSHOT.jar" );
		assertThat( jar ).exists();

		checkConfigRoots( buildDir, jar );
		checkExtensionProperties( buildDir, jar );
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
