package com.e2ee.client;

import com.e2ee.crypto.EncryptedPayload;
import com.e2ee.crypto.EcdhUtil;
import com.e2ee.protocol.ChatMessage;
import com.e2ee.protocol.JsonUtil;
import com.e2ee.protocol.MessageType;
import com.e2ee.session.E2eeSession;
import com.e2ee.client.store.KeyVault;
import com.e2ee.client.history.HistoryVault;
import com.e2ee.client.history.HistoryEntry;

import java.net.Socket;
import java.io.*;
import java.nio.charset.StandardCharsets;

import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static com.e2ee.protocol.JsonUtil.toJson;

public class ClientMain {

    // ===== 내 로컬 키 =====
    private static KeyPair myKeyPair;
    private static PrivateKey myPrivateKey;
    private static PublicKey myPublicKey;

    // 현재 로그인 사용자 ID
    private static String myId;

    // 현재 대화 상대 ID
    private static String currentTarget = null;

    // 세션 저장: 상대ID → E2eeSession
    private static final Map<String, E2eeSession> sessions = new HashMap<>();

    private static final DateTimeFormatter TS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");


    public static void main(String[] args) throws Exception {

        Scanner sc = new Scanner(System.in);

        String host = "chat-server";
        int port = 9000;

        Socket socket = null;

        while (true) {
            try {
                System.out.println("[NET] 서버 연결 중...");
                socket = new Socket(host, port);
                break;
            } catch (Exception e) {
                Thread.sleep(1000); // 1초 후 재시도
            }
        }

        System.out.println("[NET] 서버 연결 완료!");

        PrintWriter writer = new PrintWriter(
                new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8),
                true
        );
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8)
        );


        // ---------------------------------------------------
        // 인증
        // ---------------------------------------------------
        System.out.println("1. 회원가입  2. 로그인");
        int menu = Integer.parseInt(sc.nextLine());

        System.out.print("아이디 입력: ");
        myId = sc.nextLine();

        System.out.print("비밀번호 입력: ");
        String pw = sc.nextLine();


        // ===== 키 로드/생성 =====
        System.out.println("[KEYVAULT] 키 로드 또는 생성 중...");
        myKeyPair = KeyVault.loadOrCreate(myId, pw);
        myPrivateKey = myKeyPair.getPrivate();
        myPublicKey = myKeyPair.getPublic();
        System.out.println("[KEYVAULT] 공개키/개인키 준비 완료.");

        // ===== 서버로 인증 요청 =====
        String authBody =
                "{\"id\":\"" + myId + "\"," +
                        "\"password\":\"" + pw + "\"," +
                        "\"publicKey\":\"" + EcdhUtil.encodePublicKey(myPublicKey) + "\"}";

        MessageType authType = (menu == 1)
                ? MessageType.AUTH_SIGNUP
                : MessageType.AUTH_LOGIN;

        ChatMessage authMsg = new ChatMessage(
                authType,
                myId,
                "server",
                authBody,
                LocalDateTime.now().format(TS)
        );
        writer.println(toJson(authMsg));

        ChatMessage authRes =
                JsonUtil.fromJson(reader.readLine(), ChatMessage.class);

        if (!authRes.getBody().contains("_OK")) {
            System.out.println("[FAIL] 인증 실패 → 종료");
            socket.close();
            return;
        }

        System.out.println("[INFO] 로그인/회원가입 성공!");
        System.out.println("[INFO] 서버에 공개키 등록 완료.");



        // ============================================================
        //  서버 → 클라이언트 수신 스레드
        // ============================================================
        Thread recv = new Thread(() -> {
            try {
                String line;

                while ((line = reader.readLine()) != null) {

                    ChatMessage msg = JsonUtil.fromJson(line, ChatMessage.class);

                    // ------------------- SYSTEM -------------------
                    if (msg.getType() == MessageType.SYSTEM) {
                        System.out.println("[SYSTEM] " + msg.getBody());
                        continue;
                    }

                    // =======================================================
                    //  KEY_RES (상대 공개키 도착)
                    // =======================================================
                    if (msg.getType() == MessageType.KEY_RES) {

                        String peerId = msg.getSender();  // 태그 없음

                        PublicKey otherPub = EcdhUtil.decodePublicKey(msg.getBody());

                        // 세션 생성
                        E2eeSession session = E2eeSession.create(myKeyPair, otherPub);
                        sessions.put(peerId, session);

                        System.out.println("[INFO] " + peerId + " 와(과) E2EE 세션 생성 완료!");

                        // ===== 히스토리 로드 =====
                        System.out.println("[HISTORY] 이전 기록 불러오는 중...");
                        var logs = HistoryVault.load(myId, peerId);

                        if (!logs.isEmpty()) {
                            System.out.println("------ 이전 대화 기록 ------");
                            for (var h : logs) {
                                System.out.println("[" + h.timestamp + "] "
                                        + h.sender + " → " + h.receiver + " : " + h.message);
                            }
                            System.out.println("----------------------------");
                        } else {
                            System.out.println("[HISTORY] 저장된 대화 없음.");
                        }
                        continue;
                    }

                    // =======================================================
                    //  CHAT 수신
                    // =======================================================
                    if (msg.getType() == MessageType.CHAT) {

                        String senderId = msg.getSender();
                        E2eeSession session = sessions.get(senderId);

                        // 세션 없으면 RAW 처리
                        if (session == null) {

                            System.out.println("[CHAT:RAW] " + senderId + " : " + msg.getBody());

                            // 저장
                            HistoryVault.append(
                                    myId,
                                    senderId,
                                    new HistoryEntry(senderId, myId, msg.getBody(), msg.getTimestamp())
                            );

                            continue;
                        }

                        // 세션 있음 = 복호화
                        EncryptedPayload payload =
                                EncryptedPayload.fromWireString(msg.getBody());

                        try {
                            String plain = session.decrypt(payload);
                            System.out.println("[CHAT] " + senderId + " : " + plain);

                            // 저장 (복호문)
                            HistoryVault.append(
                                    myId,
                                    senderId,
                                    new HistoryEntry(senderId, myId, plain, msg.getTimestamp())
                            );

                        } catch (Exception e) {
                            System.out.println("[DECRYPT-ERR] 복호화 실패");
                        }

                        continue;
                    }

                }

            } catch (Exception e) {
                System.out.println("[RECV] 서버 연결 종료");
            }
        });

        recv.setDaemon(true);
        recv.start();



        // ============================================================
        //  메인 입력 루프
        // ============================================================
        while (true) {

            System.out.print("> ");
            String input = sc.nextLine();

            if (input.equals("/quit"))
                break;

            // -------------------- /key 요청 --------------------
            if (input.startsWith("/key ")) {

                currentTarget = input.substring(5).trim();

                ChatMessage req = ChatMessage.keyRequest(
                        myId,
                        currentTarget,
                        myPublicKey,
                        LocalDateTime.now().format(TS)
                );

                writer.println(toJson(req));

                System.out.println("[KEY] 공개키 요청 보냄 → " + currentTarget);
                continue;
            }

            // -------------------- 메시지 전송 --------------------
            if (currentTarget == null) {
                System.out.println("[WARN] 먼저 /key 상대아이디 실행");
                continue;
            }

            String peerId = currentTarget;
            E2eeSession session = sessions.get(peerId);

            String tsNow = LocalDateTime.now().format(TS);

            ChatMessage msg;

            if (session == null) {
                // 평문 전송
                msg = new ChatMessage(
                        MessageType.CHAT,
                        myId,
                        peerId,
                        input,
                        tsNow
                );
                System.out.println("[WARN] 세션 없음 → 평문 전송");

                HistoryVault.append(
                        myId,
                        peerId,
                        new HistoryEntry(myId, peerId, input, tsNow)
                );

            } else {
                // 암호화 전송
                msg = ChatMessage.encryptedChat(
                        myId,
                        peerId,
                        input,
                        session,
                        tsNow
                );
                System.out.println("[INFO] 암호화 전송");

                HistoryVault.append(
                        myId,
                        peerId,
                        new HistoryEntry(myId, peerId, input, tsNow)
                );
            }

            writer.println(toJson(msg));
        }

        socket.close();
    }
}
