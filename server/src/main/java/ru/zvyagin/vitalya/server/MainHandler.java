package ru.zvyagin.vitalya.server;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.apache.commons.io.FileUtils;
import ru.zvyagin.vitalya.common.Command;
import ru.zvyagin.vitalya.common.Sender;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;

public class MainHandler extends ChannelInboundHandlerAdapter {

    private Path currentServerPathGUI;
    private Path rootServerPath;

    public MainHandler(Path currentServerPath) {
        this.currentServerPathGUI = currentServerPath;
        this.rootServerPath = currentServerPath;
    }

    public enum State {
        IDLE,                                               // стартовая позиция
        FILE_NAME_LENGTH, FILE_NAME, FILE_LENGTH, FILE,     // для чтения директорий
        DIR_NAME_LENGTH, DIR_NAME, FILE_TYPE;               // для чтения файлов
    }

    private State currentState = State.IDLE;
    private int fileNameLength;
    private long fileLength;
    private long receivedFileLength;
    private byte readed;
    private Path filePath;
    private BufferedOutputStream out;
    private boolean directoryReading;
    private boolean fileReading;
    private Path pathBeforeDirReading;
    private final int tmpBufSize = 8192;
    private final byte[] tmpBuf = new byte[tmpBufSize];


    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        ByteBuf buf = ((ByteBuf) msg);
        while (buf.readableBytes() > 0) {
            if (currentState == State.IDLE) {
                readed = buf.readByte();
                readCommand(readed, ctx);
            }

            if (directoryReading) {
                readDirectory(buf, ctx);
            }
            if (fileReading) {
                readFile(buf, ctx);
            }
        }
        if (buf.readableBytes() == 0) {
            buf.release();
        }
    }

    private void readCommand(byte readed, ChannelHandlerContext ctx) {
        if (readed == Command.SERVER_PATH_CURRENT.getByteValue()) {
            Sender.sendFilesList(currentServerPathGUI, ctx.channel(), Command.SERVER_PATH_CURRENT);
        } else if (readed == Command.SERVER_PATH_UP.getByteValue()) {
            if (!currentServerPathGUI.equals(rootServerPath)) {
                currentServerPathGUI = currentServerPathGUI.getParent();
                Sender.sendFilesList(currentServerPathGUI, ctx.channel(), Command.SERVER_PATH_UP);
            }
        } else if (readed == Command.TRANSFER_DIRECTORY.getByteValue()) {
            pathBeforeDirReading = currentServerPathGUI;
            currentState = State.DIR_NAME_LENGTH;
            directoryReading = true;
        } else if (readed == Command.SERVER_PATH_DOWN.getByteValue() ||
                readed == Command.TRANSFER_FILE.getByteValue() ||           // прием файла
                readed == Command.DELETE_FILE.getByteValue() ||             // удаление
                readed == Command.DOWNLOAD_FILE.getByteValue()||             // удаление
                readed == Command.DOWNLOAD_DIRECTORY.getByteValue()) {            // запрос на скачивание
            fileReading = true;
            currentState = State.FILE_NAME_LENGTH;
        } else {
            System.out.println("ERROR: Invalid first byte - " + readed);
        }
    }

    private void readDirectory(ByteBuf buf, ChannelHandlerContext ctx) {
        if (currentState == State.DIR_NAME_LENGTH) {
            if (buf.readableBytes() >= 4) {
                fileNameLength = buf.readInt();
                currentState = State.DIR_NAME;
            }
        }

        if (currentState == State.DIR_NAME) {
            try {
                if (buf.readableBytes() >= fileNameLength) {
                    byte[] fileName = new byte[fileNameLength];
                    buf.readBytes(fileName);
                    currentServerPathGUI = currentServerPathGUI.resolve(new String(fileName, "UTF-8"));
                    if (!Files.exists(currentServerPathGUI)) {
                        Files.createDirectory(currentServerPathGUI);
                    }
                    currentState = State.FILE_TYPE;
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        if (currentState == State.FILE_TYPE) {
            if (buf.readableBytes() > 0) {
                byte b = buf.readByte();
                if (b == Command.IS_DIRECTORY.getByteValue()) {
                    currentState = State.DIR_NAME_LENGTH;
                } else if (b == Command.IS_FILE.getByteValue()) {
                    currentState = State.FILE_NAME_LENGTH;
                    fileReading = true;
                } else if (b == Command.END_DIRECTORY.getByteValue()) {
                    currentServerPathGUI = currentServerPathGUI.getParent();
                    if (currentServerPathGUI.equals(pathBeforeDirReading)) {
                        currentState = State.IDLE;
                        Sender.sendFilesList(currentServerPathGUI, ctx.channel(), Command.SERVER_PATH_CURRENT);
                        directoryReading = false;
                    }
                } else {
                    System.out.println("ERROR: Invalid first byte - " + b);
                    currentState = State.IDLE;
                }
            }
        }

    }

    private void readFile(ByteBuf buf, ChannelHandlerContext ctx) {
        if (currentState == State.FILE_NAME_LENGTH) {
            if (buf.readableBytes() >= 4) {
                fileNameLength = buf.readInt();
                currentState = State.FILE_NAME;
            }
        }

        if (currentState == State.FILE_NAME) {
            if (buf.readableBytes() >= fileNameLength) {
                byte[] fileName = new byte[fileNameLength];
                buf.readBytes(fileName);
                getFilePath(currentServerPathGUI, fileName);
                if (readed == Command.TRANSFER_FILE.getByteValue() || readed == Command.TRANSFER_DIRECTORY.getByteValue()) {
                    deleteFileIfExist(filePath);
                    currentState = State.FILE_LENGTH;
                } else if (!filePath.toFile().exists()) {
                    currentState = State.IDLE;
                    fileReading = false;
                    Sender.sendCommand(ctx.channel(), Command.FILE_DOES_NOT_EXIST);
                } else {
                    currentState = State.IDLE;
                    fileReading = false;
                    if (readed == Command.DELETE_FILE.getByteValue()) {
                        deleteFileIfExist(filePath);
                        Sender.sendFilesList(currentServerPathGUI, ctx.channel(), Command.SERVER_PATH_CURRENT);
                    } else if (readed == Command.DOWNLOAD_FILE.getByteValue()) {
                        Sender.sendCommand(ctx.channel(), Command.TRANSFER_FILE);
                        Sender.sendFile(filePath, ctx.channel(), future -> {
                            if (!future.isSuccess()) {
                                future.cause().printStackTrace();
                                Sender.sendCommand(ctx.channel(), Command.DOWNLOAD_FILE_ERR);
                            }
                        });
                    } else if (readed == Command.DOWNLOAD_DIRECTORY.getByteValue()) {
                        Sender.sendCommand(ctx.channel(), Command.TRANSFER_DIRECTORY);
                        Sender.sendDirectory(filePath, ctx.channel(), null);
                    } else if (readed == Command.SERVER_PATH_DOWN.getByteValue()) {
                        currentServerPathGUI = currentServerPathGUI.resolve(filePath.getFileName());
                        Sender.sendFilesList(currentServerPathGUI, ctx.channel(), Command.SERVER_PATH_DOWN);
                    }

                }
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
                            currentState = State.FILE_TYPE;
                        } else {
                            Sender.sendFilesList(currentServerPathGUI, ctx.channel(), Command.SERVER_PATH_CURRENT);
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
                            fileReading = false;
                            out.close();
                            if (directoryReading) {
                                currentState = State.FILE_TYPE;
                            } else {
                                Sender.sendFilesList(currentServerPathGUI, ctx.channel(), Command.SERVER_PATH_CURRENT);
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

    private void deleteFileIfExist(Path delPath) {
        try {
            if (Files.exists(delPath)) {
                if (Files.isDirectory(delPath)) {
                    FileUtils.deleteDirectory(new File(String.valueOf(delPath)));
                } else {
                    Files.delete(delPath);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void getFilePath(Path path, byte[] fileName) {
        try {
            filePath = path.resolve(new String(fileName, "UTF-8"));
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        ctx.close();
    }
}

