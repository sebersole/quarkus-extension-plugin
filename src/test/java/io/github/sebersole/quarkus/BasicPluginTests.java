package io.github.sebersole.quarkus;

import java.io.File;
import java.nio.file.Path;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Steve Ebersole
 */
public class BasicPluginTests {

	@Test
	public void testBundle(@TempDir Path projectDir) {
		prepareProjectDir( projectDir );

		final GradleRunner gradleRunner = GradleRunner.create()
				.withProjectDir( projectDir.toFile() )
				.withPluginClasspath()
				.withDebug( true )
				.withArguments( "build", "--stacktrace" )
				.forwardOutput();

		final BuildResult buildResult = gradleRunner.build();

		final File buildDir = new File( projectDir.toFile(), "build" );
		assertThat( buildDir ).exists();
	}

	private void prepareProjectDir(Path projectDir) {
		Copier.copyProject( "basic-extension/build.gradle", projectDir );
	}

}
