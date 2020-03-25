package com.quantxt.doc.helper;

import com.quantxt.helper.types.ExtInterval;
import com.quantxt.helper.types.ExtIntervalSimple;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Created by matin on 5/29/18.
 */
public class JADocumentHelperTest {

    private static JADocumentHelper helper = new JADocumentHelper().init();
    private static Logger logger = LoggerFactory.getLogger(JADocumentHelperTest.class);


    @Test
    public void testEntityExtract1() {
        // GIVEN
        String str = "韓国の聯合ニュースによると、金副委員長は北京を経由し30日に米国に到着する見通し。2000年以降に米国を訪問した北朝鮮高官の中では最も高位となる。米朝首脳会談に向けた準備の一環の可能性がある。";
        List<String> parts = helper.tokenize(str);

        // WHEN
        List<ExtIntervalSimple> tagged = helper.getNounAndVerbPhrases(str, parts.toArray(new String[parts.size()]));

        // THEN
        Assert.assertEquals(str.substring(tagged.get(0).getStart(), tagged.get(0).getEnd()),
                "韓国の聯合ニュースに");
        Assert.assertEquals(str.substring(tagged.get(4).getStart(), tagged.get(4).getEnd()),
                "見通し");
        Assert.assertEquals(str.substring(tagged.get(6).getStart(), tagged.get(6).getEnd()),
                "北朝鮮高官の中で");
    }

    @Test
    public void testEntityExtract2() {
        // GIVEN
        String str = "日大アメリカンフットボール部の守備選手による悪質な反則問題で、関東学生連盟は２９日、臨時理事会を開いて関係者への処分を協議した。問題を調査した同学連の規律委員会は反則が内田正人前監督（６２）と井上奨前コーチ（２９）の指示によるものと認定し、両氏に対して処分の中で最も重く永久追放に相当する「除名」処分とした。";

        List<String> parts = helper.tokenize(str);

        // WHEN
        List<ExtIntervalSimple> tagged = helper.getNounAndVerbPhrases(str, parts.toArray(new String[parts.size()]));

        // THEN
        Assert.assertEquals(str.substring(tagged.get(1).getStart(), tagged.get(1).getEnd()),
                "反則問題で");
        Assert.assertEquals(str.substring(tagged.get(6).getStart(), tagged.get(6).getEnd()),
                "問題を調査");
    }

    @Test
    public void testEntityExtract3() {
        // GIVEN
        String str = "米朝首脳会談の開催に向け、米政府は北朝鮮に課す方針だった大規模な制裁措置の実施を見送った。";
        List<String> parts = helper.tokenize(str);

        // WHEN
        List<ExtIntervalSimple> tagged = helper.getNounAndVerbPhrases(str, parts.toArray(new String[parts.size()]));

        // THEN
        Assert.assertEquals(str.substring(tagged.get(0).getStart(), tagged.get(0).getEnd()),
                "米朝首脳会談の開催に");
        Assert.assertEquals(str.substring(tagged.get(1).getStart(), tagged.get(1).getEnd()),
                "米政府は北朝鮮に");
        Assert.assertEquals(str.substring(tagged.get(3).getStart(), tagged.get(3).getEnd()),
                "規模");
    }

    @Test
    public void testEntityExtract4() {
        // GIVEN
        String str = "外務省によると、海上自衛隊の哨戒機が今月１９日未明、東シナ海の公海上で２隻を確認。"; /* +
                "互いに横付けし、ホースを接続していたという。北朝鮮船による瀬取りは制裁逃れに当たり、" +
                "国連安保理が禁じている。";*/
        List<String> parts = helper.tokenize(str);

        // WHEN
        List<ExtIntervalSimple> tagged = helper.getNounAndVerbPhrases(str, parts.toArray(new String[parts.size()]));

        // THEN
        Assert.assertEquals(str.substring(tagged.get(0).getStart(), tagged.get(0).getEnd()),
                "外務省に");
        Assert.assertEquals(str.substring(tagged.get(2).getStart(), tagged.get(2).getEnd()),
                "東シナ海の公海上で２隻を確認");
    }

    @Test
    public void testEntityExtract5() {
        // GIVEN
        String str = "現在、日本のスマートスピーカー所有率は5%程度(*4)に過ぎませんが、米国で昨年よりAmazonが実施しており、公式ブログでは今年から日本でも実施していく予定とされる報酬プログラム(*5)の開始などにより、参入企業も増加していくと見込まれます。";

        List<String> parts = helper.tokenize(str);

        // WHEN
        List<ExtIntervalSimple> tagged = helper.getNounAndVerbPhrases(str, parts.toArray(new String[parts.size()]));

        // THEN
        Assert.assertEquals(str.substring(tagged.get(2).getStart(), tagged.get(2).getEnd()),
                "程度");
        Assert.assertEquals(str.substring(tagged.get(8).getStart(), tagged.get(8).getEnd()),
                "予定と");

    }

    @Test
    public void testEntityExtract6() {
        // GIVEN
        String str = "その後の取材で、斉藤容疑者は去年、知り合いの自動車修理店の男性に対し、「免許更新が近いけど、免許を返納しようと思っている」と話していたことがわかりました。しかし、斉藤容疑者はその後、高齢者講習を受け、今年３月に免許を更新していました。";
        List<String> parts = helper.tokenize(str);

        // WHEN
        List<ExtIntervalSimple> tagged = helper.getNounAndVerbPhrases(str, parts.toArray(new String[parts.size()]));

        // THEN
        Assert.assertEquals(str.substring(tagged.get(5).getStart(), tagged.get(5).getEnd()),
                "ことが");
        Assert.assertEquals(str.substring(tagged.get(6).getStart(), tagged.get(6).getEnd()),
                "斉藤容疑者はその後");
    }

    @Test
    public void testEntityExtract7() {
        // GIVEN
        String str = "同署などによると、３人は１２階建てと６階建ての２棟の間で倒れており、２人はその場で死亡が確認され、もう１人は病院へ搬送後に亡くなった。";
        List<String> parts = helper.tokenize(str);

        // WHEN
        List<ExtIntervalSimple> tagged = helper.getNounAndVerbPhrases(str, parts.toArray(new String[parts.size()]));

        // THEN
        Assert.assertEquals(str.substring(tagged.get(0).getStart(), tagged.get(0).getEnd()),
                "同署など");
        Assert.assertEquals(str.substring(tagged.get(3).getStart(), tagged.get(3).getEnd()),
                "場で死亡が確認");
        Assert.assertEquals(str.substring(tagged.get(4).getStart(), tagged.get(4).getEnd()),
                "１人は病院へ搬送後に");
    }

    @Test
    public void testEntityExtract8() {
        // GIVEN
        String str = "近くにある上田市立北小学校の児童の一部は、下校を見合わせて学校で待機しているということです。男は緑色のジャージを着ていて、眼鏡を掛けて40代から50代とみられています。";

        List<String> parts = helper.tokenize(str);

        // WHEN
        List<ExtIntervalSimple> tagged = helper.getNounAndVerbPhrases(str, parts.toArray(new String[parts.size()]));

        // THEN
        Assert.assertEquals(str.substring(tagged.get(2).getStart(), tagged.get(2).getEnd()),
                "下校を");
        Assert.assertEquals(str.substring(tagged.get(8).getStart(), tagged.get(8).getEnd()),
                "代と");
        Assert.assertEquals(str.substring(tagged.get(6).getStart(), tagged.get(6).getEnd()),
                "眼鏡を");
    }

    @Test
    public void testEntityExtract9() {
        // GIVEN
        String str = "夕刊フジでコラム「警戒せよ」を毎週木曜に連載する武蔵野学院大学特任教授の島村英紀教授は「今回の栄村の地震は日本で２番目の活断層『糸魚川－静岡線』と、東は長野から熊本、鹿児島まで伸びる日本最大の『中央構造線活断層』が交差する地域で発生したもので、１１年の東日本大震災の翌日に起きた地震と似ている。";
        List<String> parts = helper.tokenize(str);

        // WHEN
        List<ExtIntervalSimple> tagged = helper.getNounAndVerbPhrases(str, parts.toArray(new String[parts.size()]));

        // THEN
        Assert.assertEquals(str.substring(tagged.get(0).getStart(), tagged.get(0).getEnd()),
                "夕刊フジでコラム");
        Assert.assertEquals(str.substring(tagged.get(2).getStart(), tagged.get(2).getEnd()),
                "毎週木曜に連載");
        Assert.assertEquals(str.substring(tagged.get(6).getStart(), tagged.get(6).getEnd()),
                "静岡線");
    }

    @Test
    public void testEntityExtract10() {
        // GIVEN
        String str = "同社は、マガジン形式で模型を組み立てるパートワークの新商品として、「小惑星探査機はやぶさ2をつくる」を現在開発中。";
        List<String> parts = helper.tokenize(str);

        // WHEN
        List<ExtIntervalSimple> tagged = helper.getNounAndVerbPhrases(str, parts.toArray(new String[parts.size()]));

        // THEN
        Assert.assertEquals(str.substring(tagged.get(0).getStart(), tagged.get(0).getEnd()),
                "同社は");
        Assert.assertEquals(str.substring(tagged.get(3).getStart(), tagged.get(3).getEnd()),
                "商品として");
        Assert.assertEquals(str.substring(tagged.get(6).getStart(), tagged.get(6).getEnd()),
                "現在開発中");
    }

    @Test
    @Ignore
    public void testEntityExtract11() {
        // GIVEN
        String str = "トランプ氏は大統領令を活用する考えを示した。どのような行動を取るかは不明で、詳細を示さなかった。";
        List<String> parts = helper.tokenize(str);

        List<ExtIntervalSimple> tagged = helper.getNounAndVerbPhrases(str, parts.toArray(new String[parts.size()]));

        for (ExtIntervalSimple ei : tagged){
            logger.info(ei.getType() + " | " + str.substring(ei.getStart(), ei.getEnd()));
        }
        /*
        // THEN
        Assert.assertEquals(str.substring(tagged.get(0).getStart(), tagged.get(0).getEnd()),
                "例年");
        Assert.assertEquals(str.substring(tagged.get(3).getStart(), tagged.get(3).getEnd()),
                "組み立てる");
        Assert.assertEquals(str.substring(tagged.get(6).getStart(), tagged.get(6).getEnd()),
                "小惑星探査機");
                */
    }


    @Test
    @Ignore
    public void testSentenceDetect11() {
        // GIVEN
        String str = "1980年代から1990年代前半にかけてはBit Block Transferをサポートするチップと、描画を高速化するチップは別々のチップとして実装されていたが、チップ処理技術が進化するとともに安価になり、VGAカードをはじめとするグラフィックカード上に実装され、普及していった。1987年のVGA発表とともにリリースされたIBMの8514グラフィックスシステムは、2Dの基本的な描画機能をサポートした最初のPC用グラフィックアクセラレータとなった。AmigaはビデオハードウエアにBlitterを搭載した最初のコンシューマ向けコンピュータであった。";
        String [] parts = helper.getSentences(str);

    }
}
