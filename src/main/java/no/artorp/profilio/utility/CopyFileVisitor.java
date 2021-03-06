package no.artorp.profilio.utility;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *  Standard usage:
 *  <pre>
 *  {@code Files.walkFileTree(sourcePath, new CopyFileVisitor(targetPath));}
 */
public class CopyFileVisitor extends SimpleFileVisitor<Path> {
	
	public static final Logger LOGGER = Logger.getLogger(MethodHandles.lookup().lookupClass().getName());
	
	private final Path targetPath;
	private Path sourcePath = null;
	
	public CopyFileVisitor(Path targetPath) {
		this.targetPath = targetPath;
	}

	@Override
	public FileVisitResult preVisitDirectory(final Path dir, final BasicFileAttributes attrs) throws IOException {
		// Before visiting entries in a directory we copy the directory
		if (sourcePath == null) {
			sourcePath = dir;
		}
		Files.createDirectories(targetPath.resolve(sourcePath.relativize(dir)));
		return FileVisitResult.CONTINUE;
	}

	@Override
	public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) throws IOException {
		Path target = targetPath.resolve(sourcePath.relativize(file));
		LOGGER.fine(String.format("Copying\n%s\n to \n%s", file, target));
		Files.copy(file, targetPath.resolve(sourcePath.relativize(file)), StandardCopyOption.COPY_ATTRIBUTES);
		return FileVisitResult.CONTINUE;
	}
	
	public static void main(String[] args) {
		// Test copy directory
		Path homePath = Paths.get(System.getProperty("user.home"));
		
		Path sourceDir = homePath.resolve("TestSource");
		Path targetDir = homePath.resolve("TestCopyTo");
		
		sourceDir.resolve("AnotherSubDir").toFile().mkdirs();
		
		try {
			Files.walkFileTree(sourceDir, new CopyFileVisitor(targetDir));
		} catch (IOException e) {
			LOGGER.log(Level.SEVERE, "Error walking file tree", e);
		}
	}
	
}
