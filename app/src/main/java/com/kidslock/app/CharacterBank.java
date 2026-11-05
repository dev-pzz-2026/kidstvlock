package com.kidslock.app;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * 题库管理类。
 * 每道题：展示一个汉字，从4个拼音选项中选出正确读音。
 * 支持从本地 assets JSON 文件加载题库，并保留离线兜底题库。
 */
public class CharacterBank {

    public static class CharEntry {
        public final String character;
        public final String pinyin;
        public final String type;
        public final String question;
        public final String word;
        public final int difficulty;
        public final String explanation;
        public final String questionText;
        public final List<String> options;
        public final int correctIndex;
        public final String category; // 识字, 多音字, 形近字, 古诗, 文言文, 阅读等
        public final String bankType; // normal, recitation

        CharEntry(String character, String pinyin) {
            this(character, pinyin, "char_to_pinyin", null, null, 1, null, null, null, -1, null, "normal");
        }

        CharEntry(String character, String pinyin, String type, String question, String word, int difficulty, String explanation) {
            this(character, pinyin, type, question, word, difficulty, explanation, question, null, -1, null, "normal");
        }

        CharEntry(String character, String pinyin, String type, String question, String word, int difficulty, String explanation, 
                  String questionText, List<String> options, int correctIndex, String category, String bankType) {
            this.character = character;
            this.pinyin = pinyin;
            this.type = type != null ? type : "char_to_pinyin";
            this.question = question;
            this.word = word;
            this.difficulty = difficulty;
            this.explanation = explanation;
            this.questionText = questionText != null ? questionText : (question != null ? question : character);
            this.options = options;
            this.correctIndex = correctIndex;
            this.category = category;
            this.bankType = bankType != null ? bankType : "normal";
        }
    }

    private static final CharEntry[] FALLBACK_BANK = {
        new CharEntry("誉", "yù"),
        new CharEntry("邦", "bāng"),
        new CharEntry("餐", "cān"),
        new CharEntry("沧", "cāng"),
        new CharEntry("巢", "cháo"),
        new CharEntry("撑", "chēng"),
        new CharEntry("炽", "chì"),
        new CharEntry("储", "chǔ"),
        new CharEntry("簇", "cù"),
        new CharEntry("岱", "dài"),
        new CharEntry("涤", "dí"),
        new CharEntry("淀", "diàn"),
        new CharEntry("鼎", "dǐng"),
        new CharEntry("锻", "duàn"),
        new CharEntry("恩", "ēn"),
        new CharEntry("伐", "fá"),
        new CharEntry("仿", "fǎng"),
        new CharEntry("绯", "fēi"),
        new CharEntry("蜂", "fēng"),
        new CharEntry("赋", "fù"),
        new CharEntry("钢", "gāng"),
        new CharEntry("隔", "gé"),
        new CharEntry("弧", "hú"),
        new CharEntry("焕", "huàn"),
        new CharEntry("惶", "huáng"),
        new CharEntry("慧", "huì"),
        new CharEntry("寂", "jì"),
        new CharEntry("驾", "jià"),
        new CharEntry("歼", "jiān"),
        new CharEntry("茧", "jiǎn"),
        new CharEntry("俭", "jiǎn"),
        new CharEntry("嚼", "jué"),
        new CharEntry("襟", "jīn"),
        new CharEntry("钧", "jūn"),
        new CharEntry("咖", "kā"),
        new CharEntry("恳", "kěn"),
        new CharEntry("叩", "kòu"),
        new CharEntry("窟", "kū"),
        new CharEntry("框", "kuàng"),
        new CharEntry("溃", "kuì"),
        new CharEntry("辣", "là"),
        new CharEntry("揽", "lǎn"),
        new CharEntry("烙", "lào"),
        new CharEntry("蕾", "lěi"),
        new CharEntry("凛", "lǐn"),
        new CharEntry("拎", "līn"),
        new CharEntry("隆", "lóng"),
        new CharEntry("搂", "lǒu"),
        new CharEntry("鹿", "lù"),
        new CharEntry("履", "lǚ"),
        new CharEntry("峦", "luán"),
        new CharEntry("掠", "lüè"),
        new CharEntry("脉", "mài"),
        new CharEntry("盲", "máng"),
        new CharEntry("贸", "mào"),
        new CharEntry("朦", "méng"),
        new CharEntry("弥", "mí"),
        new CharEntry("觅", "mì"),
        new CharEntry("蔑", "miè"),
        new CharEntry("铭", "míng"),
        new CharEntry("陌", "mò"),
        new CharEntry("钮", "niǔ"),
        new CharEntry("诺", "nuò"),
        new CharEntry("畔", "pàn"),
        new CharEntry("抛", "pāo"),
        new CharEntry("喷", "pēn"),
        new CharEntry("鹏", "péng"),
        new CharEntry("聘", "pìn"),
        new CharEntry("魄", "pò"),
        new CharEntry("剖", "pōu"),
        new CharEntry("祈", "qí"),
        new CharEntry("契", "qì"),
        new CharEntry("歉", "qiàn"),
        new CharEntry("腔", "qiāng"),
        new CharEntry("俏", "qiào"),
        new CharEntry("窃", "qiè"),
        new CharEntry("钦", "qīn"),
        new CharEntry("躯", "qū"),
        new CharEntry("趋", "qū"),
        new CharEntry("雀", "què"),
        new CharEntry("嚷", "rǎng"),
        new CharEntry("绕", "rào"),
        new CharEntry("刃", "rèn"),
        new CharEntry("戎", "róng"),
        new CharEntry("锐", "ruì"),
        new CharEntry("辱", "rǔ"),
        new CharEntry("嫂", "sǎo"),
        new CharEntry("莎", "shā"),
        new CharEntry("韶", "sháo"),
        new CharEntry("誓", "shì"),
        new CharEntry("舒", "shū"),
        new CharEntry("鼠", "shǔ"),
        new CharEntry("栓", "shuān"),
        new CharEntry("硕", "shuò"),
        new CharEntry("撕", "sī"),
        new CharEntry("肆", "sì"),
        new CharEntry("颂", "sòng"),
        new CharEntry("俗", "sú"),
        new CharEntry("遂", "suì"),
        new CharEntry("贪", "tān"),
        new CharEntry("毯", "tǎn"),
        new CharEntry("滔", "tāo"),
        new CharEntry("藤", "téng"),
        new CharEntry("恬", "tián"),
        new CharEntry("廷", "tíng"),
        new CharEntry("凸", "tū"),
        new CharEntry("颓", "tuí"),
        new CharEntry("褪", "tuì"),
        new CharEntry("婉", "wǎn"),
        new CharEntry("亡", "wáng"),
        new CharEntry("枉", "wǎng"),
        new CharEntry("巍", "wēi"),
        new CharEntry("蔚", "wèi"),
        new CharEntry("涡", "wō"),
        new CharEntry("污", "wū"),
        new CharEntry("熄", "xī"),
        new CharEntry("狭", "xiá"),
        new CharEntry("弦", "xián"),
        new CharEntry("馅", "xiàn"),
        new CharEntry("翔", "xiáng"),
        new CharEntry("卸", "xiè"),
        new CharEntry("械", "xiè"),
        new CharEntry("辛", "xīn"),
        new CharEntry("锈", "xiù"),
        new CharEntry("崖", "yá"),
        new CharEntry("宴", "yàn"),
        new CharEntry("秧", "yāng"),
        new CharEntry("妖", "yāo"),
        new CharEntry("钥", "yuè"),
        new CharEntry("蕴", "yùn"),
        new CharEntry("咋", "zǎ"),
        new CharEntry("榨", "zhà"),
        new CharEntry("瞻", "zhān"),
        new CharEntry("障", "zhàng"),
        new CharEntry("哲", "zhé"),
        new CharEntry("轴", "zhóu"),
        new CharEntry("铸", "zhù"),
        new CharEntry("拽", "zhuài"),
        new CharEntry("篆", "zhuàn"),
        new CharEntry("桩", "zhuāng"),
        new CharEntry("浊", "zhuó"),
        new CharEntry("滋", "zī"),
        new CharEntry("棕", "zōng"),
        new CharEntry("奏", "zòu"),
    };

    private CharEntry[] currentBank = FALLBACK_BANK;
    private final Random random = new Random();
    private final List<Integer> recentIndices = new ArrayList<>();
    private static final int RECENT_MEMORY = 10;

    /**
     * 从 assets 加载题库
     */
    public void loadFromAssets(Context context, String bankFileName) {
        if (bankFileName == null || bankFileName.isEmpty()) {
            currentBank = FALLBACK_BANK;
            return;
        }
        String json = readAssetFile(context, bankFileName);
        if (json == null || json.isEmpty()) {
            currentBank = FALLBACK_BANK;
            return;
        }
        try {
            JSONObject root = new JSONObject(json);
            // 检查是否是背诵篇目格式
            if (root.has("pieces")) {
                loadRecitationBank(root);
                return;
            }
            
            JSONArray arr = root.optJSONArray("questions");
            if (arr == null) {
                arr = root.optJSONArray("characters");
            }
            if (arr == null || arr.length() == 0) {
                currentBank = FALLBACK_BANK;
                return;
            }
            List<CharEntry> list = new ArrayList<>();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.optJSONObject(i);
                if (obj == null) continue;
                String type = obj.optString("type", "char_to_pinyin");
                String category = obj.optString("type", "");
                String questionText = obj.optString("question", "");
                int difficulty = obj.optInt("difficulty", 1);
                String explanation = obj.optString("explanation", null);
                
                // 解析选项
                JSONArray optionsArr = obj.optJSONArray("options");
                List<String> optionsList = null;
                int correctIndex = obj.optInt("correct_index", -1);
                if (optionsArr != null) {
                    optionsList = new ArrayList<>();
                    for (int j = 0; j < optionsArr.length(); j++) {
                        optionsList.add(optionsArr.optString(j, ""));
                    }
                }
                
                if ("math_calculation".equals(type)) {
                    String question = questionText;
                    String pinyin = obj.optString("pinyin", "");
                    if (question.isEmpty()) {
                        question = obj.optString("character", "");
                    }
                    list.add(new CharEntry(question, pinyin.isEmpty() ? question : pinyin, type, question, null, difficulty, explanation, questionText, optionsList, correctIndex, category, "normal"));
                } else if ("english_word".equals(type)) {
                    String word = obj.optString("word", "");
                    if (word.isEmpty()) {
                        word = questionText.isEmpty() ? obj.optString("character", "") : questionText;
                    }
                    String pinyin = obj.optString("pinyin", word);
                    list.add(new CharEntry(word, pinyin, type, null, word, difficulty, explanation, questionText, optionsList, correctIndex, category, "normal"));
                } else {
                    String character = obj.optString("character", "");
                    if (character.isEmpty()) {
                        character = questionText.isEmpty() ? obj.optString("pinyin", "") : questionText;
                    }
                    String pinyin = obj.optString("pinyin", "");
                    if (pinyin.isEmpty() && optionsList != null && correctIndex >= 0 && correctIndex < optionsList.size()) {
                        pinyin = optionsList.get(correctIndex);
                    }
                    if (!character.isEmpty() && pinyin != null && !pinyin.isEmpty()) {
                        list.add(new CharEntry(character, pinyin, type, questionText, null, difficulty, explanation, questionText, optionsList, correctIndex, category, "normal"));
                    }
                }
            }
            if (list.isEmpty()) {
                currentBank = FALLBACK_BANK;
            } else {
                currentBank = list.toArray(new CharEntry[0]);
            }
        } catch (JSONException e) {
            currentBank = FALLBACK_BANK;
        }
    }
    
    private void loadRecitationBank(JSONObject root) throws JSONException {
        JSONArray piecesArr = root.optJSONArray("pieces");
        if (piecesArr == null || piecesArr.length() == 0) {
            currentBank = FALLBACK_BANK;
            return;
        }
        List<CharEntry> list = new ArrayList<>();
        Random recRand = new Random();
        
        for (int i = 0; i < piecesArr.length(); i++) {
            JSONObject piece = piecesArr.optJSONObject(i);
            if (piece == null) continue;
            String title = piece.optString("title", "");
            String author = piece.optString("author", "");
            String firstLine = piece.optString("first_line", "");
            String keyLines = piece.optString("key_lines", "");
            String dynasty = piece.optString("dynasty", "");
            String note = piece.optString("note", "");
            
            // 题型1：填空题 - 补全诗句
            if (!keyLines.isEmpty()) {
                String[] lines = keyLines.split("[，。；？！\n]");
                for (String line : lines) {
                    line = line.trim();
                    if (line.length() >= 4) {
                        // 随机选择一个字作为填空
                        int fillIndex = recRand.nextInt(line.length());
                        String fillChar = String.valueOf(line.charAt(fillIndex));
                        StringBuilder questionBuilder = new StringBuilder();
                        for (int c = 0; c < line.length(); c++) {
                            if (c == fillIndex) {
                                questionBuilder.append("□");
                            } else {
                                questionBuilder.append(line.charAt(c));
                            }
                        }
                        String questionText = "《" + title + "》：" + questionBuilder.toString();
                        
                        // 生成干扰项
                        List<String> optionsList = new ArrayList<>();
                        optionsList.add(fillChar);
                        String[] distractors = {"之", "于", "以", "而", "为", "不", "有", "无", "大", "小"};
                        for (String d : distractors) {
                            if (!optionsList.contains(d) && optionsList.size() < 4) {
                                optionsList.add(d);
                            }
                        }
                        Collections.shuffle(optionsList, recRand);
                        int correctIdx = optionsList.indexOf(fillChar);
                        
                        list.add(new CharEntry(fillChar, fillChar, "recitation_fill", questionText, fillChar, 2, 
                            "《" + title + "》" + (author.isEmpty() ? "" : "（" + author + " " + dynasty + "）") + "：" + line, 
                            questionText, optionsList, correctIdx, "古诗", "recitation"));
                    }
                }
            }
            
            // 题型2：作者题
            if (!title.isEmpty() && !author.isEmpty() && !"《诗经》".equals(author)) {
                String questionText = "《" + title + "》的作者是谁？";
                List<String> optionsList = new ArrayList<>();
                optionsList.add(author);
                String[] authors = {"李白", "杜甫", "王维", "孟浩然", "白居易", "苏轼", "陆游", "辛弃疾", "王之涣", "贺知章"};
                for (String a : authors) {
                    if (!a.equals(author) && !optionsList.contains(a) && optionsList.size() < 4) {
                        optionsList.add(a);
                    }
                }
                Collections.shuffle(optionsList, recRand);
                int correctIdx = optionsList.indexOf(author);
                list.add(new CharEntry(author, author, "recitation_author", questionText, author, 1, note, 
                    questionText, optionsList, correctIdx, "古诗", "recitation"));
            }
            
            // 题型3：首句题
            if (!firstLine.isEmpty() && !title.isEmpty()) {
                String questionText = "《" + title + "》的第一句是什么？";
                List<String> optionsList = new ArrayList<>();
                optionsList.add(firstLine);
                // 添加其他诗的首句作为干扰
                if (piecesArr.length() > 1) {
                    for (int k = 0; k < 3 && piecesArr.length() > 0; k++) {
                        int randIdx = recRand.nextInt(piecesArr.length());
                        JSONObject otherPiece = piecesArr.optJSONObject(randIdx);
                        if (otherPiece != null) {
                            String otherFirst = otherPiece.optString("first_line", "");
                            if (!otherFirst.isEmpty() && !otherFirst.equals(firstLine) && !optionsList.contains(otherFirst)) {
                                optionsList.add(otherFirst);
                            }
                        }
                    }
                }
                while (optionsList.size() < 4) {
                    optionsList.add("（无）");
                }
                Collections.shuffle(optionsList, recRand);
                int correctIdx = optionsList.indexOf(firstLine);
                list.add(new CharEntry(firstLine, firstLine, "recitation_first", questionText, firstLine, 2, 
                    "《" + title + "》：" + keyLines, questionText, optionsList, correctIdx, "古诗", "recitation"));
            }
        }
        
        if (list.isEmpty()) {
            currentBank = FALLBACK_BANK;
        } else {
            currentBank = list.toArray(new CharEntry[0]);
        }
    }

    public void loadFromCurrentBank(PrefManager pref, Context context) {
        String bankFile = pref.getCurrentBankFile();
        if (bankFile != null && !bankFile.isEmpty()) {
            String json = readAssetFile(context, bankFile);
            if (json != null && !json.isEmpty()) {
                loadFromAssets(context, bankFile);
                if (currentBank != null && currentBank.length > 0 && currentBank != FALLBACK_BANK) {
                    return;
                }
            }
            // 用户明确指定了题库文件但不存在或为空（如错题库.json 尚未产生错题），
            // 保持当前 bankFile 不自动回退，等错题产生后会自动加载
            currentBank = FALLBACK_BANK;
            return;
        }

        String bankId = pref.getCurrentBankId();
        if (bankId == null || bankId.isEmpty()) {
            bankId = "grade5_chinese";
        }
        // 尝试加载多种文件名格式
        String[] candidates = {
                bankId + ".json",
                "bank_" + bankId + ".json",
                "五年级语文.json",
                "背诵篇目1-6年级75篇.json"
        };
        for (String name : candidates) {
            String json = readAssetFile(context, name);
            if (json != null && !json.isEmpty()) {
                loadFromAssets(context, name);
                if (currentBank != null && currentBank.length > 0 && currentBank != FALLBACK_BANK) {
                    // 保存到配置
                    pref.setCurrentBankFile(name);
                    pref.setCurrentBankId(bankId);
                    return;
                }
            }
        }
        // 如果都没找到，尝试随机加载一个可用的题库
        try {
            String[] files = context.getAssets().list("");
            if (files != null) {
                for (String f : files) {
                    if (f.endsWith(".json")) {
                        loadFromAssets(context, f);
                        if (currentBank != null && currentBank.length > 0 && currentBank != FALLBACK_BANK) {
                            pref.setCurrentBankFile(f);
                            return;
                        }
                    }
                }
            }
        } catch (Exception e) {
        }
        currentBank = FALLBACK_BANK;
    }

    /**
     * 添加题目到错题库
     */
    public void addToWrongBank(Context context, PrefManager pref, CharEntry entry) {
        try {
            String wrongFile = pref.getWrongBankFile();
            if (wrongFile == null || wrongFile.isEmpty()) return;
            java.io.File f = new java.io.File(context.getFilesDir(), wrongFile);
            org.json.JSONObject root;
            org.json.JSONArray questions;
            if (f.exists()) {
                java.io.FileInputStream fis = new java.io.FileInputStream(f);
                BufferedReader reader = new BufferedReader(new InputStreamReader(fis, "UTF-8"));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line).append('\n');
                reader.close();
                root = new org.json.JSONObject(sb.toString());
                questions = root.optJSONArray("questions");
                if (questions == null) questions = new org.json.JSONArray();
            } else {
                root = new org.json.JSONObject();
                root.put("bank_id", "wrong_bank");
                root.put("bank_name", "错题库");
                root.put("grade", 0);
                root.put("subject", "wrong");
                root.put("subject_name", "错题");
                root.put("version", 1);
                root.put("total", 0);
                questions = new org.json.JSONArray();
            }
            // 检查是否已存在
            for (int i = 0; i < questions.length(); i++) {
                org.json.JSONObject q = questions.optJSONObject(i);
                if (q != null && q.optString("question").equals(entry.questionText != null ? entry.questionText : entry.character)) {
                    return; // 已存在
                }
            }
            // 添加新题目
            org.json.JSONObject q = new org.json.JSONObject();
            q.put("id", entry.hashCode());
            q.put("question", entry.questionText != null && !entry.questionText.isEmpty() ? entry.questionText : entry.character);
            q.put("type", entry.bankType != null ? entry.bankType : "char_to_pinyin");
            q.put("difficulty", entry.difficulty);
            q.put("options", new org.json.JSONArray(entry.options));
            q.put("correct_index", entry.correctIndex);
            q.put("explanation", entry.explanation);
            q.put("character", entry.character);
            q.put("pinyin", entry.pinyin);
            questions.put(q);
            root.put("questions", questions);
            root.put("total", questions.length());
            java.io.FileWriter fw = new java.io.FileWriter(f);
            fw.write(root.toString(2));
            fw.close();
        } catch (Exception e) {
            android.util.Log.e("CharacterBank", "addToWrongBank failed", e);
        }
    }

    /**
     * 从错题库移除题目
     */
    public void removeFromWrongBank(Context context, PrefManager pref, CharEntry entry) {
        try {
            String wrongFile = pref.getWrongBankFile();
            if (wrongFile == null || wrongFile.isEmpty()) return;
            java.io.File f = new java.io.File(context.getFilesDir(), wrongFile);
            if (!f.exists()) return;
            java.io.FileInputStream fis = new java.io.FileInputStream(f);
            BufferedReader reader = new BufferedReader(new InputStreamReader(fis, "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line).append('\n');
            reader.close();
            org.json.JSONObject root = new org.json.JSONObject(sb.toString());
            org.json.JSONArray questions = root.optJSONArray("questions");
            if (questions == null) return;
            String qText = entry.questionText != null && !entry.questionText.isEmpty() ? entry.questionText : entry.character;
            org.json.JSONArray newQuestions = new org.json.JSONArray();
            for (int i = 0; i < questions.length(); i++) {
                org.json.JSONObject q = questions.optJSONObject(i);
                if (q != null && !q.optString("question").equals(qText)) {
                    newQuestions.put(q);
                }
            }
            root.put("questions", newQuestions);
            root.put("total", newQuestions.length());
            java.io.FileWriter fw = new java.io.FileWriter(f);
            fw.write(root.toString(2));
            fw.close();
        } catch (Exception e) {
            android.util.Log.e("CharacterBank", "removeFromWrongBank failed", e);
        }
    }
    private String readAssetFile(Context context, String fileName) {
        // Try internal files first (uploaded banks)
        try {
            java.io.File f = new java.io.File(context.getFilesDir(), fileName);
            if (f.exists()) {
                java.io.FileInputStream fis = new java.io.FileInputStream(f);
                BufferedReader reader = new BufferedReader(new InputStreamReader(fis, "UTF-8"));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append('\n');
                }
                reader.close();
                return sb.toString();
            }
        } catch (Exception e) {
        }
        try {
            InputStream is = context.getAssets().open(fileName);
            BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
            reader.close();
            return sb.toString();
        } catch (IOException e) {
            return null;
        }
    }

    public boolean isLoaded() {
        return currentBank != null && currentBank.length > 0;
    }

    public int getSize() {
        return currentBank == null ? 0 : currentBank.length;
    }

    /**
     * 随机取一道题（尽量不与最近用过的重复）。
     */
    public CharEntry getRandomCharacter() {
        if (currentBank == null || currentBank.length == 0) {
            currentBank = FALLBACK_BANK;
        }
        int idx;
        int attempts = 0;
        do {
            idx = random.nextInt(currentBank.length);
            attempts++;
        } while (recentIndices.contains(idx) && attempts < 20);
        recentIndices.add(idx);
        if (recentIndices.size() > RECENT_MEMORY) {
            recentIndices.remove(0);
        }
        return currentBank[idx];
    }

    /**
     * 取干扰项：3个与正确拼音不同的选项。
     */
    public List<String> getDistractors(String correctPinyin, int count) {
        Set<String> used = new HashSet<>();
        if (correctPinyin != null) {
            used.add(correctPinyin);
        }
        List<String> result = new ArrayList<>();
        int attempts = 0;
        if (currentBank == null || currentBank.length == 0) {
            currentBank = FALLBACK_BANK;
        }
        while (result.size() < count && attempts < 100) {
            int idx = random.nextInt(currentBank.length);
            String py = currentBank[idx].pinyin;
            if (py != null && !used.contains(py)) {
                used.add(py);
                result.add(py);
            }
            attempts++;
        }
        // 万一不够，补几个通用拼音
        String[] fallbackPinyin = {"bā", "pí", "mō", "fǔ", "dè", "tè", "nǐ", "lǐ", "gǔ", "kǒu"};
        int fbIdx = 0;
        while (result.size() < count && fbIdx < fallbackPinyin.length) {
            if (!used.contains(fallbackPinyin[fbIdx])) {
                used.add(fallbackPinyin[fbIdx]);
                result.add(fallbackPinyin[fbIdx]);
            }
            fbIdx++;
        }
        while (result.size() < count) {
            result.add("n/a");
        }
        return result;
    }

    /**
     * 生成一道完整题目：正确答案 + 3个干扰项，打乱顺序。
     * 返回4个选项，其中1个是正确答案。
     */
    public List<String> getOptions(String correctPinyin) {
        List<String> options = new ArrayList<>();
        if (correctPinyin != null) {
            options.add(correctPinyin);
        }
        options.addAll(getDistractors(correctPinyin, 3));
        Collections.shuffle(options, random);
        return options;
    }
}
