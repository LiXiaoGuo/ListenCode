package com.liguo.listencode;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import com.iflytek.cloud.ErrorCode;
import com.iflytek.cloud.InitListener;
import com.iflytek.cloud.SpeechConstant;
import com.iflytek.cloud.SpeechError;
import com.iflytek.cloud.SpeechSynthesizer;
import com.iflytek.cloud.SpeechUtility;
import com.iflytek.cloud.SynthesizerListener;

import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import io.github.kbiakov.codeview.CodeView;
import io.github.kbiakov.codeview.OnCodeLineClickListener;
import io.github.kbiakov.codeview.classifier.CodeProcessor;
import io.github.kbiakov.codeview.highlight.ColorTheme;
import rx.Observable;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action1;
import rx.functions.Func1;
import rx.schedulers.Schedulers;

public class MainActivity extends AppCompatActivity {

    private CodeView codeView;
    private Toolbar toolbar;
    private String[] stringContext;
    private SpeechSynthesizer mTts;
    private boolean isSinge = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        CodeProcessor.init(this);
        SpeechUtility.createUtility(this, SpeechConstant.APPID +"=57d948dc"+","+SpeechConstant.ENGINE_MODE+"=plus");

        toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        codeView = (CodeView) findViewById(R.id.code_view);
        codeView.setShadowsVisible(true);
        codeView.setColorTheme(ColorTheme.SOLARIZED_LIGHT);
        codeView.setCodeListener(new OnCodeLineClickListener() {
            @Override
            public void onCodeLineClicked(int i, @NotNull String s) {
                if(isSinge){
                    loopSpeek(i,false);
                }
            }
        });


        useSpeechRecognizer();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_check_file) {
            openSystemFile();
            return true;
        }else if(id == R.id.action_start_speek){
            loopSpeek(0,true);
            return true;
        }else if(id == R.id.action_stop_speek){
            mTts.stopSpeaking();
            return true;
        }else if(id == R.id.action_down_speeker){
            SpeechUtility.getUtility().openEngineSettings(SpeechConstant.ENG_TTS);
            return true;
        }else if(id == R.id.action_single_reading){
            isSinge = !isSinge;
            item.setTitle(getString(isSinge?R.string.action_single_reading_sta:R.string.action_single_reading_end));
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    public void openSystemFile(){
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.putExtra("android.content.extra.SHOW_ADVANCED", true);
        try{
            startActivityForResult(intent, 42);
        }catch(Exception e){
            Toast.makeText(this, "没有正确打开文件管理器", Toast.LENGTH_LONG).show();
        }
    }

    private void useSpeechRecognizer(){
        //1.创建SpeechSynthesizer 对象
        mTts= SpeechSynthesizer.createSynthesizer(this, mInitListener);

        // 2.合成参数设置 //设置引擎类型为本地
        mTts.setParameter(SpeechConstant.ENGINE_TYPE, SpeechConstant.TYPE_LOCAL);
    }

    public void onActivityResult(int requestCode, int resultCode, Intent resultData) {
        if (resultCode == RESULT_OK) {
            stringContext = null;
            Observable.just(resultData)
                    .map(new Func1<Intent, String>() {
                        @Override
                        public String call(Intent intent) {
                            return getPath(MainActivity.this,intent.getData());
                        }
                    })
                    .subscribeOn(Schedulers.computation())
                    .unsubscribeOn(Schedulers.computation())
                    .observeOn(AndroidSchedulers.mainThread())
                    .doOnNext(new Action1<String>() {
                        @Override
                        public void call(String s) {
                            int ind = s.lastIndexOf(".");
                            int ind1 = s.lastIndexOf("/");
                            if(ind>0){
                                codeView.highlightCode(s.substring(ind+1,s.length()));
                            }
                            if(ind1>0){
                                toolbar.setSubtitle(s.substring(ind1+1,s.length()));
                            }
                        }
                    })
                    .observeOn(Schedulers.computation())
                    .map(new Func1<String, String>() {
                        @Override
                        public String call(String s) {
                            try {
                                if(s != null){
                                    return getStringByInputStream(new FileInputStream(s));
                                }
                            } catch (FileNotFoundException e) {
                                e.printStackTrace();
                            }
                            return "";
                        }
                    })
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(new Action1<String>() {
                        @Override
                        public void call(String s) {
                            stringContext = s.split("\n");
                            codeView.setCodeContent(s);
                        }
                    });
        }
    }

    public static String getPath(Context context, Uri uri) {
        if ("content".equalsIgnoreCase(uri.getScheme())) {
            String[] projection = {"_data"};
            Cursor cursor = null;
            try {
                cursor = context.getContentResolver().query(uri, projection, null, null, null);
                int column_index  = cursor.getColumnIndexOrThrow("_data");
                if (cursor.moveToFirst()) {
                    return cursor.getString(column_index);
                }
            } catch (Exception e) {
                // Eat it
            }
        } else if ("file".equalsIgnoreCase(uri.getScheme())) {
            return uri.getPath();
        }

        return null;
    }

    /**
     * 读输入流
     *
     * @param is
     * @return
     */
    public static String getStringByInputStream(InputStream is) {
        String content = null;
        try {
            if (is != null) {
                byte[] buffer = new byte[is.available()];
                ByteArrayOutputStream arrayOutputStream = new ByteArrayOutputStream();
                while (true) {
                    int readLength = is.read(buffer);
                    if (readLength == -1)
                        break;
                    arrayOutputStream.write(buffer, 0, readLength);
                }
                is.close();
                arrayOutputStream.close();
                content = new String(arrayOutputStream.toByteArray());

            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
            content = null;
        } finally {
            try {
                if (is != null)
                    is.close();
            } catch (IOException ioe) {
                ioe.printStackTrace();
            }
        }
        return content;
    }

    private void loopSpeek(int index,boolean loop){
        if(index<stringContext.length){
            mTts.startSpeaking(stringContext[index],lsl.setIndexAndLoop(index,loop));
        }
    }

    private InitListener mInitListener = new InitListener() {
        public void onInit(int code) {
            if (code == ErrorCode.SUCCESS) {}
        }
    };
    private LoopSynthesizerListener lsl = new LoopSynthesizerListener();
    public class LoopSynthesizerListener implements SynthesizerListener{
        private int index;
        private boolean loop;
        public SynthesizerListener setIndexAndLoop(int indexs,boolean loops){
            index = indexs;
            loop = loops;
            return this;
        }

        @Override
        public void onSpeakBegin() {
        }

        @Override
        public void onBufferProgress(int i, int i1, int i2, String s) {
        }

        @Override
        public void onSpeakPaused() {
        }

        @Override
        public void onSpeakResumed() {
        }

        @Override
        public void onSpeakProgress(int i, int i1, int i2) {
        }

        @Override
        public void onCompleted(SpeechError speechError) {
            if(loop){
                index++;
                loopSpeek(index,loop);
            }
        }

        @Override
        public void onEvent(int i, int i1, int i2, Bundle bundle) {
        }
    }
}
