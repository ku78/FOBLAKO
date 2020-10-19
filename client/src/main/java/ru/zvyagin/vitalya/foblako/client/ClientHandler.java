package ru.zvyagin.vitalya.foblako.client;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import ru.zvyagin.vitalya.common.Command;
import ru.zvyagin.vitalya.common.FileInfo;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class ClientHandler extends ChannelInboundHandlerAdapter {
    private List<Callback> callbackList;

    public ClientHandler(List<Callback> callbackList) {
        this.callbackList = callbackList;
    }

    public enum State {
        IDLE,                                                                   // стартовая позиция
        FILE_NAME_LENGTH, FILE_NAME, FILE_LENGTH, FILE,                         // для чтения файлов
        DIR_NAME_LENGTH, DIR_NAME, FILE_TYPE_DIR,                               // для чтения директорий
        LIST_SIZE, NAME_LENGTH_LIST, FILE_TYPE, NAME_LIST, FILE_LENGTH_LIST     // для чтения списка файлов
    }

    private State currentState = State.IDLE;
    boolean fileReading = false;
    boolean fileListReading = false;

    private Path filePath;
    private byte readed;
    private int fileNameLength;
    private String fileName;
    private long fileLength;
    private long receivedFileLength;
    private BufferedOutputStream out;
    private final int tmpBufSize = 8192;
    private final byte[] tmpBuf = new byte[tmpBufSize];

    private int listSize;
    private FileInfo.FileType fileType;
    private boolean directoryReading = false;
    private Path pathBeforeDirReading;


    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        ByteBuf buf = ((ByteBuf) msg);
        while (buf.readableBytes() > 0) {
            if (currentState == State.IDLE) {
                readed = buf.readByte();
                readCommand(readed);
            }
            if (directoryReading) {
                readDirectory(buf);
            }
            if (fileReading) {
                readFile(buf);
            }
            if (fileListReading) {
                if (currentState == State.LIST_SIZE) {
                    if (buf.readableBytes() >= 4) {
                        listSize = buf.readInt();
                        currentState = State.NAME_LENGTH_LIST;
                    }
                }
                if (currentState != State.LIST_SIZE && listSize > 0) {
                    readServerFilesList(buf);
                }
            }
        }
        if (buf.readableBytes() == 0) {
            buf.release();
        }
    }

    private void readCommand(byte readed) {
        if (readed == Command.AUTH_OK.getByteValue()) {
            System.out.println("Добро пожаловать!");
            callbackList.get(0).callback();
        } else if (readed == Command.AUTH_ERR.getByteValue()) {
            GUIHelper.showError(new RuntimeException("Неверный логин или пароль"));
        } else if (readed == Command.SERVER_PATH_CURRENT.getByteValue() || readed == Command.SERVER_PATH_DOWN.getByteValue() ||
                readed == Command.SERVER_PATH_UP.getByteValue()) {
            GUIHelper.serverFilesList.clear();
            currentState = State.LIST_SIZE;
            fileListReading = true;
        } else if (readed == Command.SERVER_PATH_DOWN_EMPTY.getByteValue()) {
            GUIHelper.serverFilesList.clear();
            GUIHelper.currentServerPath = GUIHelper.currentServerPath.resolve(GUIHelper.targetServerDirectory);
            callbackList.get(1).callback();
        } else if (readed == Command.SERVER_PATH_CURRENT_EMPTY.getByteValue()) {
            GUIHelper.serverFilesList.clear();
            callbackList.get(1).callback();
        } else if (readed == Command.FILE_DOES_NOT_EXIST.getByteValue()) {
            GUIHelper.showError(new RuntimeException("Данный файл не существует"));
        } else if (readed == Command.TRANSFER_FILE_ERR.getByteValue()) {
            GUIHelper.showError(new RuntimeException("Не удалось отправить файл"));
        } else if (readed == Command.DELETE_FILE_ERR.getByteValue()) {
            GUIHelper.showError(new RuntimeException("Не удалось удалить файл"));
        } else if (readed == Command.DOWNLOAD_FILE_ERR.getByteValue()) {
            GUIHelper.showError(new RuntimeException("Не удалось скачать файл"));
        } else if (readed == Command.TRANSFER_FILE.getByteValue()) {
            currentState = State.FILE_NAME_LENGTH;
            fileReading = true;
        } else if (readed == Command.TRANSFER_DIRECTORY.getByteValue()) {
            pathBeforeDirReading = GUIHelper.currentClientPath;
            currentState = State.DIR_NAME_LENGTH;
            directoryReading = true;
        } else {
            System.out.println("ERROR: Invalid first byte - " + readed);
        }
    }

    private void readDirectory(ByteBuf buf) {
        if (currentState == State.DIR_NAME_LENGTH) {
            if (buf.readableBytes() >= 4) {
                fileNameLength = buf.readInt();
                System.out.println("Get dirName length " + fileNameLength);
                currentState = State.DIR_NAME;
            }
        }

        if (currentState == State.DIR_NAME) {
            try {
                if (buf.readableBytes() >= fileNameLength) {
                    byte[] fileName = new byte[fileNameLength];
                    buf.readBytes(fileName);
                    GUIHelper.currentClientPath = GUIHelper.currentClientPath.resolve(new String(fileName, "UTF-8"));
                    if (!Files.exists(GUIHelper.currentClientPath)) {
                        Files.createDirectory(GUIHelper.currentClientPath);
                    }
                    System.out.println("Get dirName " + GUIHelper.currentClientPath);
                    currentState = State.FILE_TYPE_DIR;
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        if (currentState == State.FILE_TYPE_DIR) {
            if (buf.readableBytes() > 0) {
                byte b = buf.readByte();
                System.out.println("FILE_TYPE " + b);
                if (b == Command.IS_DIRECTORY.getByteValue()) {
                    currentState = State.DIR_NAME_LENGTH;
                } else if (b == Command.IS_FILE.getByteValue()) {
                    currentState = State.FILE_NAME_LENGTH;
                    fileReading = true;
                } else if (b == Command.END_DIRECTORY.getByteValue()) {
                    GUIHelper.currentClientPath = GUIHelper.currentClientPath.getParent();
                    if (GUIHelper.currentClientPath.equals(pathBeforeDirReading)) {
                        currentState = State.IDLE;
                        directoryReading = false;
                        callbackList.get(2).callback();
                    }
                } else {
                    System.out.println("ERROR: Invalid first byte - " + b);
                    currentState = State.IDLE;
                }
            }
        }

    }

    private void readServerFilesList(ByteBuf buf) {
        if (currentState == State.NAME_LENGTH_LIST) {
            if (buf.readableBytes() >= 4) {
                fileNameLength = buf.readInt();
                currentState = State.NAME_LIST;
            }
        }
        if (currentState == State.NAME_LIST) {
            if (buf.readableBytes() >= fileNameLength) {
                byte[] nextFileNameBytes = new byte[fileNameLength];
                buf.readBytes(nextFileNameBytes);
                getFileName(nextFileNameBytes);
                currentState = State.FILE_TYPE;
            }
        }

        if (currentState == State.FILE_TYPE) {
            if (buf.readableBytes() > 0) {
                byte isDirectoryOrFile = buf.readByte();
                if (isDirectoryOrFile == Command.IS_DIRECTORY.getByteValue()) {
                    fileType = FileInfo.FileType.DIRECTORY;
                    currentState = State.FILE_LENGTH_LIST;
                } else if (isDirectoryOrFile == Command.IS_FILE.getByteValue()) {
                    fileType = FileInfo.FileType.FILE;
                    currentState = State.FILE_LENGTH_LIST;
                } else {
                    System.out.println("ERROR: Invalid first byte - " + isDirectoryOrFile);
                    currentState = State.IDLE;
                }
            }
        }

        if (currentState == State.FILE_LENGTH_LIST) {
            if (buf.readableBytes() >= 8) {
                long fileSize = buf.readLong();
                filePath = GUIHelper.currentClientPath.resolve(fileName);
                FileInfo fileInfo = new FileInfo(fileName, fileSize, fileType);
                GUIHelper.serverFilesList.add(fileInfo);
                listSize--;
                if (listSize == 0) {
                    if (readed == Command.SERVER_PATH_DOWN.getByteValue()) {
                        GUIHelper.currentServerPath = GUIHelper.currentServerPath.resolve(GUIHelper.targetServerDirectory);
                    }
                    if (readed == Command.SERVER_PATH_UP.getByteValue()) {
                        Path upperPath = GUIHelper.currentServerPath.getParent();
                        if (upperPath != null) {
                            GUIHelper.currentServerPath = upperPath;
                        }
                    }
                    callbackList.get(1).callback();
                    fileListReading = false;
                    currentState = State.IDLE;

                } else {
                    currentState = State.NAME_LENGTH_LIST;
                }
            }
        }

    }

    private void readFile(ByteBuf buf) {
        if (currentState == State.FILE_NAME_LENGTH) {
            if (buf.readableBytes() >= 4) {
                fileNameLength = buf.readInt();
                currentState = State.FILE_NAME;
            }
        }

        if (currentState == State.FILE_NAME) {
            if (buf.readableBytes() >= fileNameLength) {
                byte[] fileNameBytes = new byte[fileNameLength];
                buf.readBytes(fileNameBytes);
                getFileName(fileNameBytes);
                filePath = GUIHelper.currentClientPath.resolve(fileName);
                deleteFileIfExist(filePath);
                currentState = State.FILE_LENGTH;
            }
        }

        if (currentState == State.FILE_LENGTH) {
            try {
                if (buf.readableBytes() >= 8) {
                    receivedFileLength = 0L;
                    fileLength = buf.readLong();
                    if (fileLength == 0) {
                        Files.createFile(filePath);
                        if (directoryReading) {
                            currentState = State.FILE_TYPE_DIR;
                        } else {
                            callbackList.get(2).callback();
                            currentState = State.IDLE;
                        }
                    } else {
                        out = new BufferedOutputStream(new FileOutputStream(filePath.toString()));
                        currentState = State.FILE;
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        if (currentState == State.FILE) {
            try {
                while (buf.readableBytes() > 0) {
                    if (fileLength - receivedFileLength > tmpBufSize && buf.readableBytes() > tmpBufSize){
                        buf.readBytes(tmpBuf);
                        out.write(tmpBuf);
                        receivedFileLength += tmpBufSize;
                    } else {
                        out.write(buf.readByte());
                        receivedFileLength++;
                        if (fileLength == receivedFileLength) {
                            out.close();
                            fileReading = false;
                            if (directoryReading) {
                                currentState = State.FILE_TYPE_DIR;
                            } else {
                                callbackList.get(2).callback();
                                currentState = State.IDLE;
                            }
                            break;
                        }
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void getFileName(byte[] nextFileNameBytes) {
        try {
            fileName = new String(nextFileNameBytes, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
    }

    private void deleteFileIfExist(Path filePath) {
        try {
            if (Files.exists(filePath)) {
                Files.delete(filePath);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        ctx.close();
    }
}
