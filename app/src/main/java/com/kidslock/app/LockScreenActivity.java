package com.kidslock.app;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;

public class LockScreenActivity extends AppCompatActivity {

    private PrefManager pref;
    private CharacterBank bank;

    private CharacterBank.CharEntry currentChar;
    private List<String> currentOptions;
    private String correctAnswer;
    private int correctCount = 0;
    private int requiredCount;
    private String currentBankFile;

    private View layoutMain;
    private TextView tvCharacter;
    private TextView tvQuestion;
    private TextView tvProgress;
    private TextView tvFeedback;
    private Button[] optionButtons;

    private final Handler handler = new Handler(Looper.getMainLooper());

    private String getAnswerMode() {
        return pref != null ? pref.getAnswerMode() : "cumulative";
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        }
        getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                        | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                        | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                        | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                        | WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON
        );
        try {
            setContentView(R.layout.activity_lock_screen);
        } catch (Exception e) {
            finish();
            return;
        }

        try {
            pref = new PrefManager(this);
            bank = new CharacterBank();
            currentBankFile = pref.getCurrentBankFile();
            bank.loadFromCurrentBank(pref, this);
            requiredCount = pref.getUnlockCount();
            correctCount = 0;
            hideSystemUI();
            initViews();
            loadNextQuestion();
        } catch (Exception e) {
            finish();
            return;
        }
    }

    private void initViews() {
        layoutMain = findViewById(R.id.layoutMain);
        tvCharacter = findViewById(R.id.tvCharacter);
        tvQuestion = findViewById(R.id.tvQuestion);
        tvProgress = findViewById(R.id.tvProgress);
        tvFeedback = findViewById(R.id.tvFeedback);

        optionButtons = new Button[]{
                findViewById(R.id.btnOption1),
                findViewById(R.id.btnOption2),
                findViewById(R.id.btnOption3),
                findViewById(R.id.btnOption4)
        };
        for (int i = 0; i < optionButtons.length; i++) {
            final int idx = i;
            optionButtons[i].setOnClickListener(v -> onOptionSelected(idx));
        }
    }

    private void loadNextQuestion() {
        tvFeedback.setVisibility(View.GONE);
        if (pref != null) {
            requiredCount = pref.getUnlockCount();
            String bankFile = pref.getCurrentBankFile();
            if (bankFile != null && !bankFile.equals(currentBankFile)) {
                currentBankFile = bankFile;
                bank = new CharacterBank();
                bank.loadFromCurrentBank(pref, this);
            }
        }
        if (bank == null || bank.getSize() == 0) {
            bank = new CharacterBank();
            if (pref != null) bank.loadFromCurrentBank(pref, this);
        }
        if (bank == null || bank.getSize() == 0) {
            finish();
            return;
        }
        currentChar = bank.getRandomCharacter();
        if (currentChar == null) {
            finish();
            return;
        }
        if (currentChar.options != null && !currentChar.options.isEmpty() && currentChar.correctIndex >= 0 && currentChar.correctIndex < currentChar.options.size()) {
            currentOptions = new ArrayList<>(currentChar.options);
            correctAnswer = currentChar.options.get(currentChar.correctIndex);
        } else {
            correctAnswer = currentChar.pinyin;
            currentOptions = bank.getOptions(correctAnswer);
        }

        if (tvQuestion != null) {
            if (currentChar.questionText != null && !currentChar.questionText.isEmpty() && !currentChar.questionText.equals(currentChar.character)) {
                tvQuestion.setText(currentChar.questionText);
                tvQuestion.setVisibility(View.VISIBLE);
            } else if (currentChar.bankType != null && currentChar.bankType.equals("recitation") && currentChar.question != null && !currentChar.question.isEmpty()) {
                tvQuestion.setText(currentChar.question);
                tvQuestion.setVisibility(View.VISIBLE);
            } else {
                tvQuestion.setVisibility(View.GONE);
            }
        }

        if (currentChar.bankType != null && currentChar.bankType.equals("recitation")) {
            if (currentChar.questionText != null && !currentChar.questionText.isEmpty()) {
                tvCharacter.setText(currentChar.questionText);
            } else {
                tvCharacter.setText(currentChar.character);
            }
        } else {
            tvCharacter.setText(currentChar.character);
        }

        for (int i = 0; i < optionButtons.length && i < currentOptions.size(); i++) {
            optionButtons[i].setText(currentOptions.get(i));
            optionButtons[i].setBackgroundResource(R.drawable.btn_option_bg);
            optionButtons[i].setEnabled(true);
            optionButtons[i].setVisibility(View.VISIBLE);
        }
        for (int i = currentOptions.size(); i < optionButtons.length; i++) {
            optionButtons[i].setVisibility(View.GONE);
        }

        updateProgress();
        tvFeedback.setVisibility(View.GONE);
    }

    private void onOptionSelected(int index) {
        for (Button b : optionButtons) {
            b.setEnabled(false);
        }

        String selected = currentOptions.get(index);
        if (selected.equals(correctAnswer)) {
            if (bank != null && pref != null && currentChar != null) {
                try {
                    bank.removeFromWrongBank(this, pref, currentChar);
                } catch (Exception e) {}
            }
            correctCount++;
            optionButtons[index].setBackgroundResource(R.drawable.btn_option_correct);
            tvFeedback.setText("答对了！");
            tvFeedback.setTextColor(getResources().getColor(R.color.correct_green));
            tvFeedback.setVisibility(View.VISIBLE);
            updateProgress();

            if (correctCount >= requiredCount) {
                handler.postDelayed(this::unlock, 600);
            } else {
                handler.postDelayed(this::loadNextQuestion, 1000);
            }
        } else {
            optionButtons[index].setBackgroundResource(R.drawable.btn_option_wrong);
            for (int i = 0; i < currentOptions.size(); i++) {
                if (currentOptions.get(i).equals(correctAnswer)) {
                    optionButtons[i].setBackgroundResource(R.drawable.btn_option_correct);
                    break;
                }
            }
            if (bank != null && pref != null && currentChar != null) {
                try {
                    bank.addToWrongBank(this, pref, currentChar);
                } catch (Exception e) {}
            }
            String mode = getAnswerMode();
            if ("continuous".equals(mode)) {
                correctCount = 0;
                updateProgress();
            }
            StringBuilder fb = new StringBuilder();
            fb.append("答错了，正确答案：").append(correctAnswer);
            if (currentChar != null && currentChar.explanation != null && !currentChar.explanation.isEmpty()) {
                fb.append("\n解析：").append(currentChar.explanation);
            }
            fb.append("（10秒后继续）");
            tvFeedback.setText(fb.toString());
            tvFeedback.setTextColor(getResources().getColor(R.color.wrong_red));
            tvFeedback.setVisibility(View.VISIBLE);
            handler.postDelayed(this::loadNextQuestion, 10000);
        }
    }

    private void updateProgress() {
        String mode = getAnswerMode();
        if ("continuous".equals(mode)) {
            tvProgress.setText(String.format("连续答对 %d/%d 道题即可解锁", correctCount, requiredCount));
        } else {
            tvProgress.setText(String.format("已累计答对 %d/%d 道题即可解锁", correctCount, requiredCount));
        }
    }

    private void unlock() {
        pref.setLocked(false);
        pref.setHomeAliasEnabled(this, false);
        int minutes = pref.getWatchLimitMinutes();
        pref.startTimer(minutes);
        // 启动计时服务，开始新计时
        try {
            Intent svc = new Intent(this, LockService.class);
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                startForegroundService(svc);
            } else {
                startService(svc);
            }
        } catch (Exception e) {
        }
        // 答题解锁后确保 Web 监听服务运行
        WebConfigService.ensureRunning(this);
        android.util.Log.i("LockScreenActivity", "答题解锁完成，检查并启动 Web 监听服务");
        finishAffinity();
    }

    private long getRelockDelayMs() {
        if (pref == null) {
            return 2000L;
        }
        int sec = pref.getRelockDelaySeconds();
        if (sec < 0) {
            sec = 2;
        }
        return sec * 1000L;
    }

    private final Runnable reLockRunnable = new Runnable() {
        @Override
        public void run() {
            if (pref.isLocked()) {
                Intent lockIntent = new Intent(LockScreenActivity.this, LockScreenActivity.class);
                lockIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(lockIntent);
            }
        }
    };

    @Override
    public void onBackPressed() {
        // 锁定状态下禁止返回
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(reLockRunnable);
        if (pref != null && pref.isLocked()) {
            try {
                Intent lockIntent = new Intent(this, LockScreenActivity.class);
                lockIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(lockIntent);
            } catch (Exception e) {
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.removeCallbacks(reLockRunnable);
        hideSystemUI();
        if (pref == null || !pref.isLocked()) {
            finish();
            return;
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            hideSystemUI();
        }
    }

    @Override
    protected void onUserLeaveHint() {
        super.onUserLeaveHint();
    }

    private void hideSystemUI() {
        View decorView = getWindow().getDecorView();
        int uiOptions = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
        decorView.setSystemUiVisibility(uiOptions);
    }
}
