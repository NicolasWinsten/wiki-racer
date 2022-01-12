package com.nicolaswinsten.wikiracer;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * A Ladder is a sequence of elements that can be "climbed", meaning the first
 * rung (element) can bring you to the second, the second can bring you to the
 * third, and so on. A Ladder is different from a singly linked list because a
 * Ladder starts out empty except for its first and last rung. To complete the
 * Ladder, you can stack rungs onto the first rung or hang rungs under the last
 * rung until the two rungs in the center can close the gap.
 *
 */
abstract class Ladder<R> {
    /**
     * Rung this Ladder is building from (bottom rung)
     */
    protected final R start;
    /**
     * Rung this Ladder is building towards (top rung)
     */
    protected final R end;

    /**
     * sequence of links building from the start link
     */
    protected final List<R> bottomRungs;
    /**
     * sequence of links building backwards from the end link
     */
    protected final List<R> upperRungs;

    /**
     * Construct new incomplete Ladder with given starting and ending rungs.
     *
     * @param startRung
     * @param endRung
     */
    public Ladder(R startRung, R endRung) {
        start = startRung;
        end = endRung;

        bottomRungs = new LinkedList<R>();
        upperRungs = new LinkedList<R>();
        bottomRungs.add(start);
        upperRungs.add(end);
    }

    /**
     * Construct copy of given Ladder.
     *
     * @param o Ladder to copy
     */
    public Ladder(Ladder<? extends R> o) {
        start = o.start;
        end = o.end;

        bottomRungs = new LinkedList<R>(o.bottomRungs);
        upperRungs = new LinkedList<R>(o.upperRungs);
    }

    /**
     * @return the highest rung connected to the bottom rung
     */
    public R getLowerRung() {
        return bottomRungs.get(bottomRungs.size() - 1);
    }

    /**
     * @return lowest rung connected to the top rung
     */
    public R getUpperRung() {
        return upperRungs.get(upperRungs.size() - 1);
    }

    /**
     * @return True if this Ladder represents a complete sequence of Rungs that all
     *         link together
     */
    public boolean isComplete() {
        return canLinkTo(getLowerRung(), getUpperRung());
    }

    /**
     * Returns true if the given Rung source can successfully connect to the given
     * Rung dest. This is what allows a Rung to be added to a Ladder.
     *
     * @param source Rung that might link to the other
     * @param dest   Rung that might be linked to
     * @return True if link can be done
     */
    abstract protected boolean canLinkTo(R source, R dest);

    /**
     * Use the given rung as the next highest rung in the bottom section of this
     * Ladder.
     *
     *
     * @param rung Rung to add to this Ladder
     */
    public void addLowerRung(R rung) {
        R lower = getLowerRung();
        if (rung.equals(lower))
            return; // NOP if the rung being added is already the lower rung

        if (!canLinkTo(lower, rung))
            throw new RuntimeException(
                    String.format("This Ladder is not possible. There is no link to %s from %s.", rung, lower));

        if (isComplete())
            throw new RuntimeException(String.format("You are trying to add to a completed Ladder: %s", this));

        bottomRungs.add(rung);
    }

    /**
     * Use the given rung as the next lowest rung in the top section of this Ladder.
     *
     * @param rung Rung to add to this Ladder
     */
    public void addUpperRung(R rung) {
        R upper = getUpperRung();
        if (rung.equals(upper))
            return; // NOP if the rung being added is already the upper rung

        if (!canLinkTo(rung, upper))
            throw new RuntimeException(
                    String.format("This Ladder is not possible. There is no link to %s from %s.", upper, rung));

        if (isComplete())
            throw new RuntimeException(String.format("You are trying to add to a completed Ladder: %s", this));

        upperRungs.add(rung);
    }

    /**
     * Return height of ladder (number of rungs excluding start and end). Ladders
     * with shorter height are preferred in natural ordering.
     *
     * @return int
     */
    public final int height() {
        return bottomRungs.size() + upperRungs.size() - 2;
    }

    /**
     * @return String description of this Ladder
     */
    public String toString() {
        String sep = ", ... , ";
        if (isComplete())
            sep = ", ";

        List<R> reverseUpperRungs = new LinkedList<R>(upperRungs);
        Collections.reverse(reverseUpperRungs); // reversed upper rungs to display in String
        return "[" + bottomRungs.stream().map(Object::toString).collect(Collectors.joining(", ")) + sep
                + reverseUpperRungs.stream().map(Object::toString).collect(Collectors.joining(", "))
                + "]";
    }

    /**
     * Construct List version of this Ladder. If the Ladder is not complete, a null
     * value will represent the unclosed gap in the Ladder.
     *
     * @return List version of this Ladder
     */
    public List<R> toList() {
        // return bottomRungs.stream()
        List<R> l = new LinkedList<R>(bottomRungs);

        // add null item to represent gap in this Ladder
        if (!isComplete())
            l.add(null);

        List<R> reverseUpperRungs = new LinkedList<R>(upperRungs);
        Collections.reverse(reverseUpperRungs); // reversed upper rungs to add to List version
        l.addAll(reverseUpperRungs);

        return l;
    }
}