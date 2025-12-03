package com.e2ee.server.tcp;

import com.e2ee.server.protocol.AuthPayload;
import com.e2ee.server.protocol.ChatMessage;
import com.e2ee.server.protocol.MessageType;
import com.e2ee.server.store.UserStore;
import com.e2ee.server.store.HistoryStore;
import com.google.gson.Gson;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ChatTcpServer {

    private static final int PORT = 9000;
    private final Gson gson = new Gson();

    // ID → PrintWriter   (🔥 #0001 제거!)
    private final Map<String, PrintWriter> clientOutputs = new ConcurrentHashMap<>();

    // 파일 저장소
    private final UserStore userStore = new UserStore();
    private final HistoryStore historyStore = new HistoryStore();

    @PostConstruct
    public void start() {
        Thread t = new Thread(() -> {
            try (ServerSocket serverSocket = new ServerSocket(PORT)) {
                System.out.println("[TCP] ChatServer started on port " + PORT);

                while (true) {
                    Socket client = serverSocket.accept();
                    System.out.println("[TCP] 클라이언트 접속: " + client);

                    new Thread(() -> handleClient(client),
                            "client-" + client.getPort()).start();
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        t.setDaemon(true);
        t.start();
    }



    private void handleClient(Socket client) {
        System.out.println("[CLIENT] 핸들러 시작");

        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8));
             PrintWriter out = new PrintWriter(
                     new OutputStreamWriter(client.getOutputStream(), StandardCharsets.UTF_8),
                     true)) {

            String line;
            while ((line = br.readLine()) != null) {

                ChatMessage msg = gson.fromJson(line, ChatMessage.class);
                System.out.println("[서버 RAW] " + line);

                // sender는 이제 "id" 그대로!
                clientOutputs.put(msg.getSender(), out);

                handleMessage(msg, out);
            }

        } catch (Exception e) {
            System.out.println("[CLIENT] 연결 종료: " + client);
        }
    }



    // ============================================================
    //                        회원가입
    // ============================================================
    private void handleSignup(ChatMessage msg, PrintWriter out) {

        AuthPayload p = gson.fromJson(msg.getBody(), AuthPayload.class);

        String id = p.getId();
        String pw = p.getPassword();
        String publicKey = p.getPublicKey();

        if (id == null || pw == null || publicKey == null) {
            ChatMessage res = new ChatMessage(
                    MessageType.AUTH_RESULT,
                    "server",
                    msg.getSender(),
                    "SIGNUP_FAIL:BAD_PAYLOAD",
                    msg.getTimestamp()
            );
            out.println(gson.toJson(res));
            return;
        }

        if (userStore.exists(id)) {
            ChatMessage res = new ChatMessage(
                    MessageType.AUTH_RESULT,
                    "server",
                    msg.getSender(),
                    "SIGNUP_FAIL:ID_EXISTS",
                    msg.getTimestamp()
            );
            out.println(gson.toJson(res));
            return;
        }

        userStore.addUser(id, pw, publicKey);

        ChatMessage res = new ChatMessage(
                MessageType.AUTH_RESULT,
                "server",
                msg.getSender(),
                "SIGNUP_OK",
                msg.getTimestamp()
        );
        out.println(gson.toJson(res));
    }



    // ============================================================
    //                        로그인
    // ============================================================
    private void handleLogin(ChatMessage msg, PrintWriter out) {

        AuthPayload p = gson.fromJson(msg.getBody(), AuthPayload.class);

        String id = p.getId();
        String pw = p.getPassword();

        if (!userStore.exists(id)) {
            ChatMessage res = new ChatMessage(
                    MessageType.AUTH_RESULT,
                    "server",
                    msg.getSender(),
                    "LOGIN_FAIL:ID_NOT_FOUND",
                    msg.getTimestamp()
            );
            out.println(gson.toJson(res));
            return;
        }

        if (!userStore.checkPassword(id, pw)) {
            ChatMessage res = new ChatMessage(
                    MessageType.AUTH_RESULT,
                    "server",
                    msg.getSender(),
                    "LOGIN_FAIL:BAD_PASSWORD",
                    msg.getTimestamp()
            );
            out.println(gson.toJson(res));
            return;
        }

        ChatMessage res = new ChatMessage(
                MessageType.AUTH_RESULT,
                "server",
                msg.getSender(),
                "LOGIN_OK",
                msg.getTimestamp()
        );

        out.println(gson.toJson(res));
    }



    // ============================================================
    //                      KEY_REQ 처리
    // ============================================================
    private void handleKeyRequest(ChatMessage msg) {

        String requesterId = msg.getSender();  // ex) "grag"
        String targetId = msg.getReceiver();   // ex) "kasl"

        // 서버 DB에서 대상 공개키 조회
        String targetPubKey = userStore.getPublicKey(targetId);

        if (targetPubKey == null) {
            PrintWriter out = clientOutputs.get(requesterId);
            if (out != null) {
                ChatMessage warn = new ChatMessage(
                        MessageType.SYSTEM,
                        "server",
                        requesterId,
                        "NO_SUCH_USER:" + targetId,
                        msg.getTimestamp()
                );
                out.println(gson.toJson(warn));
            }
            return;
        }

        // -----------------------------------------------------------------
        // 1) 요청자에게 KEY_RES 보내기 (상대방 공개키 전달)
        // -----------------------------------------------------------------
        ChatMessage resToRequester = new ChatMessage(
                MessageType.KEY_RES,
                targetId,              // sender = 상대ID
                requesterId,           // receiver = 요청자ID
                targetPubKey,
                msg.getTimestamp()
        );

        PrintWriter outRequester = clientOutputs.get(requesterId);
        if (outRequester != null) {
            outRequester.println(gson.toJson(resToRequester));
        }

        System.out.println("[KEY] 서버가 공개키 전달: " +
                targetId + " → " + requesterId);



        // -----------------------------------------------------------------
        // 2) 대상에게 KEY_REQ 전달 (요청자의 공개키 전달)
        // -----------------------------------------------------------------
        String requesterPubKey = msg.getBody();  // 요청자 공개키 그대로 전달

        ChatMessage reqToTarget = new ChatMessage(
                MessageType.KEY_REQ,
                requesterId,         // sender = 요청자ID
                targetId,            // receiver = 대상ID
                requesterPubKey,
                msg.getTimestamp()
        );

        PrintWriter outTarget = clientOutputs.get(targetId);
        if (outTarget != null) {
            outTarget.println(gson.toJson(reqToTarget));
        }

        System.out.println("[KEY] 요청자 공개키 전달: " +
                requesterId + " → " + targetId);
    }



    // ============================================================
    //                         CHAT
    // ============================================================
    private void handleChat(ChatMessage msg, PrintWriter out) {

        System.out.println("[서버][CHAT] "
                + msg.getSender() + " -> " + msg.getReceiver()
                + " : " + msg.getBody());

        historyStore.add(msg);

        String json = gson.toJson(msg);

        // 전체방
        if ("ALL".equalsIgnoreCase(msg.getReceiver())) {
            for (PrintWriter w : clientOutputs.values()) w.println(json);
            return;
        }

        // 1:1 메시지
        String receiverId = msg.getReceiver();
        PrintWriter targetOut = clientOutputs.get(receiverId);

        if (targetOut != null) {
            targetOut.println(json);
        } else {
            ChatMessage warn = new ChatMessage(
                    MessageType.SYSTEM,
                    "server",
                    msg.getSender(),
                    "TARGET_OFFLINE:" + receiverId,
                    msg.getTimestamp()
            );
            out.println(gson.toJson(warn));
        }
    }



    // ============================================================
    //                   메시지 분배
    // ============================================================
    private void handleMessage(ChatMessage msg, PrintWriter out) {

        switch (msg.getType()) {

            case AUTH_SIGNUP:
                handleSignup(msg, out);
                return;

            case AUTH_LOGIN:
                handleLogin(msg, out);
                return;

            case KEY_REQ:
                handleKeyRequest(msg);
                return;

            case CHAT:
                handleChat(msg, out);
                return;

            default:
                System.out.println("[서버] 알 수 없는 타입: " + msg.getType());
        }
    }
}
