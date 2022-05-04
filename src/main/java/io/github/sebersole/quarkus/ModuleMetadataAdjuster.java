package io.github.sebersole.quarkus;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import org.gradle.api.Project;
import org.gradle.api.file.RegularFile;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.SourceSet;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonWriter;
import jakarta.json.JsonWriterFactory;
import jakarta.json.stream.JsonGenerator;

/**
 * @author Steve Ebersole
 */
public class ModuleMetadataAdjuster {
	static void runtimeAdjustments(RegularFile moduleFile, Project project) {
		withModuleDescriptor( project, moduleFile, (json, adjustmentsAccess) -> withVariants(
				adjustmentsAccess,
				json,
				(variant, variantAdjustments) -> {
					final JavaPluginExtension javaPluginExtension = project.getExtensions().getByType( JavaPluginExtension.class );
					final SourceSet spiSourceSet = javaPluginExtension.getSourceSets().getByName( "spi" );
					if ( spiSourceSet.getAllSource().isEmpty() ) {
						return;
					}

					final String spiModuleName = project.getName() + "-spi";
					addVariantDependency( spiModuleName, variant, variantAdjustments, project );
				}
		) );
	}

	static void deploymentAdjustments(RegularFile moduleFile, Project project) {
		withModuleDescriptor( project, moduleFile, (json, adjustmentsAccess) -> withVariants(
				adjustmentsAccess,
				json,
				(variant, variantAdjustments) -> {
					adjustVariantName( "deployment", variant, variantAdjustments );
					addVariantDependency( project.getName(), variant, variantAdjustments, project );
				}
		) );
	}

	static void spiAdjustments(RegularFile moduleFile, Project project) {
		//noinspection CodeBlock2Expr
		withModuleDescriptor( project, moduleFile, (json, adjustmentsAccess) -> withVariants(
				adjustmentsAccess,
				json,
				(variant, variantAdjustments) -> {
					adjustVariantName("spi", variant, variantAdjustments );
				}
		) );
	}

	private static void withVariants(
			Supplier<JsonObjectBuilder> descriptorAdjustmentsAccess,
			JsonObject descriptor,
			VariantAdjuster variantAdjuster) {
		final JsonArray variants = descriptor.getJsonArray( "variants" );
		if ( variants == null ) {
			return;
		}

		final JsonObjectBuilder descriptorAdjustments = descriptorAdjustmentsAccess.get();
		final JsonArrayBuilder variantArrayAdjustments = Json.createArrayBuilder();

		for ( int v = 0; v < variants.size(); v++ ) {
			final JsonObject variant = variants.getJsonObject( v );

			final JsonObjectBuilder variantAdjustments = Json.createObjectBuilder( variant );
			variantAdjuster.adjust( variant, variantAdjustments );
			variantArrayAdjustments.add( variantAdjustments );
		}

		descriptorAdjustments.add( "variants", variantArrayAdjustments );
	}

	private static void withModuleDescriptor(Project project, RegularFile moduleFile, ModuleDescriptorAdjuster adjustment) {
		final File moduleFileAsFile = moduleFile.getAsFile();
		final long lastModified = moduleFileAsFile.lastModified();

		final JsonObject json;
		try ( final FileReader reader = new FileReader( moduleFileAsFile ) ) {
			json = Json.createReader( reader ).readObject();
		}
		catch (IOException e) {
			throw new RuntimeException( "Could read Gradle module metadata file - " + moduleFileAsFile.getAbsolutePath(), e );
		}

		final AdjustmentAccess adjustmentAccess = new AdjustmentAccess( json );
		adjustment.adjust( json, adjustmentAccess::resolveAdjustments );

		if ( adjustmentAccess.getAdjustments() != null ) {
			try ( final FileWriter fileWriter = new FileWriter( moduleFileAsFile ) ) {
//				JsonWriterFactory jsonWriterFactory =

//				final JsonObject adjusted = adjustmentAccess.getAdjustments().build();
//				final Gson prettifier = new GsonBuilder().setPrettyPrinting().create();
//				final StringWriter stringWriter = new StringWriter();
//				final JsonWriter jsonWriter = Json.createWriter( stringWriter );
//				jsonWriter.write( adjusted );
//				stringWriter.flush();
//				String prettyJson = prettifier.toJson( stringWriter.toString() );

//				ObjectMapper mapper = new ObjectMapper();
//				final String prettyJson = mapper.writerWithDefaultPrettyPrinter()
//						.writeValueAsString( adjustmentAccess.getAdjustments().build() );
//				fileWriter.write( prettyJson );

				final Map<String,Object> properties = new HashMap<>();
				properties.put( JsonGenerator.PRETTY_PRINTING, true );
				JsonWriterFactory writerFactory = Json.createWriterFactory( properties );
				JsonWriter jsonWriter = writerFactory.createWriter( fileWriter );
				jsonWriter.writeObject( adjustmentAccess.getAdjustments().build() );

//				fileWriter.write( prettyJson );
				fileWriter.flush();
			}
			catch (IOException e) {
				throw new RuntimeException( "Could re-write Gradle module metadata file - " + moduleFileAsFile.getAbsolutePath(), e );
			}

			if ( moduleFileAsFile.lastModified() != lastModified ) {
				if ( !moduleFileAsFile.setLastModified( lastModified ) ) {
					project.getLogger().info(
							"Unable to reset last-modified timestamp for Gradle module metadata file - {}; up-to-date checks may be affected",
							moduleFileAsFile.getAbsolutePath()
					);
				}
			}
		}
	}

	private static void adjustVariantName(String publicationName, JsonObject variant, JsonObjectBuilder adjustments) {
		final String variantName = variant.getString( "name" );
		final String adjustedName;
		if ( variantName.endsWith( "ApiElements" ) ) {
			adjustedName = "apiElements";
		}
		else if ( variantName.endsWith( "RuntimeElements" ) ) {
			adjustedName = "runtimeElements";
		}
		else if ( variantName.endsWith( "JavadocElements" ) ) {
			adjustedName = "javadocElements";
		}
		else if ( variantName.endsWith( "SourcesElements" ) ) {
			adjustedName = "sourcesElements";
		}
		else {
			// handle it specially...
			final String initialLetter = variantName.substring( publicationName.length(), publicationName.length() + 1 );
			adjustedName = Character.toLowerCase( initialLetter.charAt( publicationName.length() ) )
					+ variantName.substring( publicationName.length() + 1 );
		}

		adjustments.add( "name", adjustedName );
	}

	private static void addVariantDependency(
			String dependencyModuleName,
			JsonObject variant,
			JsonObjectBuilder adjustments,
			Project project) {
		final JsonArray existingDependenciesNode = variant.getJsonArray( "dependencies" );
		final JsonArrayBuilder dependenciesBuilder = existingDependenciesNode == null
				? Json.createArrayBuilder()
				: Json.createArrayBuilder( existingDependenciesNode );

		final JsonObjectBuilder dependencyBuilder = Json.createObjectBuilder();

		dependencyBuilder.add( "group", project.getGroup().toString() );
		dependencyBuilder.add( "module", dependencyModuleName );
		dependencyBuilder.add(
				"version",
				Json.createObjectBuilder().add( "requires", project.getVersion().toString() )
		);

		dependenciesBuilder.add( dependencyBuilder );
		adjustments.add( "dependencies", dependenciesBuilder );
	}

	@FunctionalInterface
	interface ModuleDescriptorAdjuster {
		void adjust(JsonObject descriptor, Supplier<JsonObjectBuilder> adjustments);
	}

	@FunctionalInterface
	interface VariantAdjuster {
		void adjust(JsonObject variant, JsonObjectBuilder adjustments);
	}


	private static class AdjustmentAccess {
		private final JsonObject descriptor;
		private JsonObjectBuilder adjustments;

		public AdjustmentAccess(JsonObject descriptor) {
			this.descriptor = descriptor;
		}

		public JsonObjectBuilder resolveAdjustments() {
			if ( adjustments == null ) {
				adjustments = Json.createObjectBuilder( descriptor );
			}
			return adjustments;
		}

		public JsonObjectBuilder getAdjustments() {
			return adjustments;
		}
	}
}
