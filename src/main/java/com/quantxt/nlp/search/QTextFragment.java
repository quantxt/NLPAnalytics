package com.quantxt.nlp.search;

import com.quantxt.types.ExtInterval;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.search.highlight.*;
import org.apache.lucene.search.spans.SpanQuery;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class QTextFragment {

    private static boolean qtFollows(String str,
                                     ExtInterval match1,
                                     ExtInterval match2){
        if (match2.getStart() < match1.getEnd()) return false;
        String gap = str.substring(match1.getEnd()+1, match2.getStart());

        if (gap.replaceAll("[^A-Za-z0-9]+", "").trim().length() == 0) return true;
        return false;
    }

    public static List<ExtInterval> getBestTextFragments(TokenStream tokenStream,
                                                         SpanQuery query,
                                                         String text,
                                                         String category,
                                                         String dictionary_name,
                                                         String dictionary_id) throws IOException, InvalidTokenOffsetsException
    {
        List<ExtInterval> matchList = new ArrayList<>();
        QueryScorer fragmentScorer = new QueryScorer(query);
        Fragmenter textFragmenter = new SimpleSpanFragmenter(fragmentScorer, Integer.MAX_VALUE);

        CharTermAttribute termAtt = tokenStream.addAttribute(CharTermAttribute.class);
        OffsetAttribute offsetAtt = tokenStream.addAttribute(OffsetAttribute.class);
        fragmentScorer.setMaxDocCharsToAnalyze(Integer.MAX_VALUE);

        TokenStream newStream = fragmentScorer.init(tokenStream);
        if(newStream != null) {
            tokenStream = newStream;
        }

        fragmentScorer.startFragment(null);

        try
        {
            int startOffset;
            int endOffset;
            textFragmenter.start(text, tokenStream);

            QTokenGroup tokenGroup = new QTokenGroup(tokenStream);

            tokenStream.reset();
            for (boolean next = tokenStream.incrementToken(); next; next = tokenStream.incrementToken())
            {
                if( offsetAtt.endOffset()>text.length() || offsetAtt.startOffset()>text.length() )
                {
                    throw new InvalidTokenOffsetsException("Token "+ termAtt.toString()
                            +" exceeds length of provided text sized "+text.length());
                }
                if( tokenGroup.getNumTokens() >0 && tokenGroup.isDistinct() )
                {
                    float score = tokenGroup.getTotalScore();
                    startOffset = tokenGroup.getStartOffset();
                    endOffset = tokenGroup.getEndOffset();
                    if (score >0) {
                        String keyword = text.substring(startOffset, endOffset);
                        ExtInterval qtMatch = new ExtInterval(startOffset, endOffset);
                        qtMatch.setStr(keyword);
                        qtMatch.setCategory(category);
                        qtMatch.setDict_name(dictionary_name);
                        qtMatch.setDict_id(dictionary_id);
                        matchList.add(qtMatch);
                    }
                    tokenGroup.clear();

                    //check if current token marks the start of a new fragment
                    if(textFragmenter.isNewFragment())
                    {
                        fragmentScorer.startFragment(null);
                    }
                }

                tokenGroup.addToken(fragmentScorer.getTokenScore());
            }

            if(tokenGroup.getNumTokens() >0)
            {
                float score = tokenGroup.getTotalScore();
                if (score > 0) {
                    //flush the accumulated text (same code as in above loop)
                    startOffset = tokenGroup.getStartOffset();
                    endOffset = tokenGroup.getEndOffset();
                    String keyword = text.substring(startOffset, endOffset);
                    ExtInterval qtMatch = new ExtInterval(startOffset, endOffset);
                    qtMatch.setStr(keyword);
                    qtMatch.setCategory(category);
                    qtMatch.setDict_name(dictionary_name);
                    qtMatch.setDict_id(dictionary_id);
                    matchList.add(qtMatch);
                }
            }

            mergeContiguousFragments(text, matchList);

            return matchList.stream().filter(Objects::nonNull)
                    .collect(Collectors.toList());
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

    private static void mergeContiguousFragments(String text,
                                                 List<ExtInterval> matches)
    {
        if (matches.size() < 2) return;
        boolean mergingStillBeingDone;

        do
        {
            mergingStillBeingDone = false; //initialise loop control flag
            for (int i = 0; i < matches.size(); i++)
            {
                if (matches.get(i) == null) continue;

                //merge any contiguous blocks
                for (int x = 0; x < matches.size(); x++)
                {
                    if (matches.get(x) == null) continue;
                    if (matches.get(i) == null) break;

                    //if blocks are contiguous....
                    if (qtFollows(text, matches.get(x), matches.get(i)))
                    {
                        // match_x  match_i
                        matches.get(i).setStart(matches.get(x).getStart());
                        String new_keyword = text.substring(matches.get(i).getStart(), matches.get(i).getEnd());
                        matches.get(i).setStr(new_keyword);
                        matches.set(x, null);
                    }
                    else if ( qtFollows(text, matches.get(i), matches.get(x)))
                    {
                        // match_i match_x
                        matches.get(i).setEnd(matches.get(x).getEnd());
                        String new_keyword = text.substring(matches.get(i).getStart(), matches.get(i).getEnd());
                        matches.get(i).setStr(new_keyword);
                        matches.set(x, null);
                    }
                }
            }
        }
        while (mergingStillBeingDone);
    }
}
