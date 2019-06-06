package com.quantxt.QTDocument;

import com.quantxt.doc.JADocumentInfo;
import com.quantxt.doc.QTDocument;
import com.quantxt.doc.helper.JADocumentHelper;
import com.quantxt.helper.types.ExtInterval;
import com.quantxt.helper.types.ExtIntervalSimple;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static com.quantxt.helper.types.QTField.QTFieldType.NOUN;

/**
 * Created by matin on 2/6/18.
 */
public class JADocumentInfoTest {

    private static final Logger logger = LoggerFactory.getLogger(JADocumentInfoTest.class);
    private static JADocumentHelper helper = new JADocumentHelper();

    @Test
    public void tag1() {
        String str = "朝日新聞デジタルに掲載の記事・写真の無断転載を禁じます。すべての内容は日本の著作権法並びに国際条約により保護";
        List<String> tokens = helper.tokenize(str);
        List<ExtIntervalSimple> intervals = helper.getNounAndVerbPhrases(str, tokens.toArray(new String[tokens.size()]));
        Assert.assertEquals(intervals.get(2).getType(), NOUN);
        Assert.assertEquals(intervals.get(2).toString(), "12:14");
    }

    @Test
    public void tag2() {
        String str = "Cognitive Toolkit（CNTK）は、マイクロソフトが主導して開発しているオープンソースのディープラーニングライブラリです。Speech認識で世界記録を達成したMS Researchの研究チームが開発し、MITやStanfordなどの様々な研究者と共同で改定しています。";
        List<String> tokens = helper.tokenize(str);
        List<ExtIntervalSimple> intervals = helper.getNounAndVerbPhrases(str, tokens.toArray(new String[tokens.size()]));

   //     List<Token> tagss = helper.getPosTagsJa(str);

//        for (int i =0; i<tagss.length; i++){
//            logger.info(tokens.get(i) + " | " + tagss[i]);
//        }
//        for (ExtInterval e : intervals){
//            logger.info(e.getType() + " / " + str.substring(e.getStart(), e.getEnd()));
//        }

        Assert.assertEquals(intervals.get(0).getType(), NOUN);
        Assert.assertEquals(intervals.get(0).toString(str), "マイクロソフト");
        Assert.assertEquals(intervals.get(0).toString(), "25:32");
    }

    @Test
    public void sentDetect1() {
        String str = "パチンコホールに再び“厳冬” ４年ぶり大型倒産　新規制導入で客離散におびえる業界 　"+
        "２０１７年（１～１２月）のパチンコホール倒産（負債１０００万円以上）は２９件（前年比１４１．６％増）で、" +
        "３年ぶりに前年を上回った。負債総額は２９１億９５００万円（同６７．６％増）で２年連続で増加、４年ぶりに負債１００億円超の大型倒産も発生した。" +
         "　出玉規制で射幸性を抑えた「パチスロ５号機問題」が落ち着いた２００９年以降、倒産は減少した。だが、パチンコ出玉の上限を今ま" +
                "での約３分の２に抑える改正風俗営業法施行規則が適用される今年２月を前に、再び増加に転じた。 　" +
                "減少する遊技客の奪い合いで中小ホールの経営は厳しさを増し、資金力のある大手ホールが新規出店や買収で攻勢をかけ" +
                "ている。ギャンブル依存症への対策を狙う２月の規制強化が、今後の客足にどう変化を及ぼすか注目される。" +
                 "　２０１７年のパチンコホール倒産は２９件（前年比１４１．６％増）で、前年の２．４倍増と急増した。倒産が前年を上回ったのは３年ぶり。" +
                 "５号機問題の影響で倒産が１４４件とピークに達した２００７年以降、２０１４年を除き前年を下回っていたが、" +
                 "２０１７年は大幅増に転じた。 　負債総額は２９１億９５００万円（同６７．６％増）と、２年連続で前年を上回った。" +
                 "４年ぶりに負債１００億円超の大型倒産が発生、負債総額を押し上げた。 なぜ「スーツにリュック」の人が増えたのか　" +
                "背景にある３つの要因 高須院長を挑発した男の愚かさ　ネットの「謝ったら死ぬ病」は身を滅ぼすだけ 「お荷物」" +
                "に退職金５０００万円　バブル入社組を３０年放置した企業のツケ サボり、陰口、逆ギレ…　上司になめてかかる「地雷女」" +
                "の見分け方 中途社員こそ要注意　新興企業でやってはいけない「社内でのふるまい」 今年こそはやめよう…　" +
                "脳のコンディションを崩す「ついやってしまう」ＮＧ行動８ なぜデキる人ほど仕事に「飽きない」のか　" +
                "ダメな自分を５分で変える方法 なぜ「すぐやる人」ほど仕事が遅れるのか　一生懸命やっても評価されない理由 " +
                "「なるほど、日本企業は低いはずだ」　働く人の幸福度をはかる“たった１２の質問” 常識知らずの若手、どう指導？　" +
                "礼儀がなってない「モンスター新人」の取扱説明書 「早期決断はビジネスの基本」なのに…　なぜ日本企業は撤退を決められないのか "+
                "１０万円で裕福な生活　年金でもリッチに暮らせる移住先ベスト６ 相撲協会は「世間知らずの集団」　" +
                "日馬富士問題で分かった無能ぶり 銀座のクラブママが教える　「一流の男」と「ダメになる男」の見分け方 入社直後は大手並みだったのに…　" +
                "優良だと錯覚する「キラキラ系ブラック企業」 Copyright (c) 2018 SANKEI DIGITAL INC. All rights reserved. ";

        JADocumentInfo doc = new JADocumentInfo(str, "");
        List<QTDocument> sents = doc.getChilds(true);

        logger.info("num " + sents.size());
    }
}
