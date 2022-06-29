package com.quantxt.nlp.search.span;

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;

public class QTokenGroup {
    private static final int MAX_NUM_TOKENS_PER_GROUP = 50;

    private int numTokens = 0;
    private int startOffset = 0;
    private int endOffset = 0;
    private float tot;
    private int matchStartOffset;
    private int matchEndOffset;

    final private OffsetAttribute offsetAtt;
    final PositionIncrementAttribute postIncAtt;

    public QTokenGroup(TokenStream tokenStream) {
        postIncAtt = tokenStream.addAttribute(PositionIncrementAttribute.class);
        offsetAtt = tokenStream.addAttribute(OffsetAttribute.class);
    }

    void addToken(float score) {
        if (numTokens > MAX_NUM_TOKENS_PER_GROUP) return;
        final int termStartOffset = offsetAtt.startOffset();
        final int termEndOffset = offsetAtt.endOffset();
        if (numTokens == 0) {
            startOffset = matchStartOffset = termStartOffset;
            endOffset = matchEndOffset = termEndOffset;
            tot += score;
        } else {
            startOffset = Math.min(startOffset, termStartOffset);
            endOffset = Math.max(endOffset, termEndOffset);
            if (score > 0) {
                if (tot == 0) {
                    matchStartOffset = termStartOffset;
                    matchEndOffset = termEndOffset;
                } else {
                    matchStartOffset = Math.min(matchStartOffset, termStartOffset);
                    matchEndOffset = Math.max(matchEndOffset, termEndOffset);
                }
                tot += score;
            }
        }

        numTokens++;
    }

    boolean isDistinct() {
        return offsetAtt.startOffset() >= endOffset;
    }

    void clear() {
        numTokens = 0;
        tot = 0;
    }

    /**
     * @return the earliest start offset in the original text of a matching token in this group (score &gt; 0), or
     * if there are none then the earliest offset of any token in the group.
     */
    public int getStartOffset() {
        return matchStartOffset;
    }

    /**
     * @return the latest end offset in the original text of a matching token in this group (score &gt; 0), or
     * if there are none then {@link #getEndOffset()}.
     */
    public int getEndOffset() {
        return matchEndOffset;
    }

    /**
     * @return the number of tokens in this group
     */
    public int getNumTokens() {
        return numTokens;
    }

    /**
     * @return all tokens' scores summed up
     */
    public float getTotalScore() {
        return tot;
    }
}
