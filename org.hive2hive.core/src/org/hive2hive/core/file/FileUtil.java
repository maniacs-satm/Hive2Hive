package org.hive2hive.core.file;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.hive2hive.core.H2HConstants;
import org.hive2hive.core.log.H2HLoggerFactory;
import org.hive2hive.core.model.FolderIndex;
import org.hive2hive.core.model.Index;
import org.hive2hive.core.security.EncryptionUtil;

public class FileUtil {

	private final static Logger logger = H2HLoggerFactory.getLogger(FileUtil.class);

	private FileUtil() {
		// only static methods
	}

	/**
	 * Writes the meta data (used to synchronize) to the disk
	 * 
	 * @throws IOException
	 */
	public static void writePersistentMetaData(Path root) throws IOException {
		// generate the new persistent meta data
		PersistentMetaData metaData = new PersistentMetaData();
		PersistenceFileVisitor visitor = new PersistenceFileVisitor(root);
		Files.walkFileTree(root, visitor);
		metaData.setFileTree(visitor.getFileTree());

		byte[] encoded = EncryptionUtil.serializeObject(metaData);
		FileUtils.writeByteArrayToFile(Paths.get(root.toString(), H2HConstants.META_FILE_NAME).toFile(),
				encoded);
	}

	/**
	 * Reads the meta data (used to synchronize) from the disk
	 * 
	 * @return the read meta data (never null)
	 * @throws IOException
	 * @throws ClassNotFoundException
	 */
	public static PersistentMetaData readPersistentMetaData(Path root) throws IOException,
			ClassNotFoundException {
		byte[] content = FileUtils.readFileToByteArray(Paths
				.get(root.toString(), H2HConstants.META_FILE_NAME).toFile());
		PersistentMetaData metaData = (PersistentMetaData) EncryptionUtil.deserializeObject(content);
		return metaData;
	}

	public static String getFileSep() {
		String fileSep = System.getProperty("file.separator");
		if (fileSep.equals("\\"))
			fileSep = "\\\\";
		return fileSep;
	}

	/**
	 * Returns the file on disk from a file node of the user profile
	 * 
	 * @param fileToFind
	 * @return the path to the file or null if the parameter is null
	 */
	public static Path getPath(Path root, Index fileToFind) {
		if (fileToFind == null)
			return null;
		return Paths.get(root.toString(), fileToFind.getFullPath().toString());
	}

	/**
	 * Note that file.length can be very slowly (see
	 * http://stackoverflow.com/questions/116574/java-get-file-size-efficiently)
	 * 
	 * @return the file size in bytes
	 * @throws IOException
	 */
	public static long getFileSize(File file) {
		InputStream stream = null;
		try {
			URL url = file.toURI().toURL();
			stream = url.openStream();
			return stream.available();
		} catch (IOException e) {
			return file.length();
		} finally {
			try {
				if (stream != null)
					stream.close();
			} catch (IOException e) {
				// ignore
			}
		}
	}

	public static int getNumberOfChunks(File file, long chunkSize) {
		long fileSize = getFileSize(file);
		return (int) Math.ceil((double) fileSize / (double) chunkSize);
	}

	/**
	 * Move a file according to their nodes. This operation also support renaming and moving in the same step.
	 * 
	 * @param sourceName the name of the file at the source
	 * @param destName the name of the file at the destination
	 * @param oldParent the old parent {@link FolderIndex}
	 * @param newParent the new parent {@link FolderIndex}
	 * @param fileManager the {@link FileManager} of the user
	 * @throws IOException when moving went wrong
	 */
	public static void moveFile(Path root, String sourceName, String destName, Index oldParent,
			Index newParent) throws IOException {
		// find the file of this user on the disc
		File oldParentFile = FileUtil.getPath(root, oldParent).toFile();
		File toMoveSource = new File(oldParentFile, sourceName);

		if (!toMoveSource.exists()) {
			throw new FileNotFoundException("Cannot move file '" + toMoveSource.getAbsolutePath()
					+ "' because it's not at the source location anymore");
		}

		File newParentFile = FileUtil.getPath(root, newParent).toFile();
		File toMoveDest = new File(newParentFile, destName);

		if (toMoveDest.exists()) {
			logger.warn("Overwriting '" + toMoveDest.getAbsolutePath()
					+ "' because file has been moved remotely");
		}

		// move the file
		Files.move(toMoveSource.toPath(), toMoveDest.toPath(), StandardCopyOption.ATOMIC_MOVE);
		logger.debug("Successfully moved the file from " + toMoveSource.getAbsolutePath() + " to "
				+ toMoveDest.getAbsolutePath());
	}
}
