package com.nick.wikiracer;
// NOTE //////////////////////////////////////
/**
 * This file was ripped from MER-C's wiki bot framework: https://github.com/MER-C/wiki-java.
 * I'm using it to avoid working with the MediaWiki API directly.
 * The original file was almost 9 thousand lines long, so I've stripped it down as much as I could here and modified it to fit my needs.
 */
//////////////////////////////////////////////

/**
 *  @(#)Wiki.java 0.36 08/02/2019
 *  Copyright (C) 2007 - 2019 MER-C and contributors
 *
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU General Public License
 *  as published by the Free Software Foundation; either version 3
 *  of the License, or (at your option) any later version. Additionally
 *  this file is subject to the "Classpath" exception.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, write to the Free Software Foundation,
 *  Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */

import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.*;
import java.time.format.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import java.util.logging.*;
import java.util.stream.*;
import java.util.zip.GZIPInputStream;

/**
 * This is a somewhat sketchy bot framework for editing MediaWiki wikis.
 * Requires JDK 11 or greater. Uses the
 * <a href="https://mediawiki.org/wiki/API:Main_page">MediaWiki API</a> for most
 * operations. It is recommended that the server runs the latest version of
 * MediaWiki (1.31), otherwise some functions may not work. This framework
 * requires no dependencies outside the core JDK and does not implement any
 * functionality added by MediaWiki extensions.
 * <p>
 * Extended documentation is available <a href=
 * "https://github.com/MER-C/wiki-java/wiki/Extended-documentation">here</a>.
 * All wikilinks are relative to the English Wikipedia and all timestamps are in
 * your wiki's time zone.
 * <p>
 * Please file bug reports
 * <a href="https://en.wikipedia.org/wiki/User_talk:MER-C">here</a> or at the
 * <a href="https://github.com/MER-C/wiki-java/issues">Github issue tracker</a>.
 *
 * <h2>Configuration variables</h2>
 * <p>
 * Some configuration is available through <code>java.util.Properties</code>.
 * Set the system property <code>wiki-java.properties</code> to a file path
 * where a configuration file is located. The available variables are:
 * <ul>
 * <li><b>maxretries</b>: (default 2) the number of attempts to retry a network
 * request before stopping
 * <li><b>connecttimeout</b>: (default 30000) maximum allowed time for a HTTP(s)
 * connection to be established in milliseconds
 * <li><b>readtimeout</b>: (default 180000) maximum allowed time for the read to
 * take place in milliseconds (needs to be longer, some connections are slow and
 * the data volume is large!).
 * <li><b>loguploadsize</b>: (default 22, equivalent to 2^22 = 4 MB) controls
 * the log2(size) of each chunk in chunked uploads. Disable chunked uploads by
 * setting a large value here (50, equivalent to 2^50 = 1 PB will do). Stuff you
 * actually upload must be no larger than 4 GB. .
 * </ul>
 *
 * @author MER-C and contributors
 * @version 0.36
 */
class Wiki {
    // NAMESPACES

    /**
     * Denotes the namespace of images and media, such that there is no description
     * page. Uses the "Media:" prefix.
     *
     * @see #FILE_NAMESPACE
     * @since 0.03
     */
    public static final int MEDIA_NAMESPACE = -2;

    /**
     * Denotes the namespace of pages with the "Special:" prefix. Note that many
     * methods dealing with special pages may spew due to raw content not being
     * available.
     *
     * @since 0.03
     */
    public static final int SPECIAL_NAMESPACE = -1;

    /**
     * Denotes the main namespace, with no prefix.
     *
     * @since 0.03
     */
    public static final int MAIN_NAMESPACE = 0;

    /**
     * Denotes the namespace for talk pages relating to the main namespace, denoted
     * by the prefix "Talk:".
     *
     * @since 0.03
     */
    public static final int TALK_NAMESPACE = 1;

    /**
     * Denotes the namespace for user pages, given the prefix "User:".
     *
     * @since 0.03
     */
    public static final int USER_NAMESPACE = 2;

    /**
     * Denotes the namespace for user talk pages, given the prefix "User talk:".
     *
     * @since 0.03
     */
    public static final int USER_TALK_NAMESPACE = 3;

    /**
     * Denotes the namespace for pages relating to the project, with prefix
     * "Project:". It also goes by the name of whatever the project name was.
     *
     * @since 0.03
     */
    public static final int PROJECT_NAMESPACE = 4;

    /**
     * Denotes the namespace for talk pages relating to project pages, with prefix
     * "Project talk:". It also goes by the name of whatever the project name was, +
     * "talk:".
     *
     * @since 0.03
     */
    public static final int PROJECT_TALK_NAMESPACE = 5;

    /**
     * Denotes the namespace for file description pages. Has the prefix "File:". Do
     * not create these directly, use upload() instead.
     *
     * @see #MEDIA_NAMESPACE
     * @since 0.25
     */
    public static final int FILE_NAMESPACE = 6;

    /**
     * Denotes talk pages for file description pages. Has the prefix "File talk:".
     *
     * @since 0.25
     */
    public static final int FILE_TALK_NAMESPACE = 7;

    /**
     * Denotes the namespace for (wiki) system messages, given the prefix
     * "MediaWiki:".
     *
     * @since 0.03
     */
    public static final int MEDIAWIKI_NAMESPACE = 8;

    /**
     * Denotes the namespace for talk pages relating to system messages, given the
     * prefix "MediaWiki talk:".
     *
     * @since 0.03
     */
    public static final int MEDIAWIKI_TALK_NAMESPACE = 9;

    /**
     * Denotes the namespace for templates, given the prefix "Template:".
     *
     * @since 0.03
     */
    public static final int TEMPLATE_NAMESPACE = 10;

    /**
     * Denotes the namespace for talk pages regarding templates, given the prefix
     * "Template talk:".
     *
     * @since 0.03
     */
    public static final int TEMPLATE_TALK_NAMESPACE = 11;

    /**
     * Denotes the namespace for help pages, given the prefix "Help:".
     *
     * @since 0.03
     */
    public static final int HELP_NAMESPACE = 12;

    /**
     * Denotes the namespace for talk pages regarding help pages, given the prefix
     * "Help talk:".
     *
     * @since 0.03
     */
    public static final int HELP_TALK_NAMESPACE = 13;

    /**
     * Denotes the namespace for category description pages. Has the prefix
     * "Category:".
     *
     * @since 0.03
     */
    public static final int CATEGORY_NAMESPACE = 14;

    /**
     * Denotes the namespace for talk pages regarding categories. Has the prefix
     * "Category talk:".
     *
     * @since 0.03
     */
    public static final int CATEGORY_TALK_NAMESPACE = 15;

    /**
     * Denotes all namespaces.
     *
     * @since 0.03
     */
    public static final int ALL_NAMESPACES = 0x09f91102;

    private static final String version = "0.36";

    // fundamental URL strings
    private final String protocol, domain, scriptPath;
    private String base, articleUrl;

    /**
     * Stores default HTTP parameters for API calls.
     * Add stuff to this map if you want to add parameters to every API
     * call.
     *
     * @see #makeApiCall(Map, Map, String)
     */
    protected ConcurrentHashMap<String, String> defaultApiParams;

    /**
     * URL entrypoint for the MediaWiki API. (Needs to be accessible to subclasses.)
     *
     * @see #initVars()
     * @see <a href="https://mediawiki.org/wiki/Manual:Api.php">MediaWiki
     *      documentation</a>
     */
    protected String apiUrl;

    // user management
    private HttpClient client;
    private final CookieManager cookies;

    // preferences
    private int max = 500;
    private int slowmax = 50;
    private int maxlag = 5;
    private int querylimit = Integer.MAX_VALUE;
    private String useragent = "Wiki.java/" + version + " (https://github.com/NicolasWinsten/wiki-racer/)";
    private boolean zipped = true;
    private Level loglevel = Level.ALL;
    private static final Logger logger = Logger.getLogger("wiki");

    // config via properties
    private final int maxtries;
    private final int read_timeout_msec;

    // CONSTRUCTORS AND CONFIGURATION

    /**
     * Creates a new MediaWiki API client for the given wiki with
     * <a href="https://mediawiki.org/wiki/Manual:$wgScriptPath"><var>
     * $wgScriptPath</var></a> set to <var>scriptPath</var> and via the specified
     * protocol.
     *
     * @param domain     the wiki domain name
     * @param scriptPath the script path
     * @param protocol   a protocol e.g. "http://", "https://" or "file:///"
     * @since 0.31
     */
    protected Wiki(String domain, String scriptPath, String protocol) {
        this.domain = Objects.requireNonNull(domain);
        this.scriptPath = Objects.requireNonNull(scriptPath);
        this.protocol = Objects.requireNonNull(protocol);

        defaultApiParams = new ConcurrentHashMap<>();
        defaultApiParams.put("format", "xml");
        defaultApiParams.put("maxlag", String.valueOf(maxlag));

        logger.setLevel(loglevel);
        logger.log(Level.CONFIG, "[{0}] Using Wiki.java {1}", new Object[] { domain, version });

        // read in config
        Properties props = new Properties();
        String filename = System.getProperty("wiki-java.properties");
        if (filename != null) {
            try {
                InputStream in = new FileInputStream(new File(filename));
                props.load(in);
            } catch (IOException ex) {
                logger.log(Level.WARNING, "Unable to load properties file " + filename);
            }
        }
        maxtries = Integer.parseInt(props.getProperty("maxretries", "2"));
        read_timeout_msec = Integer.parseInt(props.getProperty("readtimeout", "180000")); // 180 seconds
        cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).cookieHandler(cookies).build();
    }

    /**
     * Creates a new MediaWiki API client for the given wiki using HTTPS.
     *
     * @param domain the wiki domain name e.g. en.wikipedia.org (defaults to
     *               en.wikipedia.org)
     * @return the constructed API client object
     * @since 0.34
     */
    public static Wiki newSession(String domain) {
        return newSession(domain, "/w", "https://");
    }

    /**
     * Creates a new MediaWiki API client for the given wiki with
     * <a href="https://mediawiki.org/wiki/Manual:$wgScriptPath"><var>
     * $wgScriptPath</var></a> set to <var>scriptPath</var> and via the specified
     * protocol. Depending on the settings of the wiki, you may need to call
     * some functionality to work correctly.
     *
     * <p>
     * All factory methods in subclasses must call {@link #initVars()}.
     *
     * @param domain     the wiki domain name
     * @param scriptPath the script path
     * @param protocol   a protocol e.g. "http://", "https://" or "file:///"
     * @return the constructed API client object
     * @since 0.34
     */
    public static Wiki newSession(String domain, String scriptPath, String protocol) {
        // Don't put network requests here. Servlets cannot afford to make
        // unnecessary network requests in initialization.
        Wiki wiki = new Wiki(domain, scriptPath, protocol);
        wiki.initVars();
        return wiki;
    }

    /**
     * Edit this if you need to change the API and human interface url configuration
     * of the wiki. One example use is to change the port number.
     *
     * <p>
     * Contributed by Tedder
     *
     * @since 0.24
     */
    protected void initVars() {
        apiUrl = protocol + domain + scriptPath + "/api.php";
    }

    /**
     * Returns the maximum number of results returned when querying the API. Default
     * = Integer.MAX_VALUE
     *
     * @return see above
     * @since 0.34
     */
    public int getQueryLimit() {
        return querylimit;
    }

    /**
     * Sets the maximum number of results returned when querying the API. Useful for
     * operating in constrained environments (e.g. web servers) or queries for which
     * results are sorted by relevance (e.g. search).
     *
     * @param limit the desired maximum number of results to retrieve
     * @throws IllegalArgumentException if <var>limit</var> is not a positive
     *                                  integer
     * @since 0.34
     */
    public void setQueryLimit(int limit) {
        if (limit < 1)
            throw new IllegalArgumentException("Query limit must be a positive integer.");
        querylimit = limit;
    }

    /**
     * Set the logging level used by the internal logger.
     *
     * @param loglevel one of the levels specified in java.util.logging.LEVEL
     * @since 0.31
     */
    public void setLogLevel(Level loglevel) {
        this.loglevel = loglevel;
        logger.setLevel(loglevel);
    }

    /**
     * Returns a set of all pages linking to this page. Equivalent to
     * [[Special:Whatlinkshere]].
     *
     * @param title the title of the page
     * @param ns    a list of namespaces to filter by, empty = all namespaces.
     * @return the list of pages linking to the specified page
     * @throws IOException or UncheckedIOException if a network error occurs
     * @since 0.10
     */
    public Set<String> whatLinksHere(String title, int limit, int... ns) throws IOException {
        return new HashSet<String>(whatLinksHere(List.of(title), limit, false, ns).get(0));
    }

    /**
     * Returns set of all pages that redirect to the given page
     *
     * @param title
     * @param ns    namespaces to filter by, empty = all namespaces
     * @return set of redirecting titles
     * @throws IOException or UncheckedIOException if a network error occurs
     */
    public Set<String> whatRedirectsHere(String title, int... ns) throws IOException {
        return new HashSet<String>(whatLinksHere(List.of(title), Integer.MAX_VALUE, true, ns).get(0));
    }

    /**
     * Returns lists of all pages linking to the given pages within the specified
     * namespaces. Output order is the same as input order. Alternatively, we can
     * retrieve a list of what redirects to a page by setting <var>redirects</var>
     * to true. Equivalent to [[Special:Whatlinkshere]].
     *
     * <p>
     * If <var>addredirects</var> is true, pages that link to a redirect that
     * targets all given pages are added to the results. If namespaces are
     * specified, both redirect and linking page must be in those namespaces.
     * WARNING: this is significantly slower!
     *
     * @param titles a list of titles
     * @param ns     a list of namespaces to filter by, empty = all namespaces.
     * @return the list of pages linking to the specified page
     * @throws IOException or UncheckedIOException if a network error occurs
     * @since 0.10
     */
    protected List<List<String>> whatLinksHere(List<String> titles, int limit, boolean redirects, int... ns)
            throws IOException {
        Map<String, String> getparams = new HashMap<>();

        getparams.put("prop", "linkshere");
        if (ns.length > 0)
            getparams.put("lhnamespace", constructNamespaceString(ns));
        if (redirects)
            getparams.put("lhshow", "redirect");
        List<List<String>> ret = makeVectorizedQuery("lh", getparams, titles, "whatLinksHere", limit,
                (data, result) -> {
                    // xml form: <lh pageid="1463" ns="1" title="Talk:Apollo program" />
                    for (int a = data.indexOf("<lh "); a > 0; a = data.indexOf("<lh ", ++a))
                        result.add(parseAttribute(data, "title", a));
                });
        return ret;
    }

    /**
     * Fetches list-type results from the MediaWiki API.
     *
     * @param <T>         a class describing the parsed API results (e.g. String,
     *                    LogEntry, Revision)
     * @param queryPrefix the request type prefix (e.g. "pl" for prop=links)
     * @param getparams   a bunch of parameters to send via HTTP GET
     * @param postparams  if not null, send these parameters via POST (see
     *                    {@link #makeApiCall(Map, Map, String) }).
     * @param caller      the name of the calling method
     * @param limit       fetch no more than this many results
     * @param parser      a BiConsumer that parses the XML returned by the MediaWiki
     *                    API into things we want, dumping them into the given List
     * @return the query results
     * @throws IOException       if a network error occurs
     * @throws SecurityException if we don't have the credentials to perform a
     *                           privileged action (mostly avoidable)
     * @since 0.34
     */
    protected <T> List<T> makeListQuery(String queryPrefix, Map<String, String> getparams,
                                        Map<String, Object> postparams, String caller, int limit, BiConsumer<String, List<T>> parser)
            throws IOException {
        if (limit < 0)
            limit = querylimit;
        getparams.put("action", "query");
        List<T> results = new ArrayList<>(1333);
        String limitstring = queryPrefix + "limit";
        do {
            getparams.put(limitstring, String.valueOf(max));
            String line = makeApiCall(getparams, postparams, caller);

            getparams.keySet().removeIf(param -> param.endsWith("continue"));

            // Continuation parameter has form:
            // <continue rccontinue="20170924064528|986351741" continue="-||" />
            if (line.contains("<continue ")) {
                int a = line.indexOf("<continue ") + 9;
                int b = line.indexOf(" />", a);
                String cont = line.substring(a, b);
                for (String contpair : cont.split("\" ")) {
                    contpair = " " + contpair.trim();
                    String contattr = contpair.substring(0, contpair.indexOf("=\""));
                    getparams.put(contattr.trim(), parseAttribute(cont, contattr, 0));
                }
            }

            parser.accept(line, results);
        } while (getparams.containsKey("continue") && results.size() < limit);
        return results;
    }

    /**
     * Performs a vectorized <samp>action=query&amp;prop=X</samp> type API query
     * over titles.
     *
     * @param queryPrefix the request type prefix (e.g. "pl" for prop=links)
     * @param getparams   a bunch of parameters to send via HTTP GET
     * @param titles      a list of titles
     * @param caller      the name of the calling method
     * @param limit       fetch no more than this many results
     * @param parser      a BiConsumer that parses the XML returned by the MediaWiki
     *                    API into things we want, dumping them into the given List
     * @return a list of results, where each element corresponds to the element at
     *         the same index in the input title list
     * @since 0.36
     * @throws IOException if a network error occurs
     */
    protected List<List<String>> makeVectorizedQuery(String queryPrefix, Map<String, String> getparams,
                                                     List<String> titles, String caller, int limit, BiConsumer<String, List<String>> parser) throws IOException {
        List<Map<String, List<String>>> stuff = new ArrayList<>();
        Map<String, Object> postparams = new HashMap<>();
        for (String temp : constructTitleString(titles)) {
            postparams.put("titles", temp);
            stuff.addAll(makeListQuery(queryPrefix, getparams, postparams, caller, limit, (line, results) -> {
                // Split the result into individual listings for each article.
                String[] x = line.split("<page ");
                // Skip first element to remove front crud.
                for (int i = 1; i < x.length; i++) {
                    String parsedtitle = parseAttribute(x[i], "title", 0);
                    List<String> list = new ArrayList<>();
                    parser.accept(x[i], list);

                    Map<String, List<String>> intermediate = new HashMap<>();
                    intermediate.put(parsedtitle, list);
                    results.add(intermediate);
                }
            }));
        }

        // fill the return list
        List<List<String>> ret = new ArrayList<>();
        List<String> normtitles = new ArrayList<>();
        for (String localtitle : titles) {
            normtitles.add(normalize(localtitle));
            ret.add(new ArrayList<>());
        }
        // then retrieve the results from the intermediate list of maps,
        // ensuring results correspond to inputs
        stuff.forEach(map -> {
            String parsedtitle = map.keySet().iterator().next();
            List<String> templates = map.get(parsedtitle);
            for (int i = 0; i < titles.size(); i++)
                if (normtitles.get(i).equals(parsedtitle))
                    ret.get(i).addAll(templates);
        });
        return ret;
    }

    /**
     * Convenience method for normalizing MediaWiki titles. (Converts all
     * underscores to spaces, localizes namespace names, fixes case of first char
     * and does some other unicode fixes).
     *
     * @param s the string to normalize
     * @return the normalized string
     * @throws IllegalArgumentException if the title is invalid
     * @throws UncheckedIOException     if the namespace cache has not been
     *                                  populated, and a network error occurs when
     *                                  populating it
     * @since 0.27
     */
    public String normalize(String s) {
        // remove section names
        if (s.contains("#"))
            s = s.substring(0, s.indexOf("#"));
        // remove leading colon
        if (s.startsWith(":"))
            s = s.substring(1);
        s = s.replace('_', ' ').trim();
        if (s.isEmpty())
            throw new IllegalArgumentException("Empty or whitespace only title.");

        char[] temp = s.toCharArray();
        // convert first character in the actual title to upper case
        temp[0] = Character.toUpperCase(temp[0]);

        for (int i = 0; i < temp.length; i++) {
            switch (temp[i]) {
                // illegal characters
                case '{':
                case '}':
                case '<':
                case '>':
                case '[':
                case ']':
                case '|':
                    throw new IllegalArgumentException(s + " is an illegal title");
            }
        }
        // https://mediawiki.org/wiki/Unicode_normalization_considerations
        String temp2 = new String(temp).replaceAll("\\s+", " ");
        return Normalizer.normalize(temp2, Normalizer.Form.NFC);
    }

    /**
     * Cuts up a list of titles into batches for prop=X&amp;titles=Y type queries.
     *
     * @param titles a list of titles.
     * @return the titles ready for insertion into a URL
     * @throws UncheckedIOException if the namespace cache has not been populated,
     *                              and a network error occurs when populating it
     * @since 0.29
     */
    protected List<String> constructTitleString(List<String> titles) {
        // sort and remove duplicates per https://mediawiki.org/wiki/API
        TreeSet<String> ts = new TreeSet<>();
        for (String title : titles)
            ts.add(normalize(title));
        List<String> titles_enc = new ArrayList<>(ts);

        // actually construct the string
        ArrayList<String> ret = new ArrayList<>();
        for (int i = 0; i < titles_enc.size() / slowmax + 1; i++) {
            ret.add(String.join("|", titles_enc.subList(i * slowmax, Math.min(titles_enc.size(), (i + 1) * slowmax))));
        }
        return ret;
    }

    /**
     * Constructs, sends and handles calls to the MediaWiki API. This is a low-level
     * method for making your own, custom API calls.
     *
     * <p>
     * If <var>postparams</var> is not {@code null} or empty, the request is sent
     * using HTTP GET, otherwise it is sent using HTTP POST. A {@code byte[]} value
     * in <var>postparams</var> causes the request to be sent as a multipart POST.
     * Anything else is converted to String via the following means:
     *
     * <ul>
     * <li>String[] -- {@code String.join("|", arr)}
     * <li>StringBuilder -- {@code sb.toString()}
     * <li>Number -- {@code num.toString()}
     * <li>OffsetDateTime --
     * {@code date.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)}
     * <li>{@code Collection<?>} -- {@code coll.stream()
     *      .map(item -> convertToString(item)) // using the above rules
     *      .collect(Collectors.joining("|"))}
     * </ul>
     *
     * <p>
     * All supplied Strings and objects converted to String are automatically
     * URLEncoded in UTF-8 if this is a normal POST request.
     *
     * <p>
     * Here we also check the database lag and wait if it exceeds <var>maxlag</var>,
     * see <a href="https://mediawiki.org/wiki/Manual:Maxlag_parameter"> here</a>
     * for how this works.
     *
     * @param getparams  append these parameters to the urlbase
     * @param postparams if null, send the request using POST otherwise use GET
     * @param caller     the caller of this method
     * @return the server response
     * @throws IOException       if a network error occurs
     * @throws SecurityException if we don't have the credentials to perform a
     *                           privileged action (mostly avoidable)
     * @throws AssertionError    if assert=user|bot fails
     * @see <a href=
     *      "http://www.w3.org/TR/html4/interact/forms.html#h-17.13.4.2">Multipart/form-data</a>
     * @since 0.18
     */
    public String makeApiCall(Map<String, String> getparams, Map<String, Object> postparams, String caller)
            throws IOException {
        // build the URL
        StringBuilder urlbuilder = new StringBuilder(apiUrl + "?");
        getparams.putAll(defaultApiParams);
        for (Map.Entry<String, String> entry : getparams.entrySet()) {
            urlbuilder.append('&');
            urlbuilder.append(entry.getKey());
            urlbuilder.append('=');
            urlbuilder.append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
        }
        String url = urlbuilder.toString();

        // POST stuff
        boolean isPOST = (postparams != null && !postparams.isEmpty());
        StringBuilder stringPostBody = new StringBuilder();
        boolean multipart = false;
        ArrayList<byte[]> multipartPostBody = new ArrayList<>();
        String boundary = "----------NEXT PART----------";
        if (isPOST) {
            // determine whether this is a multipart post and convert any values
            // to String if necessary
            for (Map.Entry<String, Object> entry : postparams.entrySet()) {
                Object value = entry.getValue();
                if (value instanceof byte[])
                    multipart = true;
                else
                    entry.setValue(convertToString(value));
            }

            // now we know how we're sending it, construct the post body
            if (multipart) {
                byte[] nextpart = ("--" + boundary + "\r\n\"Content-Disposition: form-data; name=\\\"\"")
                        .getBytes(StandardCharsets.UTF_8);
                for (Map.Entry<String, ?> entry : postparams.entrySet()) {
                    multipartPostBody.add(nextpart);
                    Object value = entry.getValue();
                    multipartPostBody.add((entry.getKey() + "\"\r\n").getBytes(StandardCharsets.UTF_8));
                    if (value instanceof String)
                        multipartPostBody
                                .add(("Content-Type: text/plain; charset=UTF-8\r\n\r\n" + (String) value + "\r\n")
                                        .getBytes(StandardCharsets.UTF_8));
                    else if (value instanceof byte[]) {
                        multipartPostBody
                                .add("Content-Type: application/octet-stream\r\n\r\n".getBytes(StandardCharsets.UTF_8));
                        multipartPostBody.add((byte[]) value);
                        multipartPostBody.add("\r\n".getBytes(StandardCharsets.UTF_8));
                    }
                }
                multipartPostBody.add((boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
            } else {
                // automatically encode Strings sent via normal POST
                for (Map.Entry<String, Object> entry : postparams.entrySet()) {
                    stringPostBody.append('&');
                    stringPostBody.append(entry.getKey());
                    stringPostBody.append('=');
                    stringPostBody.append(URLEncoder.encode(entry.getValue().toString(), StandardCharsets.UTF_8));
                }
            }
        }

        // main fetch/retry loop
        String response = null;
        int tries = maxtries;
        do {
            tries--;
            try {
                var connection = makeConnection(url);
                if (isPOST) {
                    if (multipart)
                        connection = connection.POST(HttpRequest.BodyPublishers.ofByteArrays(multipartPostBody))
                                .header("Content-Type", "multipart/form-data; boundary=" + boundary);
                    else
                        connection = connection.POST(HttpRequest.BodyPublishers.ofString(stringPostBody.toString()))
                                .header("Content-Type", "application/x-www-form-urlencoded");
                }

                HttpResponse<InputStream> hr = client.send(connection.build(),
                        HttpResponse.BodyHandlers.ofInputStream());
                if (checkLag(hr)) {
                    tries++;
                    throw new HttpRetryException("Database lagged.", 503);
                }

                try (BufferedReader in = new BufferedReader(
                        new InputStreamReader(zipped ? new GZIPInputStream(hr.body()) : hr.body(), "UTF-8"))) {
                    response = in.lines().collect(Collectors.joining("\n"));
                }

                // Check for rate limit (though might be a long one e.g. email)
                if (response.contains("error code=\"ratelimited\"")) {
                    // the Retry-After header field is useless here
                    // see https://phabricator.wikimedia.org/T172293
                    Thread.sleep(10000);
                    throw new HttpRetryException("Action throttled.", 503);
                }
                // Check for database lock
                if (response.contains("error code=\"readonly\"")) {
                    Thread.sleep(10000);
                    throw new HttpRetryException("Database locked!", 503);
                }

                // No need to retry anymore, success or unrecoverable failure.
                tries = 0;
            } catch (IOException ex) {
                // Exception deliberately ignored until retries are depleted.
                if (tries == 0)
                    throw ex;
            } catch (InterruptedException ignored) {
            }
        } while (tries != 0);

        // empty response from server
        if (response.isEmpty())
            throw new UnknownError("Received empty response from server!");
        if (response.contains("<error code=")) {
            String error = parseAttribute(response, "code", 0);
            String description = parseAttribute(response, "info", 0);
            switch (error) {
                case "assertbotfailed":
                case "assertuserfailed":
                    throw new AssertionError(description);
                case "permissiondenied":
                    throw new SecurityException(description);
                    // Harmless, response goes to calling method. TODO: remove this.
                case "nosuchsection": // getSectionText()
                case "cantundelete": // undelete(), page has no deleted revisions
                    break;
                // Something *really* bad happened. Most of these are self-explanatory
                // and are indicative of bugs (not necessarily in this framework) or
                // can be avoided entirely. Others are kicked to the caller to handle.
                default:
                    throw new UnknownError("MW API error. Server response was: " + response);
            }
        }
        return response;
    }

    /**
     * Strips entity references like &quot; from the supplied string. This might be
     * useful for subclasses.
     *
     * @param in the string to remove URL encoding from
     * @return that string without URL encoding
     * @since 0.11
     */
    protected String decode(String in) {
        // Remove entity references. Oddly enough, URLDecoder doesn't nuke these.
        in = in.replace("&lt;", "<").replace("&gt;", ">"); // html tags
        in = in.replace("&quot;", "\"");
        in = in.replace("&#039;", "'");
        in = in.replace("&amp;", "&");
        return in;
    }

    /**
     * Converts HTTP POST parameters to Strings. See
     * {@link #makeApiCall(Map, Map, String)} for the description.
     *
     * @param param the parameter to convert
     * @return that parameter, as a String
     * @throws UnsupportedOperationException if param is not a supported data type
     * @since 0.35
     */
    private String convertToString(Object param) {
        // TODO: Replace with type switch in JDK 11/12
        if (param instanceof String)
            return (String) param;
        else if (param instanceof StringBuilder || param instanceof Number)
            return param.toString();
        else if (param instanceof String[])
            return String.join("|", (String[]) param);
        else if (param instanceof OffsetDateTime) {
            OffsetDateTime date = (OffsetDateTime) param;
            // https://www.mediawiki.org/wiki/Timestamp
            // https://github.com/MER-C/wiki-java/issues/170
            return date.atZoneSameInstant(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS)
                    .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        } else if (param instanceof Collection) {
            Collection<?> coll = (Collection) param;
            return coll.stream().map(item -> convertToString(item)).collect(Collectors.joining("|"));
        } else
            throw new UnsupportedOperationException("Unrecognized data type");
    }

    /**
     * Parses the next XML attribute with the given name.
     *
     * @param xml       the xml to search
     * @param attribute the attribute to search
     * @param index     where to start looking
     * @return the value of the given XML attribute, or null if the attribute is not
     *         present
     * @since 0.28
     */
    protected String parseAttribute(String xml, String attribute, int index) {
        // let's hope the JVM always inlines this
        if (xml.contains(attribute + "=\"")) {
            int a = xml.indexOf(attribute + "=\"", index) + attribute.length() + 2;
            int b = xml.indexOf('\"', a);
            return decode(xml.substring(a, b));
        } else
            return null;
    }

    /**
     * Convenience method for converting a namespace list into String form. Negative
     * namespace numbers are removed.
     *
     * @param ns the list of namespaces to append
     * @return the namespace list in String form
     * @since 0.27
     */
    protected String constructNamespaceString(int[] ns) {
        return Arrays.stream(ns).distinct().filter(namespace -> namespace >= 0).sorted().mapToObj(String::valueOf)
                .collect(Collectors.joining("|"));
    }

    /**
     * Checks for database lag and sleeps if {@code lag < getMaxLag()}.
     *
     * @param response the HTTP response received
     * @return true if there was sufficient database lag.
     * @throws InterruptedException if any wait was interrupted
     * @see <a href="https://mediawiki.org/wiki/Manual:Maxlag_parameter"> MediaWiki
     *      documentation</a>
     * @since 0.32
     */
    protected synchronized boolean checkLag(HttpResponse response) throws InterruptedException {
        HttpHeaders hdrs = response.headers();
        long lag = hdrs.firstValueAsLong("X-Database-Lag").orElse(-5);
        // X-Database-Lag is the current lag rounded down to the nearest integer.
        // Thus, we need to retry in case of equality.
        if (lag >= maxlag) {
            long time = hdrs.firstValueAsLong("Retry-After").orElse(10);
            logger.log(Level.WARNING, "Current database lag {0} s exceeds maxlag of {1} s, waiting {2} s.",
                    new Object[] { lag, maxlag, time });
            Thread.sleep(time * 1000L);
            return true;
        }
        return false;
    }

    /**
     * Creates a new HTTP request. Override to change request properties.
     *
     * @param url a URL string
     * @return a HTTP request builder for that URL
     * @throws IOException if a network error occurs
     * @since 0.31
     */
    protected HttpRequest.Builder makeConnection(String url) throws IOException {
        var builder = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofMillis(read_timeout_msec))
                .header("User-Agent", useragent);
        if (zipped)
            builder = builder.header("Accept-encoding", "gzip");
        return builder;
    }
}