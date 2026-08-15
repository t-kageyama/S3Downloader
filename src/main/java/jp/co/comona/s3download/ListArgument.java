package jp.co.comona.s3download;

/**
 * list argument.
 * @author kageyama
 * @date: 2026/08/15
 */
public class ListArgument {

	private boolean wildcard = false;
	private String searchPrefix = null;
	private String namePattern = null;
	private boolean directory = false;

	/**
	 * constrcutor.
	 */
	protected ListArgument() {
		super();
	}

	/**
	 * is wild card?
	 * @return true if wild card.
	 */
	protected boolean isWildcard() {
		return wildcard;
	}

	/**
	 * set wild card.
	 * @param wildcard true if wild card.
	 */
	protected void setWildcard(boolean wildcard) {
		this.wildcard = wildcard;
	}

	/**
	 * get search prefix.
	 * @return search prefix.
	 */
	protected String getSearchPrefix() {
		return searchPrefix;
	}

	/**
	 * set search prefix.
	 * @param searchPrefix search prefix.
	 */
	protected void setSearchPrefix(String searchPrefix) {
		this.searchPrefix = searchPrefix;
	}

	/**
	 * get name pattern.
	 * @return name pattern
	 */
	protected String getNamePattern() {
		return namePattern;
	}

	/**
	 * set name pattern.
	 * @param namePattern name pattern
	 */
	protected void setNamePattern(String namePattern) {
		this.namePattern = namePattern;
	}

	/**
	 * search directory?
	 * @return true if search directory.
	 */
	protected boolean isDirectory() {
		return directory;
	}

	/**
	 * set search directory.
	 * @param directory true if search directory.
	 */
	protected void setDirectory(boolean directory) {
		this.directory = directory;
	}
}
