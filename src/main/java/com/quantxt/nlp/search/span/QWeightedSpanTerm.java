package com.quantxt.nlp.search.span;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class QWeightedSpanTerm extends QWeightedTerm {
    boolean positionSensitive;
    private List<QPositionSpan> positionSpans = new ArrayList<>();

    public QWeightedSpanTerm(float weight, String term) {
        super(weight, term);
        this.positionSpans = new ArrayList<>();
    }

    public QWeightedSpanTerm(float weight, String term, boolean positionSensitive) {
        super(weight, term);
        this.positionSensitive = positionSensitive;
    }

    /**
     * Checks to see if this term is valid at <code>position</code>.
     *
     * @param position to check against valid term positions
     * @return true iff this term is a hit at this position
     */
    public boolean checkPosition(int position) {
        // There would probably be a slight speed improvement if PositionSpans
        // where kept in some sort of priority queue - that way this method
        // could
        // bail early without checking each PositionSpan.
        Iterator<QPositionSpan> positionSpanIt = positionSpans.iterator();

        while (positionSpanIt.hasNext()) {
            QPositionSpan posSpan = positionSpanIt.next();

            if (((position >= posSpan.start) && (position <= posSpan.end))) {
                return true;
            }
        }

        return false;
    }

    public void addPositionSpans(List<QPositionSpan> positionSpans) {
        this.positionSpans.addAll(positionSpans);
    }

    public boolean isPositionSensitive() {
        return positionSensitive;
    }

    public void setPositionSensitive(boolean positionSensitive) {
        this.positionSensitive = positionSensitive;
    }

    public List<QPositionSpan> getPositionSpans() {
        return positionSpans;
    }
}