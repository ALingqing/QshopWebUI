package com.qshop.webui.http;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/** HTTP/1.1 请求解析器（手写，零依赖） */
public final class HttpParser {

    public static final int MAX_HEADER_BYTES = 64 * 1024;
    public static final int MAX_HEADER_COUNT = 128;

    /** 400 错误信号 */
    public static final class BadRequest extends IOException {
        public BadRequest(String msg) {
            super(msg);
        }
    }

    private HttpParser() {
    }

    /**
     * 从流中解析一个请求。
     *
     * @return null 表示连接正常结束（EOF）
     */
    public static HttpRequest parse(InputStream in, int maxBodyBytes) throws IOException {
        // 容忍前导空行
        String firstLine = null;
        for (int i = 0; i < 8; i++) {
            firstLine = readLine(in, 16384);
            if (firstLine == null) return null; // EOF
            if (!firstLine.isEmpty()) break;
            firstLine = null;
        }
        if (firstLine == null) throw new BadRequest("空请求");

        String[] parts = firstLine.split(" ");
        if (parts.length < 3) throw new BadRequest("请求行格式错误");
        HttpRequest req = new HttpRequest();
        req.method = parts[0].toUpperCase();
        req.target = parts[1];
        if (req.target.startsWith("http://") || req.target.startsWith("https://")) {
            // 绝对 URI：截出 path 部分
            int idx = req.target.indexOf('/', req.target.indexOf("//") + 2);
            req.target = idx >= 0 ? req.target.substring(idx) : "/";
        }
        String httpVersion = parts[2];

        // headers
        int headerBytes = firstLine.length();
        while (true) {
            String line = readLine(in, 16384);
            if (line == null) throw new BadRequest("头不完整");
            headerBytes += line.length() + 2;
            if (headerBytes > MAX_HEADER_BYTES) throw new BadRequest("请求头过大");
            if (line.isEmpty()) break;
            if (req.headers.size() >= MAX_HEADER_COUNT) throw new BadRequest("请求头过多");
            int colon = line.indexOf(':');
            if (colon <= 0) continue;
            String name = line.substring(0, colon).trim().toLowerCase(java.util.Locale.ROOT);
            String value = line.substring(colon + 1).trim();
            // 同名 header 以逗号合并（cookie 等除外，保留首个即可）
            req.headers.merge(name, value, (a, b) -> a + ", " + b);
        }

        // keep-alive
        String conn = req.header("connection");
        if (httpVersion.equalsIgnoreCase("HTTP/1.0")) {
            req.keepAlive = conn != null && conn.toLowerCase().contains("keep-alive");
        } else {
            req.keepAlive = conn == null || !conn.toLowerCase().contains("close");
        }

        // body
        String te = req.header("transfer-encoding");
        if (te != null && te.toLowerCase().contains("chunked")) {
            req.body = readChunked(in, maxBodyBytes);
        } else {
            long len = -1;
            String cl = req.header("content-length");
            if (cl != null) {
                try {
                    len = Long.parseLong(cl.trim());
                } catch (NumberFormatException e) {
                    throw new BadRequest("Content-Length 无效");
                }
            }
            if (len > maxBodyBytes) throw new BadRequest("请求体过大");
            if (len > 0) {
                req.body = new byte[(int) len];
                int off = 0;
                while (off < len) {
                    int r = in.read(req.body, off, (int) (len - off));
                    if (r < 0) throw new BadRequest("请求体不完整");
                    off += r;
                }
            }
        }

        // 拆分 path / query
        int q = req.target.indexOf('?');
        String rawPath = q >= 0 ? req.target.substring(0, q) : req.target;
        req.rawQuery = q >= 0 ? req.target.substring(q + 1) : null;
        req.path = urlDecodePath(rawPath);

        return req;
    }

    private static byte[] readChunked(InputStream in, int maxBodyBytes) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        while (true) {
            String sizeLine = readLine(in, 8192);
            if (sizeLine == null) throw new BadRequest("chunked 不完整");
            int semi = sizeLine.indexOf(';');
            if (semi >= 0) sizeLine = sizeLine.substring(0, semi);
            int size;
            try {
                size = Integer.parseInt(sizeLine.trim(), 16);
            } catch (NumberFormatException e) {
                throw new BadRequest("chunk 大小无效");
            }
            if (size == 0) {
                // 尾部 headers，读到空行
                while (true) {
                    String l = readLine(in, 8192);
                    if (l == null || l.isEmpty()) break;
                }
                break;
            }
            if (out.size() + size > maxBodyBytes) throw new BadRequest("请求体过大");
            byte[] chunk = new byte[size];
            int off = 0;
            while (off < size) {
                int r = in.read(chunk, off, size - off);
                if (r < 0) throw new BadRequest("chunk 不完整");
                off += r;
            }
            out.write(chunk);
            readLine(in, 16); // 丢弃 chunk 末尾 CRLF
        }
        return out.toByteArray();
    }

    /** 读一行（以 LF 结尾）；EOF 时返回 null */
    private static String readLine(InputStream in, int limit) throws IOException {
        StringBuilder sb = new StringBuilder(64);
        int c;
        boolean any = false;
        while ((c = in.read()) >= 0) {
            any = true;
            if (c == '\n') {
                int len = sb.length();
                if (len > 0 && sb.charAt(len - 1) == '\r') sb.setLength(len - 1);
                return sb.toString();
            }
            sb.append((char) c);
            if (sb.length() > limit) throw new BadRequest("行过长");
        }
        if (!any) return null; // 干净 EOF
        return sb.toString();
    }

    private static String urlDecodePath(String path) {
        try {
            // 路径解码（保留 / 结构）
            return java.net.URLDecoder.decode(path, "UTF-8");
        } catch (Exception e) {
            return path;
        }
    }

    /** 向前缀流：先消费预读字节，再读底层流 */
    public static final class PrefixInputStream extends InputStream {
        private final byte[] prefix;
        private int pos;
        private final InputStream in;

        public PrefixInputStream(byte[] prefix, InputStream in) {
            this.prefix = prefix == null ? new byte[0] : prefix;
            this.in = in;
        }

        @Override
        public int read() throws IOException {
            if (pos < prefix.length) return prefix[pos++] & 0xFF;
            return in.read();
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (len <= 0) return 0;
            if (pos < prefix.length) {
                int n = Math.min(len, prefix.length - pos);
                System.arraycopy(prefix, pos, b, off, n);
                pos += n;
                return n;
            }
            return in.read(b, off, len);
        }

        @Override
        public int available() throws IOException {
            return (prefix.length - pos) + in.available();
        }
    }

    /** 写响应 */
    public static void writeResponse(OutputStream out, HttpRequest req, HttpResponse resp, boolean head)
            throws IOException {
        StringBuilder sb = new StringBuilder(256);
        sb.append("HTTP/1.1 ").append(resp.status).append(' ').append(reason(resp.status)).append("\r\n");
        sb.append("Server: QShopWebUI\r\n");
        sb.append("Date: ").append(java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME
                .format(java.time.ZonedDateTime.now(java.time.ZoneOffset.UTC))).append("\r\n");
        sb.append("Content-Type: ").append(resp.contentType).append("\r\n");
        sb.append("Content-Length: ").append(resp.body.length).append("\r\n");
        sb.append("Access-Control-Allow-Origin: *\r\n");
        sb.append("Access-Control-Allow-Headers: *\r\n");
        sb.append("Access-Control-Allow-Methods: GET, POST, PUT, DELETE, OPTIONS, HEAD\r\n");
        boolean close = resp.close || req == null || !req.keepAlive;
        sb.append("Connection: ").append(close ? "close" : "keep-alive").append("\r\n");
        for (java.util.Map.Entry<String, String> e : resp.headers.entrySet()) {
            sb.append(e.getKey()).append(": ").append(e.getValue()).append("\r\n");
        }
        sb.append("\r\n");
        out.write(sb.toString().getBytes(StandardCharsets.US_ASCII));
        if (!head && resp.body.length > 0) {
            out.write(resp.body);
        }
        out.flush();
    }

    public static String reason(int status) {
        switch (status) {
            case 200: return "OK";
            case 201: return "Created";
            case 204: return "No Content";
            case 301: return "Moved Permanently";
            case 302: return "Found";
            case 304: return "Not Modified";
            case 400: return "Bad Request";
            case 401: return "Unauthorized";
            case 403: return "Forbidden";
            case 404: return "Not Found";
            case 405: return "Method Not Allowed";
            case 409: return "Conflict";
            case 413: return "Payload Too Large";
            case 415: return "Unsupported Media Type";
            case 429: return "Too Many Requests";
            case 500: return "Internal Server Error";
            case 503: return "Service Unavailable";
            default: return "OK";
        }
    }
}
