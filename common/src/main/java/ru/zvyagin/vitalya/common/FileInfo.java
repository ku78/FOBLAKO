package ru.zvyagin.vitalya.common;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class FileInfo {
    private String name;
    private long size;
    private FileType type;

    public enum FileType {
        FILE("F"), DIRECTORY("D");
        private String name;
        public String getName() {
            return name;
        }
        FileType(String name) {
            this.name = name;
        }
    }

    public FileInfo(Path path) {
        try {
            this.name = path.getFileName().toString();
            this.type = Files.isDirectory(path) ? FileType.DIRECTORY : FileType.FILE;
            if (Files.isDirectory(path)) {
                this.size = -1L;
            } else {
                this.size = Files.size(path);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public FileInfo(String name, long size, FileType type) {
        this.name = name;
        this.size = size;
        this.type = type;
        if (type == FileType.DIRECTORY) {
            this.size = -1L;
        }
    }


    public String getFileName() {
        return name;
    }

    public long getSize() {
        return size;
    }

    public FileType getType() {
        return type;
    }

    public void setType(FileType type) {
        this.type = type;
    }

}
