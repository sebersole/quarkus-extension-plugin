package io.github.sebersole.quarkus;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Steve Ebersole
 */
public class ExtensionPublishingTests {
	@Test
	public void testPublishing(@TempDir Path projectDir) {
		Copier.copyProject( "published-extension/build.gradle", projectDir );

		final GradleRunner gradleRunner = GradleRunner.create()
				.withProjectDir( projectDir.toFile() )
				.withPluginClasspath()
				.withDebug( true )
				.withArguments( "publishAllPublicationsToTestingRepository", "--stacktrace", "--no-build-cache" )
//				.withArguments( "tasks","--stacktrace", "--no-build-cache" )
				.forwardOutput();

		final BuildResult buildResult = gradleRunner.build();

		final File publishingPrepOutput = new File( projectDir.toFile(), "build/publications" );

		final File runtimeArtifactDir = new File( publishingPrepOutput, "extension" );
		assertThat( runtimeArtifactDir ).exists();
		final File runtimePom = new File( runtimeArtifactDir, "pom-default.xml" );
		assertThat( runtimePom ).exists();
		checkRuntimePom( runtimePom );
		assertThat( new File( runtimeArtifactDir, "module.json") ).exists();

		final File deploymentArtifactDir = new File( publishingPrepOutput, "deployment" );
		assertThat( deploymentArtifactDir ).exists();
		final File deploymentPom = new File( deploymentArtifactDir, "pom-default.xml" );
		assertThat( deploymentPom ).exists();
		checkDeploymentPom( deploymentPom );

		final File deploymentModuleDescriptor = new File( deploymentArtifactDir, "module.json" );
		assertThat( deploymentModuleDescriptor ).exists();
		checkDeploymentModuleDescriptor( deploymentModuleDescriptor );


		final File publishingOutput = new File( projectDir.toFile(), "build/test-publishing" );
		final File groupDir = new File( publishingOutput, "io/github/sebersole/quarkus" );

		checkForJar( groupDir, "published-extension" );
		checkForJar( groupDir, "published-extension-deployment" );
		checkForJar( groupDir, "published-extension-spi" );
	}

	private void checkDeploymentModuleDescriptor(File moduleDescriptor) {
		try {
			final String content = Files.readString( moduleDescriptor.toPath() );
			assertThat( content ).doesNotContain( "deploymentApiElements" );
			assertThat( content ).doesNotContain( "deploymentRuntimeElements" );
			assertThat( content ).doesNotContain( "deploymentJavadocElements" );
			assertThat( content ).doesNotContain( "deploymentSourcesElements" );

			assertThat( content ).contains( "apiElements" );
			assertThat( content ).contains( "runtimeElements" );
			assertThat( content ).contains( "javadocElements" );
			assertThat( content ).contains( "sourcesElements" );
		}
		catch (IOException e) {
			throw new RuntimeException( "Unable to read module descriptor - " + moduleDescriptor.getAbsolutePath(), e );
		}
	}

	private void checkForJar(File groupDir, String artifactId) {
		final File artifactDir = new File( groupDir, artifactId );
		final File versionDir = new File( artifactDir, "1.0-SNAPSHOT" );
		final String[] matches = versionDir.list( (dir, name) -> name.startsWith( artifactId + "-1.0" ) && name.endsWith( ".jar" ) );
		assertThat( matches ).hasSize( 3 );
	}

	private void checkDeploymentPom(File deploymentArtifactPom) {
		assertThat( deploymentArtifactPom ).exists();

		try {
			final String contents = Files.readString( deploymentArtifactPom.toPath() );
			assertThat( contents ).contains( "<artifactId>published-extension</artifactId>" );
			assertThat( contents ).contains( "<artifactId>published-extension</artifactId>" );
		}
		catch (IOException e) {
			throw new RuntimeException( "Unable to read POM file - " + deploymentArtifactPom.getAbsolutePath(), e );
		}
	}

	private void checkRuntimePom(File runtimeArtifactPom) {
		assertThat( runtimeArtifactPom ).exists();

		try {
			assertThat( Files.readString( runtimeArtifactPom.toPath() ) ).contains( "<artifactId>published-extension-spi</artifactId>" );
		}
		catch (IOException e) {
			throw new RuntimeException( "Unable to read POM file - " + runtimeArtifactPom.getAbsolutePath(), e );
		}
	}

}
