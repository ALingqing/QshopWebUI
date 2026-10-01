package com.qshop.webui.http;

/** 首包嗅探：判断一个新连接是 HTTP 还是 Minecraft 协议 */
public final class HttpSniff {

    private static final String[] METHODS = {
            "GET ", "POST ", "HEAD ", "PUT ", "DELETE ", "OPTIONS ",
            "PATCH ", "CONNECT ", "TRACE ", "PRI "
    };

    private HttpSniff() {
    }

    /**
     * @param b 已读字节
     * @param n 已读长度
     * @return TRUE=HTTP, FALSE=MC/其他, null=还不能确定（继续读）
     */
    public static Boolean check(byte[] b, int n) {
        boolean anyMatch = false;
        for (String m : METHODS) {
            int cmp = Math.min(n, m.length());
            boolean ok = true;
            for (int i = 0; i < cmp; i++) {
                if (b[i] != (byte) m.charAt(i)) {
                    ok = false;
                    break;
                }
            }
            if (ok) {
                if (n >= m.length()) return Boolean.TRUE;
                anyMatch = true;
            }
        }
        if (n == 0) return null;
        if (!anyMatch) return Boolean.FALSE;
        if (n >= 8) return Boolean.TRUE;
        return null;
    }
}
