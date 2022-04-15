package io.github.sebersole.quarkus;

import java.io.File;
import java.nio.file.Path;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Steve Ebersole
 */
public class BasicUpToDateCheckTest {
	@Test
	public void testBuild(@TempDir Path projectDir) {
		Copier.copyProject( "basic-extension/build.gradle", projectDir );

		// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
		// first run
		System.out.println( ">>> First `build` run" );

		final BuildResult firstBuildResult = buildRunner( projectDir )
				.withArguments( "build", "--stacktrace", "--no-build-cache" )
				.build();

		checkTaskOutcomes(
				firstBuildResult,
				TaskOutcome.SUCCESS,
				":compileJava",
				":indexClasses",
				":generateConfigRootsList",
				":generateExtensionProperties",
				":jar",
				":javadocJar",
				":sourcesJar",
				":compileDeploymentJava",
				":indexDeploymentClasses",
				":generateBuildStepsList",
				":deploymentJar",
				":deploymentJavadocJar",
				":deploymentSourcesJar",
				":compileSpiJava",
				":spiJar",
				":spiJavadocJar",
				":spiSourcesJar",
				":compileTestJava",
				":test"
		);

		assertThat( new File( projectDir.toFile(), "build/quarkus/jandex/main.idx" ) ).exists();
		assertThat( new File( projectDir.toFile(), "build/quarkus/jandex/deployment.idx" ) ).exists();


		// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
		// second run
		System.out.println( ">>> Second `build` run" );

		final BuildResult secondBuildResult = buildRunner( projectDir )
				.withArguments( "build", "--stacktrace", "--no-build-cache" )
				.build();

		checkTaskOutcomes(
				secondBuildResult,
				TaskOutcome.UP_TO_DATE,
				":compileJava",
				":indexClasses",
				":generateConfigRootsList",
				":generateExtensionProperties",
				":jar",
				":javadocJar",
				":sourcesJar",
				":compileDeploymentJava",
				":indexDeploymentClasses",
				":generateBuildStepsList",
				":deploymentJar",
				":deploymentJavadocJar",
				":deploymentSourcesJar",
				":compileSpiJava",
				":spiJar",
				":spiJavadocJar",
				":spiSourcesJar",
				":compileTestJava",
				":test"
		);
	}

	private GradleRunner buildRunner(Path projectDir) {
		return GradleRunner.create()
				.withProjectDir( projectDir.toFile() )
				.withPluginClasspath()
				.withDebug( true )
				.forwardOutput();
	}

	private void checkTaskOutcomes(BuildResult buildResult, TaskOutcome expectedOutcome, String... taskPaths) {
		if ( expectedOutcome == null ) {
			for ( int i = 0; i < taskPaths.length; i++ ) {
				assertThat( buildResult.task( taskPaths[ i ] ) ).isNull();
			}
		}
		else {
			assertThat( buildResult.taskPaths( expectedOutcome ) ).contains( taskPaths );
		}
	}

}
