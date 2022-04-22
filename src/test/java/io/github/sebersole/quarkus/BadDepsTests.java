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
import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author Steve Ebersole
 */
public class BadDepsTests {

	@Test
	public void testBundle(@TempDir Path projectDir) {
		prepareProjectDir( projectDir );

		final GradleRunner gradleRunner = GradleRunner.create()
				.withProjectDir( projectDir.toFile() )
				.withPluginClasspath()
				.withDebug( true )
				.withArguments( "build", "--stacktrace", "--no-build-cache" )
				.forwardOutput();

		try {
			final BuildResult buildResult = gradleRunner.build();
			fail( "Expecting a build failure" );
		}
		catch (Exception expected) {
			// expected outcome
		}
	}

	private void prepareProjectDir(Path projectDir) {
		Copier.copyProject( "bad-deps-extension/build.gradle", projectDir );
	}
}
