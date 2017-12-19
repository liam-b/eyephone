//package com.example.liam.yicteocr;
//
//import android.app.Activity;
//import android.os.Bundle;
//import android.view.textservice.SentenceSuggestionsInfo;
//import android.view.textservice.SpellCheckerSession;
//import android.view.textservice.SuggestionsInfo;
//
//public class SpellingsClient extends Activity {
//    private SpellCheckerSession spellSession;
//
//    @Override
//    protected void onCreate(Bundle savedInstanceState) {
//        super.onCreate(savedInstanceState);
////        setContentView(R.layout.activity_main);
//    }
//
//    public void onResume() {
//        super.onResume();
//    }
//
//    public void onPause() {
//        super.onPause();
//        if (spellSession != null) {
//            spellSession.close();
//        }
//    }
//
//    public void onGetSuggestions(final SuggestionsInfo[] arg0) {
//        final StringBuilder sb = new StringBuilder();
//
//        for (int i = 0; i < arg0.length; ++i) {
//            // Returned suggestions are contained in SuggestionsInfo
//            final int len = arg0[i].getSuggestionsCount();
//            sb.append('\n');
//
//            for (int j = 0; j < len; ++j) {
//                sb.append("," + arg0[i].getSuggestionAt(j));
//            }
//
//            sb.append("(" + len + ")");
//        }
//    }
//}