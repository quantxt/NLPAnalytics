package com.quantxt.nlp.search.span;

import com.quantxt.model.ExtInterval;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import org.apache.lucene.search.highlight.*;
import org.apache.lucene.search.spans.SpanQuery;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class QTextFragment {
    int startOffset;
    int endOffset;
    CharSequence markedUpText;
    int fragNum;
    int textStartPos;
    int textEndPos;
    float score;

    public int getTextEndPos() {
        return textEndPos;
    }

    public void setTextEndPos(int textEndPos) {
        this.textEndPos = textEndPos;
    }

    public int getStartOffset() {
        return startOffset;
    }

    public int getEndOffset() {
        return endOffset;
    }

    public QTextFragment(CharSequence markedUpText, int textStartPos, int fragNum, int startOffset, int endOffset) {
        this.markedUpText = markedUpText;
        this.textStartPos = textStartPos;
        this.fragNum = fragNum;
        this.startOffset = startOffset;
        this.endOffset = endOffset;
    }

    public void merge(QTextFragment frag2) {
        textEndPos = frag2.textEndPos;
        score = Math.max(score, frag2.score);
    //    endOffset = frag2.endOffset; // assuming test is left to right
    }

    public boolean follows(QTextFragment fragment) {
        return textStartPos == fragment.textEndPos;
    }

    public void setScore(float s){
        score = s;
    }

    public float getScore() {
        return score;
    }

    public int getFragNum() {
        return fragNum;
    }

    public int getTextStartPos() {
        return textStartPos;
    }

    public void setTextStartPos(int textStartPos) {
        this.textStartPos = textStartPos;
    }

    @Override
    public String toString() {
        return markedUpText.subSequence(textStartPos, textEndPos).toString();
    }


    //Assumption: Left to Right Text!
    private static boolean qtFollows(QToken token1,
                                     QToken token2){
        if (token2.end < token1.start) return false;
        if (token1.pos + token1.postInc == token2.pos) return true;
        return false;
    }

    public static List<ExtInterval> getBestTextFragments(TokenStream tokenStream,
                                                         SpanQuery query,
                                                         String text,
                                                         String category,
                                                         String dictionary_name,
                                                         String dictionary_id) throws IOException, InvalidTokenOffsetsException
    {

        List<QToken> tokenList = new ArrayList<>();
        QueryScorer fragmentScorer = new QueryScorer(query);
    //    Fragmenter textFragmenter = new SimpleSpanFragmenter(fragmentScorer, Integer.MAX_VALUE);
        Fragmenter textFragmenter = new NullFragmenter();

        OffsetAttribute offsetAtt = tokenStream.addAttribute(OffsetAttribute.class);
        PositionIncrementAttribute postIncAtt = tokenStream.addAttribute(PositionIncrementAttribute.class);
        fragmentScorer.setMaxDocCharsToAnalyze(Integer.MAX_VALUE);

        TokenStream newStream = fragmentScorer.init(tokenStream);
        if(newStream != null) {
            tokenStream = newStream;
        }

        fragmentScorer.startFragment(null);

        try
        {

            textFragmenter.start(text, tokenStream);
            QTokenGroup tokenGroup = new QTokenGroup(tokenStream);
            tokenStream.reset();
            int cur_pos  = 0;
            int next_pos = 0;
            for (boolean next = tokenStream.incrementToken(); next; next = tokenStream.incrementToken())
            {
                if( offsetAtt.endOffset()>text.length() || offsetAtt.startOffset()>text.length() )
                {
                    CharTermAttribute termAtt = tokenStream.addAttribute(CharTermAttribute.class);
                    throw new InvalidTokenOffsetsException("Token "+ termAtt.toString()
                            +" exceeds length of provided text sized "+text.length());
                }
                if( tokenGroup.getNumTokens() >0 && tokenGroup.isDistinct() )
                {
                    tokenGroup.clear();
                    //check if current token marks the start of a new fragment
                    if(textFragmenter.isNewFragment())
                    {
                        fragmentScorer.startFragment(null);
                    }
                }

                int posInc = postIncAtt.getPositionIncrement();
                if ( posInc > 0 ) {
                    cur_pos = next_pos;
                    next_pos += posInc;
                }
                float score = fragmentScorer.getTokenScore();
                if (score > 0){
                    QToken qToken = new QToken(text, offsetAtt, postIncAtt, cur_pos);
                    tokenList.add(qToken);
                }
                tokenGroup.addToken(score);
            }

            ExtInterval [] matchList = mergeContiguousFragments(tokenList, category,
                    dictionary_name,
                    dictionary_id);

            List<ExtInterval> nonNullmatches = new ArrayList<>();
            for (ExtInterval extInterval : matchList){
                if (extInterval == null) continue;
                extInterval.setStr(text.substring(extInterval.getStart(), extInterval.getEnd()));
                nonNullmatches.add(extInterval);
            }

            return nonNullmatches;
        }

        finally
        {
            if (tokenStream != null)
            {
                try
                {
                    tokenStream.end();
                    tokenStream.close();
                }
                catch (Exception e)
                {
                }
            }
        }
    }

    public static ExtInterval[]  mergeContiguousFragments(List<QToken> qTokens,
                                                           String category,
                                                           String dictionary_name,
                                                           String dictionary_id)
    {
        ExtInterval [] matches = new ExtInterval[qTokens.size()];
        for (int i=0; i< qTokens.size(); i++){
            int startOffset = qTokens.get(i).start;
            int endOffset = qTokens.get(i).end;
            String keyword = qTokens.get(i).str;
            ExtInterval qtMatch = new ExtInterval(startOffset, endOffset);
            qtMatch.setStr(keyword);
            qtMatch.setCategory(category);
            qtMatch.setDict_name(dictionary_name);
            qtMatch.setDict_id(dictionary_id);
            matches[i] = qtMatch;
        }
        if (qTokens.size() < 2) return matches;
        boolean mergingStillBeingDone;

        do
        {
            mergingStillBeingDone = false; //initialise loop control flag
            for (int i = 0; i < qTokens.size(); i++)
            {
                if (matches[i] == null) continue;
                QToken qToken_i = qTokens.get(i);
                //merge any contiguous blocks
                for (int x = 0; x < qTokens.size(); x++)
                {
                    if (i == x) continue;
                    if (matches[x] == null) continue;
                    if (matches[i] == null) break;

                    //if blocks are contiguous....
                    QToken qToken_x = qTokens.get(x);
                    if (qtFollows(qToken_x, qToken_i))
                    {
                        // match_x  match_i
                        matches[i].setStart(qToken_x.start);
                        qToken_x.postInc += qToken_i.postInc;
                        String new_keyword = qToken_i.str;
                        matches[i].setStr(new_keyword);
                        matches[x] = null;
                    }
                    else if ( qtFollows(qToken_i, qToken_x))
                    {
                        // match_i match_x
                        matches[i].setEnd(qToken_x.end);
                        qToken_i.postInc += qToken_x.postInc;
                        String new_keyword = qToken_i.str;
                        matches[i].setStr(new_keyword);
                        matches[x] = null;
                    }
                }
            }
        }
        while (mergingStillBeingDone);
       return matches;
    }
}
