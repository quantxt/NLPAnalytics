package com.quantxt.nlp.search;

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;

public class QTokenGroup {
    private static final int MAX_NUM_TOKENS_PER_GROUP = 50;

    private float[] scores = new float[MAX_NUM_TOKENS_PER_GROUP];
    private int numTokens = 0;
    private int startOffset = 0;
    private int endOffset = 0;
    private float tot;
    private int matchStartOffset;
    private int matchEndOffset;

    private OffsetAttribute offsetAtt;
    private CharTermAttribute termAtt;

    public QTokenGroup(TokenStream tokenStream) {
        offsetAtt = tokenStream.addAttribute(OffsetAttribute.class);
        termAtt = tokenStream.addAttribute(CharTermAttribute.class);
    }

    void addToken(float score) {
        if (numTokens < MAX_NUM_TOKENS_PER_GROUP) {
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

            scores[numTokens] = score;
            numTokens++;
        }
    }

    boolean isDistinct() {
        return offsetAtt.startOffset() >= endOffset;
    }

    void clear() {
        numTokens = 0;
        tot = 0;
    }

    /**
     *
     * @param index a value between 0 and numTokens -1
     * @return the "n"th score
     */
    public float getScore(int index) {
        return scores[index];
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
