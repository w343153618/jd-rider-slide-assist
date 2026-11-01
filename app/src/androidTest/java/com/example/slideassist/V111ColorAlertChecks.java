package com.example.slideassist;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.View;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Local static marker rendering, using literal expectations independent of the production palette. */
public final class V111ColorAlertChecks {
    private static final String[] KEYS={"南邮","南航","南理工"},LABELS={"红","绿","蓝"};
    private static final int[] COLORS={0xffdc2626,0xff15803d,0xff2563eb};
    private static final int[] TEXT_COLORS={0xff7f1d1d,0xff14532d,0xff1e3a8a};
    public static void run(Instrumentation i,Bundle result){
        V110KeywordAlertChecks.Harness h=new V110KeywordAlertChecks.Harness(i);
        try{
            h.start();check(!h.enabled(),"local markers must start disabled");h.expect(false);
            Seeds seeds=Seeds.find();h.refresh(seeds.overlapThree);h.setEnabled(true);h.expect(true);
            // This regression precedes styling assertions so its RED identifies lost order numbers.
            h.manual(true);
            for(int subset:new int[]{1,2,4,3,5,6,7}){h.refresh(seeds.subsets[subset]);h.expect(true);expectColors(i,h);expectData(h,true);h.expectWhiteCard(0);}
            h.refresh(seeds.overlapTwo);expectColors(i,h);expectData(h,true);
            h.refresh(seeds.overlapThree);expectColors(i,h);expectData(h,true);expectSnapshot(i,h);
            h.refresh(seeds.severalOrders);expectColors(i,h);expectData(h,true);
            Activity speed=i.startActivitySync(new Intent(i.getTargetContext(),SpeedTestActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            i.waitForIdleSync();h.expect(false);h.expectPlainStyles();ui(i,speed::finish);h.settle();h.expect(true);expectColors(i,h);expectData(h,true);
            h.refresh(seeds.subsets[0]);h.expect(false);expectData(h,false);h.expectWhiteCard(0);
            h.refresh(seeds.subsets[7]);h.setEnabled(false);h.expect(false);h.expectPlainStyles();expectData(h,false);
            h.setEnabled(true);h.expect(true);expectData(h,true);
            ui(i,h.page::finish);i.waitForIdleSync();h.expect(false);
            result.putString("result","PASS static per-order school colors: red/green/blue singles,all batch subsets,same-order two/three-color segments,no full-screen overlay,white card background,exact labels/original-text colors/school-to-order numbers,repeated-school separate orders,defensive marker palette snapshot,manual completion retains order number,off/no-match/pause/return clear pixels and active spans; local synthetic fixtures only");
        }finally{ui(i,()->{if(h.page!=null&&!h.page.isFinishing())h.page.finish();if(h.main!=null&&!h.main.isFinishing())h.main.finish();});}
    }
    private static void expectColors(Instrumentation i,V110KeywordAlertChecks.Harness h){
        List<View> markers=h.markers();
        for(int n=0;n<h.batch().orders.size();n++){
            int subset=mask(h.batch().orders.get(n));List<Integer> expected=new ArrayList<>();
            for(int k=0;k<KEYS.length;k++)if((subset&(1<<k))!=0)expected.add(COLORS[k]);
            View marker=markers.get(n);
            if(expected.isEmpty()){check(Color.alpha(pixel(i,marker,.5f))==0,"unmatched order must stay unmarked");continue;}
            for(int segment=0;segment<expected.size();segment++)for(float within:new float[]{.15f,.5f,.85f})
                check(pixel(i,marker,(segment+within)/expected.size())==expected.get(segment),"each order's static marker must keep its own canonical opaque school colors");
        }
    }
    private static void expectData(V110KeywordAlertChecks.Harness h,boolean enabled){
        ui(h.i,()->{
            String summary=h.status.getText().toString();KeywordOrderGenerator.Batch batch=h.batch();
            for(int school=0;school<KEYS.length;school++){
                List<String> numbers=new ArrayList<>();for(int index=0;index<batch.orders.size();index++)if((mask(batch.orders.get(index))&(1<<school))!=0)numbers.add(String.valueOf(index+1));
                if(numbers.isEmpty())check(!summary.contains(KEYS[school]),"summary must not invent a school match");
                else{
                    String wanted=KEYS[school]+"（"+LABELS[school]+"）：第"+android.text.TextUtils.join("、",numbers)+"单";
                    check(Arrays.asList(summary.split("\\n")).contains(wanted),"summary must list exact matching order numbers: "+wanted);
                    if(enabled)expectSpans(h.status.getText(),KEYS[school],TEXT_COLORS[school]);
                }
            }
            for(int index=0;index<batch.orders.size();index++){
                View card=h.root.findViewWithTag("keyword_order_card_"+index);TextView badge=h.root.findViewWithTag("keyword_order_matches_"+index);
                check(card!=null&&badge!=null,"every order needs its own card and match labels");List<String> labels=new ArrayList<>();int subset=mask(batch.orders.get(index));
                for(int school=0;school<KEYS.length;school++)if((subset&(1<<school))!=0){labels.add(KEYS[school]+"（"+LABELS[school]+"）");if(enabled)expectSpans(badge.getText(),KEYS[school],TEXT_COLORS[school]);}
                String wanted=labels.isEmpty()?"本单未命中":android.text.TextUtils.join(" · ",labels);
                check(wanted.contentEquals(badge.getText()),"same-order multiple matches must expose all and only its own labels");
                for(int f=0;f<4;f++){
                    TextView value=h.root.findViewWithTag("keyword_order_text_"+(index*4+f));check(value!=null,"original order text missing");
                    if(enabled)for(int school=0;school<KEYS.length;school++)if(value.getText().toString().contains(KEYS[school]))expectSpans(value.getText(),KEYS[school],TEXT_COLORS[school]);
                }
            }
        });
        if(!enabled)h.expectPlainStyles();
    }
    private static void expectSnapshot(Instrumentation i,V110KeywordAlertChecks.Harness h){
        View marker=null;for(int n=0;n<h.batch().orders.size();n++)if(Integer.bitCount(mask(h.batch().orders.get(n)))==3){marker=h.markers().get(n);break;}
        check(marker!=null,"a three-school marker fixture is needed so the two-color update cannot take the equality shortcut");View selected=marker;
        List<Integer> caller=new ArrayList<>(Arrays.asList(COLORS[0],COLORS[2]));
        ui(i,()->V110KeywordAlertChecks.call(selected,"setColors",new Class<?>[]{List.class},caller));
        check(pixel(i,selected,.25f)==COLORS[0]&&pixel(i,selected,.75f)==COLORS[2],"requested local palette must paint its segments");
        ui(i,caller::clear);
        check(pixel(i,selected,.25f)==COLORS[0]&&pixel(i,selected,.75f)==COLORS[2],"later caller mutation must not change selected marker colors");
        h.refresh(h.batch().seed);
    }
    private static void expectSpans(CharSequence value,String key,int color){
        check(value instanceof Spanned,"matched original words need actual color spans");Spanned styled=(Spanned)value;String text=value.toString();
        for(int at=text.indexOf(key);at>=0;at=text.indexOf(key,at+key.length())){
            boolean found=false;for(ForegroundColorSpan span:styled.getSpans(at,at+key.length(),ForegroundColorSpan.class))found|=span.getForegroundColor()==color&&styled.getSpanStart(span)<=at&&styled.getSpanEnd(span)>=at+key.length();
            check(found,"every matched word must keep its readable school text color: "+key);
        }
    }
    private static final class Seeds{
        final long[] subsets={-1,-1,-1,-1,-1,-1,-1,-1};long overlapTwo=-1,overlapThree=-1,severalOrders=-1;
        static Seeds find(){Seeds s=new Seeds();for(long seed=0;seed<4096;seed++){
            KeywordOrderGenerator.Batch b=KeywordOrderGenerator.generate(seed,null);int subset=V110KeywordAlertChecks.mask(b);if(s.subsets[subset]<0)s.subsets[subset]=seed;
            for(KeywordOrderGenerator.Order order:b.orders){int n=Integer.bitCount(mask(order));if(n==2&&s.overlapTwo<0)s.overlapTwo=seed;if(n==3&&s.overlapThree<0)s.overlapThree=seed;}
            for(int k=0;k<KEYS.length;k++){int count=0;for(KeywordOrderGenerator.Order order:b.orders)if((mask(order)&(1<<k))!=0)count++;if(count>1&&s.severalOrders<0)s.severalOrders=seed;}
            boolean complete=s.overlapTwo>=0&&s.overlapThree>=0&&s.severalOrders>=0;for(long value:s.subsets)complete&=value>=0;if(complete)return s;
        }throw new AssertionError("generator must supply every school subset, same-order overlaps and repeated-school order numbers");}
    }
    private static int mask(KeywordOrderGenerator.Order order){int subset=0;for(int k=0;k<KEYS.length;k++)if(order.title.contains(KEYS[k])||order.address.contains(KEYS[k])||order.goods.contains(KEYS[k])||order.note.contains(KEYS[k]))subset|=1<<k;return subset;}
    private static int pixel(Instrumentation i,View view,float y){return V110KeywordAlertChecks.pixel(i,view,y);}
    private static void ui(Instrumentation i,Runnable task){V110KeywordAlertChecks.ui(i,task);}
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
