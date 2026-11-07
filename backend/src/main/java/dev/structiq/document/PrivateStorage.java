package dev.structiq.document;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class PrivateStorage {
    private final Path root;
    public PrivateStorage(@Value("${structiq.document-root}") String root) throws IOException {
        this.root=Path.of(root).toAbsolutePath().normalize();
        Files.createDirectories(this.root);
        if(Files.isSymbolicLink(this.root)) throw new IOException("Invalid private storage root");
        Files.setPosixFilePermissions(this.root,PosixFilePermissions.fromString("rwxr-x---"));
    }
    public String key(UUID project,UUID document) { return project+"/"+document; }
    private Path path(String key) throws IOException {
        if(!key.matches("[a-f0-9-]{36}/[a-f0-9-]{36}")) throw new IOException("Invalid storage key");
        String[] parts=key.split("/");
        UUID.fromString(parts[0]); UUID.fromString(parts[1]);
        Path path=root.resolve(key).normalize();
        if(!path.startsWith(root)||Files.isSymbolicLink(path.getParent())||Files.isSymbolicLink(path))
            throw new IOException("Invalid private storage path");
        return path;
    }
    public void write(String key,byte[] bytes) throws IOException {
        Path target=path(key);
        Files.createDirectories(target.getParent(),PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwxr-x---")));
        Files.createFile(target,PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-r-----")));
        try { Files.write(target,bytes,StandardOpenOption.WRITE); }
        catch(IOException ex) { Files.deleteIfExists(target); throw ex; }
    }
    public byte[] read(String key) throws IOException { return Files.readAllBytes(path(key)); }
    public void delete(String key) throws IOException {
        Path target=path(key); Files.deleteIfExists(target);
        try { Files.deleteIfExists(target.getParent()); }
        catch(DirectoryNotEmptyException ignored) { /* Other private documents remain in this project. */ }
    }
}
