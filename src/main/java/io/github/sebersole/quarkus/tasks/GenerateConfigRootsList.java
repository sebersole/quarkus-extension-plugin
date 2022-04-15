package io.github.sebersole.quarkus.tasks;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.inject.Inject;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFile;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.DotName;
import org.jboss.jandex.Index;

import io.github.sebersole.quarkus.Names;

/**
 * @author Steve Ebersole
 */
@CacheableTask
public abstract class GenerateConfigRootsList extends DefaultTask {
	private static final DotName CONFIG_ROOT_ANN = DotName.createSimple( "io.quarkus.runtime.annotations.ConfigRoot" );

	public static final String TASK_NAME = "generateConfigRootsList";

	private final IndexManager indexManager;
	private final RegularFileProperty listFile;

	@Inject
	public GenerateConfigRootsList(IndexManager indexManager) {
		this.indexManager = indexManager;

		setGroup( Names.QUARKUS_GROUP );
		setDescription( "Generates the `quarkus-config-roots.list` file ultimately bundled into the extension runtime artifact" );

		listFile = getProject().getObjects().fileProperty();
		listFile.convention( getProject().getLayout().getBuildDirectory().file( "quarkus/quarkus-config-roots.list" ) );
	}

	@InputFile
	@PathSensitive( PathSensitivity.RELATIVE )
	public Provider<RegularFile> getIndexFileReference() {
		return indexManager.getIndexFileReferenceAccess();
	}

	@OutputFile
	public RegularFileProperty getListFileReference() {
		return listFile;
	}

	@TaskAction
	public void generateList() {
		final File listFileAsFile = listFile.get().getAsFile();
		prepareListFile( listFileAsFile );

		try ( FileWriter writer = new FileWriter( listFileAsFile, StandardCharsets.UTF_8, false ) ) {
			writeEntries( writer, indexManager.getIndex() );
			writer.flush();
		}
		catch (IOException e) {
			throw new RuntimeException( "Unable to open write for `quarkus-config-roots.list` file", e );
		}
	}

	@SuppressWarnings("ResultOfMethodCallIgnored")
	private static void prepareListFile(File listFile) {
		listFile.getParentFile().mkdirs();
	}

	private void writeEntries(FileWriter writer, Index index) {
		final List<AnnotationInstance> configRootAnnUsages = index.getAnnotations( CONFIG_ROOT_ANN );
		configRootAnnUsages.forEach( (usage) -> {
			try {
				writer.write( usage.target().asClass().name().toString() );
				writer.write( Character.LINE_SEPARATOR );
			}
			catch (IOException e) {
				throw new RuntimeException( "Error writing to `quarkus-config-roots.list` file", e );
			}
		} );
	}
}
