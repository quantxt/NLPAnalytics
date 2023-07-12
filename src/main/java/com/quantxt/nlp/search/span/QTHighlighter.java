package com.quantxt.nlp.search.span;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Objects;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import org.apache.lucene.search.highlight.*;
import org.apache.lucene.util.PriorityQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class QTHighlighter {
    final private static Logger logger = LoggerFactory.getLogger(QTHighlighter.class);

    public static final int DEFAULT_MAX_CHARS_TO_ANALYZE = 50 * 1024;

    private QSimpleHTMLFormatter formatter;
    private Encoder encoder;
    private QScorer fragmentScorer;
    private int maxDocCharsToAnalyze = DEFAULT_MAX_CHARS_TO_ANALYZE;
    private Fragmenter textFragmenter = new SimpleFragmenter();

    private ArrayList<QToken> tokenList = new ArrayList<>();

    public QTHighlighter(QScorer fragmentScorer) {
        this(new QSimpleHTMLFormatter(), fragmentScorer);
    }

    public QTHighlighter(QSimpleHTMLFormatter formatter, QScorer fragmentScorer) {
        this(formatter, new DefaultEncoder(), fragmentScorer);
    }

    public QTHighlighter(QSimpleHTMLFormatter formatter, Encoder encoder, QScorer fragmentScorer) {
        ensureArgumentNotNull(formatter, "'formatter' must not be null");
        ensureArgumentNotNull(encoder, "'encoder' must not be null");
        ensureArgumentNotNull(fragmentScorer, "'fragmentScorer' must not be null");

        this.formatter = formatter;
        this.encoder = encoder;
        this.fragmentScorer = fragmentScorer;
    }

    /**
     * Highlights chosen terms in a text, extracting the most relevant section. This is a convenience
     * method that calls {@link #getBestFragment(TokenStream, String)}
     *
     * @param analyzer  the analyzer that will be used to split <code>text</code> into chunks
     * @param text      text to highlight terms in
     * @param fieldName Name of field used to influence analyzer's tokenization policy
     * @return highlighted text fragment or null if no terms found
     * @throws InvalidTokenOffsetsException thrown if any token's endOffset exceeds the provided
     *                                      text's length
     */
    public final String getBestFragment(Analyzer analyzer, String fieldName, String text)
            throws IOException, InvalidTokenOffsetsException {
        TokenStream tokenStream = analyzer.tokenStream(fieldName, text);
        return getBestFragment(tokenStream, text);
    }

    /**
     * Highlights chosen terms in a text, extracting the most relevant section. The document text is
     * analysed in chunks to record hit statistics across the document. After accumulating stats, the
     * fragment with the highest score is returned
     *
     * @param tokenStream a stream of tokens identified in the text parameter, including offset
     *                    information. This is typically produced by an analyzer re-parsing a document's text. Some
     *                    work may be done on retrieving TokenStreams more efficiently by adding support for storing
     *                    original text position data in the Lucene index but this support is not currently available
     *                    (as of Lucene 1.4 rc2).
     * @param text        text to highlight terms in
     * @return highlighted text fragment or null if no terms found
     * @throws InvalidTokenOffsetsException thrown if any token's endOffset exceeds the provided
     *                                      text's length
     */
    public final String getBestFragment(TokenStream tokenStream, String text)
            throws IOException, InvalidTokenOffsetsException {
        String[] results = getBestFragments(tokenStream, text, 1);
        if (results.length > 0) {
            return results[0];
        }
        return null;
    }

    /**
     * Highlights chosen terms in a text, extracting the most relevant sections. This is a convenience
     * method that calls {@link #getBestFragments(TokenStream, String, int)}
     *
     * @param analyzer        the analyzer that will be used to split <code>text</code> into chunks
     * @param fieldName       the name of the field being highlighted (used by analyzer)
     * @param text            text to highlight terms in
     * @param maxNumFragments the maximum number of fragments.
     * @return highlighted text fragments (between 0 and maxNumFragments number of fragments)
     * @throws InvalidTokenOffsetsException thrown if any token's endOffset exceeds the provided
     *                                      text's length
     */
    public final String[] getBestFragments(
            Analyzer analyzer, String fieldName, String text, int maxNumFragments)
            throws IOException, InvalidTokenOffsetsException {
        TokenStream tokenStream = analyzer.tokenStream(fieldName, text);
        return getBestFragments(tokenStream, text, maxNumFragments);
    }

    /**
     * Highlights chosen terms in a text, extracting the most relevant sections. The document text is
     * analysed in chunks to record hit statistics across the document. After accumulating stats, the
     * fragments with the highest scores are returned as an array of strings in order of score
     * (contiguous fragments are merged into one in their original order to improve readability)
     *
     * @param text            text to highlight terms in
     * @param maxNumFragments the maximum number of fragments.
     * @return highlighted text fragments (between 0 and maxNumFragments number of fragments)
     * @throws InvalidTokenOffsetsException thrown if any token's endOffset exceeds the provided
     *                                      text's length
     */
    public final String[] getBestFragments(TokenStream tokenStream, String text, int maxNumFragments)
            throws IOException, InvalidTokenOffsetsException {
        maxNumFragments = Math.max(1, maxNumFragments); // sanity check

        QTextFragment[] frag = getBestTextFragments(tokenStream, text, true, maxNumFragments);

        // Get text
        ArrayList<String> fragTexts = new ArrayList<>();
        for (int i = 0; i < frag.length; i++) {
            if ((frag[i] != null) && (frag[i].getScore() > 0)) {
                fragTexts.add(frag[i].toString());
            }
        }
        return fragTexts.toArray(new String[0]);
    }

    public ArrayList<QToken> getTokenList() {
        return tokenList;
    }

    /**
     * Low level api to get the most relevant (formatted) sections of the document. This method has
     * been made public to allow visibility of score information held in TextFragment objects. Thanks
     * to Jason Calabrese for help in redefining the interface.
     *
     * @throws IOException                  If there is a low-level I/O error
     * @throws InvalidTokenOffsetsException thrown if any token's endOffset exceeds the provided
     *                                      text's length
     */
    public final QTextFragment[] getBestTextFragments(
            TokenStream tokenStream, String text, boolean mergeContiguousFragments, int maxNumFragments)
            throws IOException, InvalidTokenOffsetsException {
        ArrayList<QTextFragment> docFrags = new ArrayList<>();

        StringBuilder newText = new StringBuilder();

        CharTermAttribute termAtt = tokenStream.addAttribute(CharTermAttribute.class);
        OffsetAttribute offsetAtt = tokenStream.addAttribute(OffsetAttribute.class);
        PositionIncrementAttribute postIncAtt = tokenStream.addAttribute(PositionIncrementAttribute.class);

        QTextFragment currentFrag = new QTextFragment(newText, newText.length(), docFrags.size(), 0, 0);

        if (fragmentScorer instanceof QTScorer) {
            (fragmentScorer).setMaxDocCharsToAnalyze(maxDocCharsToAnalyze);
        }

        TokenStream newStream = fragmentScorer.init(tokenStream);
        if (newStream != null) {
            tokenStream = newStream;
        }
        fragmentScorer.startFragment(currentFrag);
        docFrags.add(currentFrag);

        FragmentQueue fragQueue = new FragmentQueue(maxNumFragments);


        String tokenText;
        int startOffset;
        int endOffset;
        int lastEndOffset = 0;
        textFragmenter.start(text, tokenStream);

        QTokenGroup tokenGroup = new QTokenGroup(tokenStream);

        int cur_pos = 0;
        int next_pos = 0;
        int old_posinc = 1;
        tokenStream.reset();
        for (boolean next = tokenStream.incrementToken(); next && (offsetAtt.startOffset() < maxDocCharsToAnalyze); next = tokenStream.incrementToken()) {
            if ((offsetAtt.endOffset() > text.length()) || (offsetAtt.startOffset() > text.length())) {
                tokenStream.end();
                tokenStream.close();
                throw new InvalidTokenOffsetsException(
                        "Token " + termAtt.toString() + " exceeds length of provided text sized " + text.length());
            }
            if ((tokenGroup.getNumTokens() > 0) && (tokenGroup.isDistinct())) {
                // the current token is distinct from previous tokens -
                // markup the cached token group info
                startOffset = tokenGroup.getStartOffset();
                endOffset = tokenGroup.getEndOffset();
                tokenText = text.substring(startOffset, endOffset);

                int posInc = postIncAtt.getPositionIncrement();
                if (posInc > 0) {
                    cur_pos = next_pos;
                    next_pos += posInc;
                }

                String markedUpText = formatter.highlightTerm(encoder.encodeText(tokenText), tokenGroup);
                if (tokenGroup.getTotalScore() > 0) {
                    QToken qToken = new QToken(text, startOffset, endOffset, old_posinc, cur_pos);
                    tokenList.add(qToken);
                }

                old_posinc = posInc;

                // store any whitespace etc from between this and last group
                if (startOffset > lastEndOffset)
                    newText.append(encoder.encodeText(text.substring(lastEndOffset, startOffset)));
                newText.append(markedUpText);
                lastEndOffset = Math.max(endOffset, lastEndOffset);

                // check if current token marks the start of a new fragment
                if (textFragmenter.isNewFragment()) {
                    currentFrag.setScore(fragmentScorer.getFragmentScore());
                    // record stats for a new fragment
                    currentFrag.textEndPos = newText.length();
                    currentFrag = new QTextFragment(newText, newText.length(),
                            docFrags.size(), startOffset, endOffset);

                    fragmentScorer.startFragment(currentFrag);
                    docFrags.add(currentFrag);
                }
                tokenGroup.clear();
            }

            tokenGroup.addToken(fragmentScorer.getTokenScore());
        }

        tokenStream.end();
        tokenStream.close();

        currentFrag.setScore(fragmentScorer.getFragmentScore());

        if (tokenGroup.getNumTokens() > 0) {
            // flush the accumulated text (same code as in above loop)
            startOffset = tokenGroup.getStartOffset();
            endOffset = tokenGroup.getEndOffset();
            tokenText = text.substring(startOffset, endOffset);
            String markedUpText = formatter.highlightTerm(encoder.encodeText(tokenText), tokenGroup);

            if (tokenGroup.getTotalScore() > 0) {
                QToken qToken = new QToken(text, startOffset, endOffset, old_posinc, cur_pos);
                tokenList.add(qToken);
            }
            // store any whitespace etc from between this and last group
            if (startOffset > lastEndOffset)
                newText.append(encoder.encodeText(text.substring(lastEndOffset, startOffset)));
            newText.append(markedUpText);
            lastEndOffset = Math.max(lastEndOffset, endOffset);
        }

        // Test what remains of the original text beyond the point where we stopped analyzing
        if (
            //          if there is text beyond the last token considered..
                (lastEndOffset < text.length())
                        &&
                        //          and that text is not too large...
                        (text.length() <= maxDocCharsToAnalyze)) {
            // append it to the last fragment
            newText.append(encoder.encodeText(text.substring(lastEndOffset)));
        }

        currentFrag.textEndPos = newText.length();

        // sort the most relevant sections of the text
        for (Iterator<QTextFragment> i = docFrags.iterator(); i.hasNext(); ) {
            currentFrag = i.next();
            fragQueue.insertWithOverflow(currentFrag);
        }

        // return the most relevant fragments
        QTextFragment[] frag = new QTextFragment[fragQueue.size()];
        for (int i = frag.length - 1; i >= 0; i--) {
            frag[i] = fragQueue.pop();
        }

        // merge any contiguous fragments to improve readability
        if (mergeContiguousFragments) {
            frag = mergeContiguousFragments(frag);
        }
        return frag;
    }

    private QTextFragment[] mergeContiguousFragments(QTextFragment[] frag) {
        mergeContiguousFragmentsHelper(frag);
        ArrayList<QTextFragment> fragTexts = new ArrayList<>();
        for (int i = 0; i < frag.length; i++) {
            if ((frag[i] != null) && (frag[i].getScore() > 0)) {
                fragTexts.add(frag[i]);
            }
        }
        QTextFragment[] mergedFrag = fragTexts.toArray(new QTextFragment[0]);
        return mergedFrag;
    }

    /**
     * Improves readability of a score-sorted list of TextFragments by merging any fragments that were
     * contiguous in the original text into one larger fragment with the correct order. This will
     * leave a "null" in the array entry for the lesser scored fragment.
     *
     * @param frag An array of document fragments in descending score
     */
    private void mergeContiguousFragmentsHelper(QTextFragment[] frag) {
        boolean mergingStillBeingDone;
        if (frag.length > 1)
            do {
                mergingStillBeingDone = false; // initialise loop control flag
                // for each fragment, scan other frags looking for contiguous blocks
                for (int i = 0; i < frag.length; i++) {
                    if (frag[i] == null) {
                        continue;
                    }
                    // merge any contiguous blocks
                    for (int x = 0; x < frag.length; x++) {
                        if (frag[x] == null) {
                            continue;
                        }
                        if (frag[i] == null) {
                            break;
                        }
                        QTextFragment frag1 = null;
                        QTextFragment frag2 = null;
                        int frag1Num = 0;
                        int frag2Num = 0;
                        int bestScoringFragNum;
                        int worstScoringFragNum;
                        // if blocks are contiguous....
                        // x .... i
                        if (frag[i].follows(frag[x])) {
                            frag1 = frag[x];
                            frag1Num = x;
                            frag2 = frag[i];
                            frag2Num = i;
                        } else if (frag[x].follows(frag[i])) {
                            frag1 = frag[i];
                            frag1Num = i;
                            frag2 = frag[x];
                            frag2Num = x;
                        }
                        // merging required..
                        if (frag1 != null) {
                            if (frag1.getScore() > frag2.getScore()) {
                                bestScoringFragNum = frag1Num;
                                worstScoringFragNum = frag2Num;
                            } else {
                                bestScoringFragNum = frag2Num;
                                worstScoringFragNum = frag1Num;
                            }
                            frag1.merge(frag2);
                            frag[worstScoringFragNum] = null;
                            mergingStillBeingDone = true;
                            frag[bestScoringFragNum] = frag1;
                        }
                    }
                }
            } while (mergingStillBeingDone);
    }

    /**
     * Highlights terms in the text , extracting the most relevant sections and concatenating the
     * chosen fragments with a separator (typically "..."). The document text is analysed in chunks to
     * record hit statistics across the document. After accumulating stats, the fragments with the
     * highest scores are returned in order as "separator" delimited strings.
     *
     * @param text            text to highlight terms in
     * @param maxNumFragments the maximum number of fragments.
     * @param separator       the separator used to intersperse the document fragments (typically "...")
     * @return highlighted text
     * @throws InvalidTokenOffsetsException thrown if any token's endOffset exceeds the provided
     *                                      text's length
     */
    public final String getBestFragments(
            TokenStream tokenStream, String text, int maxNumFragments, String separator)
            throws IOException, InvalidTokenOffsetsException {
        String[] sections = getBestFragments(tokenStream, text, maxNumFragments);
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < sections.length; i++) {
            if (i > 0) {
                result.append(separator);
            }
            result.append(sections[i]);
        }
        return result.toString();
    }

    public int getMaxDocCharsToAnalyze() {
        return maxDocCharsToAnalyze;
    }

    public void setMaxDocCharsToAnalyze(int maxDocCharsToAnalyze) {
        this.maxDocCharsToAnalyze = maxDocCharsToAnalyze;
    }

    public Fragmenter getTextFragmenter() {
        return textFragmenter;
    }

    public void setTextFragmenter(Fragmenter fragmenter) {
        textFragmenter = Objects.requireNonNull(fragmenter);
    }

    /**
     * @return Object used to score each text fragment
     */
    public QScorer getFragmentScorer() {
        return fragmentScorer;
    }

    public void setFragmentScorer(QScorer scorer) {
        fragmentScorer = Objects.requireNonNull(scorer);
    }

    public Encoder getEncoder() {
        return encoder;
    }

    public void setEncoder(Encoder encoder) {
        this.encoder = Objects.requireNonNull(encoder);
    }

    /**
     * Throws an IllegalArgumentException with the provided message if 'argument' is null.
     *
     * @param argument the argument to be null-checked
     * @param message  the message of the exception thrown if argument == null
     */
    private static void ensureArgumentNotNull(Object argument, String message) {
        if (argument == null) {
            throw new IllegalArgumentException(message);
        }
    }

    static class FragmentQueue extends PriorityQueue<QTextFragment> {
        FragmentQueue(int size) {
            super(size);
        }

        @Override
        public final boolean lessThan(QTextFragment fragA, QTextFragment fragB) {
            if (fragA.getScore() == fragB.getScore()) return fragA.fragNum > fragB.fragNum;
            else return fragA.getScore() < fragB.getScore();
        }
    }
}