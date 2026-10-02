import java.sql.*;
import java.util.*;

/** 查询 QuickShop 数据库中指定玩家（Jinx）的全部数据 */
public class JinxQuery {
    static final String UUID = "f7ec5348-e5f3-303f-9e5d-f0dfd5a60801";
    static final String NAME = "jinx";

    public static void main(String[] args) throws Exception {
        String url = "jdbc:h2:file:" + args[0] + ";IFEXISTS=TRUE";
        try (Connection c = args.length > 1
                ? DriverManager.getConnection(url, args[1], args.length > 2 ? args[2] : "")
                : DriverManager.getConnection(url)) {
            List<String> tables = new ArrayList<>();
            System.out.println("=== 表清单 ===");
            try (Statement st = c.createStatement();
                 ResultSet rs = st.executeQuery("SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='PUBLIC' ORDER BY TABLE_NAME")) {
                while (rs.next()) {
                    tables.add(rs.getString(1));
                    System.out.println("  " + rs.getString(1));
                }
            }

            for (String t : tables) {
                List<String> cols = new ArrayList<>();
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA='PUBLIC' AND TABLE_NAME=? ORDER BY ORDINAL_POSITION")) {
                    ps.setString(1, t);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) cols.add(rs.getString(1));
                    }
                }
                System.out.println("\n=== " + t + " [" + String.join(", ", cols) + "]");

                // 找出所有可能存玩家标识的列
                for (String col : cols) {
                    String u = col.toUpperCase();
                    if (!(u.equals("OWNER") || u.equals("BUYER") || u.equals("PLAYER") || u.equals("FROM") ||
                            u.equals("TO") || u.equals("TAGGER") || u.equals("CREATOR") || u.equals("NAME") ||
                            u.equals("LAST_OWNER") || u.equals("UUID"))) continue;
                    try (PreparedStatement ps = c.prepareStatement(
                            "SELECT COUNT(*) FROM \"" + t + "\" WHERE LOWER(CAST(\"" + col + "\" AS VARCHAR)) LIKE ?")) {
                        ps.setString(1, "%" + UUID + "%");
                        try (ResultSet rs = ps.executeQuery()) {
                            int n = rs.next() ? rs.getInt(1) : 0;
                            if (n > 0) System.out.println("    -> " + col + " 含 Jinx UUID: " + n + " 行");
                        }
                    } catch (Exception ignored) {
                    }
                    // 也试试名字
                    try (PreparedStatement ps = c.prepareStatement(
                            "SELECT COUNT(*) FROM \"" + t + "\" WHERE LOWER(CAST(\"" + col + "\" AS VARCHAR)) = ? OR LOWER(CAST(\"" + col + "\" AS VARCHAR)) LIKE ?")) {
                        ps.setString(1, NAME);
                        ps.setString(2, "%\"" + NAME + "\"%");
                        try (ResultSet rs = ps.executeQuery()) {
                            int n = rs.next() ? rs.getInt(1) : 0;
                            if (n > 0) System.out.println("    -> " + col + " 含 Jinx 名字: " + n + " 行");
                        }
                    } catch (Exception ignored) {
                    }
                }

                // 商店表：直接 dump Jinx 的行
                if (t.toUpperCase().contains("SHOP") && cols.stream().anyMatch(x -> x.equalsIgnoreCase("OWNER"))) {
                    try (PreparedStatement ps = c.prepareStatement(
                            "SELECT * FROM \"" + t + "\" WHERE LOWER(CAST(\"OWNER\" AS VARCHAR)) = ?")) {
                        ps.setString(1, UUID);
                        try (ResultSet rs = ps.executeQuery()) {
                            int row = 0;
                            while (rs.next()) {
                                row++;
                                StringBuilder sb = new StringBuilder("    ROW " + row + ": ");
                                ResultSetMetaData md = rs.getMetaData();
                                for (int i = 1; i <= md.getColumnCount(); i++) {
                                    String v = String.valueOf(rs.getObject(i));
                                    if (v.length() > 400) v = v.substring(0, 400) + "…";
                                    sb.append(md.getColumnName(i)).append("=[").append(v.replace("\n", "\\n")).append("] ");
                                }
                                System.out.println(sb);
                            }
                            if (row > 0) System.out.println("    （共 " + row + " 家商店属于 Jinx）");
                        }
                    } catch (Exception e) {
                        System.out.println("    dump 失败: " + e.getMessage());
                    }
                }
            }
        }
    }
}
