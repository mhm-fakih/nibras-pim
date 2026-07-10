package com.nibras.pim.web;

import com.nibras.pim.domain.BaseEntity;
import com.nibras.pim.service.AbstractCrudService;
import com.nibras.pim.service.EntityCatalog;
import com.rometools.rome.feed.synd.SyndContent;
import com.rometools.rome.feed.synd.SyndContentImpl;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndEntryImpl;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.feed.synd.SyndFeedImpl;
import com.rometools.rome.io.SyndFeedOutput;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@RestController
public class RssController {

    private final EntityCatalog catalog;

    @Value("${nibras.rss.max-items:50}")
    private int maxItems;

    public RssController(EntityCatalog catalog) {
        this.catalog = catalog;
    }

    @GetMapping(value = "/feeds/{entity}.xml", produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> feed(@PathVariable String entity, HttpServletRequest request)
            throws Exception {
        AbstractCrudService<?, ?> service = catalog.find(entity)
                .orElseThrow(() -> new ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "Unknown feed: " + entity));

        String base = request.getRequestURL().toString().replace("/feeds/" + entity + ".xml", "");

        SyndFeed feed = new SyndFeedImpl();
        feed.setFeedType("rss_2.0");
        feed.setTitle("Nibras — " + entity);
        feed.setLink(base + "/feeds/" + entity + ".xml");
        feed.setDescription("Latest " + entity + " from Nibras PIM");

        List<SyndEntry> entries = new ArrayList<>();
        for (BaseEntity e : service.latest(maxItems)) {
            SyndEntry item = new SyndEntryImpl();
            String rawTitle = e.feedTitle();
            rawTitle = rawTitle == null || rawTitle.isBlank() ? "(untitled)" : rawTitle;
            item.setTitle(dirWrap(rawTitle));
            item.setLink(base + "/api/" + entity + "/" + e.getId());
            item.setUri(base + "/api/" + entity + "/" + e.getId());
            if (e.getDateCreated() != null) {
                item.setPublishedDate(toDate(e.getDateCreated()));
            }
            // Reader title bars are LTR-styled and ignore bidi controls, so for an Arabic title lead the
            // reading-pane HTML with the title as an RTL heading (the one surface whose direction we set).
            String descHtml = e.feedDescription() != null ? htmlBlock(e.feedDescription()) : "";
            if (isRtl(rawTitle)) {
                descHtml = "<h3 dir=\"rtl\" align=\"right\" style=\"text-align:right;margin:0 0 8px;\">"
                        + escape(rawTitle) + "</h3>" + descHtml;
            }
            if (!descHtml.isEmpty()) {
                SyndContent content = new SyndContentImpl();
                content.setType("text/html");
                content.setValue(descHtml);
                item.setDescription(content);
            }
            entries.add(item);
        }
        feed.setEntries(entries);

        String xml = new SyndFeedOutput().outputString(feed);
        return ResponseEntity.ok()
                .contentType(MediaType.valueOf("application/rss+xml; charset=UTF-8"))
                .body(xml);
    }

    private static Date toDate(LocalDateTime ldt) {
        return Timestamp.valueOf(ldt);
    }

    /**
     * Wraps a description in a direction-aware block so RSS readers (e.g. QuiteRSS) render Arabic text
     * right-aligned and wrapped instead of as overlapping bidi lines. Direction is detected from the
     * content (first strong character), not any language field; newlines become {@code <br/>};
     * {@code unicode-bidi:plaintext} resolves each line's direction so embedded LTR timestamps don't
     * scramble the layout.
     */
    private static String htmlBlock(String text) {
        boolean rtl = isRtl(text);
        String dir = rtl ? "rtl" : "ltr";
        String align = rtl ? "right" : "left";
        // Emit each line as its own <p> with HTML dir/align ATTRIBUTES (not just CSS) — QuiteRSS's old
        // WebKit honours those reliably, where it ignores unicode-bidi/CSS and clips RTL text. Content is
        // HTML-escaped so stray markup in the stored text can't break the reader's layout.
        StringBuilder sb = new StringBuilder();
        sb.append("<div dir=\"").append(dir).append("\" align=\"").append(align).append("\">");
        for (String line : text.replace("\r\n", "\n").split("\n", -1)) {
            if (line.isBlank()) {
                sb.append("<br/>");
            } else {
                sb.append("<p dir=\"").append(dir).append("\" align=\"").append(align)
                        .append("\" style=\"margin:0 0 6px;word-wrap:break-word;overflow-wrap:break-word;\">")
                        .append(escape(line)).append("</p>");
            }
        }
        return sb.append("</div>").toString();
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /**
     * RSS {@code <title>} is plain text (no HTML/CSS), so direction can only be conveyed with Unicode
     * bidi controls. Wrap RTL titles in RLE…PDF (U+202B…U+202C) so readers like QuiteRSS lay the whole
     * title out right-to-left (embedded LTR dates/numbers still render correctly inside).
     */
    private static String dirWrap(String title) {
        return isRtl(title) ? "‫" + title + "‬" : title;
    }

    /** True when the first strong-directional character is RTL (Arabic/Hebrew/…). */
    private static boolean isRtl(String s) {
        if (s == null) {
            return false;
        }
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            byte d = Character.getDirectionality(cp);
            if (d == Character.DIRECTIONALITY_RIGHT_TO_LEFT
                    || d == Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC) {
                return true;
            }
            if (d == Character.DIRECTIONALITY_LEFT_TO_RIGHT) {
                return false;
            }
            i += Character.charCount(cp);
        }
        return false;
    }
}
