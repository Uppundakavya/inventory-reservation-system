package project;

import com.sun.net.httpserver.*;
import java.io.*;
import java.net.InetSocketAddress;
import java.sql.*;
import java.util.UUID;
import java.util.Timer;
import java.util.TimerTask;

public class InventoryServer {

    static final String DB_URL = "jdbc:mysql://localhost:3306/shop";
    static final String USER = "root";
    static final String PASS = "root@2004"; 

    public static void main(String[] args) throws Exception {
        Class.forName("com.mysql.cj.jdbc.Driver");

        HttpServer server = HttpServer.create(new InetSocketAddress(8082), 0);
        server.createContext("/reserve", new ReserveHandler());
        server.createContext("/confirm", new ConfirmHandler());
        server.createContext("/cancel", new CancelHandler());
        server.setExecutor(null);
        server.start();
        
        System.out.println("✅ Java Server running on http://localhost:8082");

        Timer timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() { autoExpireReservations(); }
        }, 0, 60000); 
    }

    static void setCorsHeaders(HttpExchange exchange) {
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");
    }

    static class ReserveHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            setCorsHeaders(exchange);
            if ("OPTIONS".equals(exchange.getRequestMethod())) { exchange.sendResponseHeaders(204, -1); return; }

            String jsonResponse = "{\"success\": false, \"message\": \"Out of stock\"}";

            if ("POST".equals(exchange.getRequestMethod())) {
                try (Connection conn = DriverManager.getConnection(DB_URL, USER, PASS)) {
                    conn.setAutoCommit(false); 
                    String updateStock = "UPDATE Products SET stock = stock - 1 WHERE id = 1 AND stock > 0";
                    try (PreparedStatement stmt = conn.prepareStatement(updateStock)) {
                        if (stmt.executeUpdate() == 1) {
                            String resId = UUID.randomUUID().toString();
                            String insertRes = "INSERT INTO Reservations (id, product_id, status, expires_at) VALUES (?, 1, 'PENDING', DATE_ADD(NOW(), INTERVAL 10 MINUTE))";
                            try (PreparedStatement resStmt = conn.prepareStatement(insertRes)) {
                                resStmt.setString(1, resId);
                                resStmt.executeUpdate();
                            }
                            conn.commit();
                            jsonResponse = "{\"success\": true, \"reservationId\": \"" + resId + "\"}";
                        } else { conn.rollback(); }
                    }
                } catch (Exception e) { e.printStackTrace(); }
            }
            sendResponse(exchange, jsonResponse);
        }
    }

    static class ConfirmHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            setCorsHeaders(exchange);
            if ("OPTIONS".equals(exchange.getRequestMethod())) { exchange.sendResponseHeaders(204, -1); return; }

            String jsonResponse = "{\"success\": false, \"message\": \"Invalid ID\"}";
            String query = exchange.getRequestURI().getQuery();

            // NEW FAILSAFE: Check if the query exists and is not undefined
            if (query != null && query.contains("id=") && !query.contains("undefined")) {
                String resId = query.split("=")[1];

                if ("POST".equals(exchange.getRequestMethod())) {
                    try (Connection conn = DriverManager.getConnection(DB_URL, USER, PASS)) {
                        String sql = "UPDATE Reservations SET status = 'CONFIRMED' WHERE id = ? AND status = 'PENDING'";
                        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                            stmt.setString(1, resId);
                            if (stmt.executeUpdate() == 1) jsonResponse = "{\"success\": true}";
                        }
                    } catch (Exception e) { e.printStackTrace(); }
                }
            }
            sendResponse(exchange, jsonResponse);
        }
    }

    static class CancelHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            setCorsHeaders(exchange);
            if ("OPTIONS".equals(exchange.getRequestMethod())) { exchange.sendResponseHeaders(204, -1); return; }

            String jsonResponse = "{\"success\": false, \"message\": \"Invalid ID\"}";
            String query = exchange.getRequestURI().getQuery();

            // NEW FAILSAFE: Check if the query exists and is not undefined
            if (query != null && query.contains("id=") && !query.contains("undefined")) {
                String resId = query.split("=")[1];

                if ("POST".equals(exchange.getRequestMethod())) {
                    try (Connection conn = DriverManager.getConnection(DB_URL, USER, PASS)) {
                        conn.setAutoCommit(false);
                        String cancelSql = "UPDATE Reservations SET status = 'CANCELLED' WHERE id = ? AND status = 'PENDING'";
                        try (PreparedStatement stmt = conn.prepareStatement(cancelSql)) {
                            stmt.setString(1, resId);
                            if (stmt.executeUpdate() == 1) {
                                String restoreSql = "UPDATE Products SET stock = stock + 1 WHERE id = 1";
                                try (PreparedStatement restoreStmt = conn.prepareStatement(restoreSql)) { restoreStmt.executeUpdate(); }
                                conn.commit();
                                jsonResponse = "{\"success\": true}";
                            }
                        }
                    } catch (Exception e) { e.printStackTrace(); }
                }
            }
            sendResponse(exchange, jsonResponse);
        }
    }

    static void autoExpireReservations() {
        try (Connection conn = DriverManager.getConnection(DB_URL, USER, PASS)) {
            conn.setAutoCommit(false);
            String findSql = "SELECT id, product_id FROM Reservations WHERE status = 'PENDING' AND expires_at <= NOW()";
            try (PreparedStatement findStmt = conn.prepareStatement(findSql); ResultSet rs = findStmt.executeQuery()) {
                while (rs.next()) {
                    String resId = rs.getString("id");
                    int prodId = rs.getInt("product_id");

                    String updateRes = "UPDATE Reservations SET status = 'EXPIRED' WHERE id = ?";
                    try (PreparedStatement uStmt = conn.prepareStatement(updateRes)) {
                        uStmt.setString(1, resId); uStmt.executeUpdate();
                    }

                    String restoreStock = "UPDATE Products SET stock = stock + 1 WHERE id = ?";
                    try (PreparedStatement rStmt = conn.prepareStatement(restoreStock)) {
                        rStmt.setInt(1, prodId); rStmt.executeUpdate();
                    }
                    System.out.println("⚠️ Auto-expired reservation: " + resId);
                }
                conn.commit();
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    static void sendResponse(HttpExchange exchange, String response) throws IOException {
        exchange.sendResponseHeaders(200, response.length());
        try (OutputStream os = exchange.getResponseBody()) { os.write(response.getBytes()); }
    }
}