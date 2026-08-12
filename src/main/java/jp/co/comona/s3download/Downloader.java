package jp.co.comona.s3download;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Properties;

import org.jline.reader.History.Entry;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.impl.history.DefaultHistory;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CommonPrefix;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Object;

/**
 * S3 downloader.
 * @author kageyama
 * @date: 2026/07/26
 */
public class Downloader {

	private static final String EXIT = "exit";
	private static final String HELP = "help";
	private static final String SHORT_HELP = "?";
	private static final String PWD = "pwd";
	private static final String LS = "ls";
	private static final String LSR = "lsr";
	private static final String CD = "cd";	// has 1 argument.
	private static final String DL = "dl";	// has 1 argument.

	private final Properties properties;
	private final String download;
	private S3Client s3 = null;
	private String bucket = null;
	private File downloadDir = null;
	private String currentPrefix = "";

	/**
	 * constructor.
	 * @param propertiesPath properties file path.
	 * @param download download s3 file path.
	 * @throws IOException 
	 */
	private Downloader(String propertiesPath, String download) throws IOException {
		super();
		properties = prepareProperties(propertiesPath);
		this.download = download;
	}

	/**
	 * execute.
	 * @return 0 if success, non 0 if error.
	 * @throws IOException 
	 * @throws IllegalArgumentException
	 */
	private int exec() throws IOException, IllegalArgumentException {
		String accessKeyId = properties.getProperty("accessKeyId");
		if ((accessKeyId == null) || accessKeyId.isEmpty()) {
			throw new IllegalArgumentException("Missing required property 'accessKeyId'. Please check your properties file.");
		}
		String secretAccessKey = properties.getProperty("secretAccessKey");
		if ((secretAccessKey == null) || secretAccessKey.isEmpty()) {
			throw new IllegalArgumentException("Missing required property 'secretAccessKey'. Please check your properties file.");
		}
		AwsBasicCredentials credentials = AwsBasicCredentials.create(accessKeyId, secretAccessKey);

		bucket = properties.getProperty("bucket");
		if ((bucket == null) || bucket.isEmpty()) {
			throw new IllegalArgumentException("Missing required property 'bucket'. Please check your properties file.");
		}
		String regionName = properties.getProperty("region");
		if ((regionName == null) || regionName.isEmpty()) {
			throw new IllegalArgumentException("Missing required property 'region'. Please check your properties file.");
		}
		Region region = Region.of(regionName);

		downloadDir = new File(System.getProperty("user.home"), "Downloads");

		try (S3Client s3 = S3Client.builder()
					.region(region)
					.credentialsProvider(StaticCredentialsProvider.create(credentials))
					.build();
				Terminal terminal = TerminalBuilder.builder().system(true).build();) {

			LineReader reader = LineReaderBuilder.builder().terminal(terminal).build();

			this.s3 = s3;

			if ((download != null) && !download.isEmpty()) {	// download command line argument & exit process.
				String command = String.format("%s \"%s\"", DL, download);
				try {
					download(command, reader);
					System.out.println("file: " + download + " downloaded.");
					return 0;	// argument file download success.
				} catch (NoSuchKeyException e) {
					System.out.println(e.getLocalizedMessage());
					return -1;	// argument file download error.
				}
			}

			while (true) {
				String line = reader.readLine("input[/" + currentPrefix + "] $ ");
				if (!EXIT.equals(line.trim())) {
					execUserInput(line, reader);
				} else {
					System.out.println("Bye!");
					break;
				}
			}
		}

		return 0;
	}

	/**
	 * execute user input.
	 * @param line user input line.
	 * @param reader user input line reader.
	 * @return true when user selected exit.
	 * @throws IOException 
	 */
	private void execUserInput(String line, LineReader reader) throws IOException {
		String trimmed = line.trim();
		if (!trimmed.isEmpty()) {	// do user input.
			boolean unknownCommand = false;
			if (HELP.equals(trimmed) || SHORT_HELP.equals(trimmed)) {
				printHelp();
			} else if (PWD.equals(trimmed)) {
				System.out.println("/" + currentPrefix);
			} else if (LS.equals(trimmed)) {
				listDirectory(null, false);
			} else if (LSR.equals(trimmed)) {
				listDirectory(null, true);
			} else {
				String[] splits = trimmed.split("\\s+");
				if (splits.length > 1) {
					if (CD.equals(splits[0])) {
						changeDirectory(trimmed);
					} else if (DL.equals(splits[0])) {
						try {
							download(trimmed, reader);
						} catch (NoSuchKeyException e) {
							//e.printStackTrace(System.err);
							String fileName = trimmed.substring(DL.length()).trim();
							System.out.println("file: " + fileName + " not found.");
						}
					} else if (LS.equals(splits[0])) {	// ls command with argument.
						if (!listDirectory(trimmed, false)) {
							unknownCommand = true;
						}
					} else if (LSR.equals(splits[0])) {	// lsr command with argument.
						if (!listDirectory(trimmed, true)) {
							unknownCommand = true;
						}
					} else {
						unknownCommand = true;
					}
				} else {
					unknownCommand = true;
				}
			}

			if (unknownCommand) {
				removeLastUserInputFromHistory(reader);
				System.out.println("Unknown command.");
			}
		}
	}

	/**
	 * list directory.
	 * @param userInput user input. null if current directory.
	 * @param recursive true if recursive.
	 * @return true if valid command.
	 * @throws IOException 
	 */
	private boolean listDirectory(String userInput, boolean recursive) throws IOException {
		String searchPrefix = currentPrefix.isEmpty() ? "" : currentPrefix + "/";

		if (userInput != null) {	// list with argument.
			String[] splits = userInput.split("\\s+");
			String argument = removeQuote(userInput.substring(splits[0].length()).trim());
			if (!argument.isEmpty()) {
				int index = argument.indexOf('*');	// currently not supporting wild card.
				if (index > -1) {
					return false;
				}
				index = argument.indexOf('?');
				if (index > -1) {
					return false;
				}

				String nextPrefix = resolveTargetPrefix(currentPrefix, argument);
				if (nextPrefix != null) {
					searchPrefix = nextPrefix;
					if (argument.endsWith("/")) {
						searchPrefix += "/";	// user wants search directory.
					}
				} else {
					return false;	// path resolve failed.
				}
			} else {
				return false;	// empty.
			}
		}

		list(searchPrefix, recursive, 0, userInput != null);
		return true;
	}

	/**
	 * list directory.
	 * @param searchPrefix search prefix.
	 * @param recursive true if recursive.
	 * @param depth of next list.
	 * @param hasArgument list command has argument.
	 */
	protected void list(String searchPrefix, boolean recursive, int depth, boolean hasArgument) {
		ListObjectsV2Request request = ListObjectsV2Request.builder()
				.bucket(bucket)
				.prefix(searchPrefix)
				.delimiter("/")
				.build();

		DirectoryList dirList = new DirectoryList(searchPrefix, this);
		dirList.setRecursive(recursive);
		dirList.setDepth(depth);
		dirList.setHasArgument(hasArgument);

		String searchName = null;
		if (hasArgument && !searchPrefix.endsWith("/") && (depth == 0)) {
			String[] searchNames = searchPrefix.split("/");
			searchName = searchNames[searchNames.length - 1];
		}

		for (ListObjectsV2Response response : s3.listObjectsV2Paginator(request)) {
			for (CommonPrefix cmnPrefix : response.commonPrefixes()) {
				if (searchName == null) {
					dirList.addDirectory(cmnPrefix);
				} else {
					String path = cmnPrefix.prefix();
					String[] names = path.split("/");
					if (searchName.equals(names[names.length - 1])) {
						dirList.addDirectory(cmnPrefix);
						dirList.setHitDirectory(true);
					}
				}
			}

			for (S3Object s3Obj : response.contents()) {
				if (searchPrefix.equals(s3Obj.key())) {	// .
					if (searchName == null) {	// do not 'continue' when argument specified.
						continue;
					}
				}
				if (searchName == null) {
					dirList.addFile(s3Obj);
				} else {
					String path = s3Obj.key();
					String[] names = path.split("/");
					if (searchName.equals(names[names.length - 1])) {
						dirList.addFile(s3Obj);
					}
				}
			}
		}

		dirList.print();
	}

	/**
	 * change directory.
	 * @param userInput user input.
	 */
	private void changeDirectory(String userInput) {
		String targetPrefix = userInput.substring(CD.length());
		targetPrefix = removeQuote(targetPrefix.trim());
		String nextPrefix = resolveTargetPrefix(currentPrefix, targetPrefix);
		if ((nextPrefix != null) && isDirectoryExist(nextPrefix)) {
			currentPrefix = nextPrefix;
		} else {
			System.out.println("No such directory.");
		}
	}

	/**
	 * check directory exist.
	 * @param userInput user input prefix.
	 * @return true if exist.
	 */
	private boolean isDirectoryExist(String nextPrefix) {
		if (!nextPrefix.endsWith("/") && !nextPrefix.isEmpty()) {
			nextPrefix += "/";
		}
		ListObjectsV2Request request = ListObjectsV2Request.builder()
				.bucket(bucket)
				.prefix(nextPrefix)
				.delimiter("/")
				.maxKeys(1)
				.build();
		ListObjectsV2Response response = s3.listObjectsV2(request);
		return !response.contents().isEmpty() || !response.commonPrefixes().isEmpty();
	}

	/**
	 * resolve directory.
	 * @param currentPrefix current prefix.
	 * @param userInput user input.
	 * @return next target prefix. null if user input was wrong.
	 */
	private static String resolveTargetPrefix(String currentPrefix, String userInput) {
		Deque<String> target = new ArrayDeque<>();

		if (!userInput.startsWith("/") && !currentPrefix.isEmpty()) {
			for (String part : currentPrefix.split("/")) {
				if (!part.isEmpty()) {
					target.addLast(part);
				}
			}
		}

		String input = userInput.startsWith("/") ? userInput.substring(1) : userInput;

		for (String part : input.split("/", -1)) {
			if (part.isEmpty()) {
				continue;
			}

			if (".".equals(part)) {
				continue;
			}

			if ("..".equals(part)) {
				if (target.isEmpty()) {
					return null;
				}
				target.removeLast();
				continue;
			}

			if (part.matches("\\.{3,}")) {
				return null;
			}

			target.addLast(part);
		}

		return String.join("/", target);
	}

	/**
	 * download.
	 * @param userInput user input string.
	 * @param reader user input line reader.
	 * @throws IOException
	 * @throws NoSuchKeyException
	 */
	private void download(String userInput, LineReader reader) throws IOException, NoSuchKeyException {
		String filePath = userInput.substring(DL.length());
		filePath = removeQuote(filePath.trim());

		if (filePath.endsWith("/")) {
			System.out.println("You cannot download directory.");
			return;
		}

		String downloadFilePath = null;
		if (filePath.startsWith("/")) {
			downloadFilePath = filePath.substring(1);
		} else {
			if (!currentPrefix.isEmpty()) {
				downloadFilePath = currentPrefix + "/" + filePath;
			} else {
				downloadFilePath = filePath;
			}
		}
		String[] fileNames = downloadFilePath.split("/");

		File renameDownloadFile = null;
		File downloadFile = new File(downloadDir, fileNames[fileNames.length - 1]);
		if (downloadFile.exists()) {

			String fileName = downloadFile.getName();
			int index = fileName.lastIndexOf('.');
			String prefix = "";
			String suffix = "";
			if (index > -1) {
				prefix = fileName.substring(0, index);
				suffix = fileName.substring(index);
			} else {
				prefix = fileName;
			}

			int count = 1;
			while (true) {
				String newFileName = prefix + " (" + count + ")" + suffix;
				renameDownloadFile = new File(downloadDir, newFileName);
				if (!renameDownloadFile.exists()) {
					break;
				}
				count++;
			}

			while (true) {
				String line = reader.readLine(promptForOverwrite(downloadFile, renameDownloadFile));
				removeLastUserInputFromHistory(reader);
				line = line.trim();
				if ("1".equals(line)) {
					renameDownloadFile = null;
					break;
				} else if ("2".equals(line)) {
					break;
				} else if ("3".equals(line)) {
					return;
				}
			}
		}

		Path output = renameDownloadFile == null ? downloadFile.toPath() : renameDownloadFile.toPath();
		GetObjectRequest request = GetObjectRequest.builder().bucket(bucket).key(downloadFilePath).build();
		try (ResponseInputStream<GetObjectResponse> response = s3.getObject(request)) {
			Files.copy(response, output, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	/**
	 * remove last user input from history.
	 * @param reader line reader.
	 * @throws IOException
	 */
	private static void removeLastUserInputFromHistory(LineReader reader) throws IOException {
		DefaultHistory history = (DefaultHistory) reader.getHistory();
		List<String> itemsToKeep = new ArrayList<>();
		for (Entry entry : history) {
			itemsToKeep.add(entry.line());
		}
		history.purge();
		for (int i = 0; i < itemsToKeep.size() - 1; i++) {
			String line = itemsToKeep.get(i);
			history.add(line);
		}
	}

	/**
	 * remove quotation marks.
	 * @param str string.
	 * @return string quotation removed.
	 */
	private static String removeQuote(String str) {
		if ((str.startsWith("\"") && str.endsWith("\"")) ||
				(str.startsWith("\'") && str.endsWith("\'")))	{
			str = str.substring(1, str.length() - 1);
		}
		return str;
	}

	/**
	 * get prompt overwrite message.
	 * @param downloadFile
	 * @param newDownloadFile
	 * @return prompt overwrite message.
	 */
	private static String promptForOverwrite(File downloadFile, File newDownloadFile) {
		StringBuilder sb = new StringBuilder(downloadFile.getAbsolutePath() + " exists\n");
		sb.append("1: overwrite.\n");
		sb.append("2: store in name ").append(newDownloadFile.getName()).append("\n");
		sb.append("3: abort.\n");
		sb.append("choose[1-3] $ ");
		return sb.toString();
	}

	/**
	 * print help.
	 */
	private static void printHelp() {
		System.out.println("help, ?	shows this help.");
		System.out.println("pwd	show current directory (prefix).");
		System.out.println("ls	list current directory (prefix).");
		System.out.println("lsr	list current directory (prefix) & beneath.");
		System.out.println("cd directory-path	change current directory (prefix).");
		System.out.println("dl file-or-directory-path	download file (prefix).");
	}

	/**
	 * load properties.
	 * @param propertiesPath properties file path.
	 * @return properties.
	 * @throws IOException
	 */
	private static Properties prepareProperties(String propertiesPath) throws IOException {
		Properties properties = new Properties();
		File propFile = new File(propertiesPath);
		try (FileInputStream fis = new FileInputStream(propFile)) {
			properties.load(fis);
		}
		return properties;
	}

	/**
	 * entry point.
	 * @param args argument array.
	 */
	public static void main(String[] args) {
		String help = CommandLineParser.parseArgument(args, "-?");
		if (help != null) {
			doUsage();
			return;
		}

		String propertiesPath = CommandLineParser.parseArgument(args, "-p");
		if ((propertiesPath == null) || propertiesPath.isEmpty()) {
			doUsage();
			System.exit(-1);
		}

		String download = CommandLineParser.parseArgument(args, "-d");

		int result = 0;
		try {
			Downloader downloader = new Downloader(propertiesPath, download);
			result = downloader.exec();
		} catch (Exception e) {
			e.printStackTrace(System.err);
			result = -1;
		}
		if (result != 0) {
			System.exit(result);
		}
	}

	/**
	 * print usage.
	 */
	private static void doUsage() {
		System.out.println("Downloader -p properties-file-path [-d download-file-path-in-s3]");
	}
}
