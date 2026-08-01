package jp.co.comona.s3download;

/**
 * command line parser.
 * @author kageyama
 * date: 2025/05/09
 */
public abstract class CommandLineParser {

	// MARK: - Constructor
	/**
	 * constructor.
	 */
	private CommandLineParser() {
		super();
	}

	// MARK: - Parse Argument
	/**
	 * parse command line argument.
	 * @param args command line arguments.
	 * @param opt option key to find value.
	 * @return argument value. null if not found. empty string if no value assigned.
	 */
	public static String parseArgument(String[] args, String opt) {
		String value = null;
		for (int i = 0; i < args.length; i++) {
			String arg = args[i];
			if (opt.equals(arg)) {
				int nextIndex = i + 1;
				if (nextIndex >= args.length) {
					value = "";	// option found but no value.
					break;	// exit loop.
				}
				String nextArg = args[nextIndex];
				if (nextArg.startsWith("-")) {
					value = "";	// option found but no value.
					// try to find next one.
				} else {
					return nextArg;
				}
			}
		}

		return value;
	}
}
