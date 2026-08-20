package com.dsh.mavensearch;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Client for the mvn.coderead.cn API (search JSON + version HTML),
 * plus jar URL construction and download. Tries http first, then https.
 */
public final class CodereadClient {
    private static final String[] BASES = {"http://mvn.coderead.cn", "https://mvn.coderead.cn"};
    private static final String UA = "Mozilla/5.0 (compatible; MavenSearchPlugin/1.0)";

    private CodereadClient() {
    }

    // ---------------- data classes ----------------

    public static final class Artifact {
        public final String groupId;
        public final String artifactId;
        public final String text;
        public final String updated;
        public final String pkg;

        public Artifact(String groupId, String artifactId, String text, String updated, String pkg) {
            this.groupId = groupId;
            this.artifactId = artifactId;
            this.text = text;
            this.updated = updated;
            this.pkg = pkg;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder(artifactId);
            sb.append("    ").append(groupId);
            if (pkg != null && !pkg.isEmpty()) sb.append("    [").append(pkg).append(']');
            if (updated != null && !updated.isEmpty()) sb.append("    ").append(updated);
            return sb.toString();
        }
    }

    public static final class VersionInfo {
        public final String version;
        public final String dataUrl;
        public final String refCount;
        public final String date;
        public final String maven;
        public final String gradle;

        public VersionInfo(String version, String dataUrl, String refCount, String date, String maven, String gradle) {
            this.version = version;
            this.dataUrl = dataUrl;
            this.refCount = refCount;
            this.date = date;
            this.maven = maven;
            this.gradle = gradle;
        }

        @Override
        public String toString() {
            return version + "    引用:" + refCount + "    " + date;
        }
    }

    public static final class VersionDetail {
        public final String groupId;
        public final String artifactId;
        public String description = "";
        public String docs = "";
        public String source = "";
        public final List<VersionInfo> versions = new ArrayList<>();

        public VersionDetail(String groupId, String artifactId) {
            this.groupId = groupId;
            this.artifactId = artifactId;
        }
    }

    // ---------------- HTTP ----------------

    public static String httpGet(String path) throws IOException {
        IOException last = null;
        for (String base : BASES) {
            try {
                return rawGet(base + path);
            } catch (IOException e) {
                last = e;
            }
        }
        throw last;
    }

    private static String rawGet(String url) throws IOException {
        return rawGet(url, 8000, 30000);
    }

    private static String rawGet(String url, int connectMs, int readMs) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(connectMs);
        c.setReadTimeout(readMs);
        c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent", UA);
        int code;
        try {
            code = c.getResponseCode();
        } catch (IOException e) {
            c.disconnect();
            throw e;
        }
        if (code >= 400) {
            c.disconnect();
            throw new IOException("HTTP " + code + " for " + url);
        }
        try (InputStream in = c.getInputStream()) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) bos.write(buf, 0, n);
            return bos.toString(StandardCharsets.UTF_8.name());
        } finally {
            c.disconnect();
        }
    }

    public static void downloadTo(String url, OutputStream out) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(10000);
        c.setReadTimeout(60000);
        c.setRequestProperty("User-Agent", UA);
        int code = c.getResponseCode();
        if (code >= 400) {
            c.disconnect();
            throw new IOException("HTTP " + code);
        }
        try (InputStream in = c.getInputStream()) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
        } finally {
            c.disconnect();
        }
    }

    public static String jarUrl(String groupId, String artifactId, String version) {
        return "https://repo1.maven.org/maven2/"
                + groupId.replace('.', '/') + "/"
                + artifactId + "/" + version + "/"
                + artifactId + "-" + version + ".jar";
    }

    /** 下载候选地址：优先指定仓库（如用户添加的镜像），再 Maven Central 官方 + 阿里云镜像。 */
    public static String[] jarUrls(String preferredBase, String groupId, String artifactId, String version) {
        String path = groupId.replace('.', '/') + "/" + artifactId + "/" + version + "/"
                + artifactId + "-" + version + ".jar";
        List<String> urls = new ArrayList<>();
        if (preferredBase != null && !preferredBase.trim().isEmpty()) {
            urls.add(trimBase(preferredBase) + "/" + path);
        }
        urls.add("https://repo1.maven.org/maven2/" + path);
        urls.add("https://maven.aliyun.com/repository/central/" + path);
        return urls.toArray(new String[0]);
    }

    public static String[] jarUrls(String groupId, String artifactId, String version) {
        return jarUrls(null, groupId, artifactId, version);
    }

    private static String trimBase(String base) {
        String b = base.trim();
        while (b.endsWith("/")) b = b.substring(0, b.length() - 1);
        return b;
    }

    /** 构造统一的 Maven XML 依赖片段。 */
    public static String mavenSnippet(String g, String a, String v) {
        return "<dependency>\n"
                + "    <groupId>" + g + "</groupId>\n"
                + "    <artifactId>" + a + "</artifactId>\n"
                + "    <version>" + v + "</version>\n"
                + "</dependency>";
    }

    /** 构造统一的 Gradle 依赖片段。 */
    public static String gradleSnippet(String g, String a, String v) {
        return "implementation '" + g + ":" + a + ":" + v + "'";
    }

    /** 抓取仓库的 maven-metadata.xml（官方仓库优先，阿里云镜像回退）。 */
    public static String metadataXml(String groupId, String artifactId) throws IOException {
        return metadataXmlAt(new String[]{"https://repo1.maven.org/maven2", "https://maven.aliyun.com/repository/central"},
                groupId, artifactId);
    }

    /** 抓取指定仓库地址的 maven-metadata.xml。 */
    public static String metadataXmlAt(String base, String groupId, String artifactId) throws IOException {
        return metadataXmlAt(new String[]{base}, groupId, artifactId);
    }

    private static String metadataXmlAt(String[] bases, String groupId, String artifactId) throws IOException {
        String path = "/" + groupId.replace('.', '/') + "/" + artifactId + "/maven-metadata.xml";
        IOException last = null;
        for (String base : bases) {
            try {
                return rawGet(trimBase(base) + path);
            } catch (IOException e) {
                last = e;
            }
        }
        throw last;
    }

    /** 通过 maven-metadata.xml 构造版本列表（默认官方+阿里云），最新优先。 */
    public static VersionDetail versionsFromMetadata(String groupId, String artifactId) throws IOException {
        return versionsFromMetadataAt(null, groupId, artifactId);
    }

    /** 通过指定仓库的 maven-metadata.xml 构造版本列表，最新优先。 */
    public static VersionDetail versionsFromMetadataAt(String base, String groupId, String artifactId) throws IOException {
        VersionDetail d = new VersionDetail(groupId, artifactId);
        String xml = (base == null || base.trim().isEmpty())
                ? metadataXml(groupId, artifactId)
                : metadataXmlAt(base, groupId, artifactId);
        List<String> versions = new ArrayList<>();
        Matcher vm = Pattern.compile("<versions>(.*?)</versions>", Pattern.DOTALL).matcher(xml);
        if (vm.find()) {
            Matcher m = Pattern.compile("<version>([^<]+)</version>").matcher(vm.group(1));
            while (m.find()) versions.add(m.group(1).trim());
        }
        LinkedHashSet<String> set = new LinkedHashSet<>(versions);
        List<String> ordered = new ArrayList<>(set);
        java.util.Collections.reverse(ordered);
        String path = groupId.replace('.', '/') + "/" + artifactId;
        for (String v : ordered) {
            d.versions.add(new VersionInfo(v, path + "/" + v, "0", "",
                    mavenSnippet(groupId, artifactId, v), gradleSnippet(groupId, artifactId, v)));
        }
        return d;
    }

    /** 测量一个 URL 的 HTTP 延迟（毫秒）；不可达返回 -1。 */
    public static long measureLatency(String url) {
        long start = System.nanoTime();
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(6000);
            c.setReadTimeout(8000);
            c.setInstanceFollowRedirects(true);
            c.setRequestProperty("User-Agent", UA);
            int code = c.getResponseCode();
            InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
            if (in != null) {
                byte[] b = new byte[64];
                try {
                    in.read(b);
                } catch (Exception ignore) {
                    // ignore
                }
                in.close();
            }
            c.disconnect();
            return (System.nanoTime() - start) / 1_000_000;
        } catch (Exception e) {
            return -1;
        }
    }

    /** 探测一个 URL 是否可达（短超时，仅用于数据源状态展示）。 */
    public static boolean isReachable(String url) {
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(4000);
            c.setReadTimeout(7000);
            c.setInstanceFollowRedirects(true);
            c.setRequestProperty("User-Agent", UA);
            int code = c.getResponseCode();
            c.disconnect();
            return code < 400;
        } catch (Exception e) {
            return false;
        }
    }

    /** 直接抓取一个完整 URL（不带 coderead 基础地址）。 */
    public static String httpGetUrl(String url) throws IOException {
        return rawGet(url);
    }

    /** 直接抓取一个完整 URL，可自定义连接/读取超时（毫秒）。 */
    public static String httpGetUrl(String url, int connectMs, int readMs) throws IOException {
        return rawGet(url, connectMs, readMs);
    }

    // ---------------- parsing ----------------

    @SuppressWarnings("unchecked")
    public static List<Artifact> search(String keyword, boolean cls) throws IOException {
        String path = (cls ? "/search/class?keyword=" : "/search?keyword=") + enc(keyword);
        String json = httpGet(path);
        Object root = MiniJson.parse(json);
        List<Artifact> out = new ArrayList<>();
        if (!(root instanceof Map)) return out;
        Map<String, Object> m = (Map<String, Object>) root;
        if (!Boolean.TRUE.equals(m.get("success"))) return out;
        Object rs = m.get("results");
        if (!(rs instanceof List)) return out;
        for (Object o : (List<Object>) rs) {
            if (!(o instanceof Map)) continue;
            Map<String, Object> r = (Map<String, Object>) o;
            String v = str(r.get("value"));
            if (v.isEmpty()) v = str(r.get("text"));
            int idx = v.lastIndexOf(':');
            String g = idx >= 0 ? v.substring(0, idx) : "";
            String a = idx >= 0 ? v.substring(idx + 1) : v;
            String name = str(r.get("name"));
            String updated = firstMatch(name, "lastTime description'>\\s*([^<]+)");
            String pkg = "";
            if (cls) pkg = firstMatch(name, "package description'>\\s*([^<]+)");
            out.add(new Artifact(g, a, v, updated.trim(), pkg.trim()));
        }
        return out;
    }

    public static VersionDetail versions(String groupId, String artifactId) throws IOException {
        String html = httpGet("/version?groupId=" + enc(groupId) + "&artifactId=" + enc(artifactId));
        VersionDetail d = new VersionDetail(groupId, artifactId);

        d.description = stripTags(firstMatch(html,
                "<div style=\"color: rgba\\(0,0,0,\\.6\\);line-height: 1\\.6;\">([\\s\\S]*?)</div>"));

        Matcher lm = Pattern.compile(
                "href=\"/redirect\\?site=([^\"]+)\"><i class=\"ui icon ([a-z]+)\"></i>([^<]*)</a>")
                .matcher(html);
        while (lm.find()) {
            String site = decode(lm.group(1));
            String label = stripTags(lm.group(3));
            if ("file".equals(lm.group(2)) || label.toLowerCase().contains("doc") || label.toLowerCase().contains("wiki")) {
                d.docs = site;
            } else if ("code".equals(lm.group(2)) || label.contains("源") || label.toLowerCase().contains("github")) {
                d.source = site;
            } else if (d.docs.isEmpty()) {
                d.docs = site;
            } else {
                d.source = site;
            }
        }

        Matcher rm = Pattern.compile(
                "<tr onclick=\"doFold\\(\\$\\(this\\)\\)\">\\s*<td>([^<]*)</td>"
                        + "\\s*<td class=\"right aligned\">\\s*<i class=\"download link grey icon\" data-url=\"([^\"]*)\"[^>]*></i>\\s*</td>"
                        + "\\s*<td class=\"right aligned\">([^<]*)</td>"
                        + "\\s*<td class=\"right aligned\">\\s*([^<]*)</td>\\s*</tr>"
                        + "\\s*<tr class=\"content\"[^>]*>([\\s\\S]*?)</tr>")
                .matcher(html);
        while (rm.find()) {
            String content = rm.group(5);
            d.versions.add(new VersionInfo(
                    rm.group(1).trim(),
                    rm.group(2),
                    stripTags(rm.group(3)),
                    rm.group(4).trim(),
                    dedent(extract(content, "maven")),
                    dedent(extract(content, "gradle"))));
        }
        return d;
    }

    private static String extract(String content, String kind) {
        return firstMatch(content, "data-tab=\"[^\"]*-" + kind + "\"[\\s\\S]*?<textarea[^>]*>([\\s\\S]*?)</textarea>");
    }

    private static String firstMatch(String s, String regex) {
        if (s == null || s.isEmpty()) return "";
        Matcher m = Pattern.compile(regex).matcher(s);
        return m.find() ? m.group(1) : "";
    }

    private static String stripTags(String s) {
        if (s == null) return "";
        return s.replaceAll("<[^>]*>", "")
                .replaceAll("&amp;", "&")
                .replaceAll("&lt;", "<")
                .replaceAll("&gt;", ">")
                .replaceAll("&quot;", "\"")
                .replaceAll("&#39;", "'")
                .replaceAll("&nbsp;", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static String dedent(String s) {
        if (s == null || s.isEmpty()) return "";
        String[] lines = s.split("\n", -1);
        int min = Integer.MAX_VALUE;
        for (String line : lines) {
            if (!line.trim().isEmpty()) min = Math.min(min, leadingSpaces(line));
        }
        if (min == Integer.MAX_VALUE) min = 0;
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            sb.append(line.length() >= min ? line.substring(min) : line).append('\n');
        }
        return sb.toString().trim();
    }

    private static int leadingSpaces(String line) {
        int i = 0;
        while (i < line.length() && line.charAt(i) == ' ') i++;
        return i;
    }

    static String enc(String s) {
        try {
            return URLEncoder.encode(s, StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            return s;
        }
    }

    private static String decode(String s) {
        try {
            return URLDecoder.decode(s, StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            return s;
        }
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }
}
