package com.e2ee.client.history;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.FileWriter;
import java.io.FileReader;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * ===========================
 *  HistoryVault (클라이언트 로컬)
 *  - 각 상대방과의 채팅 기록을 파일로 저장 & 로드
 *  - 파일명 규칙:
 *        <myId>__<peerId>.json
 * ===========================
 */
public class HistoryVault {

    private static final Gson gson = new Gson();

    // ~/.e2ee-chat/history/
    private static final File BASE_DIR =
            new File(System.getProperty("user.home"), ".e2ee-chat/history");

    static {
        if (!BASE_DIR.exists()) {
            BASE_DIR.mkdirs();
            System.out.println("[HistoryVault] 디렉토리 생성: " + BASE_DIR.getAbsolutePath());
        }
    }

    // 파일 경로 생성
    private static File fileOf(String myId, String peerId) {
        String filename = myId + "__" + peerId + ".json";
        return new File(BASE_DIR, filename);
    }

    /**
     * -------------------------
     * 1) 히스토리 로드 (없으면 빈 리스트)
     * -------------------------
     */
    public static synchronized List<HistoryEntry> load(String myId, String peerId) {
        try {
            File f = fileOf(myId, peerId);

            if (!f.exists()) {
                return new ArrayList<>();
            }

            FileReader reader = new FileReader(f);
            Type type = new TypeToken<List<HistoryEntry>>() {}.getType();
            List<HistoryEntry> list = gson.fromJson(reader, type);

            reader.close();

            if (list == null) return new ArrayList<>();
            return list;

        } catch (Exception e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    /**
     * -------------------------
     * 2) 히스토리에 한 줄 추가
     * -------------------------
     */
    public static synchronized void append(String myId, String peerId, HistoryEntry entry) {
        try {
            List<HistoryEntry> list = load(myId, peerId);
            list.add(entry);

            File f = fileOf(myId, peerId);

            FileWriter writer = new FileWriter(f);
            gson.toJson(list, writer);
            writer.close();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
