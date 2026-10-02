import java.sql.*;

/** 精确查询 Jinx 的商店、交易、玩家记录 */
public class JinxQuery2 {
    static final String UUID = "f7ec5348-e5f3-303f-9e5d-f0dfd5a60801";

    public static void main(String[] args) throws Exception {
        String url = "jdbc:h2:file:" + args[0] + ";IFEXISTS=TRUE";
        try (Connection c = DriverManager.getConnection(url)) {

            // 1) 玩家记录
            System.out.println("=== PLAYERS 表中的 Jinx ===");
            try (PreparedStatement ps = c.prepareStatement("SELECT * FROM PLAYERS WHERE LOWER(CAST(UUID AS VARCHAR)) = ?")) {
                ps.setString(1, UUID);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) System.out.println("  UUID=" + rs.getString(1) + " LOCALE=" + rs.getString(2) + " NAME=" + rs.getString(3));
                }
            }

            // 2) Jinx 的商店（DATA 表）
            System.out.println("\n=== Jinx 的商店（DATA 表，OWNER=" + UUID + "）===");
            try (PreparedStatement ps = c.prepareStatement("SELECT * FROM DATA WHERE LOWER(CAST(OWNER AS VARCHAR)) = ?")) {
                ps.setString(1, UUID);
                try (ResultSet rs = ps.executeQuery()) {
                    ResultSetMetaData md = rs.getMetaData();
                    int rows = 0;
                    while (rs.next()) {
                        rows++;
                        System.out.println("  ---------- 商店 #" + rows + " ----------");
                        for (int i = 1; i <= md.getColumnCount(); i++) {
                            String name = md.getColumnName(i);
                            Object o = rs.getObject(i);
                            String v = o == null ? "null" : String.valueOf(o);
                            if (v.length() > 300) v = v.substring(0, 300) + "…";
                            System.out.println("    " + name + " = " + v.replace("\n", " | "));
                        }
                    }
                    System.out.println("  共 " + rows + " 家商店");
                }
            }

            // 3) Jinx 的交易（LOG_PURCHASE）
            System.out.println("\n=== Jinx 的交易记录（LOG_PURCHASE）===");
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT ID, FORMATDATETIME(TIME,'yyyy-MM-dd HH:mm:ss'), SHOP, TYPE, AMOUNT, MONEY, TAX FROM LOG_PURCHASE WHERE LOWER(CAST(BUYER AS VARCHAR)) = ? ORDER BY ID")) {
                ps.setString(1, UUID);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        System.out.println("  #" + rs.getInt(1) + " " + rs.getString(2) + " 商店=" + rs.getInt(3)
                                + " 类型=" + rs.getString(4) + " 数量=" + rs.getInt(5)
                                + " 金额=" + rs.getBigDecimal(6) + " 税=" + rs.getBigDecimal(7));
                    }
                }
            }

            // 4) Jinx 删/建店日志（LOG_OTHERS 里与他相关的）
            System.out.println("\n=== LOG_OTHERS 中 Jinx 相关（最近 10 条）===");
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT ID, FORMATDATETIME(TIME,'yyyy-MM-dd HH:mm:ss'), TYPE, DATA FROM LOG_OTHERS WHERE CAST(DATA AS VARCHAR) LIKE ? ORDER BY ID DESC LIMIT 10")) {
                ps.setString(1, "%" + UUID + "%");
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String type = rs.getString(3);
                        String data = rs.getString(4);
                        if (data.length() > 220) data = data.substring(0, 220) + "…";
                        System.out.println("  #" + rs.getInt(1) + " " + rs.getString(2) + " " + type.substring(type.lastIndexOf('.') + 1));
                        System.out.println("      " + data.replace("\n", " | "));
                    }
                }
            }
        }
    }
}
