package net.pms.util;

import com.ibm.icu.text.CharsetDetector;
import com.ibm.icu.text.CharsetMatch;
import com.sun.jna.Platform;
import java.io.*;
import java.lang.reflect.Field;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.pms.PMS;
import net.pms.configuration.PmsConfiguration;
import net.pms.configuration.WindowsProgramPaths;
import net.pms.formats.FormatFactory;
import net.pms.io.BasicSystemUtils;
import net.pms.util.FilePermissions.FileFlag;
import net.pms.util.StringUtil.LetterCase;
import static net.pms.util.Constants.*;
import static org.apache.commons.lang3.StringUtils.*;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.text.WordUtils;
import org.apache.commons.text.similarity.JaroWinklerDistance;
import org.codehaus.plexus.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FileUtil {
	private static final Logger LOGGER = LoggerFactory.getLogger(FileUtil.class);
	private static final int S_ISVTX = 512; // Unix sticky bit mask

	/**
	 * An array of chars that qualifies as file path separators. For Windows
	 * filesystems that is forward slash in addition to
	 * {@link File#separatorChar}, for other filesystems it is only
	 * {@link File#separatorChar}. This is to be compatible with Java's behavior
	 * of accepting forward slash as a separator also on Windows.
	 */
	private static final char[] FILE_SEPARATORS;

	// Signal an invalid parameter in getFileLocation() without raising an exception or returning null
	private static final String DEFAULT_BASENAME = "NO_DEFAULT_BASENAME_SUPPLIED.conf";

	static {
		char separator = File.separatorChar;
		if (separator == '\\') {
			FILE_SEPARATORS = new char[2];
			FILE_SEPARATORS[0] = separator;
			FILE_SEPARATORS[1] = '/';
		} else {
			FILE_SEPARATORS = new char[1];
			FILE_SEPARATORS[0] = separator;
		}
	}

	// This class is not instantiable
	private FileUtil() {
	}

	/**
	 * A helper class used by {@link #getFileLocation(String, String, String)}
	 * which provides access to a file's absolute path and that of its
	 * directory.
	 *
	 * @since 1.90.0
	 */
	public static final class FileLocation {
		private String directoryPath;
		private String filePath;

		FileLocation(File directory, File file) {
			this.directoryPath = FilenameUtils.normalize(directory.getAbsolutePath());
			this.filePath = FilenameUtils.normalize(file.getAbsolutePath());
		}

		public String getDirectoryPath() {
			return directoryPath;
		}

		public String getFilePath() {
			return filePath;
		}
	}

	/**
	 * Returns a {@link FileLocation} object which provides access to the
	 * directory and file paths of the specified file as normalised, absolute
	 * paths.
	 *
	 * This determines the directory and file path of a file according to the
	 * rules outlined here:
	 * http://www.ps3mediaserver.org/forum/viewtopic.php?f=6
	 * &amp;t=3507&amp;p=49895#p49895
	 *
	 * @param customPath an optional user-defined path for the resource
	 * @param defaultDirectory a default directory path used if no custom path
	 *            is provided
	 * @param defaultBasename a default filename used if a) no custom path is
	 *            provided or b) the custom path is a directory
	 * @return a {@link FileLocation} object providing access to the file's
	 *         directory and file paths
	 * @since 1.90.0
	 */
	// this is called from a static initializer, where errors aren't clearly reported,
	// so do everything possible to return a valid response, even if the parameters aren't sane
	public static FileLocation getFileLocation(
		String customPath,
		String defaultDirectory,
		String defaultBasename
	) {
		File customFile = null;
		File directory = null;
		File file = null;

		if (isBlank(defaultBasename)) {
			// shouldn't get here
			defaultBasename = DEFAULT_BASENAME;
		}

		if (defaultDirectory == null) {
			defaultDirectory = ""; // current directory
		}

		if (customPath != null) {
			customFile = new File(customPath).getAbsoluteFile();
		}

		if (customFile != null) {
			if (customFile.exists()) {
				if (customFile.isDirectory()) {
					directory = customFile;
					file = new File(customFile, defaultBasename).getAbsoluteFile();
				} else {
					directory = customFile.getParentFile();
					file = customFile;
				}
			} else {
				File parentDirectoryFile = customFile.getParentFile();
				if (parentDirectoryFile != null && parentDirectoryFile.exists()) {
					// parent directory exists: the file can be created
					directory = parentDirectoryFile;
					file = customFile;
				}
			}
		}

		if (directory == null || file == null) {
			directory = new File(defaultDirectory).getAbsoluteFile();
			file = new File(directory, defaultBasename).getAbsoluteFile();
		}

		return new FileLocation(directory, file);
	}

	public static final class InvalidFileSystemException extends Exception {

		private static final long serialVersionUID = -4545843729375389876L;

		public InvalidFileSystemException() {
			super();
		}

		public InvalidFileSystemException(String message) {
			super(message);
		}

		public InvalidFileSystemException(Throwable cause) {
			super(cause);
		}

		public InvalidFileSystemException(String message, Throwable cause) {
			super(message, cause);
		}
	}

	/**
	 * A simple type holding mount point information for Unix file systems.
	 *
	 * @author Nadahar
	 */
	public static final class UnixMountPoint {
		public String device;
		public String folder;

		@Override
		public boolean equals(Object obj) {
			if (obj == null) {
				return false;
			}
			if (this == obj) {
				return true;
			}
			if (!(obj instanceof UnixMountPoint)) {
				return false;
			}
			return
				device.equals(((UnixMountPoint) obj).device) &&
				folder.equals(((UnixMountPoint) obj).folder);
		}

		@Override
		public int hashCode() {
			return device.hashCode() + folder.hashCode();
		}

		@Override
		public String toString() {
			return String.format("Device: \"%s\", folder: \"%s\"", device, folder);
		}
	}

	/**
	 * Checks if the specified {@link String} is the file path separator. On
	 * Windows file-systems both {@code "\"} and {@code "/"} are considered as
	 * such by this method.
	 *
	 * @param character the character to check.
	 * @return {@code true} if {@code character} is the file path separator,
	 *         {@code false} otherwise.
	 */
	public static boolean isSeparator(String character) {
		if (character == null || character.length() != 1) {
			return false;
		}
		return isSeparator(character.charAt(0));
	}

	/**
	 * Checks if the specified {@code char} is the file path separator. On
	 * Windows file-systems both {@code "\"} and {@code "/"} are considered as
	 * such by this method.
	 *
	 * @param character the character to check.
	 * @return {@code true} if {@code character} is the file path separator,
	 *         {@code false} otherwise.
	 */
	public static boolean isSeparator(char character) {
		for (char entry : FILE_SEPARATORS) {
			if (character == entry) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Returns the last index position of the file path separator in the same
	 * way as {@link String#lastIndexOf(String)} does. On Windows file-systems
	 * both {@code "\"} and {@code "/"} are considered a file path separator by
	 * this method.
	 *
	 * @param fileName the filename for which to find the last file path
	 *            separator.
	 * @return The index of the last file path separator or {@code -1} if not
	 *         found.
	 */
	public static int getIndexOfLastSeparator(String fileName) {
		if (fileName == null) {
			return -1;
		}
		if (FILE_SEPARATORS.length == 2) {
			return Math.max(fileName.lastIndexOf(FILE_SEPARATORS[0]), fileName.lastIndexOf(FILE_SEPARATORS[1]));
		}
		return fileName.lastIndexOf(FILE_SEPARATORS[0]);
	}

	public static boolean isUrl(String filename) {
		// We're intentionally avoiding stricter URI() methods, which can throw
		// URISyntaxException for psuedo-urls (e.g. librtmp-style urls containing spaces)
		return filename != null && filename.matches("\\S+://.*");
	}

	public static String getProtocol(String filename) {
		// Intentionally avoids URI.getScheme(), see above
		if (isUrl(filename)) {
			return filename.split("://")[0].toLowerCase(Locale.ROOT);
		}
		return null;
	}

	public static String urlJoin(String base, String filename) {
		if (isUrl(filename)) {
			return filename;
		}
		try {
			return new URL(new URL(base), filename).toString();
		} catch (MalformedURLException e) {
			return filename;
		}
	}

	public static String getUrlExtension(String u) {
		// Omit the query string, if any
		return getExtension(substringBefore(u, "?"));
	}

	/**
	 * Returns the file extension from the specified {@link File} or
	 * {@code null} if it has no extension.
	 *
	 * @param file the {@link File} from which to extract the extension.
	 * @return The extracted extension or {@code null}.
	 */
	@Nullable
	public static String getExtension(@Nullable File file) {
		return getExtension(file, null, null);
	}

	/**
	 * Returns the file extension from the specified {@link File} or
	 * {@code null} if it has no extension.
	 *
	 * @param file the {@link File} from which to extract the extension.
	 * @param convertTo if {@code null} makes no letter case change to the
	 *            returned {@link String}, otherwise converts the extracted
	 *            extension (if any) to the corresponding letter case.
	 * @param locale the {@link Locale} to use for letter case conversion.
	 *            Defaults to {@link Locale#ROOT} if {@code null}.
	 * @return The extracted and potentially letter case converted extension or
	 *         {@code null}.
	 */
	@Nullable
	public static String getExtension(@Nullable File file, @Nullable LetterCase convertTo, @Nullable Locale locale) {
		if (file == null || file.getName() == null) {
			return null;
		}
		return getExtension(file.getName(), convertTo, locale);
	}

	/**
	 * Returns the file extension from the specified {@link Path} or
	 * {@code null} if it has no extension.
	 *
	 * @param path the {@link Path} from which to extract the extension.
	 * @return The extracted extension or {@code null}.
	 */
	@Nullable
	public static String getExtension(@Nullable Path path) {
		return getExtension(path, null, null);
	}

	/**
	 * Returns the file extension from the specified {@link Path} or
	 * {@code null} if it has no extension.
	 *
	 * @param path the {@link Path} from which to extract the extension.
	 * @param convertTo if {@code null} makes no letter case change to the
	 *            returned {@link String}, otherwise converts the extracted
	 *            extension (if any) to the corresponding letter case.
	 * @param locale the {@link Locale} to use for letter case conversion.
	 *            Defaults to {@link Locale#ROOT} if {@code null}.
	 * @return The extracted and potentially letter case converted extension or
	 *         {@code null}.
	 */
	@Nullable
	public static String getExtension(@Nullable Path path, @Nullable LetterCase convertTo, @Nullable Locale locale) {
		if (path == null) {
			return null;
		}
		Path fileName = path.getFileName();
		if (fileName == null || isBlank(fileName.toString())) {
			return null;
		}
		return getExtension(fileName.toString(), convertTo, locale);
	}

	/**
	 * Returns the file extension from {@code fileName} or {@code null} if
	 * {@code fileName} has no extension.
	 *
	 * @param fileName the file name from which to extract the extension.
	 * @return The extracted extension or {@code null}.
	 */
	public static String getExtension(@Nullable String fileName) {
		return getExtension(fileName, null, null);
	}

	/**
	 * Returns the file extension from {@code fileName} or {@code null} if
	 * {@code fileName} has no extension.
	 *
	 * @param fileName the file name from which to extract the extension.
	 * @param convertTo if {@code null} makes no letter case change to the
	 *            returned {@link String}, otherwise converts the extracted
	 *            extension (if any) to the corresponding letter case.
	 * @param locale the {@link Locale} to use for letter case conversion.
	 *            Defaults to {@link Locale#ROOT} if {@code null}.
	 * @return The extracted and potentially letter case converted extension or
	 *         {@code null}.
	 */
	@Nullable
	public static String getExtension(@Nullable String fileName, @Nullable LetterCase convertTo, @Nullable Locale locale) {
		if (isBlank(fileName)) {
			return null;
		}

		int dot = fileName.lastIndexOf('.');
		if (dot == -1 || getIndexOfLastSeparator(fileName) > dot) {
			return null;
		}

		if (convertTo != null && locale == null) {
			locale = Locale.ROOT;
		}

		String extension = fileName.substring(dot + 1);
		if (convertTo == LetterCase.UPPER) {
			return extension.toUpperCase(locale);
		}
		if (convertTo == LetterCase.LOWER) {
			return extension.toLowerCase(locale);
		}
		return extension;
	}

	/**
	 * Returns a filename with the extension (if any) stripped off.
	 *
	 * @param file the {@link File}.
	 * @return The extensionless filename.
	 */
	public static String getFileNameWithoutExtension(File file) {
		if (file == null) {
			return null;
		}
		return getFileNameWithoutExtension(file.getName());
	}

	/**
	 * Returns a filename with the extension (if any) stripped off.
	 *
	 * @param file the {@link Path}.
	 * @return The extensionless filename.
	 */
	public static String getFileNameWithoutExtension(Path file) {
		if (file == null) {
			return null;
		}
		Path fileName = file.getFileName();
		if (fileName == null) {
			return null;
		}
		return getFileNameWithoutExtension(fileName.toString());
	}

	/**
	 * Returns a filename with the extension (if any) stripped off.
	 *
	 * @param fileName the filename.
	 * @return The extensionless filename.
	 */
	public static String getFileNameWithoutExtension(String fileName) {
		if (isBlank(fileName)) {
			return fileName;
	}
		int point = fileName.lastIndexOf('.');
		if (point == -1) {
			return fileName;
		}
		return fileName.substring(0, point);
	}

	private static final class FormattedNameAndEdition {
		public String formattedName;
		public String edition;

		public FormattedNameAndEdition(String formattedName, String edition) {
			this.formattedName = formattedName;
			this.edition = edition;
		}
	}

	/**
	 * Remove and save edition information to be added later.
	 */
	private static FormattedNameAndEdition removeAndSaveEditionToBeAddedLater(String formattedName) {
		String edition = null;
		Matcher m = COMMON_FILE_EDITIONS_PATTERN.matcher(formattedName);
		if (m.find()) {
			edition = m.group().replaceAll("\\.", " ");
			edition = "(" + WordUtils.capitalizeFully(edition) + ")";
			formattedName = formattedName.replaceAll(" - " + COMMON_FILE_EDITIONS, "");
			formattedName = formattedName.replaceAll(COMMON_FILE_EDITIONS, "");
		}
		return new FormattedNameAndEdition(formattedName, edition);
	}

	/**
	 * Capitalize the first letter of each word if the string contains no
	 * capital letters.
	 */
	private static String convertFormattedNameToTitleCaseParts(String formattedName) {
		if (formattedName.equals(formattedName.toLowerCase())) {
			StringBuilder formattedNameBuilder = new StringBuilder();
			for (String part : formattedName.split(" - ")) {
				if (formattedNameBuilder.length() > 0) {
					formattedNameBuilder.append(" - ");
				}
				formattedNameBuilder.append(convertLowerCaseStringToTitleCase(part));
			}
			formattedName = formattedNameBuilder.toString();
		}
		return formattedName;
	}

	/**
	 * Capitalize the first letter of each word if the string contains no
	 * capital letters.
	 */
	private static String convertFormattedNameToTitleCase(String formattedName) {
		if (formattedName.equals(formattedName.toLowerCase())) {
			formattedName = convertLowerCaseStringToTitleCase(formattedName);
		}
		return formattedName;
	}

	/**
	 * Remove group name from the beginning of the filename.
	 */
	private static String removeGroupNameFromBeginning(String formattedName) {
		if (!"".equals(formattedName) && formattedName.startsWith("[")) {
			Pattern pattern = Pattern.compile("^\\[[^\\]]{0,20}\\][^\\w]*(\\w.*?)\\s*$");
			Matcher matcher = pattern.matcher(formattedName);
			if (matcher.find()) {
				formattedName = matcher.group(1);
			} else if (formattedName.endsWith("]")) {
				pattern = Pattern.compile("^\\[([^\\[\\]]+)\\]\\s*$");
				matcher = pattern.matcher(formattedName);
				if (matcher.find()) {
					formattedName = matcher.group(1);
				}
			}
		}

		return formattedName;
	}

	/**
	 * Remove stuff at the end of the filename like release group, quality,
	 * source, etc.
	 */
	private static String removeFilenameEndMetadata(String formattedName) {
		formattedName = formattedName.replaceAll(COMMON_FILE_ENDS_CASE_SENSITIVE, "");
		formattedName = formattedName.replaceAll("(?i)" + COMMON_FILE_ENDS, "");
		return formattedName;
	}

	/**
	 * Strings that only occur after all useful information. When we encounter
	 * one of these strings, the string and everything after them will be
	 * removed.
	 */
	private static final String COMMON_FILE_ENDS = "[\\s\\.]AC3.*|[\\s\\.]REPACK.*|[\\s\\.]480p.*|[\\s\\.]720p.*|[\\s\\.]m-720p.*|[\\s\\.]900p.*|[\\s\\.]1080p.*|[\\s\\.]2160p.*|[\\s\\.]WEB-DL.*|[\\s\\.]HDTV.*|[\\s\\.]DSR.*|[\\s\\.]PDTV.*|[\\s\\.]WS.*|[\\s\\.]HQ.*|[\\s\\.]DVDRip.*|[\\s\\.]TVRiP.*|[\\s\\.]BDRip.*|[\\s\\.]BRRip.*|[\\s\\.]WEBRip.*|[\\s\\.]BluRay.*|[\\s\\.]Blu-ray.*|[\\s\\.]SUBBED.*|[\\s\\.]x264.*|[\\s\\.]Dual[\\s\\.]Audio.*|[\\s\\.]HSBS.*|[\\s\\.]H-SBS.*|[\\s\\.]RERiP.*|[\\s\\.]DIRFIX.*|[\\s\\.]READNFO.*|[\\s\\.]60FPS.*";
	private static final String COMMON_FILE_ENDS_MATCH = ".*[\\s\\.]AC3.*|.*[\\s\\.]REPACK.*|.*[\\s\\.]480p.*|.*[\\s\\.]720p.*|.*[\\s\\.]m-720p.*|.*[\\s\\.]900p.*|.*[\\s\\.]1080p.*|.*[\\s\\.]2160p.*|.*[\\s\\.]WEB-DL.*|.*[\\s\\.]HDTV.*|.*[\\s\\.]DSR.*|.*[\\s\\.]PDTV.*|.*[\\s\\.]WS.*|.*[\\s\\.]HQ.*|.*[\\s\\.]DVDRip.*|.*[\\s\\.]TVRiP.*|.*[\\s\\.]BDRip.*|.*[\\s\\.]BRRip.*|.*[\\s\\.]WEBRip.*|.*[\\s\\.]BluRay.*|.*[\\s\\.]Blu-ray.*|.*[\\s\\.]SUBBED.*|.*[\\s\\.]x264.*|.*[\\s\\.]Dual[\\s\\.]Audio.*|.*[\\s\\.]HSBS.*|.*[\\s\\.]H-SBS.*|.*[\\s\\.]RERiP.*|.*[\\s\\.]DIRFIX.*|.*[\\s\\.]READNFO.*|.*[\\s\\.]60FPS.*";

	/**
	 * Same as above, but they are common words so we reduce the chances of a
	 * false-positive by being case-sensitive.
	 */
	private static final String COMMON_FILE_ENDS_CASE_SENSITIVE = "[\\s\\.]PROPER[\\s\\.].*|[\\s\\.]iNTERNAL[\\s\\.].*|[\\s\\.]LIMITED[\\s\\.].*|[\\s\\.]LiMiTED[\\s\\.].*|[\\s\\.]FESTiVAL[\\s\\.].*|[\\s\\.]NORDIC[\\s\\.].*|[\\s\\.]REAL[\\s\\.].*|[\\s\\.]SUBBED[\\s\\.].*|[\\s\\.]RETAIL[\\s\\.].*|[\\s\\.]EXTENDED[\\s\\.].*|[\\s\\.]NEWEDIT[\\s\\.].*|[\\s\\.]WEB[\\s\\.].*";

	/**
	 * Editions to be added to the end of the prettified name
	 */
	private static final String COMMON_FILE_EDITIONS = "(?i)(?!\\()(Special[\\s\\.]Edition|Unrated|Final[\\s\\.]Cut|Remastered|Extended[\\s\\.]Cut|IMAX[\\s\\.]Edition|Uncensored|Directors[\\s\\.]Cut|Uncut)(?!\\))";
	private static final Pattern COMMON_FILE_EDITIONS_PATTERN = Pattern.compile(COMMON_FILE_EDITIONS);

	/**
	 * Returns the filename after being "prettified", which involves attempting
	 * to strip away certain things like information about the quality,
	 * resolution, codecs, release groups, fansubbers, etc., replacing periods
	 * with spaces, and various other things to produce a more "pretty" and
	 * standardized filename.
	 *
	 * @param f The filename
	 * @param file The file to possibly be used by the InfoDb
	 *
	 * @return The prettified filename
	 */
	public static String getFileNamePrettified(String f, File file) { //TODO: (Nad) Remove
		String fileNameWithoutExtension;
		String formattedName = "";
		String formattedNameTemp;
		String searchFormattedName;
		String edition = "";

		// These are false unless we recognize that we could use some info on the video from IMDb
		boolean isEpisodeToLookup  = false;
		boolean isTVSeriesToLookup = false;
		boolean isMovieToLookup    = false;
		boolean isMovieWithoutYear = false;

		// Remove file extension
		fileNameWithoutExtension = getFileNameWithoutExtension(f);
		formattedName = removeGroupNameFromBeginning(fileNameWithoutExtension);
		searchFormattedName = "";

		if (formattedName.matches(".*[sS]0\\d[eE]\\d\\d([eE]|-[eE])\\d\\d.*")) {
			// This matches scene and most p2p TV episodes within the first 9 seasons that are more than one episode
			isTVSeriesToLookup = true;

			// Rename the season/episode numbers. For example, "S01E01" changes to " - 101"
			// Then strip the end of the episode if it does not have the episode name in the title
			formattedName = formattedName.replaceAll(
				"(?i)[\\s\\.]S0(\\d)E(\\d)(\\d)([eE]|-[eE])(\\d)(\\d)(" + COMMON_FILE_ENDS + ")",
				" - $1$2$3-$5$6"
			);
			formattedName = formattedName.replaceAll(
				"[\\s\\.]S0(\\d)E(\\d)(\\d)([eE]|-[eE])(\\d)(\\d)(" + COMMON_FILE_ENDS_CASE_SENSITIVE + ")",
				" - $1$2$3-$5$6"
			);
			FormattedNameAndEdition result = removeAndSaveEditionToBeAddedLater(formattedName);
			formattedName = result.formattedName;
			if (result.edition != null) {
				edition = result.edition;
			}

			// If it matches this then it didn't match the previous one, which means there is probably an episode title in the filename
			formattedNameTemp = formattedName.replaceAll(
				"(?i)[\\s\\.]S0(\\d)E(\\d)(\\d)([eE]|-[eE])(\\d)(\\d)[\\s\\.]",
				" - $1$2$3-$5$6 - "
			);
			if (PMS.getConfiguration().isUseInfoFromIMDb() && formattedName.equals(formattedNameTemp)) {
				isEpisodeToLookup = true;
			}

			formattedName = formattedNameTemp;
			formattedName = removeFilenameEndMetadata(formattedName);

			// Replace periods with spaces
			formattedName = formattedName.replaceAll("\\.", " ");

			formattedName = convertFormattedNameToTitleCaseParts(formattedName);
		} else if (formattedName.matches(".*[sS][1-9]\\d[eE]\\d\\d([eE]|-[eE])\\d\\d.*")) {
			// This matches scene and most p2p TV episodes after their first 9 seasons that are more than one episode
			isTVSeriesToLookup = true;

			// Rename the season/episode numbers. For example, "S11E01" changes to " - 1101"
			formattedName = formattedName.replaceAll(
				"(?i)[\\s\\.]S([1-9]\\d)E(\\d)(\\d)([eE]|-[eE])(\\d)(\\d)(" + COMMON_FILE_ENDS + ")",
				" - $1$2$3-$5$6"
			);
			formattedName = formattedName.replaceAll(
				"[\\s\\.]S([1-9]\\d)E(\\d)(\\d)([eE]|-[eE])(\\d)(\\d)(" + COMMON_FILE_ENDS_CASE_SENSITIVE + ")",
				" - $1$2$3-$5$6"
			);
			FormattedNameAndEdition result = removeAndSaveEditionToBeAddedLater(formattedName);
			formattedName = result.formattedName;
			if (result.edition != null) {
				edition = result.edition;
			}

			// If it matches this then it didn't match the previous one, which means there is probably an episode title in the filename
			formattedNameTemp = formattedName.replaceAll(
				"(?i)[\\s\\.]S([1-9]\\d)E(\\d)(\\d)([eE]|-[eE])(\\d)(\\d)[\\s\\.]",
				" - $1$2$3-$5$6 - "
			);
			if (PMS.getConfiguration().isUseInfoFromIMDb() && formattedName.equals(formattedNameTemp)) {
				isEpisodeToLookup = true;
			}

			formattedName = formattedNameTemp;
			formattedName = removeFilenameEndMetadata(formattedName);

			// Replace periods with spaces
			formattedName = formattedName.replaceAll("\\.", " ");

			formattedName = convertFormattedNameToTitleCaseParts(formattedName);
		} else if (formattedName.matches(".*[sS]0\\d[eE]\\d\\d.*")) {
			// This matches scene and most p2p TV episodes within the first 9 seasons
			isTVSeriesToLookup = true;
			FormattedNameAndEdition result = removeAndSaveEditionToBeAddedLater(formattedName);
			formattedName = result.formattedName;
			if (result.edition != null) {
				edition = result.edition;
			}

			// Rename the season/episode numbers. For example, "S01E01" changes to " - 101"
			// Then strip the end of the episode if it does not have the episode name in the title
			formattedName = formattedName.replaceAll("(?i)[\\s\\.]S0(\\d)E(\\d)(\\d)(" + COMMON_FILE_ENDS + ")", " - $1$2$3");
			formattedName = formattedName.replaceAll("[\\s\\.]S0(\\d)E(\\d)(\\d)(" + COMMON_FILE_ENDS_CASE_SENSITIVE + ")", " - $1$2$3");

			// If it matches this then it didn't match the previous one, which means there is probably an episode title in the filename
			formattedNameTemp = formattedName.replaceAll("(?i)[\\s\\.]S0(\\d)E(\\d)(\\d)[\\s\\.]", " - $1$2$3 - ");
			if (PMS.getConfiguration().isUseInfoFromIMDb() && formattedName.equals(formattedNameTemp)) {
				isEpisodeToLookup = true;
			}

			formattedName = formattedNameTemp;
			formattedName = removeFilenameEndMetadata(formattedName);

			// Replace periods with spaces
			formattedName = formattedName.replaceAll("\\.", " ");

			formattedName = convertFormattedNameToTitleCaseParts(formattedName);
		} else if (formattedName.matches(".*[sS][1-9]\\d[eE]\\d\\d.*")) {
			// This matches scene and most p2p TV episodes after their first 9 seasons
			isTVSeriesToLookup = true;

			// Rename the season/episode numbers. For example, "S11E01" changes to " - 1101"
			formattedName = formattedName.replaceAll("(?i)[\\s\\.]S([1-9]\\d)E(\\d)(\\d)(" + COMMON_FILE_ENDS + ")", " - $1$2$3");
			formattedName = formattedName.replaceAll(
				"[\\s\\.]S([1-9]\\d)E(\\d)(\\d)(" + COMMON_FILE_ENDS_CASE_SENSITIVE + ")",
				" - $1$2$3"
			);
			FormattedNameAndEdition result = removeAndSaveEditionToBeAddedLater(formattedName);
			formattedName = result.formattedName;
			if (result.edition != null) {
				edition = result.edition;
			}

			// If it matches this then it didn't match the previous one, which means there is probably an episode title in the filename
			formattedNameTemp = formattedName.replaceAll("(?i)[\\s\\.]S([1-9]\\d)E(\\d)(\\d)[\\s\\.]", " - $1$2$3 - ");
			if (PMS.getConfiguration().isUseInfoFromIMDb() && formattedName.equals(formattedNameTemp)) {
				isEpisodeToLookup = true;
			}

			formattedName = formattedNameTemp;
			formattedName = removeFilenameEndMetadata(formattedName);

			// Replace periods with spaces
			formattedName = formattedName.replaceAll("\\.", " ");

			formattedName = convertFormattedNameToTitleCaseParts(formattedName);
		} else if (formattedName.matches(".*[\\s\\.](19|20)\\d\\d[\\s\\.][0-1]\\d[\\s\\.][0-3]\\d[\\s\\.].*")) {
			// This matches scene and most p2p TV episodes that release several times per week
			isTVSeriesToLookup = true;

			// Rename the date. For example, "2013.03.18" changes to " - 2013/03/18"
			formattedName = formattedName.replaceAll(
				"(?i)[\\s\\.](19|20)(\\d\\d)[\\s\\.]([0-1]\\d)[\\s\\.]([0-3]\\d)(" + COMMON_FILE_ENDS + ")",
				" - $1$2/$3/$4"
			);
			formattedName = formattedName.replaceAll(
				"[\\s\\.](19|20)(\\d\\d)[\\s\\.]([0-1]\\d)[\\s\\.]([0-3]\\d)(" + COMMON_FILE_ENDS_CASE_SENSITIVE + ")",
				" - $1$2/$3/$4"
			);
			FormattedNameAndEdition result = removeAndSaveEditionToBeAddedLater(formattedName);
			formattedName = result.formattedName;
			if (result.edition != null) {
				edition = result.edition;
			}

			// If it matches this then it didn't match the previous one, which means there is probably an episode title in the filename
			formattedNameTemp = formattedName.replaceAll(
				"(?i)[\\s\\.](19|20)(\\d\\d)[\\s\\.]([0-1]\\d)[\\s\\.]([0-3]\\d)[\\s\\.]",
				" - $1$2/$3/$4 - "
			);
			if (PMS.getConfiguration().isUseInfoFromIMDb() && formattedName.equals(formattedNameTemp)) {
				isEpisodeToLookup = true;
			}

			formattedName = formattedNameTemp;
			formattedName = removeFilenameEndMetadata(formattedName);

			// Replace periods with spaces
			formattedName = formattedName.replaceAll("\\.", " ");

			formattedName = convertFormattedNameToTitleCaseParts(formattedName);
		} else if (formattedName.matches(".*[\\s\\.](19|20)\\d\\d[\\s\\.].*")) {
			// This matches scene and most p2p movies
			isMovieToLookup = true;

			// Rename the year. For example, "2013" changes to " (2013)"
			formattedName = formattedName.replaceAll("[\\s\\.](19|20)(\\d\\d)", " ($1$2)");
			formattedName = removeFilenameEndMetadata(formattedName);
			FormattedNameAndEdition result = removeAndSaveEditionToBeAddedLater(formattedName);
			formattedName = result.formattedName;
			if (result.edition != null) {
				edition = result.edition;
			}

			// Replace periods with spaces
			formattedName = formattedName.replaceAll("\\.", " ");

			formattedName = convertFormattedNameToTitleCase(formattedName);
		} else if (formattedName.matches(".*\\[(19|20)\\d\\d\\].*")) {
			// This matches rarer types of movies
			isMovieToLookup = true;

			// Rename the year. For example, "2013" changes to " (2013)"
			formattedName = formattedName.replaceAll("(?i)\\[(19|20)(\\d\\d)\\].*", " ($1$2)");
			formattedName = removeFilenameEndMetadata(formattedName);

			// Replace periods with spaces
			formattedName = formattedName.replaceAll("\\.", " ");

			formattedName = convertFormattedNameToTitleCase(formattedName);
		} else if (formattedName.matches(".*\\((19|20)\\d\\d\\).*")) {
			// This matches rarer types of movies
			isMovieToLookup = true;
			formattedName = removeFilenameEndMetadata(formattedName);

			// Replace periods with spaces
			formattedName = formattedName.replaceAll("\\.", " ");

			formattedName = convertFormattedNameToTitleCase(formattedName);
		} else if (formattedName.matches(".*\\[[0-9a-zA-Z]{8}\\]$")) {
			// This matches anime with a hash at the end of the name
			isTVSeriesToLookup = true;

			// Remove underscores
			formattedName = formattedName.replaceAll("_", " ");

			// Remove stuff at the end of the filename like hash, quality, source, etc.
			formattedName = formattedName.replaceAll(
				"(?i)\\s\\(1280x720.*|\\s\\(1920x1080.*|\\s\\(720x400.*|\\[720p.*|\\[1080p.*|\\[480p.*|\\s\\(BD.*|" +
				"\\s\\[Blu-Ray.*|\\s\\[DVD.*|\\.DVD.*|\\[[0-9a-zA-Z]{8}\\]$|\\[h264.*|R1DVD.*|\\[BD.*",
				""
			);

			if (
				PMS.getConfiguration().isUseInfoFromIMDb() &&
				formattedName.substring(formattedName.length() - 3).matches("[\\s\\._]\\d\\d")
			) {
				isEpisodeToLookup = true;
				searchFormattedName = formattedName.substring(
					0,
					formattedName.length() - 2) + "S01E" + formattedName.substring(formattedName.length() - 2
				);
			}

			formattedName = convertFormattedNameToTitleCase(formattedName);
		} else if (formattedName.matches(".*\\[BD\\].*|.*\\[720p\\].*|.*\\[1080p\\].*|.*\\[480p\\].*|.*\\[Blu-Ray.*|.*\\[h264.*")) {
			// This matches anime without a hash in the name
			isTVSeriesToLookup = true;

			// Remove underscores
			formattedName = formattedName.replaceAll("_", " ");

			// Remove stuff at the end of the filename like hash, quality, source, etc.
			formattedName = formattedName.replaceAll("(?i)\\[BD\\].*|\\[720p.*|\\[1080p.*|\\[480p.*|\\[Blu-Ray.*|\\[h264.*", "");

			if (
				PMS.getConfiguration().isUseInfoFromIMDb() &&
				formattedName.substring(formattedName.length() - 3).matches("[\\s\\._]\\d\\d")
			) {
				isEpisodeToLookup = true;
				searchFormattedName = formattedName.substring(
					0,
					formattedName.length() - 2) + "S01E" + formattedName.substring(formattedName.length() - 2
				);
			}

			formattedName = convertFormattedNameToTitleCase(formattedName);
		} else if (formattedName.matches(COMMON_FILE_ENDS_MATCH)) {
			// This is probably a movie that doesn't specify a year
			isMovieToLookup = true;
			isMovieWithoutYear = true;
			formattedName = removeFilenameEndMetadata(formattedName);
			FormattedNameAndEdition result = removeAndSaveEditionToBeAddedLater(formattedName);
			formattedName = result.formattedName;
			if (result.edition != null) {
				edition = result.edition;
			}

			// Replace periods with spaces
			formattedName = formattedName.replaceAll("\\.", " ");

			formattedName = convertFormattedNameToTitleCase(formattedName);
		}

		// Remove extra spaces
		formattedName = formattedName.replaceAll("\\s+", " ");

		/*
		 * Add info from IMDb
		 *
		 * We use the Jaro Winkler similarity algorithm to make sure that
		 * changes to movie or TV show names are only made when the difference
		 * between the original and replacement names is less than 10%. This
		 * means we get proper case and special characters without worrying
		 * about incorrect results being used.
		 *
		 * TODO: Make the following logic only happen once.
		 */
		JaroWinklerDistance jaroWinklerDistance = new JaroWinklerDistance();
		if (file != null && (isTVSeriesToLookup || isMovieToLookup)) {
			InfoDb.InfoDbData info = PMS.get().infoDb().get(file);
			if (info == null) {
				PMS.get().infoDbAdd(file, isNotBlank(searchFormattedName) ? searchFormattedName : formattedName);
			} else if (isTVSeriesToLookup) {
				int showNameIndex = indexOf(Pattern.compile("(?i) - \\d\\d\\d.*"), formattedName);
				if (StringUtils.isNotEmpty(info.title) && showNameIndex != -1) {
					String titleFromFilename = formattedName.substring(0, showNameIndex);

					// The following line can run over 100 times in under 1ms
					double similarity = jaroWinklerDistance.apply(titleFromFilename, info.title);
					if (similarity > 0.91) {
						formattedName = info.title + formattedName.substring(showNameIndex);

						if (isEpisodeToLookup) {
							if (StringUtils.isNotEmpty(info.ep_name)) {
								formattedName += " - " + info.ep_name;
							}
						}
					}
					LOGGER.trace("The similarity between '" + info.title + "' and '" + titleFromFilename + "' is " + similarity);
				}
			} else if (isMovieToLookup && StringUtils.isNotEmpty(info.title) && StringUtils.isNotEmpty(info.year)) {
				double similarity;
				if (isMovieWithoutYear) {
					similarity = jaroWinklerDistance.apply(formattedName, info.title);
					LOGGER.trace("The similarity between '" + info.title + "' and '" + formattedName + "' is " + similarity);
				} else {
					int yearIndex = indexOf(Pattern.compile("\\s\\(\\d{4}\\)"), formattedName);
					String titleFromFilename = formattedName.substring(0, yearIndex);
					similarity = jaroWinklerDistance.apply(titleFromFilename, info.title);
					LOGGER.trace("The similarity between '" + info.title + "' and '" + titleFromFilename + "' is " + similarity);
				}

				if (similarity > 0.91) {
					formattedName = info.title + " (" + info.year + ")";
				}
			}
		}
		formattedName = formattedName.trim();

		// Add the edition information if it exists
		if (!edition.isEmpty()) {
			String substr = formattedName.substring(Math.max(0, formattedName.length() - 2));
			if (" -".equals(substr)) {
				formattedName = formattedName.substring(0, formattedName.length() - 2);
			}
			formattedName += " " + edition;
		}

		return formattedName;
	}

	/**
	 * Converts a lower case string to title case.
	 *
	 * It is not very smart right now so it can be expanded to be more reliable.
	 *
	 * @param value the string to convert
	 *
	 * @return the converted string
	 */
	public static String convertLowerCaseStringToTitleCase(String value) {
		if (isBlank(value)) {
			return value;
		}
		value = value.trim();
		String convertedValue = "";
		boolean loopedOnce = false;

		for (String word : value.split("\\s+")) {
			if (loopedOnce) {
				switch (word) {
					case "a":
					case "an":
					case "and":
					case "in":
					case "it":
					case "for":
					case "of":
					case "on":
					case "the":
					case "to":
					case "vs":
						convertedValue += ' ' + word;
						break;
					default:
						convertedValue += ' ' + word.substring(0, 1).toUpperCase() + word.substring(1);
				}
			} else {
				// Always capitalize the first letter of the string
				convertedValue += word.substring(0, 1).toUpperCase() + word.substring(1);
			loopedOnce = true;
		}
		}

		return convertedValue;
	}

	public static int indexOf(Pattern pattern, String s) {
		Matcher matcher = pattern.matcher(s);
		return matcher.find() ? matcher.start() : -1;
	}

	public static File getFileNameWithAddedExtension(File parent, File f, String ext) {
		File ff = new File(parent, f.getName() + ext);

		if (ff.exists()) {
			return ff;
		}

		return null;
	}

	/**
	 * Returns a new {@link File} instance where the file extension has been
	 * replaced.
	 *
	 * @param file the {@link File} for which to replace the extension.
	 * @param extension the new file extension.
	 * @param nullIfNonExisting whether or not to return {@code null} or a
	 *            non-existing {@link File} instance if the constructed
	 *            {@link File} doesn't exist.
	 * @param adjustExtensionCase whether or not to try upper- and lower-case
	 *            variants of the extension. If {@code true} and the constructed
	 *            {@link File} doesn't exist with the given case but does exist
	 *            with an either upper or lower case version of the extension,
	 *            the existing {@link File} instance will be returned.
	 * @return The constructed {@link File} instance or {@code null} if the
	 *         target file doesn't exist and {@code nullIfNonExisting} is true.
	 */
	@Nullable
	public static File replaceExtension(
		@Nullable File file,
		@Nullable String extension,
		boolean nullIfNonExisting,
		boolean adjustExtensionCase
	) {
		if (file == null) {
			return null;
		}
		return replaceExtension(
			file.getParentFile(),
			file.getName(),
			extension,
			nullIfNonExisting,
			adjustExtensionCase
		);
	}

	/**
	 * Returns a new {@link File} instance where the file extension has been
	 * replaced.
	 *
	 * @param folder the {@link File} instance representing the folder for the
	 *            constructed {@link File}. Use {@code null} for the current
	 *            folder.
	 * @param file the {@link File} for which to replace the extension. Only the
	 *            file name will be used, its path will be discarded.
	 * @param extension the new file extension.
	 * @param nullIfNonExisting whether or not to return {@code null} or a
	 *            non-existing {@link File} instance if the constructed
	 *            {@link File} doesn't exist.
	 * @param adjustExtensionCase whether or not to try upper- and lower-case
	 *            variants of the extension. If {@code true} and the constructed
	 *            {@link File} doesn't exist with the given case but does exist
	 *            with an either upper or lower case version of the extension,
	 *            the existing {@link File} instance will be returned.
	 * @return The constructed {@link File} instance or {@code null} if the
	 *         target file doesn't exist and {@code nullIfNonExisting} is true.
	 */
	@Nullable
	public static File replaceExtension(
		@Nullable File folder,
		@Nullable File file,
		@Nullable String extension,
		boolean nullIfNonExisting,
		boolean adjustExtensionCase
	) {
		if (file == null) {
			return null;
		}
		return replaceExtension(
			folder,
			file.getName(),
			extension,
			nullIfNonExisting,
			adjustExtensionCase
		);
	}

	/**
	 * Returns a new {@link File} instance where the file extension has been
	 * replaced.
	 *
	 * @param folder the {@link File} instance representing the folder for the
	 *            constructed {@link File}. Use {@code null} for the current
	 *            folder.
	 * @param fileName the {@link String} for which to replace the extension.
	 * @param extension the new file extension.
	 * @param nullIfNonExisting whether or not to return {@code null} or a
	 *            non-existing {@link File} instance if the constructed
	 *            {@link File} doesn't exist.
	 * @param adjustExtensionCase whether or not to try upper- and lower-case
	 *            variants of the extension. If {@code true} and the constructed
	 *            {@link File} doesn't exist with the given case but does exist
	 *            with an either upper or lower case version of the extension,
	 *            the existing {@link File} instance will be returned.
	 * @return The constructed {@link File} instance or {@code null} if the
	 *         target file doesn't exist and {@code nullIfNonExisting} is true.
	 */
	@Nullable
	public static File replaceExtension(
		@Nullable File folder,
		@Nullable String fileName,
		@Nullable String extension,
		boolean nullIfNonExisting,
		boolean adjustExtensionCase
	) {
		if (isBlank(fileName)) {
			return null;
		}

		int dot = fileName.lastIndexOf('.');

		String baseFileName;
		if (dot == -1 || getIndexOfLastSeparator(fileName) > dot) {
			baseFileName = fileName;
		} else {
			baseFileName = fileName.substring(0, dot);
		}

		if (isBlank(extension)) {
			File result = new File(folder, baseFileName);
			return !nullIfNonExisting || result.exists() ? result : null;
		}

		File result = new File(folder, baseFileName + "." + extension);
		if (result.exists() || !nullIfNonExisting && !adjustExtensionCase) {
			return result;
		}

		if (!Platform.isWindows() && adjustExtensionCase) {
			File adjustedResult = new File(folder, baseFileName + "." + extension.toLowerCase(Locale.ROOT));
			if (adjustedResult.exists()) {
				return adjustedResult;
			}
			adjustedResult = new File(folder, baseFileName + "." + extension.toUpperCase(Locale.ROOT));
			if (adjustedResult.exists()) {
				return adjustedResult;
			}
		}

		return nullIfNonExisting ? null : result;
	}

	/**
	 * Returns a new {@link Path} instance where the file extension has been
	 * replaced.
	 *
	 * @param file the {@link Path} for which to replace the extension.
	 * @param extension the new file extension.
	 * @param nullIfNonExisting whether or not to return {@code null} or a
	 *            non-existing {@link Path} instance if the constructed
	 *            {@link Path} doesn't exist.
	 * @param adjustExtensionCase whether or not to try upper- and lower-case
	 *            variants of the extension. If {@code true} and the constructed
	 *            {@link Path} doesn't exist with the given case but does exist
	 *            with an either upper or lower case version of the extension,
	 *            the existing {@link Path} instance will be returned.
	 * @return The constructed {@link Path} instance or {@code null} if the
	 *         target file doesn't exist and {@code nullIfNonExisting} is true.
	 */
	public static Path replaceExtension(
		Path file,
		String extension,
		boolean nullIfNonExisting,
		boolean adjustExtensionCase
	) {
		if (file == null) {
			return null;
		}
		Path fileName = file.getFileName();
		if (fileName == null) {
			return null;
		}
		return replaceExtension(
			file.getParent(),
			fileName.toString(),
			extension,
			nullIfNonExisting,
			adjustExtensionCase
		);
	}

	/**
	 * Returns a new {@link Path} instance where the file extension has been
	 * replaced.
	 *
	 * @param folder the {@link Path} instance representing the folder for the
	 *            constructed {@link Path}. Use {@code null} for the current
	 *            folder.
	 * @param file the {@link Path} for which to replace the extension. Only the
	 *            file name will be used, its path will be discarded.
	 * @param extension the new file extension.
	 * @param nullIfNonExisting whether or not to return {@code null} or a
	 *            non-existing {@link Path} instance if the constructed
	 *            {@link Path} doesn't exist.
	 * @param adjustExtensionCase whether or not to try upper- and lower-case
	 *            variants of the extension. If {@code true} and the constructed
	 *            {@link Path} doesn't exist with the given case but does exist
	 *            with an either upper or lower case version of the extension,
	 *            the existing {@link Path} instance will be returned.
	 * @return The constructed {@link Path} instance or {@code null} if the
	 *         target file doesn't exist and {@code nullIfNonExisting} is true.
	 */
	public static Path replaceExtension(
		Path folder,
		Path file,
		String extension,
		boolean nullIfNonExisting,
		boolean adjustExtensionCase
	) {
		if (file == null) {
			return null;
		}
		Path fileName = file.getFileName();
		if (fileName == null) {
			return null;
		}
		return replaceExtension(
			folder,
			fileName.toString(),
			extension,
			nullIfNonExisting,
			adjustExtensionCase
		);
	}

	/**
	 * Returns a new {@link Path} instance where the file extension has been
	 * replaced.
	 *
	 * @param folder the {@link Path} instance representing the folder for the
	 *            constructed {@link Path}. Use {@code null} for the current
	 *            folder.
	 * @param fileName the {@link String} for which to replace the extension.
	 * @param extension the new file extension.
	 * @param nullIfNonExisting whether or not to return {@code null} or a
	 *            non-existing {@link Path} instance if the constructed
	 *            {@link Path} doesn't exist.
	 * @param adjustExtensionCase whether or not to try upper- and lower-case
	 *            variants of the extension. If {@code true} and the constructed
	 *            {@link Path} doesn't exist with the given case but does exist
	 *            with an either upper or lower case version of the extension,
	 *            the existing {@link Path} instance will be returned.
	 * @return The constructed {@link Path} instance or {@code null} if the
	 *         target file doesn't exist and {@code nullIfNonExisting} is true.
	 */
	public static Path replaceExtension(
		Path folder,
		String fileName,
		String extension,
		boolean nullIfNonExisting,
		boolean adjustExtensionCase
	) {
		if (isBlank(fileName)) {
			return null;
		}

		int point = fileName.lastIndexOf('.');

		String baseFileName;
		if (point == -1) {
			baseFileName = fileName;
		} else {
			baseFileName = fileName.substring(0, point);
		}

		try {
			if (folder == null) {
				folder = Paths.get("");
			}
			if (isBlank(extension)) {
				Path result = folder.resolve(baseFileName);
				return !nullIfNonExisting || Files.exists(result) ? result : null;
			}

			Path result = folder.resolve(baseFileName + "." + extension);
			if (Files.exists(result) || !nullIfNonExisting && !adjustExtensionCase) {
				return result;
			}

			if (!Platform.isWindows() && adjustExtensionCase) {
				Path adjustedResult = folder.resolve(baseFileName + "." + extension.toLowerCase(Locale.ROOT));
				if (Files.exists(adjustedResult)) {
					return adjustedResult;
				}
				adjustedResult = folder.resolve(baseFileName + "." + extension.toUpperCase(Locale.ROOT));
				if (Files.exists(adjustedResult)) {
					return adjustedResult;
				}
			}
			return nullIfNonExisting ? null : result;
		} catch (InvalidPathException e) {
			LOGGER.error(
				"Unexpected error during replaceExtension for folder \"{}\", file \"{}\" and extension \"{}\": {}",
				folder,
				fileName,
				extension,
				e.getMessage()
			);
			LOGGER.trace("", e);
			return null;
		}
	}

	/**
	 * Detects charset/encoding for given file. Not 100% accurate for
	 * non-Unicode files.
	 *
	 * @param file the file for which to detect charset/encoding
	 * @return The match object form the detection process or {@code null} if no
	 *         match was found.
	 * @throws IOException
	 */
	@Nullable
	public static CharsetMatch getFileCharsetMatch(@Nonnull File file) throws IOException {
		InputStream in = new BufferedInputStream(new FileInputStream(file));
		CharsetDetector detector = new CharsetDetector();
		detector.setText(in);
		// Results are sorted on descending confidence, so we're only after the first one.
		CharsetMatch[] matches = detector.detectAll();
		return matches.length == 0 ? null : matches[0];
	}

	/**
	 * Detects charset/encoding for given file. Not 100% accurate for
	 * non-Unicode files.
	 *
	 * @param file the file for which to detect charset/encoding.
	 * @return The detected {@link Charset} or {@code null} if not detected.
	 * @throws IOException If an IO error occurs during the operation.
	 */
	@Nullable
	public static Charset getFileCharset(@Nullable File file) throws IOException {
		if (file == null) {
			return null;
		}
		CharsetMatch match = getFileCharsetMatch(file);
		if (match == null) {
			return null;
		}
		try {
			if (Charset.isSupported(match.getName())) {
				LOGGER.debug("Detected charset \"{}\" in file \"{}\"", match.getName(), file.getAbsolutePath());
				return Charset.forName(match.getName());
			}
			LOGGER.debug(
				"Detected charset \"{}\" in file \"{}\", but cannot use it because it's not supported by the Java Virual Machine",
				match.getName(),
				file.getAbsolutePath()
			);
			return null;
		} catch (IllegalCharsetNameException e) {
			LOGGER.debug("Illegal charset \"{}\" deteceted in file \"{}\"", match.getName(), file.getAbsolutePath());
		}
		LOGGER.debug("Found no matching charset for file \"{}\"", file.getAbsolutePath());
		return null;
	}

	/**
	 * Detects charset/encoding for given file. Not 100% accurate for
	 * non-Unicode files.
	 *
	 * @param file the {@link File} for which to detect charset/encoding.
	 * @return The name of the detected {@link Charset} or {@code null} if not
	 *         detected.
	 * @throws IOException If an IO error occurs during the operation.
	 */
	@Nullable
	public static String getFileCharsetName(@Nullable File file) throws IOException {
		if (file == null) {
			return null;
		}
		CharsetMatch match = getFileCharsetMatch(file);
		if (match == null) {
			return null;
		}
		try {
			if (Charset.isSupported(match.getName())) {
				LOGGER.debug("Detected charset \"{}\" in file \"{}\"", match.getName(), file.getAbsolutePath());
				return match.getName().toUpperCase(Locale.ROOT);
			}
			LOGGER.debug(
				"Detected charset \"{}\" in file \"{}\", but cannot use it because it's not supported by the Java Virual Machine",
				match.getName(),
				file.getAbsolutePath()
			);
			return null;
		} catch (IllegalCharsetNameException e) {
			LOGGER.debug("Illegal charset \"{}\" deteceted in file \"{}\"", match.getName(), file.getAbsolutePath());
		}
		LOGGER.debug("Found no matching charset for file \"{}\"", file.getAbsolutePath());
		return null;
	}

	/**
	 * Tests if the {@link File} is UTF-8 encoded with or without BOM.
	 *
	 * @param file {@link File} to test.
	 * @return {@code true} if {@link File} is UTF-8 encoded with or without
	 *         BOM, {@code false} otherwise.
	 * @throws IOException
	 */
	public static boolean isFileUTF8(File file) throws IOException {
		return isCharsetUTF8(getFileCharset(file));
	}

	/**
	 * Tests if the {@link Charset} is UTF-8.
	 *
	 * @param charset the {@link Charset} to test.
	 * @return {@code true} if {@link Charset} is UTF-8, {@code false}
	 *         otherwise.
	 */
	public static boolean isCharsetUTF8(Charset charset) {
		return charset != null && charset.equals(StandardCharsets.UTF_8);
	}

	/**
	 * Tests if the charset name is UTF-8.
	 *
	 * @param charsetName the charset name to test.
	 * @return {@code true} if charset name is UTF-8, {@code false} otherwise.
	 */
	public static boolean isCharsetUTF8(String charsetName) {
		return equalsIgnoreCase(charsetName, CHARSET_UTF_8);
	}

	/**
	 * Tests if the {@link File} is UTF-16 encoded.
	 *
	 * @param file the {@link File} to test.
	 * @return {@code true} if {@link File} is UTF-16 encoded, {@code false}
	 *         otherwise.
	 * @throws IOException
	 */
	public static boolean isFileUTF16(File file) throws IOException {
		return isCharsetUTF16(getFileCharset(file));
	}

	/**
	 * Tests if the {@link Charset} is {@code UTF-16}.
	 *
	 * @param charset the {@link Charset} to test.
	 * @return {@code true} if the {@link Charset} is {@code UTF-16},
	 *         {@code false} otherwise.
	 */
	public static boolean isCharsetUTF16(Charset charset) {
		return
			charset != null &&
			(
				charset.equals(StandardCharsets.UTF_16) ||
				charset.equals(StandardCharsets.UTF_16BE) ||
				charset.equals(StandardCharsets.UTF_16LE)
			);
	}

	/**
	 * Tests if the charset name is {@code UTF-16}.
	 *
	 * @param charsetName the charset name to test.
	 * @return {@code true} if {@code charsetName} is {@code UTF-16},
	 *         {@code false} otherwise.
	 */
	public static boolean isCharsetUTF16(String charsetName) {
		return (equalsIgnoreCase(charsetName, CHARSET_UTF_16LE) || equalsIgnoreCase(charsetName, CHARSET_UTF_16BE));
	}

	/**
	 * Tests if {@code charsetName} is {@code UTF-32}.
	 *
	 * @param charsetName the charset name to test.
	 * @return {@code true} if {@code charsetName} is {@code UTF-32},
	 *         {@code false} otherwise.
	 */
	public static boolean isCharsetUTF32(String charsetName) {
		return (equalsIgnoreCase(charsetName, CHARSET_UTF_32LE) || equalsIgnoreCase(charsetName, CHARSET_UTF_32BE));
	}

	/**
	 * Converts a {@code UTF-16} input file to a {@code UTF-8} output file. Does
	 * not overwrite an existing output file.
	 *
	 * @param inputFile a {@code UTF-16} {@link File}.
	 * @param outputFile the {@code UTF-8} {@link File} after conversion.
	 * @throws IOException If an IO error occurs during the operation.
	 */
	public static void convertFileFromUtf16ToUtf8(File inputFile, File outputFile) throws IOException {
		Charset charset;
		if (inputFile == null) {
			throw new IllegalArgumentException("inputFile cannot be null");
		}

		charset = getFileCharset(inputFile);
		if (isCharsetUTF16(charset)) {
			if (!outputFile.exists()) {
				/*
				 * This is a strange hack, and I'm not sure if it's needed. I
				 * did it this way to conform to the tests, which dictates that
				 * UTF-16LE should produce UTF-8 without BOM while UTF-16BE
				 * should produce UTF-8 with BOM.
				 *
				 * For some reason creating a FileInputStream with UTF_16
				 * produces an UTF-8 outputfile without BOM, while using
				 * UTF_16LE or UTF_16BE produces an UTF-8 outputfile with BOM.
				 *
				 * @author Nadahar
				 */
				try (
					BufferedReader reader = charset.equals(StandardCharsets.UTF_16LE) ?
						new BufferedReader(new InputStreamReader(new FileInputStream(inputFile), StandardCharsets.UTF_16)) :
						new BufferedReader(new InputStreamReader(new FileInputStream(inputFile), charset));
					BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
						new FileOutputStream(outputFile),
						StandardCharsets.UTF_8
					));
				) {
					char[] buffer = new char[1024];
					int num;
					do {
						num = reader.read(buffer);
						if (num > 0) {
							writer.write(buffer, 0, num);
						}
					} while (num != -1);
				}
			}
		} else {
			throw new IllegalArgumentException("File is not UTF-16");
		}
	}

	/**
	 * Return a file or folder's permissions.
	 * <p>
	 * This should <b>NOT</b> be used for checking e.g. read permissions before
	 * trying to open a file, because you can't assume that the same is true
	 * when you actually open the file. Other threads or processes could have
	 * locked the file (or changed it's permissions) in the meanwhile. Instead,
	 * use e.g {@link FileNotFoundException} like this:
	 *
	 * <pre>
	 * <code>
	 * } catch (FileNotFoundException e) {
	 *     LOGGER.debug("Can't read xxx {}", e.getMessage());
	 * }
	 * </code>
	 * </pre>
	 *
	 * {@code e.getMessage()} will contain both the full path to the file the
	 * reason it couldn't be read (e.g. no permission).
	 *
	 * @param file the {@link File} to check permissions for.
	 * @return A {@link FilePermissions} object holding the permissions.
	 * @throws FileNotFoundException If {@code file} can't be found/read.
	 * @see {@link #getFilePermissions(String)}
	 */
	@Nullable
	public static FilePermissions getFilePermissions(@Nullable File file) throws FileNotFoundException {
		return file == null ? null : new FilePermissions(file);
	}

	/**
	 * Like {@link #getFilePermissions(File)} but returns {@code null} instead
	 * of throwing {@link FileNotFoundException} if the file or folder isn't
	 * found.
	 *
	 * @param file the {@link File} to check permissions for.
	 * @return A {@link FilePermissions} object holding the permissions.
	 */
	@Nullable
	public static FilePermissions getFilePermissionsNoThrow(@Nullable File file) {
		try {
			return file == null ? null : new FilePermissions(file);
		} catch (FileNotFoundException e) {
			return null;
		}
	}

	/**
	 * Return a file or folder's permissions.
	 * <p>
	 * This should <b>NOT</b> be used for checking e.g. read permissions before
	 * trying to open a file, because you can't assume that the same is true
	 * when you actually open the file. Other threads or processes could have
	 * locked the file (or changed it's permissions) in the meanwhile. Instead,
	 * use e.g {@link FileNotFoundException} like this:
	 *
	 * <pre>
	 * <code>
	 * } catch (FileNotFoundException e) {
	 *     LOGGER.debug("Can't read xxx {}", e.getMessage());
	 * }
	 * </code>
	 * </pre>
	 *
	 * {@code e.getMessage()} will contain both the full path to the file the
	 * reason it couldn't be read (e.g. no permission).
	 *
	 * @param path the file or folder name to check permissions for.
	 * @return A {@link FilePermissions} object holding the permissions.
	 * @throws FileNotFoundException If {@code path} can't be found/read.
	 * @see {@link #getFilePermissions(File)}
	 */
	@Nullable
	public static FilePermissions getFilePermissions(@Nullable String path) throws FileNotFoundException {
		return path == null ? null : new FilePermissions(new File(path));
	}

	/**
	 * Like {@link #getFilePermissions(String)} but returns {@code null} instead
	 * of throwing {@link FileNotFoundException} if the file or folder isn't
	 * found.
	 *
	 * @param path the file or folder name to check permissions for.
	 * @return A {@link FilePermissions} object holding the permissions.
	 */
	@Nullable
	public static FilePermissions getFilePermissionsNoThrow(@Nullable String path) {
		try {
			return path == null ? null : new FilePermissions(new File(path));
		} catch (FileNotFoundException | IllegalArgumentException e) {
			return null;
		}
	}

	public static boolean isFileRelevant(File f, PmsConfiguration configuration) {
		String fileName = f.getName().toLowerCase();
		if (
			(
				configuration.isArchiveBrowsing() &&
				(
					fileName.endsWith(".zip") ||
					fileName.endsWith(".cbz") ||
					fileName.endsWith(".rar") ||
					fileName.endsWith(".cbr")
				)
			) ||
			fileName.endsWith(".iso") ||
			fileName.endsWith(".img") ||
			fileName.endsWith(".m3u") ||
			fileName.endsWith(".m3u8") ||
			fileName.endsWith(".pls") ||
			fileName.endsWith(".cue")
		) {
			return true;
		}

		return false;
	}

	public static boolean isFolderRelevant(File f, PmsConfiguration configuration) {
		return isFolderRelevant(f, configuration, Collections.<String>emptySet());
	}

	public static boolean isFolderRelevant(File f, PmsConfiguration configuration, Set<String> ignoreFiles) {
		if (f.isDirectory() && configuration.isHideEmptyFolders()) {
			File[] children = f.listFiles();

			/*
			 * listFiles() returns null if "this abstract pathname does not denote a directory, or if an I/O error occurs".
			 * in this case (since we've already confirmed that it's a directory), this seems to mean the directory is non-readable
			 * http://www.ps3mediaserver.org/forum/viewtopic.php?f=6&t=15135
			 * http://stackoverflow.com/questions/3228147/retrieving-the-underlying-error-when-file-listfiles-return-null
			 */
			if (children == null) {
				LOGGER.warn("Can't list files in non-readable directory: {}", f.getAbsolutePath());
			} else {
				for (File child : children) {
					if (ignoreFiles.contains(child.getAbsolutePath())) {
						continue;
					}

					if (child.isFile()) {
						if (FormatFactory.getAssociatedFormat(child.getName()) != null || isFileRelevant(child, configuration)) {
							return true;
						}
					} else {
						if (isFolderRelevant(child, configuration, ignoreFiles)) {
							return true;
						}
					}
				}
			}
		}
		return false;
	}

	public static String renameForSorting(String filename) {
		if (PMS.getConfiguration().isPrettifyFilenames()) {
			// This makes anime sort properly
			filename = removeGroupNameFromBeginning(filename);

			// Replace periods and underscores with spaces
			filename = filename.replaceAll("\\.|_", " ");
		}

		if (PMS.getConfiguration().isIgnoreTheWordAandThe()) {
			// Remove "a" and "the" from filename
			filename = filename.replaceAll("^(?i)A[ .]|The[ .]", "");

			// Replace multiple whitespaces with space
			filename = filename.replaceAll("\\s{2,}", " ");
		}

		return filename;
	}

	/**
	 * Attempts to detect the {@link Charset} used in the specified {@link File}
	 * and creates a {@link BufferedReader} using that {@link Charset}. If the
	 * {@link Charset} detection fails, the specified default {@link Charset}
	 * will be used.
	 *
	 * @param file the {@link File} to use.
	 * @param defaultCharset the fallback {@link Charset} it automatic detection
	 *            fails. If {@code null}, the JVM default {@link Charset} will
	 *            be used.
	 * @return The resulting {@link BufferedReaderDetectCharsetResult}.
	 * @throws IOException If an I/O error occurs during the operation.
	 */
	@Nonnull
	public static BufferedReaderDetectCharsetResult createBufferedReaderDetectCharset(
		@Nullable File file,
		@Nullable Charset defaultCharset
	) throws IOException {
		BufferedReader reader;
		Charset fileCharset = getFileCharset(file);
		if (fileCharset != null) {
			reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), fileCharset));
			return new BufferedReaderDetectCharsetResult(reader, fileCharset, true);
		}
		if (defaultCharset == null) {
			defaultCharset = Charset.defaultCharset();
		}
		reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), defaultCharset));
		LOGGER.warn(
			"Could not detect character encoding for file \"{}\"; using the default charset \"{}\"",
			file.getAbsolutePath(),
			defaultCharset
		);
		return new BufferedReaderDetectCharsetResult(reader, defaultCharset, false);
	}

	/**
	 * Checks for valid file name syntax. Path is not allowed.
	 *
	 * @param fileName the file name to be verified
	 * @return whether or not the file name is valid
	 */
	public static boolean isValidFileName(String fileName) {
		if (Platform.isWindows()) {
			if (fileName.matches("^[^\"*:<>?/\\\\]+$")) {
				return true;
			}
		} else if (Platform.isMac()) {
			if (fileName.matches("^[^:/]+$")) {
				return true;
			}
		} else {
			// Assuming POSIX
			if (fileName.matches("^[A-Za-z0-9._][A-Za-z0-9._-]*$")) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Appends a path separator of the same type last in the string if it's not
	 * already there.
	 *
	 * @param path the path to be modified.
	 * @return The corrected path or {@code null} if {@code path} is
	 *         {@code null}.
	 */
	@Nullable
	public static String appendPathSeparator(@Nullable String path) {
		if (path == null) {
			return null;
		}
		if (!path.endsWith("\\") && !path.endsWith("/")) {
			if (path.contains("\\")) {
				path += "\\";
			} else {
				path += "/";
			}
		}
		return path;
	}

	/**
	 * Appends a suffix to a filename before the last {@code "."} if there is
	 * one. If not, simply appends the suffix to the filename.
	 *
	 * @param fileName the filename to append to.
	 * @param suffix the suffix to append.
	 * @return The modified filename.
	 */
	@Nonnull
	public static String appendToFileName(@Nonnull String fileName, @Nullable String suffix) {
		if (fileName == null) {
			throw new IllegalArgumentException("fileName cannot be null");
		}
		if (isBlank(suffix)) {
			return fileName;
		}
		int i = fileName.lastIndexOf(".");
		if (i < 0) {
			return fileName + suffix;
		}
		return fileName.substring(0, i) + suffix + fileName.substring(i);
	}

	private static Boolean isAdmin = null;
	private static Object isAdminLock = new Object();

	/**
	 * Determines whether or not the program has admin/root permissions.
	 */
	public static boolean isAdmin() {
		synchronized (isAdminLock) {
			if (isAdmin != null) {
				return isAdmin;
			}
			if (Platform.isWindows()) {
				Double version = BasicSystemUtils.INSTANCE.getWindowsVersion();
				if (version == null) {
					LOGGER.error(
						"Could not determine Windows version from {}. Administrator privileges is undetermined.",
						System.getProperty("os.version")
					);
					isAdmin = false;
					return false;
				}
				if (version >= 5.1) {
					try {
						String command = "reg query \"HKU\\S-1-5-19\"";
						Process p = Runtime.getRuntime().exec(command);
						p.waitFor();
						int exitValue = p.exitValue();

						if (0 == exitValue) {
							isAdmin = true;
							return true;
						}
						isAdmin = false;
						return false;
					} catch (IOException | InterruptedException e) {
						LOGGER.error("An error prevented DMS from checking Windows permissions: {}", e.getMessage());
					}
				} else {
					isAdmin = true;
					return true;
				}
			} else if (Platform.isLinux() || Platform.isMac()) {
				try {
					final String command = "id -Gn";
					LOGGER.trace("isAdmin: Executing \"{}\"", command);
					Process p = Runtime.getRuntime().exec(command);
					InputStream is = p.getInputStream();
					InputStreamReader isr = new InputStreamReader(is, StandardCharsets.US_ASCII);
					int exitValue;
					String exitLine;
					try (BufferedReader br = new BufferedReader(isr)) {
						p.waitFor();
						exitValue = p.exitValue();
						exitLine = br.readLine();
					}
					if (exitValue != 0 || exitLine == null || exitLine.isEmpty()) {
						LOGGER.error("Could not determine root privileges, \"{}\" ended with exit code: {}", command, exitValue);
						isAdmin = false;
						return false;
					}
					LOGGER.trace("isAdmin: \"{}\" returned {}", command, exitLine);
					if (
						(Platform.isLinux() && exitLine.matches(".*\\broot\\b.*")) ||
						(Platform.isMac() && exitLine.matches(".*\\badmin\\b.*"))
					) {
						LOGGER.trace("isAdmin: DMS has {} privileges", Platform.isLinux() ? "root" : "admin");
						isAdmin = true;
						return true;
					}
					LOGGER.trace("isAdmin: DMS does not have {} privileges", Platform.isLinux() ? "root" : "admin");
					isAdmin = false;
					return false;
				} catch (IOException | InterruptedException e) {
					LOGGER.error(
						"An error prevented DMS from checking {} permissions: {}",
						Platform.isMac() ? "OS X" : "Linux",
						e.getMessage()
					);
				}
			}
			isAdmin = false;
			return false;
		}
	}

	/**
	 * Finds the {@link UnixMountPoint} for a {@link java.nio.file.Path} given
	 * that the file resides on a Unix file system.
	 *
	 * @param path the {@link java.nio.file.Path} for which to find the Unix
	 *            mount point.
	 * @return The {@link UnixMountPoint} for the given path.
	 *
	 * @throws InvalidFileSystemException
	 */
	public static UnixMountPoint getMountPoint(Path path) throws InvalidFileSystemException {
		UnixMountPoint mountPoint = new UnixMountPoint();
		FileStore store;
		try {
			store = Files.getFileStore(path);
		} catch (IOException e) {
			throw new InvalidFileSystemException(
				String.format("Could not get Unix mount point for file \"%s\": %s", path.toAbsolutePath(), e.getMessage()),
				e
			);
		}

		try {
			Field entryField = store.getClass().getSuperclass().getDeclaredField("entry");
			Field nameField = entryField.getType().getDeclaredField("name");
			Field dirField = entryField.getType().getDeclaredField("dir");
			entryField.setAccessible(true);
			nameField.setAccessible(true);
			dirField.setAccessible(true);
			mountPoint.device = new String((byte[]) nameField.get(entryField.get(store)), StandardCharsets.UTF_8);
			mountPoint.folder = new String((byte[]) dirField.get(entryField.get(store)), StandardCharsets.UTF_8);
			return mountPoint;
		} catch (NoSuchFieldException e) {
			throw new InvalidFileSystemException(String.format("File \"%s\" is not on a Unix file system", path.isAbsolute()), e);
		} catch (SecurityException | IllegalArgumentException | IllegalAccessException e) {
			throw new InvalidFileSystemException(
				String.format(
					"An error occurred while trying to find mount point for file \"%s\": %s",
					path.toAbsolutePath(),
					e.getMessage()
				),
				e
			);
		}
	}

	/**
	 * Finds the {@link UnixMountPoint} for a {@link java.io.File} given that
	 * the file resides on a Unix file system.
	 *
	 * @param file the {@link java.io.File} for which to find the Unix mount
	 *            point.
	 * @return The {@link UnixMountPoint} for the given path.
	 *
	 * @throws InvalidFileSystemException
	 */
	public static UnixMountPoint getMountPoint(File file) throws InvalidFileSystemException {
		return getMountPoint(file.toPath());
	}

	public static boolean isUnixStickyBit(Path path) throws IOException, InvalidFileSystemException {
		PosixFileAttributes attr = Files.readAttributes(path, PosixFileAttributes.class);
		try {
			Field st_modeField = attr.getClass().getDeclaredField("st_mode");
			st_modeField.setAccessible(true);
			int st_mode = st_modeField.getInt(attr);
			return (st_mode & S_ISVTX) > 0;
		} catch (NoSuchFieldException | SecurityException | IllegalArgumentException | IllegalAccessException e) {
			throw new InvalidFileSystemException("File is not on a Unix file system: " + e.getMessage(), e);
		}
	}

	private static int unixUID = Integer.MIN_VALUE;
	private static Object unixUIDLock = new Object();

	/**
	 * Gets the user ID on Unix based systems. This should not change during a
	 * session and the lookup is expensive, so we cache the result.
	 *
	 * @return The Unix user ID
	 * @throws IOException
	 */
	public static int getUnixUID() throws IOException {
		if (
			Platform.isAIX() || Platform.isFreeBSD() || Platform.isGNU() || Platform.iskFreeBSD() ||
			Platform.isLinux() || Platform.isMac() || Platform.isNetBSD() || Platform.isOpenBSD() ||
			Platform.isSolaris()
		) {
			synchronized (unixUIDLock) {
				if (unixUID < 0) {
					String response;
					Process id;
					id = Runtime.getRuntime().exec("id -u");
					try (BufferedReader reader = new BufferedReader(new InputStreamReader(id.getInputStream(), Charset.defaultCharset()))) {
						response = reader.readLine();
					}
					try {
						unixUID = Integer.parseInt(response);
					} catch (NumberFormatException e) {
						throw new UnsupportedOperationException("Unexpected response from OS: " + response, e);
					}
				}
				return unixUID;
			}
		}
		throw new UnsupportedOperationException("getUnixUID can only be called on Unix based OS'es");
	}

	/**
	 * @return The OS {@code PATH} environment variable as a {@link List} of
	 *         {@link Path}s.
	 */
	@Nonnull
	public static List<Path> getOSPath() {
		List<Path> result = new ArrayList<>();
		String osPath = System.getenv("PATH");
		if (isBlank(osPath)) {
			return result;
		}
		String[] paths = osPath.split(File.pathSeparator);
		for (String path : paths) {
			result.add(Paths.get(path));
		}
		return result;
	}

	/**
	 * Tries to find the specified relative file that is both executable and
	 * readable using the system {@code PATH} environment variable while
	 * following symbolic links. Returns the first match in the order of the
	 * system {@code PATH} or {@code null} is no match was found.
	 *
	 * @param relativePath the relative {@link Path} describing the file or
	 *            folder to return.
	 * @return The matched {@link Path} or {@code null} if no match was found.
	 * @throws IllegalArgumentException if {@code relativePath} is absolute.
	 */
	@Nullable
	public static Path findExecutableInOSPath(@Nullable Path relativePath) {
		return findInOSPath(relativePath, true, FileFlag.FILE, FileFlag.READ, FileFlag.EXECUTE);
	}

	/**
	 * Tries to find the specified relative file or folder using the system
	 * {@code PATH} environment variable. Returns the first match in the order
	 * of the system {@code PATH} or {@code null} is no match was found.
	 *
	 * @param relativePath the relative {@link Path} describing the file or
	 *            folder to return.
	 * @param followSymlinks whether or not to follow symbolic links (NIO
	 *            default is {@code true}).
	 * @param requiredFlags zero or more {@link FileFlag}s that specify
	 *            permissions or properties that must be met for a file object
	 *            to match. Use for example {@link FileFlag#FILE} to only find
	 *            files or {@link FileFlag#FOLDER} to only find folders.
	 * @return The matched {@link Path} or {@code null} if no match was found.
	 * @throws IllegalArgumentException if {@code relativePath} is absolute.
	 */
	@Nullable
	public static Path findInOSPath(
		@Nullable Path relativePath,
		boolean followSymlinks,
		FileFlag... requiredFlags
	) {
		if (relativePath == null) {
			return null;
		}
		if (relativePath.isAbsolute()) {
			throw new IllegalArgumentException("relativePath must be relative");
		}

		LinkOption[] options = followSymlinks ? new LinkOption[] {} : new LinkOption[] {LinkOption.NOFOLLOW_LINKS};
		List<Path> osPath = new ArrayList<>();
		osPath.add(null);
		osPath.addAll(getOSPath());
		Path result = null;
		List<String> extensions = new ArrayList<>();
		extensions.add(null);
		if (Platform.isWindows() && getExtension(relativePath) == null) {
			for (String s : WindowsProgramPaths.getWindowsPathExtensions()) {
				if (isNotBlank(s)) {
					extensions.add("." + s);
				}
			}
		}
		for (String extension : extensions) {
			for (Path path : osPath) {
				if (path == null) {
					path = Paths.get("").toAbsolutePath();
				}
				if (extension == null) {
					result = path.resolve(relativePath);
				} else {
					result = path.resolve(relativePath.toString() + extension);
				}
				if (Files.exists(result, options)) {
					if (requiredFlags.length == 0) {
						if (LOGGER.isTraceEnabled()) {
							LOGGER.trace("Resolved \"{}\" from \"{}\" using OS path", result, relativePath);
						}
						try {
							return result.toRealPath(options);
						} catch (IOException e) {
							LOGGER.warn("Could not get the real path of \"{}\": {}", result, e.getMessage());
							LOGGER.trace("", e);
							return result;
						}
					}
					try {
						FilePermissions permissions = new FilePermissions(result, options);
						Set<FileFlag> flags = permissions.getFlags(requiredFlags);
						if (flags.containsAll(Arrays.asList(requiredFlags))) {
							if (LOGGER.isTraceEnabled()) {
								LOGGER.trace("Resolved \"{}\" from \"{}\" using OS path", result, relativePath);
							}
							try {
								return result.toRealPath(options);
							} catch (IOException e) {
								LOGGER.warn("Could not get the real path of \"{}\": {}", result, e.getMessage());
								LOGGER.trace("", e);
								return result;
							}
						}
					} catch (FileNotFoundException e) {
						continue;
					}
				}
			}
		}
		if (LOGGER.isTraceEnabled()) {
			LOGGER.trace("Failed to resolve \"{}\" using OS path", relativePath);
		}
		return null;
	}

	/**
	 * This class holds the results from
	 * {@link FileUtil#createBufferedReaderDetectCharset}.
	 *
	 * @author Nadahar
	 */
	public static class BufferedReaderDetectCharsetResult implements Closeable {
		private final BufferedReader reader;
		private final Charset charset;
		private final boolean successfulDetection;

		/**
		 * Creates a new instance with the given parameters.
		 *
		 * @param reader the {@link BufferedReader}.
		 * @param charset the {@link Charset}.
		 * @param successfulDetection {@code true} is {@link Charset} detection
		 *            was successful, {@code false} otherwise.
		 */
		public BufferedReaderDetectCharsetResult(
			@Nullable BufferedReader reader,
			@Nullable Charset charset,
			boolean successfulDetection
		) {
			this.reader = reader;
			this.charset = charset;
			this.successfulDetection = successfulDetection;
		}

		/**
		 * @return The {@link BufferedReader}.
		 */
		@Nullable
		public BufferedReader getBufferedReader() {
			return reader;
		}

		/**
		 * @return The {@link Charset} used for the {@link BufferedReader}.
		 */
		@Nullable
		public Charset getCharset() {
			return charset;
		}

		/**
		 * @return {@code true} if {@link Charset} detection was successful,
		 *         {@code false} if the default was used..
		 */
		public boolean isSuccessfulDetection() {
			return successfulDetection;
		}

		@Override
		public void close() throws IOException {
			if (reader != null) {
				reader.close();
			}
		}
	}
}
