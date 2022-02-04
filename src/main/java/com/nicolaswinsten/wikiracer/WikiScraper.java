package com.nicolaswinsten.wikiracer;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for pulling wikilinks from MediaWiki or reading wikilinks
 * directly off the html of a wikipage
 *
 *
 * @author Nicolas Winsten
 */
class WikiScraper {

    /**
     * Limit results from the mediawiki api. max is 500
     */
    private final int queryLimit;

    /**
     * Maximum number of API calls allowed for one query
     */
    private final int fetchLimit;

    /**
     * Mapping of Wikipages to the Set of all the wikilinks on their page.
     *
     * Key: Wikipage title String
     *
     * Value: Set of Strings, titles of wikipages linked on the key's page
     */
    private final Map<String, Set<String>> linksOn;

    /**
     * Mapping of Wikipages to the Set of all the wikipages that link to it.
     *
     * Key: Wikipage title String
     *
     * Value: Set of Strings, titles of wikipages that have a link to the key page
     * on their page
     */
    private final Map<String, Set<String>> linksTo;


    /**
     * Construct WikiScraper
     *
     * @param maxQueryLimit limit results of API query
     * @param maxFetchLimit limit number of API requests for one query
     */
    public WikiScraper(int maxQueryLimit, int maxFetchLimit) {
        queryLimit = maxQueryLimit;
        fetchLimit = maxFetchLimit;
        linksOn = new HashMap<>();
        linksTo = new HashMap<>();
    }

    /**
     * This function takes in wikipage titles and returns the number of wikilinks
     * shared between their wikipages.
     *
     * @param a : String wikipage title
     * @param b : String wikipage title
     * @return int : number of common links that appear on both wikipage a and
     *         wikipage b
     */
    public int linksInCommon(String a, String b) {
        Set<String> setToIterate;
        Set<String> setToCompare;
        int numCommonLinks = 0;

        Set<String> aLinks = getLinksOn(a);
        Set<String> bLinks = getLinksOn(b);

        // compare size of each set, and iterate over smallest one.
        // one set could be quite large and the other could be very small.
        // we want to iterate over the small one, and then count links in
        // common with the large
        if (aLinks.size() < bLinks.size()) {
            setToIterate = aLinks;
            setToCompare = bLinks;
        } else {
            setToIterate = bLinks;
            setToCompare = aLinks;
        }

        for (String s : setToIterate)
            if (setToCompare.contains(s))
                numCommonLinks++;

        return numCommonLinks;
    }

    /**
     * Return true if the wikipage for a contains a wikilink to the wikipage for b.
     *
     * Might not work if a contains a redirect for b.
     *
     * @param a : String of wikipage title to possibly containing wikilink to b page
     * @param b : String of wikipage title that can possibly be reached from the
     *          wikipage for a
     * @return true, if the wikipage of b can be reached from a wikilink in the
     *         wikipage of a
     */
    public boolean hasLinkTo(String a, String b) {
        if (linksTo.containsKey(b) && linksTo.get(b).contains(a))
            return true;
        else if (linksOn.containsKey(a) && linksOn.get(a).contains(b))
            return true;
        return getLinksOn(a).contains(b);
    }

    /**
     * Returns a set of wikipages that contain a link to the given page including
     * redirects.
     *
     * @param title wikipedia page title
     * @return set of titles linking to the given title
     */
    public Set<String> getLinksTo(String title) {
        // do not redo work if this query has already been made
        if (linksTo.containsKey(title))
            return Collections.unmodifiableSet(linksTo.get(title));

        // this query does not include pages containing redirects to the desired page
        Set<String> links = queryWhatLinksHere(title);

        linksTo.put(title, links);

        return Collections.unmodifiableSet(links);
    }

    /**
     * Return the number of wikilinks found on the wikipage with the given title
     *
     * @param title String title of wikipage to query
     * @return int number of wikilinks on the page
     */
    public int degree(String title) {
        // subtract 1 for title itself
        return getLinksOn(title).size() - 1;
    }

    /**
     * Return the number of wikipages that link to the given page. Might take very
     * long for a particularly popular page, since MediaWiki only returns 500 links
     * per query
     *
     * @param title String title of wikipage to query
     * @return int number of pages linking to the given one
     */
    public int popularity(String title) {
        // subtract number of redirects that the given title has from total links to the
        // given title because getLinksTo includes redirects
        return getLinksTo(title).size();
    }

    /**
     * Return a set of all the wiki links on a given wiki page. Example: given the
     * wiki page title "Michael_Jackson" the return set will include "Tito_Jackson"
     * and "Thriller_(album)" because wiki links to those pages are found on the
     * wiki page for Michael_Jackson <br>
     * <br>
     * Note: links to the Main_Page and the title itself are expected to be in the
     * set
     *
     * @param title : String wiki page title
     * @return Set of wiki page titles
     */
    public Set<String> getLinksOn(String title) {
        if (linksOn.containsKey(title))
            // check dictionary to see if the given wiki page
            // has already been scraped for wiki links
            return Collections.unmodifiableSet(linksOn.get(title));

        // we are scraping html here rather than querying MediaWiki because if the given
        // title is a redirect then MediaWiki will only return one valid link (the page
        // it redirects to). By scraping the html instead, we get the links on its
        // redirection
        String html = "";
        try {
            html = fetchHTML(title);
        }
        catch (RuntimeException e) {
            System.out.println("Something went wrong fetching " + title);
        }
        Set<String> links = scrapeHTML(html);
        links.add(title); // ensure that we consider a title as linking to itself
        links.remove("Main Page"); // we don't consider Main Page
        // record wiki links found in the given wiki page in
        // lookup dictionary
        linksOn.put(title, links);

        return Collections.unmodifiableSet(links);
    }

    /**
     * Returns the html of the given wiki page title
     *
     *
     *
     * @param link : String title of a wiki page.
     * @return String html of that wiki page
     */
    private static String fetchHTML(String link) {
        StringBuilder buffer = new StringBuilder();
        try {
            URL url = new URL(getURL(encodeTitle(link)));
            InputStream is = url.openStream();
            int ptr = 0;
            while ((ptr = is.read()) != -1) {
                buffer.append((char) ptr);
            }
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage());
        }
        return buffer.toString();
    }

    /**
     * Return the full url String of the given wiki page title.
     *
     * @param link : String name of a wiki page
     * @return String url
     */
    private static String getURL(String link) {
        return "https://en.wikipedia.org/wiki/" + link;
    }

    /**
     * Scrape the html of a wikipedia page for wiki links. If a normal wiki article
     * is directly accessible from a wikilink on the give page, it is returned
     *
     * @param html : html of wiki page as a String
     * @return Set<String> of link names
     */
    private static Set<String> scrapeHTML(String html) {
        // match wikilinks to pages of namespace 0 (normal article pages)
        Set<String> allMatches = new HashSet<>();
        Matcher m = Pattern.compile("<a href=\"/wiki/([^:\"]+)\"").matcher(html);
        while (m.find()) {
            allMatches.add(decodeTitle(m.group(1).replaceAll("#.*", "")));
        }
        return allMatches;
    }

    private static final Map<String, String> encodings;
    private static final Map<String, String> decodings;
    static { // create mappings for decoder and encoder
        Map<String, String> map = new HashMap<>();
        map.put(" ", "_");
        map.put("!", "%21");
        map.put("\"", "%22");
        map.put("&", "%26");
        map.put("'", "%27");
        map.put("*", "%2A");
        map.put("+", "%2B");
        map.put(",", "%2C");
        map.put("/", "%2F");
        map.put(";", "%3B");
        map.put("=", "%3D");
        map.put("?", "%3F");
        map.put("@", "%40");
        map.put("\\", "%5C");
        map.put("`", "%60");
        map.put("–", "%E2%80%93");
        encodings = Collections.unmodifiableMap(map);

        map = new HashMap<>();
        for (String key : encodings.keySet())
            map.put(encodings.get(key), key);

        decodings = Collections.unmodifiableMap(map);
    }

    /**
     * Takes a title and percent encodes it to match the standard wikipedia naming
     * style
     *
     * @param title
     * @return percent encoded title
     */
    public static String encodeTitle(String title) {
        for (String ch : encodings.keySet())
            title = title.replace(ch, encodings.get(ch));
        // replace any % signs with %25 but only if it isnt already part of a percent
        // encoded character
        return title.replaceAll("%(?![0-9a-fA-F][0-9a-fA-F])", "%25");
    }

    /**
     * Reverses the percent encoding of encodeTitle()
     *
     * @return decoded version of the percent encoded title
     */
    public static String decodeTitle(String title) {
        for (String ch : decodings.keySet())
            title = title.replace(ch, decodings.get(ch));
        return title.replaceAll("%25", "%");
    }

    /**
     * @param title Wikipedia page title
     * @return Set of pages linking to the given title
     */
    private Set<String> queryWhatLinksHere(String title) {
        Map<String, String> params = new HashMap<>();
        params.put("action", "query");
        params.put("prop", "linkshere");
        params.put("lhprop", "title");
        params.put("lhnamespace", "0");
        params.put("lhshow", "!redirect");
        params.put("lhlimit", String.valueOf(queryLimit));
        params.put("titles", URLEncoder.encode(title, StandardCharsets.UTF_8));

        int currFetchLimit = 0;
        Set<String> links = new HashSet<>();
        String continuePageId = null;
        boolean batchComplete = true;
        do {
            JSONObject json = makeApiCall(params);
            JSONObject page = json.getJSONObject("query").getJSONObject("pages");

            JSONArray linkshere = page.getJSONObject(page.keys().next()).optJSONArray("linkshere");
            if (linkshere == null) return links;

            linkshere.forEach(
                    s -> links.add(((JSONObject) s).getString("title"))
            );

            currFetchLimit++;
            batchComplete = json.has("batchcomplete");

            if (!batchComplete) {
                continuePageId = json.getJSONObject("continue").getString("lhcontinue");
                params.put("lhcontinue", continuePageId);
            }
        } while (currFetchLimit < fetchLimit && !batchComplete);

        return links;
    }


    private static JSONObject makeApiCall(Map<String, String> params) {
        String api = "https://en.wikipedia.org/w/api.php?";
        params.put("format", "json");

        String paramStr = params.keySet().stream().map(
                k -> k + "=" + params.get(k)
        ).reduce("", (s1, s2) -> s1 + "&" + s2);

        StringBuilder buffer = new StringBuilder();
        try {
            URL url = new URL(api + paramStr);
            InputStream is = url.openStream();
            int ptr = 0;
            while ((ptr = is.read()) != -1) {
                buffer.append((char) ptr);
            }
        } catch (IOException e) {
            throw new RuntimeException(e.getMessage());
        }


        return new JSONObject(buffer.toString());
    }



}