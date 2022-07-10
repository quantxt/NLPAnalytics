package com.quantxt.nlp.search.span;

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import org.apache.lucene.search.highlight.Fragmenter;

import java.util.List;

public class QSimpleSpanFragmenter implements Fragmenter {
    private static final int DEFAULT_FRAGMENT_SIZE = 100;
    private int fragmentSize;
    private int currentNumFrags;
    private int position = -1;
    private QScorer queryScorer;
    private int waitForPos = -1;
    private int textSize;
    private CharTermAttribute termAtt;
    private PositionIncrementAttribute posIncAtt;
    private OffsetAttribute offsetAtt;

    /** @param queryScorer QueryScorer that was used to score hits */
    public QSimpleSpanFragmenter(QScorer queryScorer) {
        this(queryScorer, DEFAULT_FRAGMENT_SIZE);
    }

    /**
     * @param queryScorer QueryScorer that was used to score hits
     * @param fragmentSize size in chars of each fragment
     */
    public QSimpleSpanFragmenter(QScorer queryScorer, int fragmentSize) {
        this.fragmentSize = fragmentSize;
        this.queryScorer = queryScorer;
    }

    /* (non-Javadoc)
     * @see org.apache.lucene.search.highlight.Fragmenter#isNewFragment()
     */
    @Override
    public boolean isNewFragment() {
        position += posIncAtt.getPositionIncrement();

        if (waitForPos <= position) {
            waitForPos = -1;
        } else if (waitForPos != -1) {
            return false;
        }

        QWeightedSpanTerm wSpanTerm = queryScorer.getWeightedSpanTerm(termAtt.toString());

        if (wSpanTerm != null) {
            List<QPositionSpan> positionSpans = wSpanTerm.getPositionSpans();

            for (QPositionSpan positionSpan : positionSpans) {
                if (positionSpan.start == position) {
                    waitForPos = positionSpan.end + 1;
                    break;
                }
            }
        }

        boolean isNewFrag =
                offsetAtt.endOffset() >= (fragmentSize * currentNumFrags)
                        && (textSize - offsetAtt.endOffset()) >= (fragmentSize >>> 1);

        if (isNewFrag) {
            currentNumFrags++;
        }

        return isNewFrag;
    }

    /* (non-Javadoc)
     * @see org.apache.lucene.search.highlight.Fragmenter#start(java.lang.String, org.apache.lucene.analysis.TokenStream)
     */
    @Override
    public void start(String originalText, TokenStream tokenStream) {
        position = -1;
        currentNumFrags = 1;
        textSize = originalText.length();
        termAtt = tokenStream.addAttribute(CharTermAttribute.class);
        posIncAtt = tokenStream.addAttribute(PositionIncrementAttribute.class);
        offsetAtt = tokenStream.addAttribute(OffsetAttribute.class);
    }
}
