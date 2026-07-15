package org.example.service;

import org.example.config.FileUploadConfig;
import org.example.config.FullTextDownloadProperties;
import org.example.entity.Paper;
import org.example.enums.FullTextSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class FullTextDownloadService {
    private static final Logger logger = LoggerFactory.getLogger(FullTextDownloadService.class);
    private static final int MAX_LANDING_PAGE_BYTES = 2 * 1024 * 1024;
    private static final List<Pattern> PDF_LINK_PATTERNS = List.of(
            Pattern.compile("(?is)<meta[^>]+name\\s*=\\s*['\\\"]citation_pdf_url['\\\"][^>]+content\\s*=\\s*['\\\"]([^'\\\"]+)"),
            Pattern.compile("(?is)<meta[^>]+content\\s*=\\s*['\\\"]([^'\\\"]+)['\\\"][^>]+name\\s*=\\s*['\\\"]citation_pdf_url['\\\"]"),
            Pattern.compile("(?is)<link[^>]+type\\s*=\\s*['\\\"]application/pdf['\\\"][^>]+href\\s*=\\s*['\\\"]([^'\\\"]+)"),
            Pattern.compile("(?is)<link[^>]+href\\s*=\\s*['\\\"]([^'\\\"]+)['\\\"][^>]+type\\s*=\\s*['\\\"]application/pdf['\\\"]"),
            Pattern.compile("(?is)href\\s*=\\s*['\\\"]([^'\\\"]+\\.pdf(?:\\?[^'\\\"]*)?)['\\\"]")
    );

    private final FileUploadConfig uploadConfig;
    private final FullTextDownloadProperties properties;
    private final HttpClient client;

    public FullTextDownloadService(FileUploadConfig uploadConfig, FullTextDownloadProperties properties) {
        this.uploadConfig = uploadConfig;
        this.properties = properties;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(properties.getConnectTimeoutSeconds()))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    public boolean canDownload(Paper paper) {
        return paper != null && (!blank(paper.getPdfUrl()) || !blank(paper.getArxivId())
                || !blank(paper.getLandingUrl()) || !blank(paper.getDoi()));
    }

    /**
     * 按“明确 PDF → arXiv →公开落地页 → DOI 落地页”的顺序尝试。
     * 落地页只解析公开声明的 PDF 地址，不携带 Cookie/登录态，也不绕过付费墙。
     */
    public DownloadResult download(Paper paper) throws Exception {
        if (!canDownload(paper)) throw new FullTextUnavailableException("没有可尝试的开放全文地址");
        Exception lastError = null;
        for (DownloadCandidate candidate : candidates(paper)) {
            try {
                return downloadCandidate(paper, candidate);
            } catch (Exception e) {
                lastError = e;
                logger.info("开放全文候选地址不可用: source={}, url={}, reason={}",
                        candidate.source(), candidate.uri(), concise(e));
            }
        }
        String reason = lastError == null ? "没有发现开放 PDF" : concise(lastError);
        throw new FullTextUnavailableException("自动获取开放全文失败：" + reason);
    }

    private DownloadResult downloadCandidate(Paper paper, DownloadCandidate candidate) throws Exception {
        HttpResponse<InputStream> response = executeWithRedirects(candidate.uri(), 0);
        int status = response.statusCode();
        if (status < 200 || status >= 300) {
            response.body().close();
            throw new IllegalStateException("HTTP " + status);
        }
        String contentType = response.headers().firstValue("Content-Type").orElse("").toLowerCase(Locale.ROOT);
        if (contentType.contains("text/html") || contentType.contains("application/xhtml")) {
            String html;
            try (InputStream body = response.body()) {
                html = readLimitedText(body, MAX_LANDING_PAGE_BYTES);
            }
            URI pdfUri = discoverPdfUri(response.uri(), html);
            if (pdfUri == null) throw new IllegalStateException("落地页没有声明可直接访问的 PDF");
            return downloadDirectPdf(paper, pdfUri, candidate.source());
        }
        return savePdfResponse(paper, response, candidate.source());
    }

    private DownloadResult downloadDirectPdf(Paper paper, URI uri, FullTextSource preferredSource) throws Exception {
        HttpResponse<InputStream> response = executeWithRedirects(uri, 0);
        int status = response.statusCode();
        if (status < 200 || status >= 300) {
            response.body().close();
            throw new IllegalStateException("PDF 地址返回 HTTP " + status);
        }
        String contentType = response.headers().firstValue("Content-Type").orElse("").toLowerCase(Locale.ROOT);
        if (contentType.contains("text/html") || contentType.contains("application/xhtml")) {
            response.body().close();
            throw new IllegalStateException("PDF 地址返回了 HTML，可能需要登录或订阅");
        }
        return savePdfResponse(paper, response, preferredSource);
    }

    private DownloadResult savePdfResponse(Paper paper, HttpResponse<InputStream> response,
                                             FullTextSource preferredSource) throws Exception {
        long maxBytes = properties.getMaxSizeMb() * 1024L * 1024L;
        long declaredLength = response.headers().firstValueAsLong("Content-Length").orElse(-1);
        if (declaredLength > maxBytes) {
            response.body().close();
            throw new IllegalStateException("PDF 超过允许大小 " + properties.getMaxSizeMb() + "MB");
        }

        Path dir = Path.of(uploadConfig.getPath(), "papers", String.valueOf(paper.getId())).normalize();
        Files.createDirectories(dir);
        Path temp = Files.createTempFile(dir, "download-", ".tmp");
        long total = 0;
        try (InputStream in = response.body(); var out = Files.newOutputStream(temp)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) >= 0) {
                total += read;
                if (total > maxBytes) throw new IllegalStateException("PDF 下载超过允许大小 " + properties.getMaxSizeMb() + "MB");
                out.write(buffer, 0, read);
            }
        } catch (Exception e) {
            Files.deleteIfExists(temp);
            throw e;
        }
        if (!hasPdfMagic(temp)) {
            Files.deleteIfExists(temp);
            throw new IllegalStateException("下载内容不是有效 PDF，可能是 HTML 错误页或登录页面");
        }
        Path target = dir.resolve("auto-fulltext.pdf").normalize();
        try {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        }
        return new DownloadResult(target, detectSource(response.uri(), preferredSource), total);
    }

    private List<DownloadCandidate> candidates(Paper paper) {
        Map<String, DownloadCandidate> unique = new LinkedHashMap<>();
        if (!blank(paper.getArxivId())) {
            addCandidate(unique, "https://arxiv.org/pdf/" + paper.getArxivId().trim() + ".pdf", FullTextSource.ARXIV);
        }
        addCandidate(unique, paper.getPdfUrl(), sourceForDirectPdf(paper));
        addCandidate(unique, paper.getLandingUrl(), FullTextSource.OPEN_ACCESS_URL);
        if (!blank(paper.getDoi())) {
            String doi = paper.getDoi().trim().replaceFirst("(?i)^https?://(dx\\.)?doi\\.org/", "");
            addCandidate(unique, "https://doi.org/" + doi, FullTextSource.OPEN_ACCESS_URL);
        }
        return new ArrayList<>(unique.values());
    }

    private void addCandidate(Map<String, DownloadCandidate> output, String value, FullTextSource source) {
        if (blank(value)) return;
        try {
            URI uri = URI.create(value.trim());
            output.putIfAbsent(uri.normalize().toString(), new DownloadCandidate(uri, source));
        } catch (Exception ignored) { }
    }

    private FullTextSource sourceForDirectPdf(Paper paper) {
        String pdf = paper.getPdfUrl() == null ? "" : paper.getPdfUrl().toLowerCase(Locale.ROOT);
        if (pdf.contains("arxiv.org")) return FullTextSource.ARXIV;
        if (!blank(paper.getOpenalexId())) return FullTextSource.OPENALEX;
        return FullTextSource.OPEN_ACCESS_URL;
    }

    private URI discoverPdfUri(URI base, String html) {
        if (html == null || html.isBlank()) return null;
        for (Pattern pattern : PDF_LINK_PATTERNS) {
            Matcher matcher = pattern.matcher(html);
            if (!matcher.find()) continue;
            String href = decodeHtmlAttribute(matcher.group(1));
            try {
                URI resolved = base.resolve(href);
                validatePublicHttpUri(resolved);
                return resolved;
            } catch (Exception ignored) { }
        }
        return null;
    }

    private HttpResponse<InputStream> executeWithRedirects(URI uri, int depth) throws Exception {
        if (depth > properties.getMaxRedirects()) throw new IllegalStateException("PDF 重定向次数过多");
        validatePublicHttpUri(uri);
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(properties.getReadTimeoutSeconds()))
                .header("User-Agent", "ResearchLens/1.0 (open-access research assistant)")
                .header("Accept", "application/pdf,text/html;q=0.8,application/octet-stream;q=0.7,*/*;q=0.2")
                .GET().build();
        HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        int status = response.statusCode();
        if (status >= 300 && status < 400) {
            response.body().close();
            String location = response.headers().firstValue("Location")
                    .orElseThrow(() -> new IllegalStateException("重定向缺少 Location"));
            return executeWithRedirects(uri.resolve(location), depth + 1);
        }
        validatePublicHttpUri(response.uri());
        return response;
    }

    private void validatePublicHttpUri(URI uri) throws Exception {
        if (uri == null || uri.getHost() == null) throw new IllegalArgumentException("非法全文地址");
        String scheme = uri.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            throw new IllegalArgumentException("只允许 HTTP/HTTPS 地址");
        }
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        if (host.equals("localhost") || host.endsWith(".local")) throw new IllegalArgumentException("不允许访问本地地址");
        for (InetAddress address : InetAddress.getAllByName(host)) {
            if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                    || address.isSiteLocalAddress() || address.isMulticastAddress()) {
                throw new IllegalArgumentException("不允许访问内网或本地地址");
            }
        }
    }

    private boolean hasPdfMagic(Path file) throws Exception {
        if (!Files.isRegularFile(file) || Files.size(file) < 5) return false;
        try (InputStream in = Files.newInputStream(file)) {
            byte[] header = in.readNBytes(5);
            return header.length == 5 && header[0] == '%' && header[1] == 'P'
                    && header[2] == 'D' && header[3] == 'F' && header[4] == '-';
        }
    }

    private String readLimitedText(InputStream input, int maxBytes) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream(Math.min(maxBytes, 32 * 1024));
        byte[] buffer = new byte[8192];
        int total = 0, read;
        while ((read = input.read(buffer)) >= 0) {
            total += read;
            if (total > maxBytes) throw new IllegalStateException("论文落地页过大，已停止解析");
            out.write(buffer, 0, read);
        }
        return out.toString(StandardCharsets.UTF_8);
    }

    private String decodeHtmlAttribute(String value) {
        return value == null ? "" : value.replace("&amp;", "&").replace("&#38;", "&")
                .replace("&quot;", "\"").trim();
    }

    private FullTextSource detectSource(URI finalUri, FullTextSource preferredSource) {
        String host = finalUri == null || finalUri.getHost() == null ? "" : finalUri.getHost().toLowerCase(Locale.ROOT);
        if (host.contains("arxiv.org")) return FullTextSource.ARXIV;
        return preferredSource == null ? FullTextSource.OPEN_ACCESS_URL : preferredSource;
    }

    private boolean blank(String value) { return value == null || value.isBlank(); }
    private String concise(Exception e) {
        String value = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        return value.length() > 500 ? value.substring(0, 500) : value;
    }

    private record DownloadCandidate(URI uri, FullTextSource source) { }
    public record DownloadResult(Path path, FullTextSource source, long sizeBytes) { }
    public static class FullTextUnavailableException extends IllegalStateException {
        public FullTextUnavailableException(String message) { super(message); }
    }
}
