package com.condation.cms.modules.pebble;

/*-
 * #%L
 * cms-server
 * %%
 * Copyright (C) 2023 Marx-Software
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */

import com.github.benmanes.caffeine.cache.Caffeine;
import com.condation.cms.api.ServerProperties;
import com.condation.cms.api.db.DB;
import com.condation.cms.api.db.DBFileSystem;
import com.condation.cms.api.template.TemplateEngine;
import com.condation.cms.api.theme.Theme;
import io.pebbletemplates.pebble.PebbleEngine;
import io.pebbletemplates.pebble.cache.tag.CaffeineTagCache;
import io.pebbletemplates.pebble.cache.template.CaffeineTemplateCache;
import io.pebbletemplates.pebble.loader.DelegatingLoader;
import io.pebbletemplates.pebble.loader.FileLoader;
import io.pebbletemplates.pebble.loader.Loader;
import io.pebbletemplates.pebble.loader.StringLoader;
import io.pebbletemplates.pebble.template.PebbleTemplate;
import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author thmar
 */
public class PebbleTemplateEngine implements TemplateEngine {

	private PebbleEngine fileTemplateEngine;
	private PebbleEngine stringTemplateEngine;
	
	final DB db;
	final ServerProperties properties;
	
	public PebbleTemplateEngine(final DB db, final ServerProperties properties, final Theme theme) {
		
		this.db = db;
		this.properties = properties;
		buildEngine(theme);
	}
	
	
	
	private Loader<?> createLoader (final DBFileSystem fileSystem, final Theme theme) {
		List<Loader<?>> loaders = new ArrayList<>();
		
		var siteLoader = new FileLoader();
		siteLoader.setPrefix(fileSystem.resolve("templates/").toString() + File.separatorChar);
		loaders.add(siteLoader);
		
		if (!theme.empty()) {
			var themeLoader = new FileLoader();
			themeLoader.setPrefix(theme.templatesPath().toString() + File.separatorChar);
			loaders.add(themeLoader);
			
			if (theme.getParentTheme() != null) {
				var parentLoader = new FileLoader();
				parentLoader.setPrefix(theme.getParentTheme().templatesPath().toString() + File.separatorChar);
				loaders.add(parentLoader);
			}
		}
		
		return new DelegatingLoader(loaders);
	}

	@Override
	public String render(String template, Model model) throws IOException {
		Writer writer = new StringWriter();
		PebbleTemplate compiledTemplate = fileTemplateEngine.getTemplate(template);
		compiledTemplate.evaluate(writer, model.values);
		return writer.toString();
	}

	@Override
	public String renderFromString(String templateString, Model model) throws IOException {
		Writer writer = new StringWriter();
		PebbleTemplate compiledTemplate = stringTemplateEngine.getTemplate(templateString);
		compiledTemplate.evaluate(writer, model.values);
		return writer.toString();
	}
	
	

	@Override
	public void invalidateCache() {
		fileTemplateEngine.getTemplateCache().invalidateAll();
	}

	@Override
	public void updateTheme(Theme theme) {
		buildEngine(theme);
	}

	private void buildEngine(Theme theme) {
		final PebbleEngine.Builder builder = new PebbleEngine.Builder()
				.loader(createLoader(db.getFileSystem(), theme));
		
		if (properties.dev()) {
			builder.templateCache(null);
			builder.tagCache(null);
			builder.cacheActive(false);
			builder.strictVariables(true);
		} else {
			var templateCache = new CaffeineTemplateCache(
					Caffeine.newBuilder()
							.expireAfterWrite(Duration.ofMinutes(1))
							.build()
			);
			builder.templateCache(templateCache);
			var tagCache = new CaffeineTagCache(
					Caffeine.newBuilder()
							.expireAfterWrite(Duration.ofMinutes(1))
							.build()
			);
			builder.tagCache(tagCache);
			builder.cacheActive(true);
		}
		
		fileTemplateEngine = builder
				.build();
		
		stringTemplateEngine = builder.loader(new StringLoader()).build();
	}

}
