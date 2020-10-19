package ru.zvyagin.vitalya.server;


import java.sql.*;
import java.util.concurrent.*;

public class DataBaseAuthService {
    private static Connection conn;
    private static Statement stmt;
    private ExecutorService ex = Executors.newFixedThreadPool(1);


    public void stop() {
        ex.shutdown();
    }


    public String getDirectoryByLoginPass(String loginEntry, String passEntry)  {
        String directory = null;
        Future<String> future = ex.submit(new DirectoryOfUser(loginEntry, passEntry));
        try {
            directory = future.get();
        } catch (ExecutionException | InterruptedException e) {
            e.printStackTrace();
        }
        return directory;
    }



    private static void connection() throws ClassNotFoundException, SQLException {
        Class.forName("org.sqlite.JDBC");
        conn = DriverManager.getConnection("jdbc:sqlite:server\\mainDB.db");
        stmt = conn.createStatement();
    }

    private static void disconnect() throws SQLException {
        conn.close();
    }


    private static class DirectoryOfUser implements Callable<String>  {
        String loginEntry;
        String passEntry;

        DirectoryOfUser(String loginEntry, String passEntry) {
            this.loginEntry = loginEntry;
            this.passEntry = passEntry;
        }

        @Override
        public String call() {
            String directory = null;
            try {
                connection();
                ResultSet rs = stmt.executeQuery("SELECT * FROM users_info " +
                        "WHERE login = '" + loginEntry + "' AND password = '" + passEntry + "' LIMIT 1");
                while (rs.next()) {
                    directory = rs.getString("login");
                    rs.close();
                }
                disconnect();
            } catch (SQLException | ClassNotFoundException e) {
                e.printStackTrace();

            }
            return directory;
        }
    };





}
