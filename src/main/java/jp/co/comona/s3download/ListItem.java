package jp.co.comona.s3download;

import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.Date;

/**
 * list item.
 * @author kageyama
 * @date: 2026/07/26
 */
public class ListItem {
	private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm");

	private final String name;
	private final Long size;
	private final Instant modifyDate;

	/**
	 * constructor.
	 * @param name file name.
	 */
	protected ListItem(String name) {
		super();
		this.name = name;
		this.size = null;
		this.modifyDate = null;
	}

	/**
	 * constructor.
	 * @param name file name.
	 * @param size file size.
	 * @param modifyDate file modify date.
	 */
	protected ListItem(String name, Long size, Instant modifyDate) {
		super();
		this.name = name;
		this.size = size;
		this.modifyDate = modifyDate;
	}

	/**
	 * get file name.
	 * @return file name.
	 */
	protected String getName() {
		return name;
	}

	/**
	 * get file size.
	 * @return file size.
	 */
	protected Long getSize() {
		return size;
	}

	/**
	 * get file modify date.
	 * @return file modify date.
	 */
	protected Instant getModifyDate() {
		return modifyDate;
	}

	/**
	 * get name width.
	 * @return get name width.
	 */
	protected int getNameWidth() {
		return getNameWidth(name);
	}

	/**
	 * get name width.
	 * @param name name.
	 * @return name width.
	 */
	private static int getNameWidth(String name) {
		int length = 0;
		for (int cp : name.codePoints().toArray()) {
			length += cp < 0x80 ? 1 : 2;
		}
		return length;
	}

	/**
	 * get size in string format.
	 * @return size in string format.
	 */
	protected String getSizeString() {
		return String.format("%,d", size);
	}

	/**
	 * get size string width.
	 * @return size string width.
	 */
	protected int getSizeWidth() {
		return getSizeString().length();
	}

	/**
	 * get print line.
	 * @param fileWidth file width.
	 * @param sizeWidth size width.
	 * @return print line.
	 */
	protected String getPrintLine(int fileWidth, int sizeWidth) {
		String name = getName();
		while (getNameWidth(name) < fileWidth) {
			name += ' ';
		}

		String size = getSizeString();
		while (size.length() < sizeWidth) {
			size = " " + size;
		}

		String date = DATE_FORMAT.format(Date.from(modifyDate));

		return String.format("%s %s bytes %s", name, size, date);
	}
}
