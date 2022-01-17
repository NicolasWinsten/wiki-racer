package com.nicolaswinsten.wikiracer;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * This class is a bot that can play the WikiGame <br>
 * <br>
 * Example: WikiRacer.findWikiLadder("Emu", "Stanford_University") should
 * return something like:<br>
 * [Emu, Food_and_drug_adminstration, Duke_University, Stanford_University] <br>
 * <br>
 * The ladder returned here because there is a wiki link a
 * href="/wiki/Food_and_drug_administration" on the wikipedia page for Emu. And
 * on that page for the FDA, there's a wiki link to Duke University. And on the
 * Duke page, there's a wiki link to Stanford University. <br>
 * <br>
 * Requires JDK 11+
 *
 *
 * @author Nicolas Winsten
 */
public class WikiRacer {

    private final WikiScraper scraper;

    /**
     * Minimum number of pages referencing a title to consider that title to be a
     * sufficient anchor page
     */
    private final int anchorThreshold;

    private static final int DEFAULT_QUERY_LIMIT = 500;
    private static final int DEFAULT_ANCHOR_THRESHOLD = 1000;
    private static final int DEFAULT_FETCH_LIMIT = 2;

    /**
     * Construct a WikiRacer that uses the given parameters
     *
     * @param queryLimit      limit number of results from MediaWiki api call (max 500)
     * @param anchorThreshold minimum popularity for a page to be considered a
     *                        sufficient anchor
     *
     * @param fetchLimit maximum number of MediaWiki API calls allotted to each query
     */
    public WikiRacer(int queryLimit, int anchorThreshold, int fetchLimit) {
        if (queryLimit < 1 || anchorThreshold < 1 || fetchLimit < 1)
            throw new IllegalArgumentException("Parameters queryLimit and anchorThreshold and fetchLimit must be positive");

        if (queryLimit * 500 < anchorThreshold)
            throw new IllegalArgumentException("Given query limit must be able to achieve anchor threshold");

        this.anchorThreshold = anchorThreshold;
        scraper = new WikiScraper(queryLimit, fetchLimit);
    }

    /**
     * Construct WikiRacer with default parameters
     */
    public WikiRacer() {
        this(DEFAULT_QUERY_LIMIT, DEFAULT_ANCHOR_THRESHOLD, DEFAULT_FETCH_LIMIT);
    }

    /**
     * Build and return the WikiLadder between the two given pages. Given titles
     * should conform to Wikipedia's naming rules. Titles that aren't capitalized
     * correctly will commonly raise a FileNotFound exception
     *
     * @param start title of the starting page
     * @param end   title of the destination page
     * @return Sequence of clickable wikilinks that can bring you from the
     *         <var>start</var> page to <var>end</var> page
     */
    public List<String> findWikiLadder(String start, String end) {
        return complete(anchor(new WikiLadder(start, end))).toList();
    }

    /**
     * Take a WikiLadder and returns an anchored version. This means to make it's
     * lowest upper rung more reachable. For a WikiLadder, this means make the
     * lowest upper rung be a wikipage that will be easier to link to. This function
     * will build the ladder from the top (back) until it finds a page of sufficient
     * popularity. A RuntimeException will be thrown if the given ladder cannot be
     * anchored. This occurs if the end page is not referenced much by other
     * articles.
     *
     *
     * @param ladder WikiLadder to anchor
     * @return anchored version of the given ladder.
     */
    private WikiLadder anchor(WikiLadder ladder) {
        if (ladder.isAnchored())
            return ladder;

        // create priority queue that will order prospective anchors by their popularity
        // (number of pages linking to them)
        PriorityQueue<WikiLadder> q = new PriorityQueue<>(
                Comparator.comparing(WikiLadder::getUpperRung, (s1, s2) -> Integer.compare(scraper.popularity(s2), scraper.popularity(s1))));
        q.add(ladder);

        while (!q.isEmpty()) {
            WikiLadder bestSoFar = q.remove();
            System.out.println("best anchor: " + bestSoFar);
            String upper = bestSoFar.getUpperRung();

            for (String rung : getLinksTo(upper)) {
                // skip over YEAR_in_PLACE pages because only similar pages link to them
                // Ex: 1809_in_Denmark
                // TODO gets lost in pages like 1995 Men's Curling Championship.
                // figure out good pattern to weed out patterns like this
                if (rung.matches("[0-9]+ in .*"))
                    continue;

                WikiLadder newLadder = new WikiLadder(bestSoFar);
                newLadder.addUpperRung(rung);

                if (newLadder.isAnchored())
                    return newLadder;

                q.add(newLadder);
            }
        }

        // if the given ladder could not be anchored, just return the original ladder
        return ladder;
    }

    /**
     * Add rungs to the lower section of a given Ladder until it becomes complete.
     * Returns new Ladder. Given Ladder is unchanged.
     *
     * @param ladder WikiLadder to complete
     * @return the completed WikiLadder
     */
    private WikiLadder complete(WikiLadder ladder) {
        if (ladder.isComplete()) {
            // if a link to the end page is on the start page,
            // just return the two step ladder
            return ladder;
        }

        // recall known links to the upper rung and use them to cast a wider net for a
        // completed path
        Set<String> net = getLinksTo(ladder.getUpperRung());

        PriorityQueue<WikiLadder> q = new PriorityQueue<>();
        // set to keep track of already visited pages
        Set<String> visited = new HashSet<>();
        visited.add(ladder.getLowerRung());
        q.add(ladder);

        while (!q.isEmpty()) {
            // get the closest ladder's last wiki page
            WikiLadder bestSoFar = q.remove();
            System.out.println("best so far: " + bestSoFar);

            // iterate through the neighboring wiki pages, and create new ladders
            // for each by adding each neighbor page as the last rung
            for (String neighborPage : getLinksOn(bestSoFar.getLowerRung())) {
                if (!visited.contains(neighborPage)) {
                    WikiLadder newLadder = new WikiLadder(bestSoFar);
                    newLadder.addLowerRung(neighborPage);
                    // add the new ladder to the queue with its priority being the
                    // number of common links on the end destination wiki page and
                    // the new ladder's last wiki page.

                    if (newLadder.isComplete()) {
                        // this page has the end link, so return the valid ladder
                        return newLadder;
                    }

                    // check if net has caught the new page
                    for (String title : net)
                        if (scraper.hasLinkTo(neighborPage, title)) {
                            newLadder.addLowerRung(title);
                            return newLadder;
                        }

                    q.add(newLadder);
                    visited.add(neighborPage);
                }
            }
        }

        // if the above while loop exits, then no ladder could be found linking
        // the given wiki pages, so return empty ladder
        return ladder;

    }

    /**
     * @param title Wikipedia page title
     * @return set of all titles that can be accessed through wikilinks on the given
     *         page
     */
    private Set<String> getLinksOn(String title) {
        return scraper.getLinksOn(title);
    }

    /**
     * @param title Wikipedia page title
     * @return set of all titles that link to the given page
     */
    private Set<String> getLinksTo(String title) {
        return scraper.getLinksTo(title);
    }

    /**
     *
     * A WikiLadder is used by WikiRacer to build paths connecting Wikipedia pages
     * together through clickable wikilinks. Every WikiLadder has a start point and
     * a destination point. Wikipedia pages (specifically the String titles for the
     * pages) can be added to the bottom of the Ladder (building up from the start
     * rung) or to the top of the ladder (building down from the destination rung).
     * A WikiLadder is "complete" once two links in the middle meet.
     *
     * @author Nicolas Winsten
     *
     */
    private class WikiLadder extends Ladder<String> implements Comparable<WikiLadder> {

        /**
         * Measure of "completeness" for this WikiLadder. If the Ladder is complete,
         * proximity is INTEGER.MAX_VALUE, otherwise it is the number of links that are
         * shared between the upper and lower rungs
         */
        private int proximity;

        /**
         * Construct new WikiLadder
         *
         * @param startLink
         * @param endLink
         */
        public WikiLadder(String startLink, String endLink) {
            super(WikiScraper.decodeTitle(startLink), WikiScraper.decodeTitle(endLink));
            updateProximity();
        }

        public WikiLadder(WikiLadder o) {
            super(o);
            updateProximity();
        }

        /**
         * Update the proximity of this Ladder to be the links shared between the
         * current highest bottom rung and lowest top rung. Execute everytime a rung is
         * added to this ladder.
         *
         * If the Ladder is complete, then proximity should be considered maximal
         * (Integer.MAX_VALUE)
         */
        private void updateProximity() {
            if (isComplete())
                proximity = Integer.MAX_VALUE;
            else
                proximity = scraper.linksInCommon(getLowerRung(), getUpperRung());
        }

        /**
         * A WikiLadder is complete if all its wikilinks all chain together
         * successfully.
         *
         * @return True if this Ladder represents a complete sequence of Rungs that all
         *         link together.
         */
        @Override
        public boolean isComplete() {
            // If start and destination are equal, Ladder is complete
            if (start.equalsIgnoreCase(end))
                return true;

            // The Ladder is complete when the rung connected to the start can be connected
            // to the rung connected to the end
            return canLinkTo(getLowerRung(), getUpperRung());
        }


        /**
         * defines natural ordering of WikiLadder objects to be descending order by
         * Ladder "completeness". A Ladder that is closer to being finished is ordered
         * before a Ladder that is less complete. For Ladders that are equally complete,
         * order by height (shortest first).
         */
        @Override
        public int compareTo(WikiLadder o) {
            if (this.proximity > o.proximity)
                return -1;
            else if (this.proximity < o.proximity)
                return 1;
            else
                return Integer.compare(this.height(), o.height());
        }

        /**
         * Returns true if the given source wikipage title contains a wikilink to the
         * dest title page
         *
         * @param source
         * @param dest
         * @return true if source directly links to dest
         */
        @Override
        protected boolean canLinkTo(String source, String dest) {
            return scraper.hasLinkTo(source, dest);
        }

        /**
         * Add new link on top of the links built up from the source link for this
         * WikiLadder
         *
         * @param title Wikipage title to add to this WikiLadder
         */
        @Override
        public void addLowerRung(String title) {
            super.addLowerRung(title);
            updateProximity();
        }

        /**
         * Add new link to the bottom of the links connecting to the end link
         *
         * @param title Wikipage title to add to this WikiLadder
         */
        @Override
        public void addUpperRung(String title) {
            super.addUpperRung(title);
            updateProximity();
        }

        /**
         * A WikiLadder is considered to be anchored when the popularity of its
         * getUpperRung() is sufficient. In this case, the rung is a sufficient anchor
         * if there are <var>queryLimit</var>*500 known pages that link to it. We
         * multiply by 500 because 500 titles are returned by MediaWiki per API call.
         *
         * @return true if this WikiLadder is "anchored"
         */
        public boolean isAnchored() {
            return isComplete() || getLinksTo(getUpperRung()).size() >= anchorThreshold;
        }

    }

}
