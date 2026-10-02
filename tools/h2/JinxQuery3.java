import java.sql.*;

/** 查询 Jinx（正确 UUID）的商店/交易/日志 + 关联商店详情 */
public class JinxQuery3 {
    static final String JINX = "7abaa3a9-882d-355d-a82c-d89b8821ff08";
    static final String SKYCHING = "f7ec5348-e5f3-303f-9e5d-f0dfd5a60801";

    public static void main(String[] args) throws Exception {
        String url = "jdbc:h2:file:" + args[0] + ";IFEXISTS=TRUE";
        try (Connection c = DriverManager.getConnection(url)) {

            System.out.println("=== PLAYERS: Jinx / SKYCHING ===");
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT * FROM PLAYERS WHERE LOWER(CAST(UUID AS VARCHAR)) IN (?, ?)")) {
                ps.setString(1, JINX);
                ps.setString(2, SKYCHING);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) System.out.println("  " + rs.getString(1) + " | " + rs.getString(2) + " | " + rs.getString(3));
                }
            }

            System.out.println("\n=== Jinx 的商店（DATA 表）===");
            dumpShops(c, "SELECT * FROM DATA WHERE LOWER(CAST(OWNER AS VARCHAR)) = ?", JINX);

            System.out.println("\n=== Jinx 的建店/删店/其他日志（LOG_OTHERS，最近 15 条）===");
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT ID, FORMATDATETIME(TIME,'yyyy-MM-dd HH:mm:ss'), TYPE, DATA FROM LOG_OTHERS WHERE CAST(DATA AS VARCHAR) LIKE ? ORDER BY ID DESC LIMIT 15")) {
                ps.setString(1, "%" + JINX + "%");
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String type = rs.getString(3);
                        String data = rs.getString(4);
                        String m = data;
                        int ix = m.indexOf("id: minecraft:");
                        if (ix >= 0) m = m.substring(ix, Math.min(m.length(), ix + 60)).replace("\n", " ");
                        if (m.length() > 160) m = m.substring(0, 160) + "…";
                        System.out.println("  #" + rs.getInt(1) + " " + rs.getString(2) + " " + type.substring(type.lastIndexOf('.') + 1));
                        System.out.println("      " + m);
                    }
                }
            }

            System.out.println("\n=== Jinx 的购买记录（LOG_PURCHASE）===");
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT ID, FORMATDATETIME(TIME,'yyyy-MM-dd HH:mm:ss'), SHOP, TYPE, AMOUNT, MONEY, TAX FROM LOG_PURCHASE WHERE LOWER(CAST(BUYER AS VARCHAR)) = ? ORDER BY ID")) {
                ps.setString(1, JINX);
                try (ResultSet rs = ps.executeQuery()) {
                    int n = 0;
                    while (rs.next()) {
                        n++;
                        System.out.println("  #" + rs.getInt(1) + " " + rs.getString(2) + " 商店=" + rs.getInt(3)
                                + " " + rs.getString(4) + " 数量=" + rs.getInt(5) + " 金额=" + rs.getBigDecimal(6) + " 税=" + rs.getBigDecimal(7));
                    }
                    if (n == 0) System.out.println("  （无购买记录）");
                }
            }

            System.out.println("\n=== SKYCHING 那 8 笔交易涉及的商店（DATA ID 697-702, 701）===");
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT ID, OWNER, ITEM, NAME, TYPE, PRICE, CREATE_TIME FROM DATA WHERE ID IN (697,698,699,700,701,702) ORDER BY ID")) {
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String item = String.valueOf(rs.getString(3));
                        int ix = item.indexOf("id: minecraft:");
                        String mat = ix >= 0 ? item.substring(ix + 4, Math.min(item.length(), ix + 50)).split("\n")[0].trim() : "?";
                        System.out.println("  #" + rs.getInt(1) + " 店主=" + rs.getString(2) + " 物品=" + mat
                                + " 店名=" + rs.getString(4) + " 模式=" + rs.getInt(5) + " 单价=" + rs.getBigDecimal(6)
                                + " 建店=" + rs.getString(7));
                    }
                }
            }

            System.out.println("\n=== 这 6 家商店的 owner UUID 对应玩家名 ===");
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT DISTINCT P.CACHEDNAME, D.OWNER FROM DATA D LEFT JOIN PLAYERS P ON LOWER(CAST(P.UUID AS VARCHAR)) = LOWER(CAST(D.OWNER AS VARCHAR)) WHERE D.ID IN (697,698,699,700,701,702)")) {
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) System.out.println("  " + rs.getString(1) + " (" + rs.getString(2) + ")");
                }
            }
        }
    }

    static void dumpShops(Connection c, String sql, String uuid) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, uuid);
            try (ResultSet rs = ps.executeQuery()) {
                ResultSetMetaData md = rs.getMetaData();
                int rows = 0;
                while (rs.next()) {
                    rows++;
                    System.out.println("  ---------- 商店 " + rows + " ----------");
                    for (int i = 1; i <= md.getColumnCount(); i++) {
                        String name = md.getColumnName(i);
                        Object o = rs.getObject(i);
                        String v = o == null ? "null" : String.valueOf(o);
                        if (v.length() > 260) v = v.substring(0, 260) + "…";
                        System.out.println("    " + name + " = " + v.replace("\n", " | "));
                    }
                }
                System.out.println("  共 " + rows + " 家商店");
            }
        }
    }
}
