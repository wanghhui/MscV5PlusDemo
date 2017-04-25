package com.iflytek.mscv5plusdemo;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.os.Environment;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.iflytek.cloud.ErrorCode;
import com.iflytek.cloud.InitListener;
import com.iflytek.cloud.RecognizerListener;
import com.iflytek.cloud.RecognizerResult;
import com.iflytek.cloud.SpeechConstant;
import com.iflytek.cloud.SpeechError;
import com.iflytek.cloud.SpeechRecognizer;
import com.iflytek.cloud.SpeechSynthesizer;
import com.iflytek.cloud.SpeechUnderstander;
import com.iflytek.cloud.SpeechUnderstanderListener;
import com.iflytek.cloud.SynthesizerListener;
import com.iflytek.cloud.TextUnderstander;
import com.iflytek.cloud.TextUnderstanderListener;
import com.iflytek.cloud.UnderstanderResult;
import com.iflytek.cloud.ui.RecognizerDialog;
import com.iflytek.cloud.ui.RecognizerDialogListener;
import com.iflytek.cloud.util.ResourceUtil;
import com.iflytek.speech.setting.IatSettings;
import com.iflytek.speech.setting.UnderstanderSettings;
import com.iflytek.speech.util.DatabaseHelper;
import com.iflytek.speech.util.JsonParser;

import org.ansj.app.keyword.KeyWordComputer;
import org.ansj.app.keyword.Keyword;
import org.ansj.splitWord.analysis.NlpAnalysis;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;

public class UnderstanderDemo extends Activity implements OnClickListener {
    private static String TAG = UnderstanderDemo.class.getSimpleName();
    // 语义理解对象（语音到语义）。
    //private SpeechUnderstander mSpeechUnderstander;
    // 语义理解对象（文本到语义）。
    private TextUnderstander mTextUnderstander;
    private Toast mToast;
    private EditText mFinalSearchResultText;

    //private TextView mQuestion;
    private SpeechSynthesizer mTts;
    public static String voicerCloud = "xiaoqi";
    private SharedPreferences mSharedPreferences;
    //缓冲进度
    private int mPercentForBuffering = 0;
    //播放进度
    private int mPercentForPlaying = 0;

    // 语音听写对象
    private SpeechRecognizer mIat;
    // 语音听写UI
    private RecognizerDialog mIatDialog;
    // 听写结果内容
    private EditText mQuestionText;

    // 用HashMap存储听写结果
    private HashMap<String, String> mIatResults = new LinkedHashMap<String, String>();


    @SuppressLint("ShowToast")
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.understander);
        initLayout();
        /**
         * 申请的appid时，我们为开发者开通了开放语义（语义理解）
         * 由于语义理解的场景繁多，需开发自己去开放语义平台：http://www.xfyun.cn/services/osp
         * 配置相应的语音场景，才能使用语义理解，否则文本理解将不能使用，语义理解将返回听写结果。
         */
        // 初始化对象
        //mSpeechUnderstander = SpeechUnderstander.createUnderstander(this, speechUnderstanderListener);
        mTextUnderstander = TextUnderstander.createTextUnderstander(this, textUnderstanderListener);

        mToast = Toast.makeText(this, "", Toast.LENGTH_SHORT);

        // 初始化合成对象
        mTts = SpeechSynthesizer.createSynthesizer(this, mTtsInitListener);

        // 初始化识别无UI识别对象
        // 使用SpeechRecognizer对象，可根据回调消息自定义界面；
        mIat = SpeechRecognizer.createRecognizer(this, mInitListener);

        // 初始化听写Dialog，如果只使用有UI听写功能，无需创建SpeechRecognizer
        // 使用UI听写功能，请根据sdk文件目录下的notice.txt,放置布局文件和图片资源
        mIatDialog = new RecognizerDialog(this, mInitListener);

        mSharedPreferences = getSharedPreferences(IatSettings.PREFER_NAME, Activity.MODE_PRIVATE);
        mToast = Toast.makeText(this, "", Toast.LENGTH_SHORT);
        mQuestionText = ((EditText) findViewById(R.id.question));
        // 设置参数
        setParam();
        setTTSParam();
    }

    /**
     * 初始化Layout。
     */
    private void initLayout() {
        //findViewById(R.id.text_understander).setOnClickListener(this);
        findViewById(R.id.start_understander).setOnClickListener(this);

        mFinalSearchResultText = (EditText) findViewById(R.id.understander_text);
        mSharedPreferences = getSharedPreferences(UnderstanderSettings.PREFER_NAME, Activity.MODE_PRIVATE);
    }

    /**
     * 初始化监听器（语音到语义）。
     */
    private InitListener speechUnderstanderListener = new InitListener() {
        @Override
        public void onInit(int code) {
            Log.d(TAG, "speechUnderstanderListener init() code = " + code);
            if (code != ErrorCode.SUCCESS) {
                showTip("初始化失败,错误码：" + code);
            }
        }
    };
    /**
     * 初始化监听。
     */
    private InitListener mTtsInitListener = new InitListener() {
        @Override
        public void onInit(int code) {
            Log.d(TAG, "InitListener init() code = " + code);
            if (code != ErrorCode.SUCCESS) {
                showTip("初始化失败,错误码：" + code);
            } else {
                // 初始化成功，之后可以调用startSpeaking方法
                // 注：有的开发者在oncde3@WSX
                // Create方法中创建完合成对象之后马上就调用startSpeaking进行合成，
                // 正确的做法是将onCreate中的startSpeaking调用移至这里
            }
        }
    };


    int ret = 0;// 函数调用返回值

    @Override
    public void onClick(View view) {
        if (null == mIat) {
            // 创建单例失败，与 21001 错误为同样原因，参考 http://bbs.xfyun.cn/forum.php?mod=viewthread&tid=9688
            this.showTip("创建对象失败，请确认 libmsc.so 放置正确，\n 且有调用 createUtility 进行初始化");
            return;
        }

        switch (view.getId()) {

            case R.id.start_understander:

                mQuestionText.setText(null);// 清空显示内容
                mIatResults.clear();
                if(mTts.isSpeaking())mTts.stopSpeaking();

                boolean isShowDialog = mSharedPreferences.getBoolean(getString(R.string.pref_key_iat_show), true);
                if (isShowDialog) {
                    // 显示听写对话框
                    mIatDialog.setListener(mRecognizerDialogListener);
                    mIatDialog.show();
                    showTip(getString(R.string.text_begin));
                } else {
                    // 不显示听写对话框
                    ret = mIat.startListening(mRecognizerListener);
                    if (ret != ErrorCode.SUCCESS) {
                        showTip("听写失败,错误码：" + ret);
                    } else {
                        showTip(getString(R.string.text_begin));
                    }
                }


                break;

            default:
                break;
        }
    }

    /**
     * 听写UI监听器
     */
    private RecognizerDialogListener mRecognizerDialogListener = new RecognizerDialogListener() {
        public void onResult(RecognizerResult results, boolean isLast) {
            Log.d(TAG, "recognizer result：" + results.getResultString());
            String text = JsonParser.parseIatResult(results.getResultString());
            mQuestionText.append(text);
            mQuestionText.setSelection(mQuestionText.length());

            //core search
            boolean isLocalSearchFulfilled = false;
            try {

                String question = mQuestionText.getText().toString();
                KeyWordComputer kwc = new KeyWordComputer(5);
                Collection<Keyword> keywordsCollection = kwc.computeArticleTfidf(question);
                StringBuffer matchingClause = new StringBuffer(100);
                Iterator<Keyword> kwi = keywordsCollection.iterator();
                while (kwi.hasNext()) {
                    Keyword kw = kwi.next();
                    matchingClause.append(kw.getName());
                    matchingClause.append(" ");
                }
                if (matchingClause.length() > 2)
                    isLocalSearchFulfilled = localSearch(matchingClause.toString());
            } catch (IOException e) {
                e.printStackTrace();
            }
            if (isLocalSearchFulfilled) {
                mTts.startSpeaking(mFinalSearchResultText.getText().toString(), mTtsListener);
                showTip("local search returned");
            } else {

                ret = mTextUnderstander.understandText(mQuestionText.getText().toString(), textListener);
                if (ret != 0) {
                    showTip("语义理解失败,错误码:" + ret);

                }
            }

        }

        /**
         * 识别回调错误.
         */
        public void onError(SpeechError error) {
            showTip(error.getPlainDescription(true));
        }

    };

    private boolean localSearch(String matchClause) throws IOException {
        DatabaseHelper myDbHelper = new DatabaseHelper(this);
        myDbHelper.createDataBase();
        myDbHelper.openDataBase();

        SQLiteDatabase mydatabase = myDbHelper.getWritableDatabase();
        //mydatabase.execSQL("CREATE TABLE \"android_metadata\" (\"locale\" TEXT DEFAULT 'en_US')");
        //mydatabase.execSQL("INSERT INTO \"android_metadata\" VALUES ('en_US')");
        Cursor FTSIDset = mydatabase.rawQuery("select FTSID from taxqafts where taxqafts match '" + matchClause +"'", null);
        String FTSID = "";
            if(FTSIDset.moveToFirst())FTSID = FTSIDset.getString(0);
            if (FTSID.length()>2) {
                Cursor contentSet = mydatabase.rawQuery("select bsznywgs from bszn12366 where BSZNID = '" + FTSID +"'",null);
                contentSet.moveToFirst();
                String bsznywgs = contentSet.getString(0);
                //max length that makes sense
                if(bsznywgs.length()>2000)bsznywgs=bsznywgs.substring(0,1999);
                mFinalSearchResultText.setText(bsznywgs);
                return true;
            }

        return false;
    }

    /**
     * 听写监听器。
     */
    private RecognizerListener mRecognizerListener = new RecognizerListener() {

        @Override
        public void onBeginOfSpeech() {
            // 此回调表示：sdk内部录音机已经准备好了，用户可以开始语音输入
            showTip("开始说话");
        }

        @Override
        public void onError(SpeechError error) {
            // Tips：
            // 错误码：10118(您没有说话)，可能是录音机权限被禁，需要提示用户打开应用的录音权限。
            showTip(error.getPlainDescription(true));
        }

        @Override
        public void onEndOfSpeech() {
            // 此回调表示：检测到了语音的尾端点，已经进入识别过程，不再接受语音输入
            showTip("结束说话");
        }

        @Override
        public void onResult(RecognizerResult results, boolean isLast) {
            String text = JsonParser.parseIatResult(results.getResultString());
            mQuestionText.append(text);
            mQuestionText.setSelection(mQuestionText.length());
            if (isLast) {
                //TODO 最后的结果
            }
        }

        @Override
        public void onVolumeChanged(int volume, byte[] data) {
            showTip("当前正在说话，音量大小：" + volume);
            Log.d(TAG, "返回音频数据：" + data.length);
        }

        @Override
        public void onEvent(int eventType, int arg1, int arg2, Bundle obj) {
            // 以下代码用于获取与云端的会话id，当业务出错时将会话id提供给技术支持人员，可用于查询会话日志，定位出错原因
            // 若使用本地能力，会话id为null
            //	if (SpeechEvent.EVENT_SESSION_ID == eventType) {
            //		String sid = obj.getString(SpeechEvent.KEY_EVENT_SESSION_ID);
            //		Log.d(TAG, "session id =" + sid);
            //	}
        }
    };

    /**
     * 初始化监听器（文本到语义）。
     */
    private InitListener textUnderstanderListener = new InitListener() {

        @Override
        public void onInit(int code) {
            Log.d(TAG, "textUnderstanderListener init() code = " + code);
            if (code != ErrorCode.SUCCESS) {
                showTip("初始化失败,错误码：" + code);
            }
        }
    };
    private TextUnderstanderListener textListener = new TextUnderstanderListener() {

        @Override
        public void onResult(final UnderstanderResult result) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (null != result) {
                        // 显示
                        Log.d(TAG, "understander result：" + result.getResultString());
                        String text = result.getResultString();
                        if (!TextUtils.isEmpty(text)) {
                            try {
                                JSONObject rst = new JSONObject(text);
                                JSONObject answer = rst.getJSONObject("answer");
                                mFinalSearchResultText.setText(answer.getString("text"));

                                mTts.startSpeaking(mFinalSearchResultText.getText().toString(), mTtsListener);
                            } catch (JSONException e) {
                                mFinalSearchResultText.setText("找不到答案:-(");
                                e.printStackTrace();
                            }
                        }
                    } else {
                        Log.d(TAG, "understander result:null");
                        showTip("识别结果不正确。");
                    }
                }
            });
        }

        @Override
        public void onError(SpeechError error) {
            // 文本语义不能使用回调错误码14002，请确认您下载sdk时是否勾选语义场景和私有语义的发布
            showTip("onError Code：" + error.getErrorCode());

        }
    };

    /**
     * 识别回调。
     * <p>
     * private SpeechUnderstanderListener speechUnderstandListener = new SpeechUnderstanderListener() {
     *
     * @Override public void onResult(final UnderstanderResult result) {
     * runOnUiThread(new Runnable() {
     * @Override public void run() {
     * if (null != result) {
     * // 显示
     * String text = result.getResultString();
     * if (!TextUtils.isEmpty(text)) {
     * try {
     * JSONObject rst = new JSONObject(text);
     * JSONObject answer = rst.getJSONObject("answer");
     * mQuestion.setText(rst.getString("text"));
     * mUnderstanderText.setText(answer.getString("text"));
     * setTTSParam();
     * int code = mTts.startSpeaking(mUnderstanderText.getText().toString(), mTtsListener);
     * <p>
     * } catch (JSONException e) {
     * mQuestion.setText("这问题我不懂 ;(");
     * e.printStackTrace();
     * }
     * }
     * } else {
     * mQuestion.setText("这问题我不懂 ;(");
     * showTip("识别结果不正确。");
     * }
     * }
     * });
     * }
     * @Override public void onVolumeChanged(int volume, byte[] data) {
     * showTip("当前正在说话，音量大小：" + volume);
     * Log.d(TAG, data.length+"");
     * }
     * @Override public void onEndOfSpeech() {
     * // 此回调表示：检测到了语音的尾端点，已经进入识别过程，不再接受语音输入
     * showTip("结束说话");
     * }
     * @Override public void onBeginOfSpeech() {
     * // 此回调表示：sdk内部录音机已经准备好了，用户可以开始语音输入
     * showTip("开始说话");
     * }
     * @Override public void onError(SpeechError error) {
     * showTip(error.getPlainDescription(true));
     * }
     * @Override public void onEvent(int eventType, int arg1, int arg2, Bundle obj) {
     * // 以下代码用于获取与云端的会话id，当业务出错时将会话id提供给技术支持人员，可用于查询会话日志，定位出错原因
     * //	if (SpeechEvent.EVENT_SESSION_ID == eventType) {
     * //		String sid = obj.getString(SpeechEvent.KEY_EVENT_SESSION_ID);
     * //		Log.d(TAG, "session id =" + sid);
     * //	}
     * }
     * };
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (null != mIat) {
            // 退出时释放连接
            mIat.cancel();
            mIat.destroy();
            if (mTextUnderstander.isUnderstanding())
                mTextUnderstander.cancel();
            mTextUnderstander.destroy();
            mTts.stopSpeaking();
            mTts.destroy();
        }

    }

    private void showTip(final String str) {
        runOnUiThread(new Runnable() {

            @Override
            public void run() {
                mToast.setText(str);
                mToast.show();
            }
        });
    }


    /**
     * 参数设置
     *
     * @return
     */
    private void setTTSParam() {
        // 清空参数
        mTts.setParameter(SpeechConstant.PARAMS, null);
        //设置合成

        mTts.setParameter(SpeechConstant.ENGINE_TYPE, SpeechConstant.TYPE_CLOUD);
        //设置发音人
        mTts.setParameter(SpeechConstant.VOICE_NAME, voicerCloud);

        //设置合成语速
        mTts.setParameter(SpeechConstant.SPEED, mSharedPreferences.getString("speed_preference", "50"));
        //设置合成音调
        mTts.setParameter(SpeechConstant.PITCH, mSharedPreferences.getString("pitch_preference", "50"));
        //设置合成音量
        mTts.setParameter(SpeechConstant.VOLUME, mSharedPreferences.getString("volume_preference", "50"));
        //设置播放器音频流类型
        mTts.setParameter(SpeechConstant.STREAM_TYPE, mSharedPreferences.getString("stream_preference", "3"));

        // 设置播放合成音频打断音乐播放，默认为true
        mTts.setParameter(SpeechConstant.KEY_REQUEST_FOCUS, "true");

        // 设置音频保存路径，保存音频格式支持pcm、wav，设置路径为sd卡请注意WRITE_EXTERNAL_STORAGE权限
        // 注：AUDIO_FORMAT参数语记需要更新版本才能生效
        mTts.setParameter(SpeechConstant.AUDIO_FORMAT, "wav");
        mTts.setParameter(SpeechConstant.TTS_AUDIO_PATH, Environment.getExternalStorageDirectory() + "/msc/tts.wav");
    }
    public void setParam(){
        // 清空参数
        mIat.setParameter(SpeechConstant.PARAMS, null);
        String lag = mSharedPreferences.getString("iat_language_preference", "mandarin");
        // 设置引擎
        mIat.setParameter(SpeechConstant.ENGINE_TYPE, SpeechConstant.TYPE_CLOUD);
        // 设置返回结果格式
        mIat.setParameter(SpeechConstant.RESULT_TYPE, "json");

        if (lag.equals("en_us")) {
            // 设置语言
            mIat.setParameter(SpeechConstant.LANGUAGE, "en_us");
        }else {
            // 设置语言
            mIat.setParameter(SpeechConstant.LANGUAGE, "zh_cn");
            // 设置语言区域
            mIat.setParameter(SpeechConstant.ACCENT,lag);
        }

        // 设置语音前端点:静音超时时间，即用户多长时间不说话则当做超时处理
        mIat.setParameter(SpeechConstant.VAD_BOS, mSharedPreferences.getString("iat_vadbos_preference", "4000"));

        // 设置语音后端点:后端点静音检测时间，即用户停止说话多长时间内即认为不再输入， 自动停止录音
        mIat.setParameter(SpeechConstant.VAD_EOS, mSharedPreferences.getString("iat_vadeos_preference", "1000"));

        // 设置标点符号,设置为"0"返回结果无标点,设置为"1"返回结果有标点
        mIat.setParameter(SpeechConstant.ASR_PTT, mSharedPreferences.getString("iat_punc_preference", "0"));

        // 设置音频保存路径，保存音频格式支持pcm、wav，设置路径为sd卡请注意WRITE_EXTERNAL_STORAGE权限
        // 注：AUDIO_FORMAT参数语记需要更新版本才能生效
        mIat.setParameter(SpeechConstant.AUDIO_FORMAT,"wav");
        mIat.setParameter(SpeechConstant.ASR_AUDIO_PATH, Environment.getExternalStorageDirectory()+"/msc/iat.wav");
    }
    /**
     * 初始化语音听写监听器。
     */
    private InitListener mInitListener = new InitListener() {

        @Override
        public void onInit(int code) {
            Log.d(TAG, "SpeechRecognizer init() code = " + code);
            if (code != ErrorCode.SUCCESS) {
                showTip("初始化失败，错误码：" + code);
            }
        }
    };
    private SynthesizerListener mTtsListener = new SynthesizerListener() {

        @Override
        public void onSpeakBegin() {
            showTip("开始播放");
        }

        @Override
        public void onSpeakPaused() {
            showTip("暂停播放");
        }

        @Override
        public void onSpeakResumed() {
            showTip("继续播放");
        }

        @Override
        public void onBufferProgress(int percent, int beginPos, int endPos,
                                     String info) {
            // 合成进度
            mPercentForBuffering = percent;
            showTip(String.format(getString(R.string.tts_toast_format),
                    mPercentForBuffering, mPercentForPlaying));
        }

        @Override
        public void onSpeakProgress(int percent, int beginPos, int endPos) {
            // 播放进度
            mPercentForPlaying = percent;
            showTip(String.format(getString(R.string.tts_toast_format),
                    mPercentForBuffering, mPercentForPlaying));
        }

        @Override
        public void onCompleted(SpeechError error) {
            if (error == null) {
                showTip("播放完成");
            } else if (error != null) {
                showTip(error.getPlainDescription(true));
            }
        }

        @Override
        public void onEvent(int eventType, int arg1, int arg2, Bundle obj) {
            // 以下代码用于获取与云端的会话id，当业务出错时将会话id提供给技术支持人员，可用于查询会话日志，定位出错原因
            // 若使用本地能力，会话id为null
            //	if (SpeechEvent.EVENT_SESSION_ID == eventType) {
            //		String sid = obj.getString(SpeechEvent.KEY_EVENT_SESSION_ID);
            //		Log.d(TAG, "session id =" + sid);
            //	}
        }
    };
}
