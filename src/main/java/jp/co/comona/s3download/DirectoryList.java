package jp.co.comona.s3download;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import software.amazon.awssdk.services.s3.model.CommonPrefix;
import software.amazon.awssdk.services.s3.model.S3Object;

/**
 * directory list.
 * @author kageyama
 * @date: 2026/07/26
 */
public class DirectoryList {

	private static final int LEAST_FILE_WIDTH = 12;
	private static final int LEAST_SIZE_WIDTH = 2;
	private static final String LEAD_CHARS = "  ";

	private final String searchPrefix;
	private final Downloader downloader;
	private final List<ListItem> directories;
	private final List<ListItem> files;
	private int depth = 0;
	private boolean recursive = false;
	private boolean hasArgument = false;
	private boolean hitDirectory = false;

	/**
	 * constructor.
	 * @param searchPrefix search prefix.
	 * @param downloader downloader object.
	 */
	protected DirectoryList(String searchPrefix, Downloader downloader) {
		super();
		this.searchPrefix = searchPrefix;
		this.downloader = downloader;
		directories = new ArrayList<>();
		files = new ArrayList<>();
	}

	/**
	 * add directory.
	 * @param cmnPrefix directory type prefix.
	 */
	protected void addDirectory(CommonPrefix cmnPrefix) {
		String path = cmnPrefix.prefix();
		String[] names = path.split("/");
		directories.add(new ListItem(names[names.length - 1] + "/"));
	}

	/**
	 * add file.
	 * @param s3Obj s3 file object.
	 */
	protected void addFile(S3Object s3Obj) {
		String path = s3Obj.key();
		String[] names = path.split("/");
		files.add(new ListItem(names[names.length - 1], s3Obj.size(), s3Obj.lastModified()));
	}

	/**
	 * print directory.
	 */
	protected void print() {
		int maxNameLength = 0;
		int maxSizeLength = 0;
		String leadBlock = String.join("", Collections.nCopies(depth, LEAD_CHARS));

		for (ListItem item : directories) {
			int len = Math.max(LEAST_FILE_WIDTH, item.getNameWidth());
			if (len > maxNameLength) {
				maxNameLength = len;
			}
		}
		for (ListItem item : files) {
			int len = Math.max(LEAST_FILE_WIDTH, item.getNameWidth());
			if (len > maxNameLength) {
				maxNameLength = len;
			}
			int size = Math.max(LEAST_SIZE_WIDTH, item.getSizeWidth());
			if (size > maxSizeLength) {
				maxSizeLength = size;
			}
		}

		for (ListItem item : directories) {
			System.out.println(leadBlock + item.getName());
			if (recursive || hitDirectory) {
				doRecursive(item);
			}
		}

		for (ListItem item : files) {
			System.out.println(leadBlock + item.getPrintLine(maxNameLength, maxSizeLength));
		}
	}

	/**
	 * get depth.
	 * @return depth
	 */
	protected int getDepth() {
		return depth;
	}

	/**
	 * set depth.
	 * @param depth depth.
	 */
	protected void setDepth(int depth) {
		this.depth = depth;
	}

	/**
	 * get recursive.
	 * @return true if recursive.
	 */
	protected boolean isRecursive() {
		return recursive;
	}

	/**
	 * get recursive.
	 * @param recursive true if recursive.
	 */
	protected void setRecursive(boolean recursive) {
		this.recursive = recursive;
	}

	/**
	 * do recursive.
	 * @param item item to recursive.
	 */
	private void doRecursive(ListItem item) {
		String newSearchPrefix = searchPrefix;
		if (hitDirectory) {
			newSearchPrefix += "/";
		} else {
			newSearchPrefix += item.getName();
		}
		downloader.list(newSearchPrefix, recursive, depth + 1, hasArgument);
	}

	/**
	 * has argument?
	 * @return true if has argument.
	 */
	protected boolean hasArgument() {
		return hasArgument;
	}

	/**
	 * set has argument.
	 * @param hasArgument true if has argument.
	 */
	protected void setHasArgument(boolean hasArgument) {
		this.hasArgument = hasArgument;
	}

	/**
	 * is hit directory.
	 * @return true if hit directory.
	 */
	public boolean isHitDirectory() {
		return hitDirectory;
	}

	/**
	 * set hit directory.
	 * @param hitDirectory true if hit directory.
	 */
	public void setHitDirectory(boolean hitDirectory) {
		this.hitDirectory = hitDirectory;
	}
}
