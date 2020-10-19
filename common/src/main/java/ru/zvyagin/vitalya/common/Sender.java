package ru.zvyagin.vitalya.common;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

public class Sender {
    public static void sendFile(Path path, Channel channel, ChannelFutureListener finishListener) {
        if (!path.toFile().exists()) {
            System.out.println("Данный файл не существует");
            return;
        }
        sendFileName(path, channel);
        ByteBuf buf;
        try {
            FileRegion region = new DefaultFileRegion(path.toFile(), 0, Files.size(path));
            buf = ByteBufAllocator.DEFAULT.directBuffer(8);
            buf.writeLong(Files.size(path));
            channel.writeAndFlush(buf);

            ChannelFuture transferOperationFuture = channel.writeAndFlush(region);
            if (finishListener != null) {
                transferOperationFuture.addListener(finishListener);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void sendFileName(Path path, Channel channel) {
        ByteBuf buf;
        byte[] fileNameBytes = path.getFileName().toString().getBytes(StandardCharsets.UTF_8);
        buf = ByteBufAllocator.DEFAULT.directBuffer(4 + fileNameBytes.length);
        buf.writeInt(fileNameBytes.length);
        buf.writeBytes(fileNameBytes);
        channel.writeAndFlush(buf);
    }

    public static void sendCommand(Channel channel, Command command) {
        ByteBuf buf = ByteBufAllocator.DEFAULT.directBuffer(1);
        buf.writeByte(command.getByteValue());
        channel.writeAndFlush(buf);
    }

    public static void sendFilesListRequest(Path path, Channel channel, Command command) {
        sendCommand(channel, command);
        sendFileName(path, channel);
    }

    public static void sendFilesList(Path path, Channel channel, Command command) {
        List<Path> list;
        try {
            list = Files.list(path).
                    filter(Files::exists).collect(Collectors.toList());
            if (list.isEmpty() && command == Command.SERVER_PATH_DOWN) {
                sendCommand(channel, Command.SERVER_PATH_DOWN_EMPTY);
                return;
            } else if (list.isEmpty() && command == Command.SERVER_PATH_CURRENT) {
                sendCommand(channel, Command.SERVER_PATH_CURRENT_EMPTY);
                return;
            }
            sendCommand(channel, command);

            ByteBuf buf;
            buf = ByteBufAllocator.DEFAULT.directBuffer(4);
            buf.writeInt(list.size());
            channel.writeAndFlush(buf);

            for (Path p : list) {
                if (Files.exists(p)) {
                    sendFileName(p, channel);
                    buf = ByteBufAllocator.DEFAULT.directBuffer(8);
                    FileInfo fileInfo = new FileInfo(p);
                    if (Files.isDirectory(p)) {
                        sendCommand(channel, Command.IS_DIRECTORY);
                    } else {
                        sendCommand(channel, Command.IS_FILE);
                    }
                    buf.writeLong(fileInfo.getSize());
                    channel.writeAndFlush(buf);

                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void sendDirectory(Path path, Channel channel, ChannelFutureListener finishListener) {
        sendFileName(path, channel);
        List<Path> list;
        try {
            list = Files.list(path).filter(Files::exists).collect(Collectors.toList());
            for (Path p : list) {
                if (Files.isDirectory(p)) {
                    sendCommand(channel, Command.IS_DIRECTORY);
                    sendDirectory(p, channel, null);

                } else {
                    sendCommand(channel, Command.IS_FILE);
                    sendFile(p, channel, null);
                }
            }
            sendCommand(channel, Command.END_DIRECTORY);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void sendAuthInfo(Channel channel, String login, String password) {
        byte[] loginBytes = login.getBytes();
        byte[] passwordBytes = password.getBytes();
        int bufLength = 4 + loginBytes.length + 4 + passwordBytes.length;
        ByteBuf buf = ByteBufAllocator.DEFAULT.directBuffer(bufLength);
        buf.writeInt(login.length());
        buf.writeBytes(loginBytes);
        buf.writeInt(password.length());
        buf.writeBytes(passwordBytes);
        channel.writeAndFlush(buf);

    }
}