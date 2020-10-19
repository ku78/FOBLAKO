package ru.zvyagin.vitalya.common;

public enum Command {

    AUTH_OK(1),
    AUTH_ERR(2),
    FILE_DOES_NOT_EXIST(3),
    DELETE_FILE(10),
    DELETE_FILE_ERR(11),
    TRANSFER_FILE(20),
    TRANSFER_FILE_ERR(21),
    TRANSFER_DIRECTORY(22),
    DOWNLOAD_FILE(30),
    DOWNLOAD_DIRECTORY(31),
    DOWNLOAD_FILE_ERR(32),
    SERVER_PATH_DOWN(41),
    SERVER_PATH_UP(42),
    SERVER_PATH_CURRENT(43),
    SERVER_PATH_DOWN_EMPTY(44),
    SERVER_PATH_CURRENT_EMPTY(45),
    IS_FILE(46),
    IS_DIRECTORY(47),
    END_DIRECTORY(48);


    final int value;

    Command(int value) {
        this.value = value;
    }

    public byte getByteValue() {
        return (byte) value;
    }
}
