package com.cx.ai_agent_backend.service;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Service
public class WebSearchService {

    public String search(String query) {
        try {
            String url = "https://www.bing.com/search?q=" + URLEncoder.encode(query, StandardCharsets.UTF_8);
            Document document = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0")
                    .timeout(8000)
                    .get();

            List<String> results = new ArrayList<>();
            for (Element item : document.select("li.b_algo")) {
                String title = item.select("h2").text();
                String link = item.select("h2 a").attr("href");
                String snippet = item.select(".b_caption p").text();
                if (!title.isBlank()) {
                    results.add("- " + title + "\n  " + snippet + "\n  " + link);
                }
                if (results.size() >= 5) {
                    break;
                }
            }

            if (results.isEmpty()) {
                return "没有检索到可用的网页摘要。";
            }
            return String.join("\n", results);
        } catch (Exception e) {
            return "联网搜索失败：" + e.getMessage();
        }
    }
}
