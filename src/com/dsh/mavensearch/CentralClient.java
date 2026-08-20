package com.dsh.mavensearch;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Maven Central data source: search via search.maven.org (Solr), versions via
 * the standard maven-metadata.xml on repo1 (with the Aliyun mirror as fallback).
 */
public final class CentralClient {
    private CentralClient() {
    }

    public static List<CodereadClient.Artifact> search(String keyword) throws IOException {
        String url = "https://search.maven.org/solrsearch/select?q="
                + CodereadClient.enc(keyword) + "&rows=20&wt=json";
        // search.maven.org 在当前网络可能较慢/不稳定，用较短超时快速失败
        String json = CodereadClient.httpGetUrl(url, 8000, 12000);
        Object root = MiniJson.parse(json);
        List<CodereadClient.Artifact> out = new ArrayList<>();
        if (!(root instanceof Map)) return out;
        Map<String, Object> m = (Map<String, Object>) root;
        Object resp = m.get("response");
        if (!(resp instanceof Map)) return out;
        Object docs = ((Map<String, Object>) resp).get("docs");
        if (!(docs instanceof List)) return out;
        for (Object o : (List<Object>) docs) {
            if (!(o instanceof Map)) continue;
            Map<String, Object> doc = (Map<String, Object>) o;
            String g = str(doc.get("g"));
            String a = str(doc.get("a"));
            String id = str(doc.get("id"));
            if (id.isEmpty() && !g.isEmpty() && !a.isEmpty()) id = g + ":" + a;
            if (id.isEmpty()) continue;
            int idx = id.lastIndexOf(':');
            if (g.isEmpty()) g = idx >= 0 ? id.substring(0, idx) : "";
            if (a.isEmpty()) a = idx >= 0 ? id.substring(idx + 1) : id;
            String latest = str(doc.get("latestVersion"));
            // 右侧列显示最新版本号（版本号自动更新）
            String updated = latest.isEmpty() ? "" : "最新 " + latest;
            out.add(new CodereadClient.Artifact(g, a, id, updated, ""));
        }
        return out;
    }

    public static CodereadClient.VersionDetail versions(String groupId, String artifactId) throws IOException {
        CodereadClient.VersionDetail d = new CodereadClient.VersionDetail(groupId, artifactId);
        String xml = CodereadClient.metadataXml(groupId, artifactId);
        List<String> versions = new ArrayList<>();
        Matcher vm = Pattern.compile("<versions>(.*?)</versions>", Pattern.DOTALL).matcher(xml);
        if (vm.find()) {
            Matcher m = Pattern.compile("<version>([^<]+)</version>").matcher(vm.group(1));
            while (m.find()) versions.add(m.group(1).trim());
        }
        // 去重并按最新优先
        LinkedHashSet<String> set = new LinkedHashSet<>(versions);
        List<String> ordered = new ArrayList<>(set);
        java.util.Collections.reverse(ordered);
        String path = groupId.replace('.', '/') + "/" + artifactId;
        for (String v : ordered) {
            d.versions.add(new CodereadClient.VersionInfo(
                    v,
                    path + "/" + v,
                    "0",
                    "",
                    CodereadClient.mavenSnippet(groupId, artifactId, v),
                    CodereadClient.gradleSnippet(groupId, artifactId, v)));
        }
        return d;
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }
}
