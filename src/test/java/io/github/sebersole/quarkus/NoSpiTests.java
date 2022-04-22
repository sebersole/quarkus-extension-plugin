package io.github.sebersole.quarkus;

import java.io.File;
import java.nio.file.Path;

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
public class NoSpiTests {

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
		Copier.copyProject( "no-spi-extension/build.gradle", projectDir );
	}

	private void checkRuntime(File buildDir) {
		assertThat( new File( buildDir, "classes/java/main" ) ).exists();
		assertThat( new File( buildDir, "classes/java/test" ) ).exists();

		final File jar = new File( buildDir, "libs/no-spi-extension-1.0-SNAPSHOT.jar" );
		assertThat( jar ).exists();
	}

	private void checkDeployment(File buildDir) {
		assertThat( new File( buildDir, "classes/java/deployment" ) ).exists();

		final File deploymentJar = new File( buildDir, "libs/no-spi-extension-deployment-1.0-SNAPSHOT.jar" );
		assertThat( deploymentJar ).exists();
	}

	private void checkSpi(File buildDir) {
		assertThat( new File( buildDir, "classes/java/spi" ) ).doesNotExist();
		assertThat( new File( buildDir, "libs/no-spi-extension-spi-1.0-SNAPSHOT.jar" ) ).doesNotExist();
	}

}
