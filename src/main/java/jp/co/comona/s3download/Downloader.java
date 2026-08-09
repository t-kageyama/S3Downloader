package jp.co.comona.s3download;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Properties;
import java.util.Scanner;

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
				Scanner scanner = new Scanner(System.in)) {

			this.s3 = s3;

			if ((download != null) && !download.isEmpty()) {	// download command line argument & exit process.
				String command = String.format("%s \"%s\"", DL, download);
				try {
					download(command, scanner);
					System.out.println("file: " + download + " downloaded.");
					return 0;	// argument file download success.
				} catch (NoSuchKeyException e) {
					System.out.println(e.getLocalizedMessage());
					return -1;	// argument file download error.
				}
			}

			showPrompt();

			while (scanner.hasNextLine()) {
				String line = scanner.nextLine();
				if (!EXIT.equals(line.trim())) {
					execUserInput(line, scanner);
					showPrompt();
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
	 * @param scanner user input scanner.
	 * @return true when user selected exit.
	 * @throws IOException 
	 */
	private void execUserInput(String line, Scanner scanner) throws IOException {
		String trimmed = line.trim();
		if (!trimmed.isEmpty()) {	// do user input.
			boolean unknownCommand = false;
			if (HELP.equals(trimmed) || SHORT_HELP.equals(trimmed)) {
				printHelp();
			} else if (PWD.equals(trimmed)) {
				System.out.println("/" + currentPrefix);
			} else if (LS.equals(trimmed)) {
				listCurrentDirectory(false);
			} else if (LSR.equals(trimmed)) {
				listCurrentDirectory(true);
			} else {
				String[] splits = trimmed.split("\\s+");
				if (splits.length > 1) {
					if (CD.equals(splits[0])) {
						changeDirectory(trimmed);
					} else if (DL.equals(splits[0])) {
						try {
							download(trimmed, scanner);
						} catch (NoSuchKeyException e) {
							//e.printStackTrace(System.err);
							String fileName = trimmed.substring(DL.length()).trim();
							System.out.println("file: " + fileName + " not found.");
						}
					} else {
						unknownCommand = true;
					}
				} else {
					unknownCommand = true;
				}
			}

			if (unknownCommand) {
				System.out.println("Unknown command.");
			}
		}
	}

	/**
	 * list current directory.
	 * @param recursive true if recursive.
	 */
	private void listCurrentDirectory(boolean recursive) {
		String searchPrefix = currentPrefix.isEmpty() ? "" : currentPrefix + "/";
		list(searchPrefix, recursive, 0);
	}

	/**
	 * list directory.
	 * @param searchPrefix search prefix.
	 * @param recursive true if recursive.
	 * @param depth of next list.
	 */
	protected void list(String searchPrefix, boolean recursive, int depth) {
		ListObjectsV2Request request = ListObjectsV2Request.builder()
				.bucket(bucket)
				.prefix(searchPrefix)
				.delimiter("/")
				.build();

		DirectoryList dirList = new DirectoryList(searchPrefix, this);
		dirList.setRecursive(recursive);
		dirList.setDepth(depth);

		for (ListObjectsV2Response response : s3.listObjectsV2Paginator(request)) {
			for (CommonPrefix cmnPrefix : response.commonPrefixes()) {
				dirList.addDirectory(cmnPrefix);
			}

			for (S3Object s3Obj : response.contents()) {
				if (searchPrefix.equals(s3Obj.key())) {	// .
					continue;
				}
				dirList.addFile(s3Obj);
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
	 * @param scanner user input scanner.
	 * @throws IOException
	 * @throws NoSuchKeyException
	 */
	private void download(String userInput, Scanner scanner) throws IOException, NoSuchKeyException {
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

		File newDownloadFile = null;
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
				newDownloadFile = new File(downloadDir, newFileName);
				if (!newDownloadFile.exists()) {
					break;
				}
				count++;
			}

			promptOverwrite(downloadFile, newDownloadFile);
			while (scanner.hasNextLine()) {
				String line = scanner.nextLine();
				line = line.trim();
				if ("1".equals(line)) {
					newDownloadFile = null;
					break;
				} else if ("2".equals(line)) {
					break;
				} else if ("3".equals(line)) {
					return;
				}

				promptOverwrite(downloadFile, newDownloadFile);
			}
		}

		Path output = newDownloadFile == null ? downloadFile.toPath() : newDownloadFile.toPath();
		GetObjectRequest request = GetObjectRequest.builder().bucket(bucket).key(downloadFilePath).build();
		try (ResponseInputStream<GetObjectResponse> response = s3.getObject(request)) {
			Files.copy(response, output, StandardCopyOption.REPLACE_EXISTING);
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
	 * prompt overwrite.
	 * @param downloadFile existing file.
	 */
	private static void promptOverwrite(File downloadFile, File newDownloadFile) {
		System.out.println(downloadFile.getAbsolutePath() + " exists");
		System.out.println("1: overwrite.");
		System.out.println("2: store in name " + newDownloadFile.getName());
		System.out.println("3: abort.");
		System.out.print("choose[1-3] $ ");
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
	 * show input.
	 */
	private void showPrompt() {
		System.out.print("input[/" + currentPrefix + "] $ ");	// wait next input.
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
