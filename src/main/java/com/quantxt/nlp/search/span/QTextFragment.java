package com.quantxt.nlp.search.span;

import com.quantxt.model.ExtInterval;

import java.util.List;

public class QTextFragment {
    int startOffset;
    int endOffset;
    CharSequence markedUpText;
    int fragNum;
    int textStartPos;
    int textEndPos;
    float score;

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

    public static ExtInterval[] mergeContiguousFragments(List<QToken> qTokens,
                                                         int max_merge,
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
                    if (qToken_x.num_merged < max_merge && qtFollows(qToken_x, qToken_i))
                    {
                        // match_x  match_i
                        matches[i].setStart(qToken_x.start);
                        qToken_x.postInc += qToken_i.postInc;
                        qToken_x.num_merged++;
                        //TODO: Is this right?
                        String new_keyword = qToken_i.str; // ??
                        matches[i].setStr(new_keyword);
                        matches[x] = null;
                    }
                    else if ( qToken_i.num_merged < max_merge && qtFollows(qToken_i, qToken_x))
                    {
                        // match_i match_x
                        matches[i].setEnd(qToken_x.end);
                        qToken_i.postInc += qToken_x.postInc;
                        qToken_i.num_merged++;
                        String new_keyword = qToken_i.str;
                        //TODO: Is this right?
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
