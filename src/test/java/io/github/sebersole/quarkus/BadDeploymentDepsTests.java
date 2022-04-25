package io.github.sebersole.quarkus;

import java.nio.file.Path;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author Steve Ebersole
 */
public class BadDeploymentDepsTests {

	@Test
	public void testRuntimeDependencyOnDeploymentArtifactFails(@TempDir Path projectDir) {
		prepareProjectDir( projectDir );

		final GradleRunner gradleRunner = GradleRunner.create()
				.withProjectDir( projectDir.toFile() )
				.withPluginClasspath()
				.withDebug( true )
				.withArguments( "build", "--stacktrace", "--no-build-cache" )
				.forwardOutput();

		final BuildResult buildResult = gradleRunner.buildAndFail();
		assertThat( buildResult.getOutput() ).contains( "io.github.sebersole.quarkus.ValidationException" );
		assertThat( buildResult.getOutput() ).contains( "io.quarkus:quarkus-agroal" );
		assertThat( buildResult.getOutput() ).contains( "io.quarkus:quarkus-datasource" );
	}

	private void prepareProjectDir(Path projectDir) {
		Copier.copyProject( "bad-deployment-deps-extension/build.gradle", projectDir );
	}
}
