/*
 * Digital Media Server, for streaming digital media to UPnP AV or DLNA
 * compatible devices based on PS3 Media Server and Universal Media Server.
 * Copyright (C) 2016 Digital Media Server developers.
 *
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see http://www.gnu.org/licenses/.
 */
package net.pms.configuration;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import java.io.File;
import java.util.Locale;
import net.pms.logging.LogLevel;
import net.pms.util.FileUtil;
import net.pms.util.Languages;
import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.LoggerFactory;

public class PmsConfigurationTest {

	private PmsConfiguration configuration;
	@Before
	public void setUp() throws ConfigurationException, InterruptedException {
		// Silence all log messages from the DMS code that is being tested
		LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
		context.getLogger(Logger.ROOT_LOGGER_NAME).setLevel(Level.OFF);

		// Create default configuration instance
		configuration = new PmsConfiguration(false);
	}

	/**
	 * Test Logging Configuration defaults
	 */
	@Test
	public void LoggingConfigurationDefaultsTest() {
		// Test defaults and valid values where applicable
		assertFalse("LogSearchCaseSensitiveDefault", configuration.getGUILogSearchCaseSensitive());
		assertFalse("LogSearchMultiLineDefault", configuration.getGUILogSearchMultiLine());
		assertFalse("LogSearchRegEx", configuration.getGUILogSearchRegEx());
		assertTrue("LogFileNameValid", FileUtil.isValidFileName(configuration.getDefaultLogFileName()));
		assertEquals("LogFileNameDefault", configuration.getDefaultLogFileName(), "debug.log");
		File file = new File(configuration.getDefaultLogFileFolder());
		assertTrue("DefaultLogFileFolder", file.isDirectory());
		file = new File(configuration.getDefaultLogFilePath());
		assertTrue("DefaultLogFilePath", configuration.getDefaultLogFilePath().endsWith("debug.log"));
		assertFalse("LoggingBufferedDefault", configuration.getLoggingBuffered());
		assertEquals("LoggingFilterConsoleDefault", LogLevel.TRACE, configuration.getLoggingFilterConsole());
		assertEquals("LoggingFilterLogsTabDefault", LogLevel.INFO, configuration.getLoggingFilterLogsTab());
		assertEquals("LoggingLogsTabLinebufferDefault", configuration.getLoggingLogsTabLinebuffer(), 1000);
		assertTrue("LoggingLogsTabLinebufferLegal",
			configuration.getLoggingLogsTabLinebuffer() >= PmsConfiguration.LOGGING_LOGS_TAB_LINEBUFFER_MIN &&
			configuration.getLoggingLogsTabLinebuffer() <= PmsConfiguration.LOGGING_LOGS_TAB_LINEBUFFER_MAX);
		assertEquals("LoggingSyslogFacilityDefault", configuration.getLoggingSyslogFacility(), "USER");
		assertEquals("LoggingSyslogHostDefault", configuration.getLoggingSyslogHost(), "");
		assertEquals("LoggingSyslogPortDefault", configuration.getLoggingSyslogPort(), 514);
		assertFalse("LoggingUseSyslogDefault", configuration.getLoggingUseSyslog());
		assertEquals("getLanguageLocaleDefault", configuration.getLanguageLocale(), Languages.toLocale(Locale.getDefault()));
		assertEquals("getLanguageTagDefault", configuration.getLanguageTag(), Languages.toLanguageTag(Locale.getDefault()) );
		configuration.getConfiguration().setProperty("language", "");
		assertEquals("getLanguageLocaleDefault", configuration.getLanguageLocale(), Languages.toLocale(Locale.getDefault()));
		assertEquals("getLanguageTagDefault", configuration.getLanguageTag(), Languages.toLanguageTag(Locale.getDefault()) );
		configuration.getConfiguration().setProperty("language", "en-GB");
		assertEquals("getLanguageLocaleBritishEnglish", configuration.getLanguageLocale(), Locale.forLanguageTag("en-GB"));
		assertEquals("getLanguageTagBritishEnglish", configuration.getLanguageTag(), "en-GB");
		configuration.getConfiguration().setProperty("language", "en");
		assertEquals("getLanguageLocaleEnglish", configuration.getLanguageLocale(), Locale.forLanguageTag("en-US"));
		assertEquals("getLanguageTagEnglish", configuration.getLanguageTag(), "en-US");
		configuration.getConfiguration().setProperty("language", "zh");
		assertEquals("getLanguageLocaleChinese", configuration.getLanguageLocale(), Locale.forLanguageTag("zh-Hant"));
		assertEquals("getLanguageTagChinese", configuration.getLanguageTag(), "zh-Hant");
		configuration.setLanguage(Locale.UK);
		assertEquals("setLanguageUK", configuration.getLanguageLocale(), Locale.forLanguageTag("en-GB"));
		configuration.setLanguage(Locale.SIMPLIFIED_CHINESE);
		assertEquals("setLanguageSimplifiedChinese", configuration.getLanguageLocale(), Locale.forLanguageTag("zh-Hans"));
		configuration.setLanguage(Locale.TRADITIONAL_CHINESE);
		assertEquals("setLanguageTraditionalChinese", configuration.getLanguageLocale(), Locale.forLanguageTag("zh-Hant"));
		Locale locale = null;
		configuration.setLanguage(locale);
		assertEquals("setLanguageNull", configuration.getLanguageLocale(), Locale.forLanguageTag("zh-Hant"));
		String code = null;
		configuration.setLanguage(code);
		assertEquals("setLanguageNull", configuration.getLanguageLocale(), Locale.forLanguageTag("zh-Hant"));
		configuration.setLanguage("");
		assertEquals("setLanguageEmpty", configuration.getLanguageLocale(), Locale.forLanguageTag("zh-Hant"));
		configuration.setLanguage("en");
		assertEquals("setLanguageEnglish", configuration.getLanguageLocale(), Locale.forLanguageTag("en-US"));
	}

	@Test
	public void defaultsTest() {
		assertNull("getLanguageRawStringDefault", configuration.getLanguageRawString());
		configuration.setLanguage((Locale) null);
		assertEquals("setLanguage(null)SetsBlankString", configuration.getLanguageRawString(), "");
	}

	@Test
	public void getDatabaseCacheSizeTest() {
		long jvmMemory = Runtime.getRuntime().maxMemory();
		int defaultSize = jvmMemory == Long.MAX_VALUE ? 0 : (int) ((jvmMemory * (jvmMemory < 1073741824 ? 10 : 20)) / 102400);
		int maxSize = jvmMemory == Long.MAX_VALUE ? Integer.MAX_VALUE : (int) (jvmMemory / 2048);
		assertEquals(defaultSize, configuration.getDatabaseCacheSize());
		Configuration configurationObject = configuration.configuration;
		configurationObject.setProperty(PmsConfiguration.KEY_DATABASE_CACHE_SIZE, "5");
		assertEquals(maxSize == Integer.MAX_VALUE ? 5120 : Math.min(5120, maxSize), configuration.getDatabaseCacheSize());
		configurationObject.setProperty(PmsConfiguration.KEY_DATABASE_CACHE_SIZE, "5TiB");
		assertEquals(maxSize == Integer.MAX_VALUE ? 5242880 : Math.min(5242880, maxSize), configuration.getDatabaseCacheSize());
		configurationObject.setProperty(PmsConfiguration.KEY_DATABASE_CACHE_SIZE, "1.3 GB");
		assertEquals(maxSize == Integer.MAX_VALUE ? 1269531 : Math.min(1269531, maxSize), configuration.getDatabaseCacheSize());
		configurationObject.setProperty(PmsConfiguration.KEY_DATABASE_CACHE_SIZE, "7E");
		assertEquals(maxSize == Integer.MAX_VALUE ? 6835937500000000L : Math.min(6835937500000000L, maxSize), configuration.getDatabaseCacheSize());
		configurationObject.setProperty(PmsConfiguration.KEY_DATABASE_CACHE_SIZE, " 1.3 E2 k");
		assertEquals(maxSize == Integer.MAX_VALUE ? 126 : Math.min(126, maxSize), configuration.getDatabaseCacheSize());
		configurationObject.setProperty(PmsConfiguration.KEY_DATABASE_CACHE_SIZE, "foo");
		assertEquals(defaultSize, configuration.getDatabaseCacheSize());
		configurationObject.setProperty(PmsConfiguration.KEY_DATABASE_CACHE_SIZE, "0");
		assertEquals(0, configuration.getDatabaseCacheSize());
		configurationObject.setProperty(PmsConfiguration.KEY_DATABASE_CACHE_SIZE, "0%");
		assertEquals(0, configuration.getDatabaseCacheSize());
		configurationObject.setProperty(PmsConfiguration.KEY_DATABASE_CACHE_SIZE, " 0 %");
		assertEquals(0, configuration.getDatabaseCacheSize());
		configurationObject.setProperty(PmsConfiguration.KEY_DATABASE_CACHE_SIZE, "0 mB");
		assertEquals(0, configuration.getDatabaseCacheSize());
		configurationObject.setProperty(PmsConfiguration.KEY_DATABASE_CACHE_SIZE, "0 Gi");
		assertEquals(0, configuration.getDatabaseCacheSize());
		configurationObject.setProperty(PmsConfiguration.KEY_DATABASE_CACHE_SIZE, "4.3 MiD");
		assertEquals(defaultSize, configuration.getDatabaseCacheSize());
		configurationObject.setProperty(PmsConfiguration.KEY_DATABASE_CACHE_SIZE, "1243 D");
		assertEquals(defaultSize, configuration.getDatabaseCacheSize());
		configurationObject.setProperty(PmsConfiguration.KEY_DATABASE_CACHE_SIZE, "4.3 ");
		assertEquals(maxSize == Integer.MAX_VALUE ? 4403 : Math.min(4403, maxSize), configuration.getDatabaseCacheSize());
		configurationObject.setProperty(PmsConfiguration.KEY_DATABASE_CACHE_SIZE, " 1243b ");
		assertEquals(maxSize == Integer.MAX_VALUE ? 1 : Math.min(1, maxSize), configuration.getDatabaseCacheSize());
		configurationObject.setProperty(PmsConfiguration.KEY_DATABASE_CACHE_SIZE, " 20% ");
		assertEquals(maxSize == Integer.MAX_VALUE ? 0 : Math.min(((jvmMemory * 20) / 102400), maxSize), configuration.getDatabaseCacheSize());
		configurationObject.setProperty(PmsConfiguration.KEY_DATABASE_CACHE_SIZE, " 30 % ");
		assertEquals(maxSize == Integer.MAX_VALUE ? 0 : Math.min(((jvmMemory * 30) / 102400), maxSize), configuration.getDatabaseCacheSize());
		configurationObject.setProperty(PmsConfiguration.KEY_DATABASE_CACHE_SIZE, " 45 Ki% ");
		assertEquals(defaultSize, configuration.getDatabaseCacheSize());
		configurationObject.setProperty(PmsConfiguration.KEY_DATABASE_CACHE_SIZE, " 76 % ");
		assertEquals(maxSize == Integer.MAX_VALUE ? 0 : Math.min(((jvmMemory * 50) / 102400), maxSize), configuration.getDatabaseCacheSize());
		configurationObject.setProperty(PmsConfiguration.KEY_DATABASE_CACHE_SIZE, " -76 % ");
		assertEquals(defaultSize, configuration.getDatabaseCacheSize());
		configurationObject.setProperty(PmsConfiguration.KEY_DATABASE_CACHE_SIZE, " -2.6 E-4");
		assertEquals(defaultSize, configuration.getDatabaseCacheSize());
		configurationObject.setProperty(PmsConfiguration.KEY_DATABASE_CACHE_SIZE, Double.valueOf(4.5));
		assertEquals(maxSize == Integer.MAX_VALUE ? 4608 : Math.min(4608, maxSize), configuration.getDatabaseCacheSize());
	}
}
