package com.liguo.listencode;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import com.iflytek.cloud.ErrorCode;
import com.iflytek.cloud.InitListener;
import com.iflytek.cloud.RecognizerListener;
import com.iflytek.cloud.RecognizerResult;
import com.iflytek.cloud.SpeechConstant;
import com.iflytek.cloud.SpeechError;
import com.iflytek.cloud.SpeechRecognizer;
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
import io.github.kbiakov.codeview.highlight.prettify.parser.Util;
import rx.Observable;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action1;
import rx.functions.Func1;
import rx.schedulers.Schedulers;

public class MainActivity extends AppCompatActivity {

    private CodeView codeView;
    private Toolbar toolbar;
    private String[] stringContext;
    private SpeechSynthesizer mTts;//语音合成器
    private SpeechRecognizer mAsr;//语音识别器
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
        initSpeechSynthesizer();
        initSpeechRecognizer();
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
            //打开系统文件管理器
            openSystemFile();
            return true;
        }else if(id == R.id.action_start_speek){
            //开始语音合成
            try{
                loopSpeek(0,true);
            }catch (Exception e){
                Toast.makeText(this, "播放语音出现异常，请稍后再试", Toast.LENGTH_SHORT).show();
            }finally {
                return true;
            }
        }else if(id == R.id.action_stop_speek){
            //停止语音合成
            mTts.stopSpeaking();
            return true;
        }else if(id == R.id.action_down_speeker){
            //下载发音人
            SpeechUtility.getUtility().openEngineSettings(SpeechConstant.ENG_TTS);
            return true;
        }else if(id == R.id.action_single_reading){
            //是否单行阅读
            isSinge = !isSinge;
            item.setTitle(getString(isSinge?R.string.action_single_reading_sta:R.string.action_single_reading_end));
            return true;
        }else if(id == R.id.action_speek_distinguish){
            //开启语音识别
        }

        return super.onOptionsItemSelected(item);
    }

    /**
     * 打开系统文件管理器
     */
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

    /**
     * 初始化语音合成器
     */
    private void initSpeechSynthesizer(){
        //1.创建SpeechSynthesizer 对象
        mTts= SpeechSynthesizer.createSynthesizer(this, mInitListener);

        // 2.合成参数设置 //设置引擎类型为本地
        mTts.setParameter(SpeechConstant.ENGINE_TYPE, SpeechConstant.TYPE_LOCAL);
    }

    /**
     * 初始化语音识别器
     */
    private void initSpeechRecognizer(){
        //1.创建SpeechRecognizer 对象，需传入初始化监听器
        mAsr= SpeechRecognizer.createRecognizer(this, new InitListener() {
            @Override
            public void onInit(int i) {
                if(i == ErrorCode.SUCCESS){
                    Log.e("onInit","成功");
                    //设置语法ID和 SUBJECT 为空，以免因之前有语法调用而设置了此参数；或直接清空所有参数，具体可参考 DEMO 的示例。
                    mAsr.setParameter( SpeechConstant.CLOUD_GRAMMAR, null );
                    mAsr.setParameter( SpeechConstant.SUBJECT, null );

                    //2.开始识别,设置引擎类型为本地
                    mAsr.setParameter(SpeechConstant.ENGINE_TYPE, SpeechConstant.TYPE_LOCAL);
                    // 设置引擎模式
                    mAsr.setParameter( SpeechConstant.ENGINE_MODE, SpeechConstant.MODE_PLUS );

                    //设置本地识别使用语法 id(此 id在语法文件中定义)、门限值
//        mAsr.setParameter(SpeechConstant.LOCAL_GRAMMAR, "call");
//        mAsr.setParameter(SpeechConstant.ASR_THRESHOLD, "30");
                    setRlListener();
                }else{
                    Log.e("onInit","失败"+i);
                }
            }
        });


    }

    /**
     * 获取选中的文件内容
     * @param requestCode
     * @param resultCode
     * @param resultData
     */
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

    /**
     * Uri转文件路径
     * @param context
     * @param uri
     * @return
     */
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

    /**
     * 是否循环阅读
     * @param index
     * @param loop
     */
    private void loopSpeek(int index,boolean loop){
        if(index<stringContext.length){
            //是否循环读这一段话
            mTts.startSpeaking(stringContext[index],lsl.setIndexAndLoop(index,loop));
        }
    }

    /**
     * 语音初始化监听器
     */
    private InitListener mInitListener = new InitListener() {
        public void onInit(int code) {
            if (code == ErrorCode.SUCCESS) {
                Log.e("初始化","成功");
            }
        }
    };
    /**
     * 语音合成监听器
     */
    private LoopSynthesizerListener lsl = new LoopSynthesizerListener();
    public class LoopSynthesizerListener implements SynthesizerListener{
        private int index;
        private boolean loop;
        public SynthesizerListener setIndexAndLoop(int indexs,boolean loops){
            index = indexs;
            loop = loops;
            return this;
        }

        //开始播放
        @Override
        public void onSpeakBegin() {
        }

        //缓冲进度回调
        // percent为缓冲进度0~100，beginPos为缓冲音频在文本中开始位置，
        // endPos表示缓冲音频在 文本中结束位置，info为附加信息。
        @Override
        public void onBufferProgress(int i, int i1, int i2, String s) {
        }

        //暂停播放
        @Override
        public void onSpeakPaused() {
        }

        //恢复播放回调接口
        @Override
        public void onSpeakResumed() {
        }

        //播放进度回调
        // percent为播放进度0~100,beginPos为播放音频在文本中开始位置，
        // endPos表示播放音频在文本中结束位置.
        @Override
        public void onSpeakProgress(int i, int i1, int i2) {
        }

        //会话结束回调接口，没有错误时，error为null
        @Override
        public void onCompleted(SpeechError speechError) {
            if(loop){
                index++;
                loopSpeek(index,loop);
            }
        }

        //会话事件回调接口
        @Override
        public void onEvent(int i, int i1, int i2, Bundle bundle) {
        }
    }

    /**
     * 语音识别监听器
     */
    private RecognizerListener rl = new RecognizerListener() {
        // 音量变化
        @Override
        public void onVolumeChanged(int i, byte[] bytes) {
//            Log.e("初始化","音量变化");
        }

        // 开始说话
        @Override
        public void onBeginOfSpeech() {
            Log.e("初始化","开始说话");
        }

        // 结束说话
        @Override
        public void onEndOfSpeech() {
            Log.e("初始化","结束说话");
        }

        // 返回结果
        @Override
        public void onResult(RecognizerResult recognizerResult, boolean b) {
//            Log.e("初始化","返回结果");
            Log.e("语音识别结果：",b+"\n"+recognizerResult.getResultString());
            setRlListener();
        }
        // 错误回调
        @Override
        public void onError(SpeechError speechError) {
//            Log.e("初始化","错误回调");
            setRlListener();
        }
        // 事件回调
        @Override
        public void onEvent(int i, int i1, int i2, Bundle bundle) {
//            Log.e("初始化","事件回调");
        }
    };

    private void setRlListener(){
        if(mAsr.isListening()){
           //true
            mAsr.stopListening();
        }
        int s = mAsr.startListening(rl);
        if(s == ErrorCode.SUCCESS){
            Log.e("mAsr code","成功");
        }else{
            Log.e("mAsr code","失败"+s);
        }
    }
}
